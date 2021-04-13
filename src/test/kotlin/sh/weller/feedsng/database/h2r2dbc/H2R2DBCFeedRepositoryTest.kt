package sh.weller.feedsng.database.h2r2dbc

import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.Row
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.flow
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.user.api.provided.UserId
import strikt.api.expectThat
import strikt.assertions.*
import strikt.java.time.isAfter
import java.time.Instant
import java.util.*
import kotlin.test.Test

internal class H2R2DBCFeedRepositoryTest {

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
    fun `insertFeed, getFeed, getFeedWithFeedURL, getAllFeeds`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val insertedId = cut.insertFeed(firstTestFeed)
            cut.insertFeed(secondTestFeed)

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

            val allFeeds = cut.getAllFeeds().toList()
            expectThat(allFeeds)
                .isNotEmpty()
                .and {
                    map { it.feedData.name }
                        .containsExactlyInAnyOrder(firstTestFeed.name, secondTestFeed.name)
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
    fun `insertFeed, insertFeedItemsIfNotExist, getFeedItems, getFeedItem, getAllFeedItemIds`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val feedId = cut.insertFeed(firstTestFeed)
            val feedItemIds = cut.insertFeedItemsIfNotExist(feedId, flowOf(*testFeedItems.toTypedArray())).toList()
            expectThat(feedItemIds)
                .isNotEmpty()
                .hasSize(3)

            val duplicatedItems = cut.insertFeedItemsIfNotExist(feedId, flowOf(*testFeedItems.toTypedArray())).toList()
            expectThat(duplicatedItems)
                .containsExactlyInAnyOrder(feedItemIds)

            val allFeedIds = cut.getAllFeedItemIds(feedId).toList()
            expectThat(allFeedIds)
                .isNotEmpty()
                .containsExactlyInAnyOrder(feedItemIds)

            val feedIdsBefore = cut.getAllFeedItemIds(feedId, before = testFeedItems.last().created).toList()
            expectThat(feedIdsBefore)
                .isNotEmpty()
                .hasSize(2)
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
            val user = UserId(1)

            val firstFeed = cut.insertFeed(firstTestFeed)
            val secondFeed = cut.insertFeed(secondTestFeed)

            val firstGroup = cut.insertUserGroup(user, GroupData("firstGroup", emptyList()))
            cut.addFeedToUserGroup(firstGroup, firstFeed)
            cut.addFeedToUser(user, secondFeed)

            cut.insertUserGroup(user, GroupData("emptyGroup", emptyList()))

            val feeds = cut.getAllUserFeeds(user).toList()
            expectThat(feeds)
                .hasSize(2)
                .map { it.feedData.name }
                .containsExactlyInAnyOrder(firstTestFeed.name, secondTestFeed.name)
        }
    }

    @Test
    fun `getAllUserFeedItemsOfFeed, insertFeed, insertFeedItem, updateUserFeedItemForFeedItems`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val user = UserId(1)

            val firstFeedId = cut.insertFeed(firstTestFeed)
            val feedItemIds = cut.insertFeedItemsIfNotExist(firstFeedId, flowOf(*testFeedItems.toTypedArray())).toList()

            val secondFeedId = cut.insertFeed(secondTestFeed)
            cut.insertFeedItemsIfNotExist(
                secondFeedId,
                flowOf(FeedItemData("Test3", "Test", "asdfasdf", "adfsadf", Instant.now()))
            )

            cut.addFeedToUser(user, firstFeedId)
            cut.updateUserFeedItem(user, flowOf(feedItemIds.first()), FeedUpdateAction.READ)

            val userFeedItems = cut.getAllUserFeedItemsOfFeed(user, firstFeedId).toList()
            expectThat(userFeedItems)
                .hasSize(3)
                .and {
                    map { it.isRead }
                        .containsExactlyInAnyOrder(true, false, false)
                    map { it.isSaved }
                        .containsExactly(false, false, false)
                }

            val unreadUserFeedItems =
                cut.getAllUserFeedItemsOfFeed(user, firstFeedId, filter = FeedItemFilter.UNREAD).toList()

            expectThat(unreadUserFeedItems)
                .hasSize(2)
                .and {
                    map { it.isRead }
                        .containsExactly(false, false)
                    map { it.isSaved }
                        .containsExactly(false, false)
                }

            val readUserFeedItemsBetween =
                cut.getAllUserFeedItemsOfFeed(
                    user,
                    firstFeedId,
                    since = testFeedItems.first().created.plusMillis(100)
                ).toList()
            expectThat(readUserFeedItemsBetween)
                .hasSize(2)
        }
    }

    private fun getTestSetup(): Pair<DatabaseClient, H2R2DBCFeedRepository> {
        val factory = H2ConnectionFactory.inMemory(UUID.randomUUID().toString())
        val client = DatabaseClient.create(factory)
        val repo = H2R2DBCFeedRepository(client)
        return Pair(client, repo)
    }

    private val firstTestFeed = FeedData(
        name = "Test",
        description = "Test",
        feedUrl = "http://foo.bar",
        siteUrl = "https://bar.foo",
        lastUpdated = Instant.now().minusMillis(500)
    )
    private val secondTestFeed = FeedData(
        name = "Test",
        description = "Test",
        feedUrl = "http://foobar.bar",
        siteUrl = "https://barfoo.foo",
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
            title = "TestItem2",
            author = "TestAuthor",
            html = "<body>Foo</body>",
            url = "https://foo.baz/",
            created = Instant.now().minusMillis(1000)
        ),
        FeedItemData(
            title = "TestItem3",
            author = "TestAuthor",
            html = "<body>Foo</body>",
            url = "https://foo.bux/",
            created = Instant.now()
        )
    )

}