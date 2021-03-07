package sh.weller.feedsng.feed

import sh.weller.feedsng.user.UserId

interface FeedService {
    suspend fun importFromOPML(userId: UserId, fileContent: String): OPMLImportResult

    suspend fun addGroup(userId: UserId, groupName: String): GroupId
    suspend fun addFeed(userId: UserId, feedUrl: String): FeedId?
    suspend fun addFeedToGroup(userId: UserId, groupId: GroupId, feedId: FeedId)

    suspend fun updateFeedGroup(userId: UserId, groupId: GroupId, action: UpdateAction)
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

sealed class OPMLImportResult {
    object Success : OPMLImportResult()
    data class Error(val reason: String) : OPMLImportResult()
}

enum class UpdateAction {
    READ, UNREAD, SAVE, UNSAVE
}

