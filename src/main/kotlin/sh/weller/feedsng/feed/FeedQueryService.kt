package sh.weller.feedsng.feed

import sh.weller.feedsng.user.UserId
import java.time.Instant

interface FeedQueryService {
    suspend fun getGroups(userId: UserId): List<Group>
    suspend fun getGroup(userId: UserId, groupId: GroupId): Group

    suspend fun getFeeds(userId: UserId): List<Feed>
    suspend fun getFeed(userId: UserId, feedId: FeedId): Feed

    suspend fun getFeedItems(userId: UserId, feedIds: List<FeedId>?, since: Instant?): List<UserFeedItem>
    suspend fun getSavedFeedItems(userId: UserId, feedIdList: List<FeedId>?, since: Instant?): List<UserFeedItem>
    suspend fun getUnreadFeedItems(userId: UserId, feedIdList: List<FeedId>?, since: Instant?): List<UserFeedItem>
    suspend fun getFeedItem(userId: UserId, feedItemId: FeedItemId): UserFeedItem
}

data class Group(
    val groupId: GroupId,
    val groupData: GroupData
)

data class GroupData(
    val name: String,
    val feeds: List<FeedId>,
)

data class Feed(
    val feedId: FeedId,
    val feedData: FeedData
)

data class FeedData(
    val name: String,
    val description: String,
    val feedUrl: String,
    val siteUrl: String,
    val lastUpdated: Instant,
)

data class UserFeedItem(
    val feedItem: FeedItem,
    val isSaved: Boolean,
    val isRead: Boolean,
)

data class FeedItem(
    val feedItemId: FeedItemId,
    val feedId: FeedId,
    val feedItemData: FeedItemData
)

data class FeedItemData(
    val title: String,
    val author: String,
    val html: String,
    val url: String,
    val created: Instant,
)