package sh.weller.feedsng.feed.impl.database.impl

import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.Row
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.flow
import sh.weller.feedsng.feed.FeedData
import sh.weller.feedsng.feed.FeedItemData
import sh.weller.feedsng.feed.GroupData
import sh.weller.feedsng.user.UserId
import strikt.api.expectThat
import strikt.assertions.*
import strikt.java.time.isAfter
import java.time.Instant
import kotlin.test.Test

internal class SpringR2DBCFeedRepositoryTest {

    @Test
    fun `tables get created on init`() {
        val (client, _) = getTestSetup()

        runBlocking {
            val mappedList = mutableListOf<String>()
            client
                .sql("SHOW TABLES")
                .map<String> { row: Row -> row.getReified("TABLE_NAME") }
                .flow()
                .toCollection(mappedList)

            expectThat(mappedList)
                .contains(
                    "FEED",
                    "FEED_ITEM",
                    "USER_GROUP",
                    "USER_GROUP_FEED",
                    "USER_FEED_ITEM"
                )
        }
    }

    @Test
    fun `insertFeed, getFeed and getFeedWithFeedURL`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val insertedId = cut.insertFeed(testFeed)

            val queryById = cut.getFeed(insertedId)
            expectThat(queryById)
                .isNotNull()
                .and {
                    get { feedId }.isEqualTo(insertedId)
                    get { feedData }
                        .and {
                            get { name }.isEqualTo(testFeed.name)
                            get { description }.isEqualTo(testFeed.description)
                            get { feedUrl }.isEqualTo(testFeed.feedUrl)
                            get { siteUrl }.isEqualTo(testFeed.siteUrl)
                            get { lastUpdated }.isEqualTo(testFeed.lastUpdated)
                        }
                }

            val queryByFeedURL = cut.getFeedWithFeedURL(testFeed.feedUrl)
            expectThat(queryByFeedURL)
                .isNotNull()
                .and {
                    get { feedId }.isEqualTo(insertedId)
                    get { feedData }
                        .and {
                            get { name }.isEqualTo(testFeed.name)
                            get { description }.isEqualTo(testFeed.description)
                            get { feedUrl }.isEqualTo(testFeed.feedUrl)
                            get { siteUrl }.isEqualTo(testFeed.siteUrl)
                            get { lastUpdated }.isEqualTo(testFeed.lastUpdated)
                        }
                }
        }
    }

    @Test
    fun `setFeedLastRefreshedTimestamp updates the timestamp`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val insertedId = cut.insertFeed(testFeed)

            val currentTimestamp = Instant.now()
            delay(500)

            cut.setFeedLastRefreshedTimestamp(insertedId)
            val feed = cut.getFeed(insertedId)

            expectThat(feed)
                .isNotNull()
                .get { feedData }
                .get { lastUpdated }
                .isAfter(currentTimestamp)
        }
    }

    @Test
    fun `insertFeedItems, getFeedItems and getItem`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val feedId = cut.insertFeed(testFeed)
            val feedItemIds = cut.insertFeedItems(feedId, testFeedItems).toList()
            expectThat(feedItemIds)
                .isNotEmpty()
                .hasSize(2)

            val feedItems = cut.getFeedItems(feedId).toList()
            expectThat(feedItems)
                .isNotEmpty()
                .hasSize(2)

            val feedItemsWithLimit = cut.getFeedItems(feedId, limit = 1).toList()
            expectThat(feedItemsWithLimit)
                .isNotEmpty()
                .hasSize(1)

            val feedItemsSince = cut.getFeedItems(feedId, since = testFeedItems.last().created).toList()
            expectThat(feedItemsSince)
                .isNotEmpty()
                .hasSize(1)

            val singleFeedItem = cut.getFeedItem(feedId, feedItemIds.first())
            expectThat(singleFeedItem)
                .isNotNull()
        }
    }

    @Test
    fun `insertUserGroup, getAllUserGroups`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            cut.insertUserGroup(UserId(1), GroupData("firstGroup", emptyList()))
            cut.insertUserGroup(UserId(1), GroupData("secondGroup", emptyList()))
            cut.insertUserGroup(UserId(2), GroupData("thirdGroup", emptyList()))

            val userGroups = cut.getAllUserGroups(UserId(1)).toList()
            expectThat(userGroups)
                .hasSize(2)
                .map { it.groupData.name }
                .containsExactlyInAnyOrder("firstGroup", "secondGroup")
        }
    }

    private fun getTestSetup(): Pair<DatabaseClient, SpringR2DBCFeedRepository> {
        val factory = H2ConnectionFactory.inMemory("testdb")
        val client = DatabaseClient.create(factory)
        val repository = SpringR2DBCFeedRepository(Dispatchers.Default, factory)
        return Pair(client, repository)
    }

    private val testFeed = FeedData(
        name = "Test",
        description = "Test",
        feedUrl = "http://foo.bar",
        siteUrl = "https://bar.foo",
        lastUpdated = Instant.now()
    )

    private val testFeedItems = listOf(
        FeedItemData(
            title = "TestItem1",
            author = "TestAuthor",
            html = "<body>Foo</body>",
            url = "https://foo.bar/",
            created = Instant.now().minusMillis(5000)
        ),
        FeedItemData(
            title = "TestItem1",
            author = "TestAuthor",
            html = "<body>Foo</body>",
            url = "https://foo.bar/",
            created = Instant.now().minusMillis(1000)
        )
    )

}
