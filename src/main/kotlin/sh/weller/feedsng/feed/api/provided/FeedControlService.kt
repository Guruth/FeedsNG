package sh.weller.feedsng.feed.api.provided

import sh.weller.feedsng.common.Result
import sh.weller.feedsng.user.api.provided.UserId
import java.time.Instant

interface FeedControlService {

    suspend fun importFromOPML(userId: UserId, fileContent: String): Result<Unit, List<String>>

    // TODO: Move Feed to Group

    suspend fun addGroup(userId: UserId, groupName: String): GroupId
    suspend fun addFeedToGroup(userId: UserId, groupId: GroupId, feedUrl: String): Result<FeedId, String>
    suspend fun addFeed(userId: UserId, feedUrl: String): Result<FeedId, String>

    suspend fun updateGroup(userId: UserId, groupId: GroupId, action: FeedUpdateAction, before: Instant? = null)
    suspend fun updateFeed(userId: UserId, feedId: FeedId, action: FeedUpdateAction, before: Instant? = null)
    suspend fun updateFeedItem(userId: UserId, feedItemId: FeedItemId, action: FeedUpdateAction)
}
