package sh.weller.feedsng.feed.api.provided

import java.time.Instant

@JvmInline
value class GroupId(val id: Int) {
    init {
        require(id > 0)
    }
}

fun Int.toGroupId(): GroupId = GroupId(this)
fun Int?.toGroupId(): GroupId? = this?.toGroupId()

data class Group(
    val groupId: GroupId,
    val groupData: GroupData
)

data class GroupData(
    val name: String,
    val feeds: List<FeedId>,
)


@JvmInline
value class FeedId(val id: Int) {
    init {
        require(id > 0)
    }
}

fun Int.toFeedId(): FeedId = FeedId(this)
fun Int?.toFeedId(): FeedId? = this?.toFeedId()
fun String?.toFeedIdOrNull(): FeedId? = this?.toIntOrNull()?.toFeedId()

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

@JvmInline
value class FeedItemId(val id: Int) {
    init {
        require(id > 0)
    }
}

fun Int.toFeedItemId(): FeedItemId = FeedItemId(this)
fun Int?.toFeedItemId(): FeedItemId? = this?.toFeedItemId()

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

sealed class FeedItemIdFilter {
    data class SinceIdFilter(val value: FeedItemId) : FeedItemIdFilter()
    data class MaxIdFilter(val value: FeedItemId) : FeedItemIdFilter()
    data class WithIdFilter(val value: List<FeedItemId>) : FeedItemIdFilter()
}

enum class FeedUpdateAction {
    READ, UNREAD, SAVE, UNSAVE;

    fun getUpdateColumnName(): String = when (this) {
        READ, UNREAD -> "read"
        SAVE, UNSAVE -> "saved"
    }

    fun getUpdateValue(): Boolean = when (this) {
        READ, SAVE -> true
        UNREAD, UNSAVE -> false
    }

    companion object {
        fun fromStringOrNull(value: String?): FeedUpdateAction? =
            when (value) {
                "read" -> READ
                "unread" -> UNREAD
                "saved" -> SAVE
                "unsave" -> UNSAVE
                else -> null
            }
    }
}

fun String?.toUpdateAction(): FeedUpdateAction? = FeedUpdateAction.fromStringOrNull(this)
