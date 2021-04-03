package sh.weller.feedsng.web.fever

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.reactive.function.server.*
import sh.weller.feedsng.feed.*
import sh.weller.feedsng.user.UserId
import java.time.Instant
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@Controller
class FeverAPIHandler(
    private val feedControlService: FeedControlService,
    private val feedQueryService: FeedQueryService
) {

    @OptIn(ExperimentalTime::class)
    fun getRouterFunction(): RouterFunction<ServerResponse> =
        coRouter {
            path("/api/fever.php") {
                val result: ServerResponse
                val duration = measureTime {
                    result = feverHandler(it)
                }
                logger.info("Request took ${duration.inMilliseconds} ms")
                return@path result
            }
        }

    private suspend fun feverHandler(request: ServerRequest): ServerResponse {
        val requestParameters = request.getFeverRequestParameter()
        logger.info("Fever request parameters: $requestParameters ")

        // TODO: Fetch from request
        val userId = UserId(1)

        val responseBuilder = FeverResponse.Builder()

        val groups = withContext(Dispatchers.IO) { feedQueryService.getGroups(userId).toList() }
        val feeds = withContext(Dispatchers.IO) { feedQueryService.getFeeds(userId).toList() }
        val feedItems = withContext(Dispatchers.IO) { feedQueryService.getFeedItems(userId = userId).toList() }


        responseBuilder.lastRefreshedOnTime(feeds.maxOf { it.feedData.lastUpdated })

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


            val filteredFeedItemFlow = if (feedIdsToFilter.isNotEmpty()) {
                feedItems
                    .filter { it.feedItem.feedId in feedIdsToFilter }
            } else feedItems


            val itemIdFilter = requestParameters.queryIntListOrNull("with_ids")
                ?.map { it.toFeedItemId() }
            val itemIdFiltered = if (itemIdFilter != null) {
                filteredFeedItemFlow
                    .filter { it.feedItem.feedItemId in itemIdFilter }
            } else filteredFeedItemFlow

            val sinceFilter = request.queryParamOrNull("since_id")?.toIntOrNull()
            val sinceFilteredItems = if (sinceFilter != null) {
                itemIdFiltered
                    .filter { it.feedItem.feedItemId.id > sinceFilter }
            } else filteredFeedItemFlow

            val maxIdFilter = request.queryParamOrNull("max_id")?.toIntOrNull()
            val maxIdFilteredItems = if (maxIdFilter != null) {
                sinceFilteredItems.filter { it.feedItem.feedItemId.id <= maxIdFilter }
            } else sinceFilteredItems

            responseBuilder.items(maxIdFilteredItems)
        }

        if (requestParameters.contains("unread_item_ids")) {
            val unreadIds = getUnreadItemIds(userId)
            responseBuilder.unreadItemIds(unreadIds)
        }

        if (requestParameters.contains("saved_item_ids")) {
            val savedIds = getSavedItemIds(userId)
            responseBuilder.savedItemIds(savedIds)
        }

        if (requestParameters["unread_recently_read"] == "1") {
            feedQueryService
                .getFeedItemsIds(
                    userId,
                    filter = FeedItemFilter.READ,
                    since = Instant.now().minusSeconds(30)
                )
                .onEach {
                    feedControlService.updateFeedItem(userId, it, UpdateAction.UNREAD)
                }
        }

        if (requestParameters.contains("mark")) {
            val target = requestParameters["mark"]
            val action = requestParameters["as"]
                .let {
                    UpdateAction.fromStringOrNull(it)
                }

            val id = requestParameters["id"]
                ?.toIntOrNull()

            val before = requestParameters["before"]
                ?.toInstant()
            if (id != null && action != null) {
                when (target) {
                    "item" -> feedControlService.updateFeedItem(userId, id.toFeedItemId(), action)
                    "feed" -> feedControlService.updateFeed(userId, id.toFeedId(), action, before)
                    "group" -> {
                        if (id == 0) {
                            // 0 is the group with all feeds
                            feeds.updateAllFeeds(userId, action, before)
                        } else {
                            feedControlService.updateGroup(userId, id.toGroupId(), action, before)
                        }
                    }
                }
            }

            when (action) {
                UpdateAction.READ, UpdateAction.UNREAD -> {
                    val unreadIds = getUnreadItemIds(userId)
                    responseBuilder.unreadItemIds(unreadIds)
                }
                UpdateAction.SAVE, UpdateAction.UNSAVE -> {
                    val savedIds = getSavedItemIds(userId)
                    responseBuilder.savedItemIds(savedIds)
                }
            }
        }

        val response = responseBuilder.build()

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


    private suspend fun List<Feed>.updateAllFeeds(userId: UserId, action: UpdateAction, before: Instant?) {
        coroutineScope {
            this@updateAllFeeds
                .map {
                    launch { feedControlService.updateFeed(userId, it.feedId, action, before) }
                }
                .joinAll()
        }
    }

    companion object {
        const val FEVER_API_VERSION = 3
        private val logger: Logger = LoggerFactory.getLogger(FeverAPIHandler::class.java)
    }

}

private suspend fun ServerRequest.getFeverRequestParameter(): Map<String, String> {
    val requestParameters: MutableMap<String, String> = this.queryParams().toSingleValueMap()
    val bodyMap = this.awaitBodyOrNull<String>()
        ?.split("&")
        ?.map {
            val values = it.split("=")
            values.first() to values.last()
        }?.toMap()
    if (bodyMap != null) {
        requestParameters.putAll(bodyMap)
    }
    return requestParameters
}

private fun Map<String, String>.queryIntListOrNull(parameterName: String): List<Int>? =
    this.get(parameterName)?.toIntegerList()

private fun String.toIntegerList(): List<Int> =
    this.split(",").mapNotNull { it.toIntOrNull() }

private fun String?.toInstant(): Instant? =
    this
        ?.toLong()
        ?.let { Instant.ofEpochSecond(it) }
