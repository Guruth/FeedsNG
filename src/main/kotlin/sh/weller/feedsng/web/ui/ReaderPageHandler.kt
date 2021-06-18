package sh.weller.feedsng.web.ui

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.web.server.AuthorizeExchangeDsl
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*
import sh.weller.feedsng.common.onFailure
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.user.api.provided.User
import sh.weller.feedsng.user.api.provided.UserQueryService
import sh.weller.feedsng.web.support.WebRequestHandler
import java.net.URI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Component
class MustacheHandler(
    private val feedQueryService: FeedQueryService,
    private val feedControlService: FeedControlService,
    private val userQueryService: UserQueryService
) : WebRequestHandler {

    override fun AuthorizeExchangeDsl.addAuthorization() {
        authorize("/reader/**", authenticated)
    }

    override fun getCSRFPathPatternMatcher(): PathPatternParserServerWebExchangeMatcher? =
        PathPatternParserServerWebExchangeMatcher("/login", HttpMethod.PUT)

    override fun getRouterFunction(): RouterFunction<ServerResponse> =
        coRouter {
            GET("/reader", ::getReaderPage)
            GET("/reader/{feedId}", ::getReaderPage)
            GET("/reader/{feedId}/{page}", ::getReaderPage)
//            PUT("/reader/feed", ::addFeed)
        }


    private suspend fun getReaderPage(request: ServerRequest): ServerResponse = coroutineScope {
        val username = request.getUsernameOrNull()
            ?: return@coroutineScope ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()
        val user = userQueryService.getUserByUsername(username)
            ?: return@coroutineScope ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()

        val selectedFeedId = request.pathVariables()["feedId"].toFeedIdOrNull()

        val selectedPage = request.pathVariables()["page"]?.toIntOrNull() ?: 0
        val pageSize = request.queryParamOrNull("pageSize")?.toIntOrNull() ?: 15

        val feedItemModels = async { getFeedItemModels(user, selectedFeedId, selectedPage, pageSize) }
        val feedModels = async { getFeedModels(user, selectedFeedId) }
        val paginationModel = async { getPaginationModel(user, selectedFeedId, selectedPage, pageSize) }

        val modelMap = request.getModelMap()
        modelMap["feeds"] = feedModels.await()
        modelMap["feedItems"] = feedItemModels.await()
        modelMap["pagination"] = paginationModel.await()

        return@coroutineScope ServerResponse.ok()
            .renderAndAwait("sites/reader/reader", modelMap)
    }

    private suspend fun getFeedItemModels(
        user: User,
        selectedFeedId: FeedId?,
        selectedPage: Int,
        pageSize: Int
    ): List<FeedItemModel> {
        return if (selectedFeedId != null) {
            feedQueryService
                .getFeedItems(user.userId, selectedFeedId, limit = pageSize, offset = selectedPage * pageSize)
                .toList()
                .map { it.toFeedItemModel() }
        } else {
            emptyList()
        }
    }

    private suspend fun getFeedModels(user: User, selectedFeedId: FeedId?): List<FeedModel> =
        coroutineScope {
            feedQueryService
                .getFeedIds(user.userId)
                .toList()
                .map {
                    async {
                        val feed = async { feedQueryService.getFeed(it) }
                        val feedItemCount = async {
                            feedQueryService
                                .countFeedItems(user.userId, it, FeedItemFilter.UNREAD)
                        }
                        feed.await()?.toFeedModel(it == selectedFeedId, feedItemCount.await())
                    }
                }
                .awaitAll()
                .filterNotNull()
                .sortedByDescending { it.numberOfUnreadItems }
        }

    private suspend fun getPaginationModel(
        user: User,
        selectedFeedId: FeedId?,
        selectedPage: Int,
        pageSize: Int
    ): PaginationModel? =
        if (selectedFeedId != null) {
            val numberOfItems = feedQueryService.countFeedItems(user.userId, selectedFeedId)
            val numberOfPages = ceil(numberOfItems.toDouble() / pageSize).toInt()

            val (lowerBound, upperBound) = if (numberOfPages > 5) {
                // Always have at least 5 items 0 - 5 or (size-5) - size
                val lowerBound = min(max(0, selectedPage - 2), (numberOfPages - 5))
                val upperBound = max(5, min(numberOfPages, selectedPage + 3))
                lowerBound to upperBound
            } else {
                0 to numberOfPages
            }


            PaginationModel(
                hasPrevious = selectedPage != 0,
                hasNext = selectedPage < numberOfPages,
                selectedFeed = selectedFeedId.id,
                pages = (lowerBound until upperBound).map {
                    PageLink(
                        label = (it + 1).toString(),
                        pageNumber = it,
                        isSelected = it == selectedPage
                    )
                }
            )
        } else {
            null
        }


    private suspend fun addFeed(request: ServerRequest): ServerResponse {
        val username = request.getUsernameOrNull()
            ?: return ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()
        val user = userQueryService.getUserByUsername(username)
            ?: return ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()

        val addFeedModel = request.awaitBodyOrNull<AddFeedModel>()
            ?: return ServerResponse.badRequest().buildAndAwait()

        val result = feedControlService.addFeed(user.userId, addFeedModel.feedUrl)
            .onFailure {
                return ServerResponse.badRequest().bodyValueAndAwait(it.reason)
            }
        return ServerResponse.created(URI("/reader/${result.id}")).buildAndAwait()
    }
}

private data class FeedModel(
    val feedId: Int,
    val name: String,
    val isSelected: Boolean,
    val numberOfUnreadItems: Int
)

private fun Feed.toFeedModel(isSelected: Boolean, numberOfUnreadItems: Int) =
    FeedModel(
        feedId = feedId.id,
        name = feedData.name,
        isSelected = isSelected,
        numberOfUnreadItems = numberOfUnreadItems
    )

private data class FeedItemModel(
    val feedItemId: Int,
    val title: String,
    val url: String,
    val html: String,
    val isRead: Boolean
)

private fun UserFeedItem.toFeedItemModel() = FeedItemModel(
    feedItemId = feedItem.feedItemId.id,
    title = feedItem.feedItemData.title,
    url = feedItem.feedItemData.url,
    html = feedItem.feedItemData.html,
    isRead = isRead
)

private data class PaginationModel(
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val selectedFeed: Int,
    val pages: List<PageLink>
)

private data class PageLink(
    val label: String,
    val pageNumber: Int,
    val isSelected: Boolean
)

@Serializable
private data class AddFeedModel(
    val feedUrl: String
)