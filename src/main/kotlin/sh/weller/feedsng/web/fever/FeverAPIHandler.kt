package sh.weller.feedsng.web.fever

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.config.web.server.AuthorizeExchangeDsl
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.stereotype.Controller
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.server.*
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.user.api.provided.UserId
import sh.weller.feedsng.user.api.provided.UserQueryService
import sh.weller.feedsng.web.support.WebRequestHandler
import java.time.Instant
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
@Controller
class FeverAPIHandler(
    private val feedControlService: FeedControlService,
    private val feedQueryService: FeedQueryService,
    private val userQueryService: UserQueryService
) : WebRequestHandler {

    override fun AuthorizeExchangeDsl.addAuthorization() {
        authorize("/api/fever.php", permitAll)
    }

    override fun getCSRFPathPatternMatcher(): PathPatternParserServerWebExchangeMatcher? = null

    override fun getRouterFunction(): RouterFunction<ServerResponse> =
        coRouter {
            path(feverAPIPath) {
                val response: ServerResponse
                val duration = measureTime {
                    response = feverHandler(it)
                }
                logger.debug("Request took ${duration.toDouble(DurationUnit.MILLISECONDS)} ms")
                return@path response
            }
        }

    private suspend fun feverHandler(request: ServerRequest): ServerResponse {
        val requestParameters = request.getFeverRequestParameters().toMutableMap()
        val apiKeyHash = requestParameters["api_key"]
            ?: return ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()
        val user = userQueryService.getUserByFeverAPIKey(apiKeyHash)
            ?: return ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()
        if (user.isDisabled()) {
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).buildAndAwait()
        }

        requestParameters.remove("api_key")
        logger.debug("Fever request parameters: $requestParameters. ")

        val responseBuilder = FeverResponse.Builder()

        val groups = feedQueryService.getGroups(user.userId).toList()
        val feeds = feedQueryService.getFeeds(user.userId).toList()

        responseBuilder.lastRefreshedOnTime(feeds.maxOfOrNull { it.feedData.lastUpdated })

        if (requestParameters.containsKey("groups") || requestParameters.containsKey("feeds")) {
            responseBuilder.feedGroupMappings(groups)

            if (requestParameters.containsKey("groups")) {
                responseBuilder.groups(groups)
            }
            if (requestParameters.containsKey("feeds")) {
                responseBuilder.feeds(feeds)
            }
        }

        if (requestParameters.containsKey("items")) {
            // TODO: Instead of fetching all feed items, query only the needed ones
            val feedIdsToFilter = mutableListOf<FeedId>()

            requestParameters.queryIntListOrNull("feed_ids")
                ?.map { it.toFeedId() }
                ?.also { feedIdsToFilter.addAll(it) }

            requestParameters.queryIntListOrNull("group_ids")
                ?.run {
                    return@run groups
                        .filter { group -> group.groupId.id in this }
                        .map { it.groupData.feeds }
                        .toList().flatten()
                }
                ?.also { feedIdsToFilter.addAll(it) }

            val feedItems = feedQueryService
                .getFeedItems(
                    userId = user.userId,
                    feedIdList = feedIdsToFilter.takeIf { it.isNotEmpty() }
                )
                .toList()
                .sortedBy { it.feedItem.feedItemId.id }

            val itemIdFilter = requestParameters.queryIntListOrNull("with_ids")
                ?.map { it.toFeedItemId() }
            val itemIdFiltered = if (itemIdFilter != null) {
                feedItems
                    .filter { it.feedItem.feedItemId in itemIdFilter }
            } else feedItems

            val sinceFilter = request.queryParamOrNull("since_id")?.toIntOrNull()
            val sinceFilteredItems = if (sinceFilter != null) {
                itemIdFiltered
                    .filter { it.feedItem.feedItemId.id > sinceFilter }
            } else itemIdFiltered

            val maxIdFilter = request.queryParamOrNull("max_id")?.toIntOrNull()
            val maxIdFilteredItems = if (maxIdFilter != null) {
                sinceFilteredItems.filter { it.feedItem.feedItemId.id <= maxIdFilter }
            } else sinceFilteredItems

            responseBuilder.items(maxIdFilteredItems)
        }

        if (requestParameters.contains("unread_item_ids")) {
            val unreadIds = getUnreadItemIds(user.userId)
            responseBuilder.unreadItemIds(unreadIds)
        }

        if (requestParameters.contains("saved_item_ids")) {
            val savedIds = getSavedItemIds(user.userId)
            responseBuilder.savedItemIds(savedIds)
        }

        if (requestParameters["unread_recently_read"] == "1") {
            feedQueryService
                .getFeedItemsIds(
                    user.userId,
                    filter = FeedItemFilter.READ,
                    since = Instant.now().minusSeconds(30)
                )
                .onEach {
                    feedControlService.updateFeedItem(user.userId, it, FeedUpdateAction.UNREAD)
                }
        }

        if (requestParameters.contains("mark")) {
            val action = requestParameters["as"]?.toUpdateAction()
            val id = requestParameters["id"]?.toIntOrNull()

            if (id != null && action != null) {
                val before = requestParameters["before"]?.toInstant()

                when (requestParameters["mark"]) {
                    "item" ->
                        feedControlService.updateFeedItem(
                            user.userId,
                            id.toFeedItemId(),
                            action
                        )

                    "feed" ->
                        feedControlService.updateFeed(
                            user.userId,
                            id.toFeedId(),
                            action,
                            before
                        )

                    "group" -> {
                        if (id == 0) {
                            // 0 is the group with all feeds
                            feeds.updateAllFeeds(user.userId, action, before)
                        } else {
                            feedControlService.updateGroup(
                                user.userId,
                                id.toGroupId(),
                                action,
                                before
                            )
                        }
                    }
                }
            }

            when (action) {
                FeedUpdateAction.READ, FeedUpdateAction.UNREAD -> {
                    val unreadIds = getUnreadItemIds(user.userId)
                    responseBuilder.unreadItemIds(unreadIds)
                }
                FeedUpdateAction.SAVE, FeedUpdateAction.UNSAVE -> {
                    val savedIds = getSavedItemIds(user.userId)
                    responseBuilder.savedItemIds(savedIds)
                }
            }
        }

        val response = responseBuilder.build()
        logger.trace("Fever Response: $response")

        return ServerResponse.ok().json().bodyValueAndAwait(response)
    }

    suspend fun getSavedItemIds(userId: UserId): List<FeedItemId> =
        feedQueryService
            .getFeedItems(
                userId = userId,
                feedIdList = null,
                filter = FeedItemFilter.SAVED
            )
            .map { it.feedItem.feedItemId }
            .toList()

    suspend fun getUnreadItemIds(userId: UserId): List<FeedItemId> =
        feedQueryService
            .getFeedItems(
                userId = userId,
                feedIdList = null,
                filter = FeedItemFilter.UNREAD
            )
            .map { it.feedItem.feedItemId }
            .toList()


    private suspend fun List<Feed>.updateAllFeeds(userId: UserId, action: FeedUpdateAction, before: Instant?) {
        coroutineScope {
            this@updateAllFeeds
                .map { launch { feedControlService.updateFeed(userId, it.feedId, action, before) } }
                .joinAll()
        }
    }

    companion object {
        const val FEVER_API_VERSION = 3
        const val feverAPIPath = "/api/fever.php"
        private val logger: Logger = LoggerFactory.getLogger(FeverAPIHandler::class.java)
    }

}

private suspend fun ServerRequest.getFeverRequestParameters(): Map<String, String> {
    val feverParameters = LinkedMultiValueMap<String, String>()
    feverParameters.addAll(this.queryParams())
    feverParameters.addAll(this.awaitFormData())
    return feverParameters.toSingleValueMap()
}

private fun Map<String, String>.queryIntListOrNull(parameterName: String): List<Int>? =
    this[parameterName]?.toIntegerList()

private fun String.toIntegerList(): List<Int> =
    this.split(",").mapNotNull { it.toIntOrNull() }

private fun String?.toInstant(): Instant? =
    this
        ?.toLong()
        ?.let { Instant.ofEpochSecond(it) }
