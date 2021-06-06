package sh.weller.feedsng.feed.api.provided

import kotlinx.coroutines.flow.Flow
import sh.weller.feedsng.user.api.provided.UserId
import java.time.Instant

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
     * Fetches all [UserFeedItem]s of a user for the given list of [FeedId]s that match the [FeedItemFilter].
     */
    suspend fun getFeedItems(
        userId: UserId,
        feedIdList: List<FeedId>? = null,
        filter: FeedItemFilter? = null,
        since: Instant? = null,
        limit: Int? = null
    ): Flow<UserFeedItem>


    /**
     * Fetches all [FeedItemId]s of a user for the given list of [FeedId]s that match the [FeedItemFilter].
     */
    suspend fun getFeedItemsIds(
        userId: UserId,
        feedIdList: List<FeedId>? = null,
        filter: FeedItemFilter? = null,
        since: Instant? = null
    ): Flow<FeedItemId>
}


suspend fun FeedQueryService.getFeedItems(
    userId: UserId,
    feedId: FeedId?,
    filter: FeedItemFilter? = null,
    since: Instant? = null,
    limit: Int? = null
) = this.getFeedItems(userId, listOfNotNull(feedId), filter, since, limit)