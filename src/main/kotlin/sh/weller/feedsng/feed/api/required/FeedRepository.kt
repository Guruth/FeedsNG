package sh.weller.feedsng.feed.api.required

import kotlinx.coroutines.flow.Flow
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.user.UserId
import java.time.Instant

interface FeedRepository {
    suspend fun insertFeed(feedData: FeedData): FeedId
    suspend fun setFeedLastRefreshedTimestamp(feedId: FeedId)
    suspend fun getFeedWithFeedURL(feedUrl: String): Feed?
    suspend fun getFeed(feedId: FeedId): Feed?
    suspend fun getAllFeeds(): Flow<Feed>

    fun insertFeedItemsIfNotExist(feedId: FeedId, feedItemDataFlow: Flow<FeedItemData>): Flow<FeedItemId>
    suspend fun getAllFeedItemIds(feedId: FeedId, before: Instant? = null): Flow<FeedItemId>

    suspend fun insertUserGroup(userId: UserId, groupData: GroupData): GroupId
    suspend fun getAllUserGroups(userId: UserId): Flow<Group>

    suspend fun addFeedToUserGroup(groupId: GroupId, feedId: FeedId)
    suspend fun addFeedToUser(userId: UserId, feedId: FeedId)
    suspend fun getAllUserFeeds(userId: UserId): Flow<Feed>

    suspend fun updateUserFeedItem(userId: UserId, feedItemIdFlow: Flow<FeedItemId>, updateAction: UpdateAction)
    suspend fun getAllUserFeedItemsOfFeed(
        userId: UserId,
        feedId: FeedId,
        filter: FeedItemFilter? = null,
        since: Instant? = null
    ): Flow<UserFeedItem>

    suspend fun getAllUserFeedItemIdsOfFeed(
        userId: UserId,
        feedId: FeedId,
        filter: FeedItemFilter? = null,
        since: Instant? = null
    ): Flow<FeedItemId>

    suspend fun getUserFeedItem(userId: UserId, feedId: FeedId, feedItemId: FeedItemId): UserFeedItem?
}