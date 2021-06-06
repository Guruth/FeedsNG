package sh.weller.feedsng.web.ui

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.Serializable
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.web.server.AuthorizeExchangeDsl
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers
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
        val username = request.principal().awaitSingle().name
        val user = userQueryService.getUserByUsername(username)
            ?: return@coroutineScope ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()

        val selectedFeedId = request.pathVariables()["feedId"].toFeedIdOrNull()

        val feeds = async {
            feedQueryService
                .getFeeds(user.userId)
                .map {
                    val isSelected = (it.feedId == selectedFeedId)
                    it.toFeedModel(isSelected)
                }
                .toList()
                .sortedBy { it.name }
        }

        val feedItems = async {
            feedQueryService
                .getFeedItems(user.userId, selectedFeedId)
                .map { it.toFeedItemModel() }
                .toList()
        }

        val model = mapOf(
            "feeds" to feeds.await(),
            "feedItems" to feedItems.await()
        )

        return@coroutineScope ServerResponse.ok()
            .render("sites/reader", model).awaitSingle()
    }

    private suspend fun addFeed(request: ServerRequest): ServerResponse {
        val username = request.principal().awaitSingle().name
        val user = userQueryService.getUserByUsername(username)
            ?: return ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()

        val feedUrlModel = request.awaitBody<AddFeedModel>()

        val result = feedControlService.addFeed(user.userId, feedUrlModel.feedUrl)
            .onFailure {
                return ServerResponse.badRequest().bodyValueAndAwait(it.reason)
            }
        return ServerResponse.created(URI("/reader/${result.id}")).buildAndAwait()
    }
}

private data class FeedModel(
    val feedId: Int,
    val name: String,
    var isSelected: Boolean
)

private fun Feed.toFeedModel(isSelected: Boolean) =
    FeedModel(
        feedId = feedId.id,
        name = feedData.name,
        isSelected
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