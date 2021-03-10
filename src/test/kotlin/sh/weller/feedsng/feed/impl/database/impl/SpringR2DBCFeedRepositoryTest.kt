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
import sh.weller.feedsng.feed.FeedId
import sh.weller.feedsng.feed.FeedItemData
import sh.weller.feedsng.feed.GroupData
import sh.weller.feedsng.user.UserId
import strikt.api.expectThat
import strikt.assertions.*
import strikt.java.time.isAfter
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertTrue

internal class SpringR2DBCFeedRepositoryTest {

    @Test
    fun `init`() {
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
    fun `insertFeed, getFeed, getFeedWithFeedURL`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val insertedId = cut.insertFeed(firstTestFeed)

            val queryById = cut.getFeed(insertedId)
            expectThat(queryById)
                .isNotNull()
                .and {
                    get { feedId }.isEqualTo(insertedId)
                    get { feedData }
                        .and {
                            get { name }.isEqualTo(firstTestFeed.name)
                            get { description }.isEqualTo(firstTestFeed.description)
                            get { feedUrl }.isEqualTo(firstTestFeed.feedUrl)
                            get { siteUrl }.isEqualTo(firstTestFeed.siteUrl)
                            get { lastUpdated }.isEqualTo(firstTestFeed.lastUpdated)
                        }
                }

            val queryByFeedURL = cut.getFeedWithFeedURL(firstTestFeed.feedUrl)
            expectThat(queryByFeedURL)
                .isNotNull()
                .and {
                    get { feedId }.isEqualTo(insertedId)
                    get { feedData }
                        .and {
                            get { name }.isEqualTo(firstTestFeed.name)
                            get { description }.isEqualTo(firstTestFeed.description)
                            get { feedUrl }.isEqualTo(firstTestFeed.feedUrl)
                            get { siteUrl }.isEqualTo(firstTestFeed.siteUrl)
                            get { lastUpdated }.isEqualTo(firstTestFeed.lastUpdated)
                        }
                }
        }
    }

    @Test
    fun `insertFeed, setFeedLastRefreshedTimestamp, getFeed`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val insertedId = cut.insertFeed(firstTestFeed)

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
    fun `insertFeed, insertFeedItems, getFeedItems, getFeedItem`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val feedId = cut.insertFeed(firstTestFeed)
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
    fun `insertUserGroup, addFeedToUserGroup, getAllUserGroups`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val firstUser = UserId(1)
            val secondUser = UserId(2)

            val firstGroup = cut.insertUserGroup(firstUser, GroupData("firstGroup", emptyList()))
            cut.addFeedToUserGroup(firstGroup, FeedId(1))
            cut.addFeedToUserGroup(firstGroup, FeedId(2))

            val secondGroup = cut.insertUserGroup(firstUser, GroupData("secondGroup", emptyList()))
            cut.addFeedToUserGroup(secondGroup, FeedId(3))

            val thirdGroup = cut.insertUserGroup(secondUser, GroupData("thirdGroup", emptyList()))
            cut.addFeedToUserGroup(thirdGroup, FeedId(4))
            cut.addFeedToUserGroup(thirdGroup, FeedId(5))

            val userGroups = cut.getAllUserGroups(firstUser).toList()
            expectThat(userGroups)
                .hasSize(2)
                .and {
                    map { it.groupData.name }
                        .containsExactlyInAnyOrder("firstGroup", "secondGroup")

                    flatMap { group -> group.groupData.feeds.map { it.id } }
                        .hasSize(3)
                        .containsExactlyInAnyOrder(1, 2, 3)
                }
        }
    }

    @Test
    fun `insertFeed, addFeedToUserGroup, addFeedToUser, getAllUserFeeds`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val firstUser = UserId(1)

            val firstFeed = cut.insertFeed(firstTestFeed)
            val secondFeed = cut.insertFeed(secondTestFeed)

            val firstGroup = cut.insertUserGroup(firstUser, GroupData("firstGroup", emptyList()))
            cut.addFeedToUserGroup(firstGroup, firstFeed)
            cut.addFeedToUser(firstUser, secondFeed)

            val feeds = cut.getAllUserFeeds(firstUser).toList()
            expectThat(feeds)
                .hasSize(2)
                .map { it.feedData.name }
                .containsExactlyInAnyOrder(firstTestFeed.name, secondTestFeed.name)
        }
    }

    @Test
    fun `getAllUserFeedItemsOfFeed, insertFeed, insertFeedItem, updateUserFeedItem`() {
        assertTrue { false }
    }

    private fun getTestSetup(): Pair<DatabaseClient, SpringR2DBCFeedRepository> {
        val factory = H2ConnectionFactory.inMemory(UUID.randomUUID().toString())
        val client = DatabaseClient.create(factory)
        val repository = SpringR2DBCFeedRepository(Dispatchers.Default, factory)
        return Pair(client, repository)
    }

    private val firstTestFeed = FeedData(
        name = "Test",
        description = "Test",
        feedUrl = "http://foo.bar",
        siteUrl = "https://bar.foo",
        lastUpdated = Instant.now()
    )
    private val secondTestFeed = FeedData(
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