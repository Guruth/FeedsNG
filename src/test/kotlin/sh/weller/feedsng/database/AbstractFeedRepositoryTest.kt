package sh.weller.feedsng.database

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.feed.api.required.FeedRepository
import sh.weller.feedsng.user.api.provided.UserId
import strikt.api.expectThat
import strikt.assertions.*
import strikt.java.time.isAfter
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test


internal abstract class AbstractFeedRepositoryTest(
    private val databaseClient: DatabaseClient,
    private val repository: FeedRepository
) {

    @BeforeTest
    fun cleanupDatabase(): Unit = runBlocking {
        databaseClient.sql("TRUNCATE TABLE FEED").await()
        databaseClient.sql("TRUNCATE TABLE FEED_ITEM").await()
        databaseClient.sql("TRUNCATE TABLE USER_GROUP").await()
        databaseClient.sql("TRUNCATE TABLE USER_GROUP_FEED").await()
        databaseClient.sql("TRUNCATE TABLE USER_FEED").await()
        databaseClient.sql("TRUNCATE TABLE USER_FEED_ITEM").await()
    }

    @Test
    fun `insertFeed, getFeed, getFeedWithFeedURL, getAllFeeds`() {
        runBlocking {
            val insertedId = repository.insertFeed(firstTestFeed)
            repository.insertFeed(secondTestFeed)

            val queryById = repository.getFeed(insertedId)
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

            val queryByFeedURL = repository.getFeedWithFeedURL(firstTestFeed.feedUrl)
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

            val allFeeds = repository.getAllFeeds().toList()
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
        runBlocking {
            val insertedId = repository.insertFeed(firstTestFeed)

            val currentTimestamp = Instant.now()
            delay(500)

            repository.setFeedLastRefreshedTimestamp(insertedId)
            val feed = repository.getFeed(insertedId)

            expectThat(feed)
                .isNotNull()
                .get { feedData }
                .get { lastUpdated }
                .isAfter(currentTimestamp)
        }
    }

    @Test
    fun `insertFeed, insertFeedItemsIfNotExist, getFeedItems, getFeedItem, getAllFeedItemIdsOfFeed`() {
        runBlocking {
            val feedId = repository.insertFeed(firstTestFeed)
            val feedItemIds =
                repository.insertFeedItemsIfNotExist(feedId, flowOf(*testFeedItems.toTypedArray())).toList()
            expectThat(feedItemIds)
                .isNotEmpty()
                .hasSize(3)

            val duplicatedItems =
                repository.insertFeedItemsIfNotExist(feedId, flowOf(*testFeedItems.toTypedArray())).toList()
            expectThat(duplicatedItems)
                .isNotEmpty()
                .containsExactlyInAnyOrder(feedItemIds)

            val allFeedIds = repository.getAllFeedItemIdsOfFeed(feedId).toList()
            expectThat(allFeedIds)
                .isNotEmpty()
                .containsExactlyInAnyOrder(feedItemIds)

            val feedIdsBefore =
                repository.getAllFeedItemIdsOfFeed(feedId, before = testFeedItems.last().created).toList()
            expectThat(feedIdsBefore)
                .isNotEmpty()
                .hasSize(2)
        }
    }

    @Test
    fun `insertUserGroup, addFeedToUserGroup, getAllUserGroups`() {
        runBlocking {
            val firstUser = UserId(1)
            val secondUser = UserId(2)

            val firstGroup = repository.insertUserGroup(firstUser, GroupData("firstGroup", emptyList()))
            repository.addFeedToUserGroup(firstGroup, FeedId(1))
            repository.addFeedToUserGroup(firstGroup, FeedId(2))

            val secondGroup = repository.insertUserGroup(firstUser, GroupData("secondGroup", emptyList()))
            repository.addFeedToUserGroup(secondGroup, FeedId(3))

            val thirdGroup = repository.insertUserGroup(secondUser, GroupData("thirdGroup", emptyList()))
            repository.addFeedToUserGroup(thirdGroup, FeedId(4))
            repository.addFeedToUserGroup(thirdGroup, FeedId(5))

            val userGroups = repository.getAllUserGroups(firstUser).toList()
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
    fun `insertFeed, addFeedToUserGroup, addFeedToUser, getAllFeedsOfUser`() {
        runBlocking {
            val user = UserId(1)

            val firstFeed = repository.insertFeed(firstTestFeed)
            val secondFeed = repository.insertFeed(secondTestFeed)

            val firstGroup = repository.insertUserGroup(user, GroupData("firstGroup", emptyList()))
            repository.addFeedToUserGroup(firstGroup, firstFeed)
            repository.addFeedToUser(user, secondFeed)

            repository.insertUserGroup(user, GroupData("emptyGroup", emptyList()))

            val feeds = repository.getAllFeedsOfUser(user).toList()
            expectThat(feeds)
                .hasSize(2)
                .map { it.feedData.name }
                .containsExactlyInAnyOrder(firstTestFeed.name, secondTestFeed.name)
        }
    }

    @Test
    fun `getAllFeedItemsOfUser, insertFeed, insertFeedItem, updateUserFeedItem`() {
        runBlocking {
            val user = UserId(1)

            val firstFeedId = repository.insertFeed(firstTestFeed)
            val feedItemIds =
                repository.insertFeedItemsIfNotExist(firstFeedId, flowOf(*testFeedItems.toTypedArray())).toList()

            val secondFeedId = repository.insertFeed(secondTestFeed)
            repository.insertFeedItemsIfNotExist(
                secondFeedId,
                flowOf(FeedItemData("Test3", "Test", "asdfasdf", "adfsadf", Instant.now()))
            )

            repository.addFeedToUser(user, firstFeedId)
            repository.updateFeedItemOfUser(user, flowOf(feedItemIds.first()), FeedUpdateAction.READ)

            val userFeedItems = repository.getAllFeedItemsOfUser(
                user,
                listOf(firstFeedId)
            ).toList()
            expectThat(userFeedItems)
                .hasSize(3)
                .and {
                    map { it.isRead }
                        .containsExactlyInAnyOrder(true, false, false)
                    map { it.isSaved }
                        .containsExactly(false, false, false)
                }

            val sinceFilteredItems = repository.getAllFeedItemsOfUser(
                user,
                listOf(firstFeedId),
                FeedItemIdFilter.SinceIdFilter(userFeedItems.last().feedItem.feedItemId)
            ).toList()
            expectThat(sinceFilteredItems)
                .hasSize(2)

            val maxFilteredItems = repository.getAllFeedItemsOfUser(
                user,
                listOf(firstFeedId),
                FeedItemIdFilter.MaxIdFilter(userFeedItems.first().feedItem.feedItemId)
            ).toList()
            expectThat(maxFilteredItems)
                .hasSize(3)

            val withIdsFilteredItems = repository.getAllFeedItemsOfUser(
                user,
                listOf(firstFeedId),
                FeedItemIdFilter.WithIdFilter(userFeedItems.drop(1).map { it.feedItem.feedItemId })
            ).toList()
            expectThat(withIdsFilteredItems)
                .hasSize(2)

            val limitedUserFeedItems =
                repository.getAllFeedItemsOfUser(
                    user,
                    listOf(firstFeedId),
                    limit = 1
                ).toList()
            expectThat(limitedUserFeedItems)
                .hasSize(1)

            val countedFeedItems = repository.countFeedItemsOfFeedOfUser(user, firstFeedId, null)
            expectThat(countedFeedItems)
                .isEqualTo(3)

            val countedFeedItemsWitFilter =
                repository.countFeedItemsOfFeedOfUser(user, firstFeedId, FeedItemFilter.UNREAD)
            expectThat(countedFeedItemsWitFilter)
                .isEqualTo(2)
        }
    }

    // TODO Test with Date 0000-12-30T00:00:00Z

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