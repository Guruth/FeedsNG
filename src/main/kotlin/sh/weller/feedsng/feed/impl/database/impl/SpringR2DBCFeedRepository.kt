package sh.weller.feedsng.feed.impl.database.impl

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.*
import sh.weller.feedsng.feed.*
import sh.weller.feedsng.feed.impl.database.FeedRepository
import sh.weller.feedsng.user.UserId
import java.time.Instant
import kotlin.coroutines.CoroutineContext

class SpringR2DBCFeedRepository(
    context: CoroutineContext,
    factory: ConnectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///test?options=DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
) : FeedRepository {

    private val client = DatabaseClient.create(factory)

    init {
        runBlocking(context) {
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

    override suspend fun insertFeed(feedData: FeedData): FeedId {
        val id = client
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

        return FeedId(id)
    }

    override suspend fun setFeedLastRefreshedTimestamp(feedId: FeedId) {
        client
            .sql("UPDATE feed SET last_updated = CURRENT_TIMESTAMP WHERE id = :id")
            .bind("id", feedId.id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun getFeedWithFeedURL(feedUrl: String): Feed? {
        return client
            .sql("SELECT id, name, description, feed_url, site_url, last_updated FROM feed WHERE feed_url = :feed_url")
            .bind("feed_url", feedUrl)
            .mapToFeed()
            .awaitOneOrNull()
    }

    override suspend fun getFeed(feedId: FeedId): Feed? {
        return client
            .sql("SELECT id, name, description, feed_url, site_url, last_updated FROM feed WHERE id = :id")
            .bind("id", feedId.id)
            .mapToFeed()
            .awaitOneOrNull()
    }

    override fun insertFeedItems(feedId: FeedId, feedItems: List<FeedItemData>): Flow<FeedItemId> {
        return flow {
            feedItems.forEach {
                val feedItemId = client
                    .sql(
                        """
                    |INSERT INTO feed_item 
                    |(feed_id, title, author, html, item_url, created) 
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
        }
    }

    override fun getFeedItems(feedId: FeedId, since: Instant?, limit: Int?): Flow<FeedItem> {
        return client
            .sql(
                """
                |SELECT * FROM feed_item 
                |WHERE feed_id = :feed_id 
                |${andWhereIfNotNull("created", ">=", since)} 
                |ORDER BY created 
                |${limitIfNotNull(limit)}
            """.trimMargin()
            )
            .bind("feed_id", feedId.id)
            .bindIfNotNull("created", since)
            .mapToFeedItem(feedId)
            .flow()
    }

    override suspend fun getFeedItem(feedId: FeedId, feedItemId: FeedItemId): FeedItem? {
        return client
            .sql(
                """
                |SELECT * FROM feed_item 
                |WHERE feed_id = :feed_id 
                |AND id = :id
            """.trimMargin()
            )
            .bind("feed_id", feedId.id)
            .bind("id", feedItemId.id)
            .mapToFeedItem(feedId)
            .awaitOneOrNull()
    }

    override suspend fun insertUserGroup(userId: UserId, groupData: GroupData): GroupId {
        return client
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

    override suspend fun getAllUserGroups(userId: UserId): Flow<Group> {
        return client
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

    private suspend fun Flow<Triple<GroupId, String, FeedId?>>.toGroupFlow(): Flow<Group> = flow {
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

    override suspend fun addFeedToUserGroup(groupId: GroupId, feedId: FeedId) {
        client
            .sql("INSERT INTO user_group_feed (group_id, feed_id) VALUES (:group_id, :feed_id)")
            .bind("group_id", groupId.id)
            .bind("feed_id", feedId.id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun addFeedToUser(userId: UserId, feedId: FeedId) {
        client
            .sql("INSERT INTO user_feed (user_id, feed_id) VALUES (:user_id, :feed_id)")
            .bind("user_id", userId.id)
            .bind("feed_id", feedId.id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }


    override fun getAllUserFeeds(userId: UserId): Flow<Feed> {
        val groupFeedFlow = client
            .sql(
                """
                |SELECT 
                |F.id, F.name, F.description, F.feed_url, F.site_url, F.last_updated 
                |FROM user_group AS UG LEFT JOIN user_group_feed AS UGF ON UG.id = UGF.group_id 
                |LEFT JOIN feed AS F ON UGF.feed_id = F.id
                |WHERE UG.user_id = :user_id 
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

        return flowOf(groupFeedFlow, userFeedFlow).flattenMerge()
    }

    override suspend fun updateUserFeedItem(
        userId: UserId,
        feedItemIdList: List<FeedItemId>,
        updateAction: UpdateAction
    ) {
        val columnToUpdate = when (updateAction) {
            UpdateAction.READ, UpdateAction.UNREAD -> "read"
            UpdateAction.SAVE, UpdateAction.UNSAVE -> "saved"
        }
        val updateValue = when (updateAction) {
            UpdateAction.READ, UpdateAction.SAVE -> true
            UpdateAction.UNREAD, UpdateAction.UNSAVE -> false
        }
        feedItemIdList.forEach {
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
    }

    override fun getAllUserFeedItemsOfFeed(
        userId: UserId,
        feedId: FeedId,
        since: Instant?,
        limit: Int?
    ): Flow<UserFeedItem> {
        return client
            .sql(
                """
                |SELECT 
                |FI.title, FI.author, FI.html, FI.item_url, FI.created, UFI.saved, UFI.read 
                |FROM feed_item AS FI LEFT JOIN user_feed_item AS UFI ON FI.id = UFI.feed_item_id 
                |WHERE FI.feed_id = :feed_id 
                |AND UFI.user_id = :user_id 
                |${andWhereIfNotNull("created", ">=", since)}
                |${limitIfNotNull(limit)}
                |ORDER BY created
            """.trimMargin()
            )
            .bind("feed_id", feedId.id)
            .bind("user_id", userId.id)
            .bindIfNotNull("created", since)
            .mapToUserFeedItem(feedId)
            .flow()
    }
}

private fun DatabaseClient.GenericExecuteSpec.mapToUserFeedItem(feedId: FeedId) =
    this.map { row ->
        UserFeedItem(
            isSaved = row.getReified("saved"),
            isRead = row.getReified("read"),
            feedItem = FeedItem(
                feedItemId = row.getInt("id").toFeedItemId(),
                feedId = feedId,
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

private fun DatabaseClient.GenericExecuteSpec.mapToFeedItem(feedId: FeedId) =
    this.map { row ->
        FeedItem(
            feedItemId = row.getInt("id").toFeedItemId(),
            feedId = feedId,
            feedItemData = FeedItemData(
                title = row.getReified("title"),
                author = row.getReified("author"),
                html = row.getReified("html"),
                url = row.getReified("item_url"),
                created = row.getReified("created")
            )
        )
    }

private fun DatabaseClient.GenericExecuteSpec.mapToFeed() =
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


private fun andWhereIfNotNull(fieldName: String, operator: String, value: Any?): String =
    if (value != null) {
        " AND $fieldName $operator :$fieldName"
    } else {
        ""
    }

private fun DatabaseClient.GenericExecuteSpec.bindIfNotNull(
    fieldName: String,
    value: Any?
): DatabaseClient.GenericExecuteSpec =
    if (value != null) {
        this.bind(fieldName, value)
    } else {
        this
    }

private fun limitIfNotNull(limit: Int?): String =
    if (limit != null) {
        " LIMIT $limit"
    } else {
        ""
    }