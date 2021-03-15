package sh.weller.feedsng.feed.impl.database

import kotlinx.coroutines.flow.Flow
import sh.weller.feedsng.feed.*
import sh.weller.feedsng.user.UserId
import java.time.Instant

interface FeedRepository {
    suspend fun insertFeed(feedData: FeedData): FeedId
    suspend fun setFeedLastRefreshedTimestamp(feedId: FeedId)
    suspend fun getFeedWithFeedURL(feedUrl: String): Feed?
    suspend fun getFeed(feedId: FeedId): Feed?
    suspend fun getAllFeeds(): Flow<Feed>

    fun insertFeedItems(feedId: FeedId, feedItemDataFlow: Flow<FeedItemData>): Flow<FeedItemId>
    fun getFeedItems(feedId: FeedId, since: Instant? = null, limit: Int? = null): Flow<FeedItem>
    suspend fun getFeedItem(feedId: FeedId, feedItemId: FeedItemId): FeedItem?

    suspend fun insertUserGroup(userId: UserId, groupData: GroupData): GroupId
    fun getAllUserGroups(userId: UserId): Flow<Group>

    suspend fun addFeedToUserGroup(groupId: GroupId, feedId: FeedId)
    suspend fun addFeedToUser(userId: UserId, feedId: FeedId)
    fun getAllUserFeeds(userId: UserId): Flow<Feed>

    suspend fun updateUserFeedItem(userId: UserId, feedItemIdFlow: Flow<FeedItemId>, updateAction: UpdateAction)
    fun getAllUserFeedItemsOfFeed(
        userId: UserId,
        feedId: FeedId,
        since: Instant? = null,
        limit: Int? = null,
        filter: FeedItemFilter? = null
    ): Flow<UserFeedItem>

    suspend fun getUserFeedItem(userId: UserId, feedId: FeedId, feedItemId: FeedItemId): UserFeedItem
}