package sh.weller.feedsng.feed

import kotlinx.coroutines.flow.Flow
import sh.weller.feedsng.user.UserId
import java.time.Instant

interface FeedQueryService {
    suspend fun getFeed(feedId: FeedId): Feed?

    suspend fun getGroups(userId: UserId): Flow<Group>
    fun getFeeds(userId: UserId): Flow<Feed>
    fun getFeedItems(
        userId: UserId,
        feedIdList: List<FeedId>,
        since: Instant?,
        limit: Int?,
        filter: FeedItemFilter?
    ): Flow<UserFeedItem>
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

enum class FeedItemFilter {
    UNREAD, SAVED;
}