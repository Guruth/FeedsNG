package sh.weller.feedsng.database.h2r2dbc

import io.r2dbc.spi.Row
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.RowsFetchSpec
import sh.weller.feedsng.feed.api.provided.*

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
            feedItem = FeedItem(
                feedItemId = row.getInt("id").toFeedItemId(),
                feedId = row.getInt("feed_id").toFeedId(),
                feedItemData = FeedItemData(
                    title = row.getReified("title"),
                    author = row.getReified("author"),
                    html = row.getReified("html"),
                    url = row.getReified("item_url"),
                    created = row.getReified("created")
                )
            )
        )
    }

fun DatabaseClient.GenericExecuteSpec.mapToFeedItem(): RowsFetchSpec<FeedItem> =
    this.map { row ->
        FeedItem(
            feedItemId = row.getInt("id").toFeedItemId(),
            feedId = row.getInt("feed_id").toFeedId(),
            feedItemData = FeedItemData(
                title = row.getReified("title"),
                author = row.getReified("author"),
                html = row.getReified("html"),
                url = row.getReified("item_url"),
                created = row.getReified("created")
            )
        )
    }

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
