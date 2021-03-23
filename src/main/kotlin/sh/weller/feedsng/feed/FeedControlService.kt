package sh.weller.feedsng.feed

import sh.weller.feedsng.common.Result
import sh.weller.feedsng.user.UserId

interface FeedControlService {
    suspend fun importFromOPML(userId: UserId, fileContent: String): Result<Unit, List<String>>

    // TODO: Move Feed to Group

    suspend fun addGroup(userId: UserId, groupName: String): GroupId
    suspend fun addFeedToGroup(userId: UserId, groupId: GroupId, feedUrl: String): Result<FeedId, String>
    suspend fun addFeed(userId: UserId, feedUrl: String): Result<FeedId, String>

    suspend fun updateGroup(userId: UserId, groupId: GroupId, action: UpdateAction)
    suspend fun updateFeed(userId: UserId, feedId: FeedId, action: UpdateAction)
    suspend fun updateFeedItem(userId: UserId, feedItemId: FeedItemId, action: UpdateAction)
}

inline class GroupId(val id: Int) {
    init {
        require(id > 0)
    }
}

fun Int.toGroupId(): GroupId = GroupId(this)
fun Int?.toGroupId(): GroupId? = this?.toGroupId()

inline class FeedId(val id: Int) {
    init {
        require(id > 0)
    }
}

fun Int.toFeedId(): FeedId = FeedId(this)
fun Int?.toFeedId(): FeedId? = this?.toFeedId()

inline class FeedItemId(val id: Int) {
    init {
        require(id > 0)
    }
}

fun Int.toFeedItemId(): FeedItemId = FeedItemId(this)
fun Int?.toFeedItemId(): FeedItemId? = this?.toFeedItemId()

enum class UpdateAction {
    READ, UNREAD, SAVE, UNSAVE;

    fun getUpdateColumnName(): String = when (this) {
        READ, UNREAD -> "read"
        SAVE, UNSAVE -> "saved"
    }

    fun getUpdateValue(): Boolean = when (this) {
        READ, SAVE -> true
        UNREAD, UNSAVE -> false
    }
}

