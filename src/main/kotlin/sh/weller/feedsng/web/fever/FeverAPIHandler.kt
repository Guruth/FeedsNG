package sh.weller.feedsng.web.fever

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.reactive.function.server.*
import sh.weller.feedsng.feed.*
import sh.weller.feedsng.user.UserId


@Controller
class FeverAPIHandler(
    private val feedControlService: FeedControlService,
    private val feedQueryService: FeedQueryService
) {

    fun getRouterFunction(): RouterFunction<ServerResponse> =
        coRouter {
            path("/api/fever.php", ::feverHandler)
        }

    private suspend fun feverHandler(request: ServerRequest): ServerResponse {
        val queryParameters = request.queryParams()

        // TODO: Fetch from request
        val userId = UserId(1)

        val responseBuilder = FeverResponse.Builder()

        val groups = feedQueryService.getGroups(userId).toList()
        val feeds = feedQueryService.getFeeds(userId).toList()
        val feedItems = feedQueryService.getFeedItems(userId = userId).toList()


        responseBuilder.lastRefreshedOnTime(feeds.maxOf { it.feedData.lastUpdated })

        if (queryParameters.containsKey("groups") || queryParameters.containsKey("feeds")) {
            responseBuilder.feedGroupMappings(groups)

            if (queryParameters.containsKey("groups")) {
                responseBuilder.groups(groups)
            }
            if (queryParameters.containsKey("feeds")) {
                responseBuilder.feeds(feeds)
            }
        }

        if (queryParameters.containsKey("items")) {
            val feedIdsToFilter = mutableListOf<FeedId>()

            request.queryIntListOrNull("feed_ids")
                ?.map { it.toFeedId() }
                ?.also { feedIdsToFilter.addAll(it) }

            request.queryIntListOrNull("group_ids")
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


            val itemIdFilter = request.queryIntListOrNull("with_ids")
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

        if (queryParameters.contains("unread_item_ids")) {
            val unreadIds = feedQueryService
                .getFeedItems(
                    userId = userId,
                    feedIdList = null,
                    since = null,
                    filter = FeedItemFilter.UNREAD
                )
                .map { it.feedItem.feedItemId }
                .toList()
            responseBuilder.unreadItemIds(unreadIds)
        }


        if (queryParameters.contains("saved_item_ids")) {
            val unreadIds = feedQueryService
                .getFeedItems(
                    userId = userId,
                    feedIdList = null,
                    since = null,
                    filter = FeedItemFilter.SAVED
                )
                .map { it.feedItem.feedItemId }
                .toList()
            responseBuilder.savedItemIds(unreadIds)
        }

        return ServerResponse.ok().json().bodyValueAndAwait(responseBuilder.build())
    }


    companion object {
        const val FEVER_API_VERSION = 3
        private val logger: Logger = LoggerFactory.getLogger(FeverAPIHandler::class.java)
    }

}

private fun ServerRequest.queryIntListOrNull(parameterName: String): List<Int>? =
    this.queryParamOrNull(parameterName)?.toIntegerList()

private fun String.toIntegerList(): List<Int> =
    this.split(",").mapNotNull { it.toIntOrNull() }