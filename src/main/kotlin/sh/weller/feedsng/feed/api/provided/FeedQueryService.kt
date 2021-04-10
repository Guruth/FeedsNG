package sh.weller.feedsng.feed.api.provided

import kotlinx.coroutines.flow.Flow
import sh.weller.feedsng.user.UserId
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
        feedIdList: Flow<FeedId>? = null,
        filter: FeedItemFilter? = null,
        since: Instant? = null
    ): Flow<UserFeedItem>


    /**
     * Fetches all [FeedItemId]s of a user for the given list of [FeedId]s that match the [FeedItemFilter].
     */
    suspend fun getFeedItemsIds(
        userId: UserId,
        feedIdList: Flow<FeedId>? = null,
        filter: FeedItemFilter? = null,
        since: Instant? = null
    ): Flow<FeedItemId>
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
    READ, UNREAD, SAVED;
}