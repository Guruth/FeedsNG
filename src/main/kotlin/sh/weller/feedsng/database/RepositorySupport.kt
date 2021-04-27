package sh.weller.feedsng.database

import io.r2dbc.spi.Row
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.RowsFetchSpec
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.user.api.provided.User
import sh.weller.feedsng.user.api.provided.UserData
import sh.weller.feedsng.user.api.provided.toUserId

internal inline fun <reified T> Row.getReified(columnName: String): T = this.get(columnName, T::class.java)!!
internal inline fun <reified T> Row.getReifiedOrNull(columnName: String): T? = this.get(columnName, T::class.java)

internal fun Row.getInt(columnName: String): Int = this.get(columnName, Integer::class.java)!!.toInt()
internal fun Row.getIntOrNull(columnName: String): Int? = this.get(columnName, Integer::class.java)?.toInt()

internal fun andWhereIfNotNull(fieldName: String, parameterName: String, operator: String, value: Any?): String =
    if (value != null) {
        "AND $fieldName $operator :$parameterName"
    } else {
        ""
    }

internal fun DatabaseClient.GenericExecuteSpec.bindIfNotNull(
    parameterName: String,
    value: Any?
): DatabaseClient.GenericExecuteSpec =
    if (value != null) {
        this.bind(parameterName, value)
    } else {
        this
    }

fun DatabaseClient.GenericExecuteSpec.mapToUserFeedItem(): RowsFetchSpec<UserFeedItem> =
    this.map { row ->
        UserFeedItem(
            isSaved = row.getReifiedOrNull("saved") ?: false,
            isRead = row.getReifiedOrNull("read") ?: false,
            feedItem = row.toFeedItem()
        )
    }

fun Row.toFeedItem(): FeedItem =
    FeedItem(
        feedItemId = this.getInt("id").toFeedItemId(),
        feedId = this.getInt("feed_id").toFeedId(),
        feedItemData = FeedItemData(
            title = this.getReified("title"),
            author = this.getReified("author"),
            html = this.getReified("html"),
            url = this.getReified("item_url"),
            created = this.getReified("created")
        )
    )

fun DatabaseClient.GenericExecuteSpec.mapToFeed(): RowsFetchSpec<Feed> =
    this.map { row ->
        Feed(
            feedId = row.getInt("id").toFeedId(),
            feedData = FeedData(
                name = row.getReified("name"),
                description = row.getReified("description"),
                feedUrl = row.getReified("feed_url"),
                siteUrl = row.getReified("site_url"),
                lastUpdated = row.getReified("last_updated")
            )
        )
    }

fun DatabaseClient.GenericExecuteSpec.mapToUser(): RowsFetchSpec<User> =
    this.map { row ->
        User(
            userId = row.getInt("id").toUserId(),
            userData = UserData(
                username = row.getReified("username"),
                passwordHash = row.getReified("password_hash"),
                feverAPIKeyHash = row.getReifiedOrNull("fever_api_key_hash")
            )
        )
    }