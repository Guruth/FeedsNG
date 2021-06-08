package sh.weller.feedsng.web.ui

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
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
import sh.weller.feedsng.user.api.provided.UserQueryService
import sh.weller.feedsng.web.support.WebRequestHandler
import java.net.URI

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
//            PUT("/reader/feed", ::addFeed)
        }


    private suspend fun getReaderPage(request: ServerRequest): ServerResponse = coroutineScope {
        val username = request.getUsernameOrNull()
            ?: return@coroutineScope ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()
        val user = userQueryService.getUserByUsername(username)
            ?: return@coroutineScope ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()

        val selectedFeedId = request.pathVariables()["feedId"].toFeedIdOrNull()
        val feeds = async {
            feedQueryService
                .getFeeds(user.userId)
                .toList()
                .map {
                    async {
                        val numberOfUnreadItems = feedQueryService
                            .countFeedItems(user.userId, it.feedId, FeedItemFilter.UNREAD)
                        val isSelected = (it.feedId == selectedFeedId)
                        it.toFeedModel(isSelected, numberOfUnreadItems)
                    }
                }
                .awaitAll()
                .sortedByDescending { it.numberOfUnreadItems }
        }

        val feedItems = async {
            if (selectedFeedId != null) {
                feedQueryService
                    .getFeedItems(user.userId, selectedFeedId, limit = 10)
                    .map { it.toFeedItemModel() }
                    .toList()
            } else {
                emptyList()
            }
        }

        val modelMap = request.getModelMap()
        modelMap["feeds"] = feeds.await()
        modelMap["feedItems"] = feedItems.await()

        return@coroutineScope ServerResponse.ok()
            .renderAndAwait("sites/reader/reader", modelMap)
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
    val html: String
)

private fun UserFeedItem.toFeedItemModel() = FeedItemModel(
    feedItemId = feedItem.feedItemId.id,
    title = feedItem.feedItemData.title,
    url = feedItem.feedItemData.url,
    html = feedItem.feedItemData.html
)

@Serializable
private data class AddFeedModel(
    val feedUrl: String
)