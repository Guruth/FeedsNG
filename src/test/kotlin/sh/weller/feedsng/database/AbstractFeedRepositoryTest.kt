package sh.weller.feedsng.database

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.feed.api.required.FeedRepository
import sh.weller.feedsng.user.api.provided.UserId
import strikt.api.expectThat
import strikt.assertions.*
import strikt.java.time.isAfter
import java.time.Instant
import kotlin.test.Test


internal abstract class AbstractFeedRepositoryTest {

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
    fun `insertFeed, insertFeedItemsIfNotExist, getFeedItems, getFeedItem, getAllFeedItemIdsOfFeed`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val feedId = cut.insertFeed(firstTestFeed)
            val feedItemIds = cut.insertFeedItemsIfNotExist(feedId, flowOf(*testFeedItems.toTypedArray())).toList()
            expectThat(feedItemIds)
                .isNotEmpty()
                .hasSize(3)

            val duplicatedItems = cut.insertFeedItemsIfNotExist(feedId, flowOf(*testFeedItems.toTypedArray())).toList()
            expectThat(duplicatedItems)
                .isNotEmpty()
                .containsExactlyInAnyOrder(feedItemIds)

            val allFeedIds = cut.getAllFeedItemIdsOfFeed(feedId).toList()
            expectThat(allFeedIds)
                .isNotEmpty()
                .containsExactlyInAnyOrder(feedItemIds)

            val feedIdsBefore = cut.getAllFeedItemIdsOfFeed(feedId, before = testFeedItems.last().created).toList()
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
    fun `insertFeed, addFeedToUserGroup, addFeedToUser, getAllFeedsOfUser`() {
        val (_, cut) = getTestSetup()

        runBlocking {
            val user = UserId(1)

            val firstFeed = cut.insertFeed(firstTestFeed)
            val secondFeed = cut.insertFeed(secondTestFeed)

            val firstGroup = cut.insertUserGroup(user, GroupData("firstGroup", emptyList()))
            cut.addFeedToUserGroup(firstGroup, firstFeed)
            cut.addFeedToUser(user, secondFeed)

            cut.insertUserGroup(user, GroupData("emptyGroup", emptyList()))

            val feeds = cut.getAllFeedsOfUser(user).toList()
            expectThat(feeds)
                .hasSize(2)
                .map { it.feedData.name }
                .containsExactlyInAnyOrder(firstTestFeed.name, secondTestFeed.name)
        }
    }

    @Test
    fun `getAllFeedItemsOfUser, insertFeed, insertFeedItem, updateUserFeedItem`() {
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
            cut.updateFeedItemOfUser(user, flowOf(feedItemIds.first()), FeedUpdateAction.READ)

            val userFeedItems = cut.getAllFeedItemsOfUser(
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

            val sinceFilteredItems = cut.getAllFeedItemsOfUser(
                user,
                listOf(firstFeedId),
                FeedItemIdFilter.SinceIdFilter(userFeedItems.last().feedItem.feedItemId)
            ).toList()
            expectThat(sinceFilteredItems)
                .hasSize(2)

            val maxFilteredItems = cut.getAllFeedItemsOfUser(
                user,
                listOf(firstFeedId),
                FeedItemIdFilter.MaxIdFilter(userFeedItems.first().feedItem.feedItemId)
            ).toList()
            expectThat(maxFilteredItems)
                .hasSize(3)

            val withIdsFilteredItems = cut.getAllFeedItemsOfUser(
                user,
                listOf(firstFeedId),
                FeedItemIdFilter.WithIdFilter(userFeedItems.drop(1).map { it.feedItem.feedItemId })
            ).toList()
            expectThat(withIdsFilteredItems)
                .hasSize(2)

            val limitedUserFeedItems =
                cut.getAllFeedItemsOfUser(
                    user,
                    listOf(firstFeedId),
                    limit = 1
                ).toList()
            expectThat(limitedUserFeedItems)
                .hasSize(1)

            val countedFeedItems = cut.countFeedItemsOfFeedOfUser(user, firstFeedId, null)
            expectThat(countedFeedItems)
                .isEqualTo(3)

            val countedFeedItemsWitFilter = cut.countFeedItemsOfFeedOfUser(user, firstFeedId, FeedItemFilter.UNREAD)
            expectThat(countedFeedItemsWitFilter)
                .isEqualTo(2)
        }
    }

    // TODO Test with Date 0000-12-30T00:00:00Z


    abstract fun getTestSetup(): Pair<DatabaseClient, FeedRepository>

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