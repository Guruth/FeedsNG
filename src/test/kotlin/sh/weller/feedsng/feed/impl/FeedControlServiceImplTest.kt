package sh.weller.feedsng.feed.impl

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import sh.weller.feedsng.common.Failure
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.common.valueOrNull
import sh.weller.feedsng.feed.api.provided.FeedControlService
import sh.weller.feedsng.feed.api.provided.FeedUpdateAction
import sh.weller.feedsng.feed.api.required.FeedRepository
import sh.weller.feedsng.user.api.provided.UserId
import strikt.api.expectThat
import strikt.assertions.*
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull


@SpringBootTest(
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///~/db/testdb"
    ]
)
internal class FeedControlServiceImplTest(
    @Autowired private val databaseClient: DatabaseClient,
    @Autowired private val repo: FeedRepository,
    @Autowired private val cut: FeedControlService
) {

    @BeforeTest
    fun cleanupDatabase(): Unit = runBlocking {
        databaseClient.sql("TRUNCATE TABLE FEED").await()
        databaseClient.sql("TRUNCATE TABLE FEED_ITEM").await()
        databaseClient.sql("TRUNCATE TABLE USER_GROUP").await()
        databaseClient.sql("TRUNCATE TABLE USER_GROUP_FEED").await()
        databaseClient.sql("TRUNCATE TABLE USER_FEED").await()
        databaseClient.sql("TRUNCATE TABLE USER_FEED_ITEM").await()

        databaseClient.sql("TRUNCATE TABLE ACCOUNT").await()
        databaseClient.sql("TRUNCATE TABLE INVITE_CODE").await()
    }

    @Test
    fun importFromOPML() {
        val userId = UserId(1)
        val testFileContents = File(javaClass.classLoader.getResource("ValidOPML.xml")!!.file).readText()

        runBlocking {
            val importResult = cut.importFromOPML(userId, testFileContents)
            expectThat(importResult)
                .isA<Success<Unit>>()

            val importedFeeds = repo.getAllFeedsOfUser(userId).toList()

            expectThat(importedFeeds)
                .isNotEmpty()
        }
    }

    @Test
    fun `addGroup, addFeed and addFeedToGroup`() {
        val userId = UserId(1)

        runBlocking {
            val groupId = cut.addGroup(userId, "TestGroup")

            val groups = repo.getAllUserGroups(userId).toList()
            expectThat(groups)
                .hasSize(1)
                .map { it.groupId }
                .containsExactly(groupId)

            val feedIdWithoutGroup = cut.addFeed(userId, "https://blog.jetbrains.com/kotlin/feed/").valueOrNull()
            assertNotNull(feedIdWithoutGroup)

            val duplicateFeed = cut.addFeed(userId, "https://blog.jetbrains.com/kotlin/feed/")
            assertIs<Failure<String>>(duplicateFeed)

            val feedIdWithGroup = cut.addFeedToGroup(userId, groupId, "https://blog.jetbrains.com/feed/").valueOrNull()
            assertNotNull(feedIdWithGroup)

            val feeds = repo.getAllFeedsOfUser(userId).toList()
            expectThat(feeds)
                .hasSize(2)
                .map { it.feedId }
                .containsExactlyInAnyOrder(feedIdWithoutGroup, feedIdWithGroup)
        }
    }

    @Test
    fun `updateGroup, updateFeed, updateFeedItem`() {
        val userId = UserId(1)

        runBlocking {
            val groupId = cut.addGroup(userId, "TestGroup")

            val feedIdWithoutGroup = cut.addFeed(userId, "https://blog.jetbrains.com/kotlin/feed/").valueOrNull()
            assertNotNull(feedIdWithoutGroup)
            val feedIdWithGroup = cut.addFeedToGroup(userId, groupId, "https://blog.jetbrains.com/feed/").valueOrNull()
            assertNotNull(feedIdWithGroup)

            cut.updateGroup(userId, groupId, FeedUpdateAction.SAVE)
            val savedItems = repo.getAllFeedItemsOfUser(userId, listOf(feedIdWithGroup)).toList()
            expectThat(savedItems)
                .isNotEmpty()
                .map { it.isSaved }
                .doesNotContain(false)

            cut.updateFeed(userId, feedIdWithoutGroup, FeedUpdateAction.READ)
            val readItems = repo.getAllFeedItemsOfUser(userId, listOf(feedIdWithoutGroup)).toList()
            expectThat(readItems)
                .isNotEmpty()
                .map { it.isRead }
                .doesNotContain(false)

            cut.updateFeedItem(userId, readItems.first().feedItem.feedItemId, FeedUpdateAction.UNREAD)
            cut.updateFeedItem(userId, readItems.first().feedItem.feedItemId, FeedUpdateAction.SAVE)
            val unreadItem =
                repo.getFeedItemOfUser(userId, readItems.first().feedItem.feedId, readItems.first().feedItem.feedItemId)
            expectThat(unreadItem)
                .isNotNull()
                .and {
                    get { isRead }.isEqualTo(false)
                    get { isSaved }.isEqualTo(true)
                }
        }
    }
}