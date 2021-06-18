package sh.weller.feedsng.feed.api.provided

import kotlinx.coroutines.flow.Flow
import sh.weller.feedsng.user.api.provided.UserId

interface FeedQueryService {
    /**
     * Fetches a specific [Feed] by its [FeedId], if it exists
     */
    suspend fun getFeed(feedId: FeedId): Feed?

    /**
     * Fetches all [Group]s of a user
     */
    suspend fun getGroups(userId: UserId): Flow<Group>

    /**
     * Fetches all [Feed]s of a user
     */
    suspend fun getFeeds(userId: UserId): Flow<Feed>

    /**
     * Fetches all [FeedId]s of a user
     */
    suspend fun getFeedIds(userId: UserId): Flow<FeedId>

    /**
     * Fetches all [UserFeedItem]s of a user for the given list of [FeedId]s that match the [FeedItemFilter].
     */
    suspend fun getFeedItems(
        userId: UserId,
        feedIdList: List<FeedId>? = null,
        feedItemIdFilter: FeedItemIdFilter? = null,
        limit: Int? = null,
        offset: Int = 0
    ): Flow<UserFeedItem>


    /**
     * Fetches all [FeedItemId]s of a user for the given list of [FeedId]s that match the [FeedItemFilter].
     */
    suspend fun getFeedItemsIds(
        userId: UserId,
        feedIdList: List<FeedId>? = null,
        filter: FeedItemFilter? = null,
    ): Flow<FeedItemId>


    /**
     * Fetches the number of items matching the [FeedItemFilter]
     */
    suspend fun countFeedItems(
        userId: UserId,
        feedId: FeedId,
        filter: FeedItemFilter? = null
    ): Int
}

suspend fun FeedQueryService.getFeedItems(
    userId: UserId,
    feedId: FeedId?,
    feedItemIdFilter: FeedItemIdFilter? = null,
    limit: Int? = null,
    offset: Int = 0
) = this.getFeedItems(userId, listOfNotNull(feedId), feedItemIdFilter, limit, offset)