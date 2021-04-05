package sh.weller.feedsng.feed.impl.database.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.r2dbc.core.*
import org.springframework.stereotype.Repository
import sh.weller.feedsng.feed.*
import sh.weller.feedsng.feed.impl.database.FeedRepository
import sh.weller.feedsng.user.UserId
import java.time.Instant

@OptIn(FlowPreview::class)
@Repository
class SpringR2DBCFeedRepository(
    private val client: DatabaseClient
) : FeedRepository {

    init {
        runBlocking {
            client
                .sql(
                    """
                CREATE TABLE IF NOT EXISTS feed(  
                    id INTEGER AUTO_INCREMENT PRIMARY KEY, 
                    name VARCHAR(256), 
                    description VARCHAR(2048), 
                    feed_url VARCHAR(2048), 
                    site_url VARCHAR(2048), 
                    last_updated TIMESTAMP WITH TIME ZONE
                )
            """.trimMargin()
                )
                .await()

            client
                .sql(
                    """
                CREATE TABLE IF NOT EXISTS feed_item(  
                    id INTEGER AUTO_INCREMENT PRIMARY KEY, 
                    feed_id INTEGER,
                    title VARCHAR(2048), 
                    author VARCHAR(256),
                    html TEXT, 
                    item_url VARCHAR(2048), 
                    created TIMESTAMP WITH TIME ZONE 
                )
            """.trimMargin()
                )
                .await()

            client
                .sql(
                    """
                CREATE TABLE IF NOT EXISTS user_group(  
                    id INTEGER AUTO_INCREMENT PRIMARY KEY, 
                    user_id INTEGER,
                    name VARCHAR(256)
                )
            """.trimMargin()
                )
                .await()

            client
                .sql(
                    """
                CREATE TABLE IF NOT EXISTS user_group_feed(  
                    group_id INTEGER, 
                    feed_id INTEGER
                )
            """.trimMargin()
                )
                .await()

            client
                .sql(
                    """
                CREATE TABLE IF NOT EXISTS user_feed(
                    user_id INTEGER,
                    feed_id INTEGER
                )
                """.trimIndent()
                )
                .await()

            client
                .sql(
                    """
                    CREATE TABLE IF NOT EXISTS user_feed_item(  
                        feed_item_id INTEGER,
                        user_id INTEGER,
                        saved BOOLEAN DEFAULT FALSE,
                        read BOOLEAN DEFAULT FALSE
                    )
                    """.trimMargin()
                )
                .await()

        }
    }

    override suspend fun insertFeed(feedData: FeedData): FeedId =
        withContext(Dispatchers.IO) {
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
        }


    override suspend fun setFeedLastRefreshedTimestamp(feedId: FeedId) =
        withContext(Dispatchers.IO) {
            client
                .sql("UPDATE feed SET last_updated = CURRENT_TIMESTAMP WHERE id = :id")
                .bind("id", feedId.id)
                .await()
        }


    override suspend fun getFeedWithFeedURL(feedUrl: String): Feed? =
        withContext(Dispatchers.IO) {
            client
                .sql("SELECT id, name, description, feed_url, site_url, last_updated FROM feed WHERE feed_url = :feed_url")
                .bind("feed_url", feedUrl)
                .mapToFeed()
                .awaitOneOrNull()
        }

    override suspend fun getFeed(feedId: FeedId): Feed? =
        withContext(Dispatchers.IO) {
            client
                .sql("SELECT id, name, description, feed_url, site_url, last_updated FROM feed WHERE id = :id")
                .bind("id", feedId.id)
                .mapToFeed()
                .awaitOneOrNull()
        }

    override suspend fun getAllFeeds(): Flow<Feed> =
        withContext(Dispatchers.IO) {
            client
                .sql("SELECT id, name, description, feed_url, site_url, last_updated FROM feed")
                .mapToFeed()
                .flow()
        }

    override fun insertFeedItemsIfNotExist(feedId: FeedId, feedItemDataFlow: Flow<FeedItemData>): Flow<FeedItemId> =
        feedItemDataFlow
            .transform {
                val feedItemId = client
                    .sql(
                        """
                    |MERGE INTO feed_item (feed_id, title, author, html, item_url, created) 
                    |KEY (feed_id, item_url)
                    |VALUES (:feed_id, :title, :author, :html, :item_url, :created)""".trimMargin()
                    )
                    .bind("feed_id", feedId.id)
                    .bind("title", it.title)
                    .bind("author", it.author)
                    .bind("html", it.html)
                    .bind("item_url", it.url)
                    .bind("created", it.created)
                    .filter { s -> s.returnGeneratedValues() }
                    .map { row -> row.get("id", Integer::class.java)!!.toInt().toFeedItemId() }
                    .awaitSingle()
                emit(feedItemId)
            }
            .flowOn(Dispatchers.IO)

    override suspend fun getAllFeedItemIds(feedId: FeedId, before: Instant?): Flow<FeedItemId> =
        withContext(Dispatchers.IO) {
            client
                .sql(
                    """
                |SELECT id FROM feed_item 
                |WHERE feed_id = :feed_id 
                |${andWhereIfNotNull("created", "createdBefore", "<", before)} 
                |ORDER BY created 
            """.trimMargin()
                )
                .bind("feed_id", feedId.id)
                .bindIfNotNull("createdBefore", before)
                .map { row -> row.get("id", Integer::class.java)!!.toInt().toFeedItemId() }
                .flow()
        }


    override suspend fun insertUserGroup(userId: UserId, groupData: GroupData): GroupId =
        withContext(Dispatchers.IO) {
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
        }

    override suspend fun getAllUserGroups(userId: UserId): Flow<Group> =
        withContext(Dispatchers.IO) {
            client
                .sql(
                    """
                |SELECT 
                |UG.id, UG.name, UGF.feed_id
                |FROM user_group AS UG LEFT JOIN user_group_feed AS UGF ON UG.id = UGF.group_id 
                |WHERE UG.user_id = :user_id 
                |ORDER BY UG.id
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
        }

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
        withContext(Dispatchers.IO) {
            client
                .sql("INSERT INTO user_group_feed (group_id, feed_id) VALUES (:group_id, :feed_id)")
                .bind("group_id", groupId.id)
                .bind("feed_id", feedId.id)
                .await()
        }

    override suspend fun addFeedToUser(userId: UserId, feedId: FeedId) =
        withContext(Dispatchers.IO) {
            client
                .sql("INSERT INTO user_feed (user_id, feed_id) VALUES (:user_id, :feed_id)")
                .bind("user_id", userId.id)
                .bind("feed_id", feedId.id)
                .await()
        }

    override suspend fun getAllUserFeeds(userId: UserId): Flow<Feed> = withContext(Dispatchers.IO) {
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

        return@withContext flowOf(groupFeedFlow, userFeedFlow).flattenConcat()
    }

    override suspend fun updateUserFeedItem(
        userId: UserId,
        feedItemIdFlow: Flow<FeedItemId>,
        updateAction: UpdateAction
    ) {
        val columnToUpdate = updateAction.getUpdateColumnName()
        val updateValue = updateAction.getUpdateValue()

        feedItemIdFlow
            .onEach {
                client
                    .sql(
                        """
                |MERGE INTO user_feed_item (feed_item_id, user_id, $columnToUpdate) 
                |KEY (feed_item_id, user_id) 
                |VALUES (:feed_item_id, :user_id, :updateValue) 
            """.trimMargin()
                    )
                    .bind("feed_item_id", it.id)
                    .bind("user_id", userId.id)
                    .bind("updateValue", updateValue)
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()
            }
            .flowOn(Dispatchers.IO)
            .collect()
    }

    override suspend fun getAllUserFeedItemsOfFeed(
        userId: UserId,
        feedId: FeedId,
        filter: FeedItemFilter?,
        since: Instant?
    ): Flow<UserFeedItem> =
        withContext(Dispatchers.IO) {
            val filterQuery = filter.toWhereClause()

            return@withContext client
                .sql(
                    """
                |SELECT 
                |FI.id, FI.feed_id, FI.title, FI.author, FI.html, FI.item_url, FI.created, UFI.saved, UFI.read 
                |FROM feed_item AS FI LEFT JOIN user_feed_item AS UFI ON FI.id = UFI.feed_item_id 
                |WHERE FI.feed_id = :feed_id 
                |AND (UFI.user_id = :user_id OR UFI.user_id IS NULL) 
                |${andWhereIfNotNull("FI.created", "createdSince", ">=", since)}
                |$filterQuery 
                |ORDER BY created 
            """.trimMargin()
                )
                .bind("feed_id", feedId.id)
                .bind("user_id", userId.id)
                .bindIfNotNull("createdSince", since)
                .mapToUserFeedItem()
                .flow()
        }

    override suspend fun getAllUserFeedItemIdsOfFeed(
        userId: UserId,
        feedId: FeedId,
        filter: FeedItemFilter?,
        since: Instant?
    ): Flow<FeedItemId> = withContext(Dispatchers.IO) {
        val filterQuery = filter.toWhereClause()

        return@withContext client
            .sql(
                """
                |SELECT FI.id 
                |FROM feed_item AS FI LEFT JOIN user_feed_item AS UFI ON FI.id = UFI.feed_item_id 
                |WHERE FI.feed_id = :feed_id 
                |AND (UFI.user_id = :user_id OR UFI.user_id IS NULL) 
                |${andWhereIfNotNull("FI.created", "createdSince", ">=", since)}
                |$filterQuery 
                |ORDER BY created 
            """.trimMargin()
            )
            .bind("feed_id", feedId.id)
            .bind("user_id", userId.id)
            .bindIfNotNull("createdSince", since)
            .map { row -> row.get("id", Integer::class.java)!!.toInt().toFeedItemId() }
            .flow()
            .flowOn(Dispatchers.IO)
    }

    private fun FeedItemFilter?.toWhereClause(): String =
        when (this) {
            FeedItemFilter.READ -> "AND UFI.read = true "
            FeedItemFilter.UNREAD -> "AND (UFI.read IS NULL OR UFI.read = false) "
            FeedItemFilter.SAVED -> "AND UFI.saved = true "
            else -> ""
        }

    override suspend fun getUserFeedItem(userId: UserId, feedId: FeedId, feedItemId: FeedItemId): UserFeedItem? =
        withContext(Dispatchers.IO) {
            client
                .sql(
                    """
                |SELECT
                |FI.id, FI.feed_id, FI.title, FI.author, FI.html, FI.item_url, FI.created, UFI.saved, UFI.read
                |FROM feed_item AS FI LEFT JOIN user_feed_item AS UFI ON FI.id = UFI.feed_item_id
                |WHERE FI.ID = :feed_item_id
                |AND FI.feed_id = :feed_id
                |AND (UFI.user_id = :user_id OR UFI.user_id IS NULL)
                |ORDER BY created
            """.trimMargin()
                )
                .bind("feed_id", feedId.id)
                .bind("feed_item_id", feedItemId.id)
                .bind("user_id", userId.id)
                .mapToUserFeedItem()
                .awaitSingleOrNull()
        }
}
