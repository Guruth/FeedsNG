package sh.weller.feedsng.feed

import sh.weller.feedsng.common.ResultNG
import sh.weller.feedsng.user.UserId

interface FeedControlService {
    suspend fun importFromOPML(userId: UserId, fileContent: String): ResultNG<List<Pair<String, String>>, String>

    fun addGroup(userId: UserId, groupName: String): GroupId
    fun addFeedToGroup(userId: UserId, groupId: GroupId, feedUrl: String): ResultNG<FeedId, String>
    fun addFeed(userId: UserId, feedUrl: String): ResultNG<FeedId, String>

    fun updateFeedGroup(userId: UserId, groupId: GroupId, action: UpdateAction)
    fun updateFeed(userId: UserId, feedId: FeedId, action: UpdateAction)
    fun updateFeedItem(userId: UserId, feedItemId: FeedItemId, action: UpdateAction)
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
    READ, UNREAD, SAVE, UNSAVE
}

