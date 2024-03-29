package sh.weller.feedsng.database.postgres

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import org.springframework.r2dbc.core.*
import org.springframework.stereotype.Repository
import sh.weller.feedsng.database.*
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.feed.api.required.FeedRepository
import sh.weller.feedsng.user.api.provided.UserId
import java.time.Instant

@PostgresRepositoryCondition
@Repository
class PostgresFeedRepository(
    private val client: DatabaseClient
) : FeedRepository {

    override suspend fun insertFeed(feedData: FeedData): FeedId =
        client
            .sql(
                """
           INSERT INTO feed(
                name,
                description,
                feed_url,
                site_url,
                last_updated
            ) VALUES (
                :name,
                :description,
                :feed_url,
                :site_url,
                :last_updated
            )
        """.trimMargin()
            )
            .bind("name", feedData.name)
            .bind("description", feedData.description)
            .bind("feed_url", feedData.feedUrl)
            .bind("site_url", feedData.siteUrl)
            .bind("last_updated", feedData.lastUpdated)
            .filter { s -> s.returnGeneratedValues() }
            .map { row -> row.get("id", Integer::class.java)!!.toInt() }
            .awaitOne()
            .toFeedId()

    override suspend fun setFeedLastRefreshedTimestamp(feedId: FeedId) =
        client
            .sql("UPDATE feed SET last_updated = CURRENT_TIMESTAMP WHERE id = :id")
            .bind("id", feedId.id)
            .await()


    override suspend fun getFeedWithFeedURL(feedUrl: String): Feed? =
        client
            .sql("SELECT id, name, description, feed_url, site_url, last_updated FROM feed WHERE feed_url = :feed_url")
            .bind("feed_url", feedUrl)
            .mapToFeed()
            .awaitOneOrNull()


    override suspend fun getFeed(feedId: FeedId): Feed? =
        client
            .sql("SELECT id, name, description, feed_url, site_url, last_updated FROM feed WHERE id = :id")
            .bind("id", feedId.id)
            .mapToFeed()
            .awaitOneOrNull()


    override suspend fun getAllFeeds(): Flow<Feed> =
        client
            .sql("SELECT id, name, description, feed_url, site_url, last_updated FROM feed")
            .mapToFeed()
            .flow()


    override fun insertFeedItemsIfNotExist(feedId: FeedId, feedItemDataFlow: Flow<FeedItemData>): Flow<FeedItemId> =
        feedItemDataFlow
            .transform {
                client
                    .sql(
                        """
                    |INSERT INTO feed_item (feed_id, title, author, html, item_url, created) 
                    |VALUES (:feed_id, :title, :author, :html, :item_url, :created)
                    |ON CONFLICT DO NOTHING""".trimMargin()
                    )
                    .bind("feed_id", feedId.id)
                    .bind("title", it.title)
                    .bind("author", it.author)
                    .bind("html", it.html)
                    .bind("item_url", it.url)
                    .bind("created", it.created)
                    .await()

                val feedItemId =
                    client.sql("SELECT id FROM feed_item WHERE feed_id = :feedId and item_url = :itemURL")
                        .bind("feedId", feedId.id)
                        .bind("itemURL", it.url)
                        .map { row -> row.get("id", Integer::class.java)!!.toInt().toFeedItemId() }
                        .awaitSingle()
                emit(feedItemId)
            }

    override suspend fun getAllFeedItemIdsOfFeed(feedId: FeedId, before: Instant?): Flow<FeedItemId> =
        client
            .sql(
                """
                |SELECT id FROM feed_item 
                |WHERE feed_id = :feed_id 
                |${andWhereIfNotNull("created", "createdBefore", "<", before)} 
                |ORDER BY id DESC
            """.trimMargin()
            )
            .bind("feed_id", feedId.id)
            .bindIfNotNull("createdBefore", before)
            .map { row -> row.get("id", Integer::class.java)!!.toInt().toFeedItemId() }
            .flow()


    override suspend fun insertUserGroup(userId: UserId, groupData: GroupData): GroupId =
        client
            .sql(
                """
                |INSERT INTO user_group (user_id, name)
                |VALUES (:user_id, :name)
            """.trimMargin()
            )
            .bind("user_id", userId.id)
            .bind("name", groupData.name)
            .filter { s -> s.returnGeneratedValues() }
            .map { row -> row.get("id", Integer::class.java)!!.toInt().toGroupId() }
            .awaitOne()


    override suspend fun getAllUserGroups(userId: UserId): Flow<Group> =
        client
            .sql(
                """
                |SELECT 
                |UG.id, UG.name, UGF.feed_id
                |FROM user_group AS UG LEFT JOIN user_group_feed AS UGF ON UG.id = UGF.group_id 
                |WHERE UG.user_id = :user_id 
                |ORDER BY UG.id DESC
                |""".trimMargin()
            )
            .bind("user_id", userId.id)
            .map { row ->
                Triple<GroupId, String, FeedId?>(
                    row.getInt("id").toGroupId(),
                    row.getReified("name"),
                    row.getIntOrNull("feed_id").toFeedId()
                )
            }
            .flow()
            .toGroupFlow()


    private fun Flow<Triple<GroupId, String, FeedId?>>.toGroupFlow(): Flow<Group> = flow {
        val groupNameMap = mutableMapOf<GroupId, String>()
        val groupFeedMap = mutableMapOf<GroupId, MutableList<FeedId?>>()

        collect { triple ->
            groupNameMap.putIfAbsent(triple.first, triple.second)
            groupFeedMap.getOrPut(triple.first) { mutableListOf() } += triple.third
        }

        groupNameMap.forEach { (groupId, groupName) ->
            this.emit(
                Group(
                    groupId = groupId,
                    groupData = GroupData(
                        name = groupName,
                        feeds = groupFeedMap[groupId]?.filterNotNull() ?: emptyList()
                    )
                )
            )
        }
    }

    override suspend fun addFeedToUserGroup(groupId: GroupId, feedId: FeedId) =
        client
            .sql("INSERT INTO user_group_feed (group_id, feed_id) VALUES (:group_id, :feed_id)")
            .bind("group_id", groupId.id)
            .bind("feed_id", feedId.id)
            .await()


    override suspend fun addFeedToUser(userId: UserId, feedId: FeedId) =
        client
            .sql("INSERT INTO user_feed (user_id, feed_id) VALUES (:user_id, :feed_id)")
            .bind("user_id", userId.id)
            .bind("feed_id", feedId.id)
            .await()


    override suspend fun getAllFeedsOfUser(userId: UserId): Flow<Feed> {
        val groupFeedFlow = client
            .sql(
                """
                |SELECT 
                |F.id, F.name, F.description, F.feed_url, F.site_url, F.last_updated 
                |FROM user_group AS UG LEFT JOIN user_group_feed AS UGF ON UG.id = UGF.group_id 
                |LEFT JOIN feed AS F ON UGF.feed_id = F.id
                |WHERE UG.user_id = :user_id 
                |AND F.id IS NOT NULL
                |""".trimMargin()
            )
            .bind("user_id", userId.id)
            .mapToFeed()
            .flow()

        val userFeedFlow = client
            .sql(
                """
                |SELECT 
                |F.id, F.name, F.description, F.feed_url, F.site_url, F.last_updated 
                |FROM user_feed AS UF LEFT JOIN feed AS F ON UF.feed_id = F.id
                |WHERE UF.user_id = :user_id 
            """.trimMargin()
            )
            .bind("user_id", userId.id)
            .mapToFeed()
            .flow()

        return flowOf(groupFeedFlow, userFeedFlow).flattenConcat()
    }

    override suspend fun getAllFeedIdsOfUser(userId: UserId): Flow<FeedId> {
        val groupFeedIdFlow = client
            .sql(
                """
                |SELECT 
                |F.id
                |FROM user_group AS UG LEFT JOIN user_group_feed AS UGF ON UG.id = UGF.group_id 
                |LEFT JOIN feed AS F ON UGF.feed_id = F.id
                |WHERE UG.user_id = :user_id 
                |AND F.id IS NOT NULL
                |""".trimMargin()
            )
            .bind("user_id", userId.id)
            .map { row -> row.getInt("id").toFeedId() }
            .flow()

        val userFeedIdFlow = client
            .sql(
                """
                |SELECT 
                |F.id
                |FROM user_feed AS UF LEFT JOIN feed AS F ON UF.feed_id = F.id
                |WHERE UF.user_id = :user_id 
            """.trimMargin()
            )
            .bind("user_id", userId.id)
            .map { row -> row.getInt("id").toFeedId() }
            .flow()

        return flowOf(groupFeedIdFlow, userFeedIdFlow).flattenConcat()
    }

    override suspend fun updateFeedItemOfUser(
        userId: UserId,
        feedItemIdFlow: Flow<FeedItemId>,
        updateAction: FeedUpdateAction
    ): Unit = coroutineScope {
        val columnToUpdate = updateAction.getUpdateColumnName()
        val updateValue = updateAction.getUpdateValue()

        feedItemIdFlow
            .toList()
            .map {
                async {
                    client
                        .sql(
                            """
                |INSERT INTO user_feed_item (feed_item_id, user_id, $columnToUpdate) 
                |VALUES (:feed_item_id, :user_id, :updateValue) 
                |ON CONFLICT (feed_item_id, user_id) DO UPDATE SET $columnToUpdate = :updateValue
            """.trimMargin()
                        )
                        .bind("feed_item_id", it.id)
                        .bind("user_id", userId.id)
                        .bind("updateValue", updateValue)
                        .await()
                }
            }
            .awaitAll()
    }

    override suspend fun getAllFeedItemsOfUser(
        userId: UserId,
        feedIds: List<FeedId>,
        feedItemIdFilter: FeedItemIdFilter?,
        limit: Int?,
        offset: Int
    ): Flow<UserFeedItem> {
        return client
            .sql(
                """
                |SELECT 
                |FI.id, FI.feed_id, FI.title, FI.author, FI.html, FI.item_url, FI.created, UFI.saved, UFI.read 
                |FROM feed_item AS FI LEFT JOIN user_feed_item AS UFI ON FI.id = UFI.feed_item_id 
                |WHERE FI.feed_id IN (:feed_ids) 
                |AND (UFI.user_id = :user_id OR UFI.user_id IS NULL) 
                |${feedItemIdFilter.toWhereStatement()}
                |ORDER BY FI.id DESC
                |${limitIfNotNull(limit)} 
                |OFFSET :offset
            """.trimMargin()
            )
            .bind("feed_ids", feedIds.map { it.id })
            .bind("user_id", userId.id)
            .bindIfNotNull(feedItemIdFilter)
            .bindLimitIfNotNull(limit)
            .bind("offset", offset)
            .mapToUserFeedItem()
            .flow()
    }

    private fun FeedItemIdFilter?.toWhereStatement(): String =
        when (this) {
            is FeedItemIdFilter.MaxIdFilter -> "AND FI.id  <= :max_feed_item_id "
            is FeedItemIdFilter.SinceIdFilter -> "AND FI.id > :since_feed_item_id"
            is FeedItemIdFilter.WithIdFilter -> "AND FI.id in (:with_feed_item_id)"
            null -> ""
        }

    private fun DatabaseClient.GenericExecuteSpec.bindIfNotNull(feedItemIdFilter: FeedItemIdFilter?): DatabaseClient.GenericExecuteSpec =
        when (feedItemIdFilter) {
            is FeedItemIdFilter.MaxIdFilter -> this.bind("max_feed_item_id", feedItemIdFilter.value.id)
            is FeedItemIdFilter.SinceIdFilter -> this.bind("since_feed_item_id", feedItemIdFilter.value.id)
            is FeedItemIdFilter.WithIdFilter -> this.bind("with_feed_item_id", feedItemIdFilter.value.map { it.id })
            null -> this
        }


    override suspend fun countFeedItemsOfFeedOfUser(userId: UserId, feedId: FeedId, filter: FeedItemFilter?): Int {
        return client
            .sql(
                """
                |SELECT COUNT(FI.id) as count
                |FROM feed_item AS FI LEFT JOIN user_feed_item AS UFI ON FI.id = UFI.feed_item_id 
                |WHERE FI.feed_id IN (:feed_ids) 
                |AND (UFI.user_id = :user_id OR UFI.user_id IS NULL) 
                |${filter.toWhereClause()}
            """.trimMargin()
            )
            .bind("feed_ids", feedId.id)
            .bind("user_id", userId.id)
            .map { row -> row.getInt("count") }
            .awaitOne()
    }

    override suspend fun getAllFeedItemIdsOfFeed(
        userId: UserId,
        feedIds: List<FeedId>,
        filter: FeedItemFilter?
    ): Flow<FeedItemId> {
        val filterQuery = filter.toWhereClause()

        return client
            .sql(
                """
                |SELECT FI.id 
                |FROM feed_item AS FI LEFT JOIN user_feed_item AS UFI ON FI.id = UFI.feed_item_id 
                |WHERE FI.feed_id IN (:feed_ids) 
                |AND (UFI.user_id = :user_id OR UFI.user_id IS NULL) 
                |$filterQuery 
                |ORDER BY FI.id DESC
            """.trimMargin()
            )
            .bind("feed_ids", feedIds.map { it.id })
            .bind("user_id", userId.id)
            .map { row -> row.getInt("id").toFeedItemId() }
            .flow()
    }

    private fun FeedItemFilter?.toWhereClause(): String =
        when (this) {
            FeedItemFilter.READ -> "AND UFI.read = true "
            FeedItemFilter.UNREAD -> "AND (UFI.read IS NULL OR UFI.read = false) "
            FeedItemFilter.SAVED -> "AND UFI.saved = true "
            else -> ""
        }

    override suspend fun getFeedItemOfUser(userId: UserId, feedId: FeedId, feedItemId: FeedItemId): UserFeedItem? =
        client
            .sql(
                """
                |SELECT
                |FI.id, FI.feed_id, FI.title, FI.author, FI.html, FI.item_url, FI.created, UFI.saved, UFI.read
                |FROM feed_item AS FI LEFT JOIN user_feed_item AS UFI ON FI.id = UFI.feed_item_id
                |WHERE FI.ID = :feed_item_id
                |AND FI.feed_id = :feed_id
                |AND (UFI.user_id = :user_id OR UFI.user_id IS NULL)
            """.trimMargin()
            )
            .bind("feed_id", feedId.id)
            .bind("feed_item_id", feedItemId.id)
            .bind("user_id", userId.id)
            .mapToUserFeedItem()
            .awaitSingleOrNull()
}
