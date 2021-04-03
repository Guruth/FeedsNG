package sh.weller.feedsng.web.fever

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sh.weller.feedsng.feed.*
import sh.weller.feedsng.web.fever.FeverAPIHandler.Companion.FEVER_API_VERSION
import java.time.Instant


@Serializable
class FeverResponse private constructor(
    @SerialName("api_version")
    val apiVersion: Int,
    val auth: Int,
    @SerialName("last_refreshed_on_time")
    val lastRefreshedOnTime: String? = null,
    @SerialName("feeds_groups")
    val feedsGroups: List<FeverFeedGroupMapping>? = null,
    val groups: List<FeverGroup>? = null,
    val feeds: List<FeverFeed>? = null,
    val favicons: List<FeverFavIcon>? = null,
    @SerialName("total_items")
    val totalItems: Int? = null,
    val items: List<FeverEntry>? = null,
    // String of comma separated Ints "1,2,3,4"
    @SerialName("unread_item_ids")
    val unreadItemIds: String? = null,
    @SerialName("saved_item_ids")
    // String of comma separated Ints "1,2,3,4"
    val savedItemIds: String? = null,
) {
    val links: Unit? = null // Hot Links not supported


    // TODO: A deferred Builder? Takes only Deferred and awaits on build...
    class Builder {
        private var lastRefreshedOnTime: String? = null
        private var feedsGroups: List<FeverFeedGroupMapping>? = null
        private var groups: List<FeverGroup>? = null
        private var feeds: List<FeverFeed>? = null
        private var favicons: List<FeverFavIcon>? = null
        private var totalItems: Int? = null
        private var items: List<FeverEntry>? = null
        private var unreadItemIds: String? = null
        private var savedItemIds: String? = null

        fun lastRefreshedOnTime(lastRefreshedOnTime: Instant) =
            apply { this.lastRefreshedOnTime = lastRefreshedOnTime.epochSecond.toString() }

        fun feedGroupMappings(feedsGroups: List<Group>) {
            this.feedsGroups = feedsGroups.map { group ->
                FeverFeedGroupMapping(
                    group.groupId.id,
                    group.groupData.feeds.map(FeedId::id).joinToString("")
                )
            }
        }

        fun groups(groups: List<Group>) {
            this.groups = groups.map { group ->
                FeverGroup(
                    group.groupId.id,
                    group.groupData.name
                )
            }
        }

        fun feeds(feeds: List<Feed>) {
            this.feeds = feeds.map { feed ->
                FeverFeed(
                    feed.feedId.id,
                    1, // TODO
                    feed.feedData.name,
                    feed.feedData.feedUrl,
                    feed.feedData.siteUrl,
                    feed.feedData.lastUpdated.epochSecond.toString()
                )
            }
        }

        fun favicons(favicons: List<FeverFavIcon>) {
            this.favicons = favicons
        }

        fun items(items: List<UserFeedItem>) {
            this.totalItems = items.size
            this.items = items.map { userFeedItem ->
                FeverEntry(
                    id = userFeedItem.feedItem.feedItemId.id,
                    feedId = userFeedItem.feedItem.feedId.id,
                    title = userFeedItem.feedItem.feedItemData.title,
                    author = userFeedItem.feedItem.feedItemData.author,
                    html = userFeedItem.feedItem.feedItemData.html,
                    url = userFeedItem.feedItem.feedItemData.url,
                    isSaved = userFeedItem.isSaved,
                    isRead = userFeedItem.isRead,
                    createdOnTime = userFeedItem.feedItem.feedItemData.created.epochSecond.toString()
                )
            }
        }

        fun unreadItemIds(unreadItemIds: List<FeedItemId>) {
            this.unreadItemIds = unreadItemIds.map { it.id }.joinToString(",")
        }

        fun savedItemIds(savedItemIds: List<FeedItemId>) {
            this.savedItemIds = savedItemIds.map { it.id }.joinToString(",")
        }

        fun build(): FeverResponse = FeverResponse(
            apiVersion = FEVER_API_VERSION,
            auth = 1,
            lastRefreshedOnTime = lastRefreshedOnTime,
            feedsGroups = feedsGroups,
            groups = groups,
            feeds = feeds,
            favicons = favicons,
            totalItems = totalItems,
            items = items,
            unreadItemIds = unreadItemIds,
            savedItemIds = savedItemIds
        )

        companion object {
            private val logger: Logger = LoggerFactory.getLogger(Builder::class.java)
        }
    }
}

@Serializable
data class FeverFeedGroupMapping(
    @SerialName("group_id")
    val groupId: Int,
    // String of comma separated Ints "1,2,3,4"
    @SerialName("feed_ids")
    val feedIds: String
)


@Serializable
data class FeverGroup(
    val id: Int,
    val title: String
)

@Serializable
data class FeverFeed(
    val id: Int,
    @SerialName("favicon_id")
    val faviconId: Int,
    val title: String,
    val url: String,
    @SerialName("site_url")
    val siteUrl: String,
    @SerialName("last_updated_on_time")
    val lastUpdatedOnTime: String,
    @SerialName("is_spark")
    val isSpark: Int = 0 // Not supported
)

@Serializable
data class FeverFavIcon(
    val id: Int,
    // base64 encoded image data; prefixed by image type "image/gif;base64,R0lGODlhAQABAIAAAObm5gAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw=="
    val data: String
)

@Serializable
data class FeverEntry(
    val id: Int,
    @SerialName("feed_id")
    val feedId: Int,
    val title: String,
    val author: String,
    val html: String,
    val url: String,
    @SerialName("is_saved")
    val isSaved: Boolean,
    @SerialName("is_read")
    val isRead: Boolean,
    @SerialName("created_on_time")
    val createdOnTime: String
)