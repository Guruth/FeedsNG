package sh.weller.feedsng.feed.api.required

import kotlinx.coroutines.flow.Flow
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.user.api.provided.UserId
import java.time.Instant

interface FeedRepository {
    suspend fun insertFeed(feedData: FeedData): FeedId
    suspend fun setFeedLastRefreshedTimestamp(feedId: FeedId)
    suspend fun getFeedWithFeedURL(feedUrl: String): Feed?
    suspend fun getFeed(feedId: FeedId): Feed?
    suspend fun getAllFeeds(): Flow<Feed>

    fun insertFeedItemsIfNotExist(feedId: FeedId, feedItemDataFlow: Flow<FeedItemData>): Flow<FeedItemId>
    suspend fun getAllFeedItemIdsOfFeed(feedId: FeedId, before: Instant? = null): Flow<FeedItemId>

    suspend fun insertUserGroup(userId: UserId, groupData: GroupData): GroupId
    suspend fun getAllUserGroups(userId: UserId): Flow<Group>

    suspend fun addFeedToUserGroup(groupId: GroupId, feedId: FeedId)
    suspend fun addFeedToUser(userId: UserId, feedId: FeedId)
    suspend fun getAllFeedsOfUser(userId: UserId): Flow<Feed>

    suspend fun updateFeedItemOfUser(userId: UserId, feedItemIdFlow: Flow<FeedItemId>, updateAction: FeedUpdateAction)

    suspend fun getAllFeedItemsOfUser(
        userId: UserId,
        feedId: FeedId,
        filter: FeedItemFilter? = null,
        since: Instant? = null,
        limit: Int? = null
    ): Flow<UserFeedItem>

    suspend fun getAllFeedItemIdsOfFeed(
        userId: UserId,
        feedId: FeedId,
        filter: FeedItemFilter? = null,
        since: Instant? = null
    ): Flow<FeedItemId>

    suspend fun countFeedItemsOfFeedOfUser(userId: UserId, feedId: FeedId, filter: FeedItemFilter?): Int

    suspend fun getFeedItemOfUser(userId: UserId, feedId: FeedId, feedItemId: FeedItemId): UserFeedItem?
}