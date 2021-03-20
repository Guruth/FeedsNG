package sh.weller.feedsng.feed.impl

import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.common.valueOrNull
import sh.weller.feedsng.feed.UpdateAction
import sh.weller.feedsng.feed.impl.database.FeedRepository
import sh.weller.feedsng.feed.impl.database.impl.SpringR2DBCFeedRepository
import sh.weller.feedsng.feed.impl.fetch.impl.RomeFeedFetcherServiceImpl
import sh.weller.feedsng.feed.impl.import.impl.RomeOPMLFeedImportServiceImpl
import sh.weller.feedsng.user.UserId
import strikt.api.expectThat
import strikt.assertions.*
import java.io.File
import java.util.*
import kotlin.test.assertNotNull

internal class FeedControlServiceImplTest {


    @Test
    fun importFromOPML() {
        val (repo, cut) = getTestSetup()
        val userId = UserId(1)
        val testFileContents = File(javaClass.classLoader.getResource("ValidOPML.xml")!!.file).readText()

        runBlocking {
            val importResult = cut.importFromOPML(userId, testFileContents)
            expectThat(importResult)
                .isA<Success<List<Pair<String, String>>>>()
                .get { value }
                .isEmpty()

            val importedFeeds = repo.getAllUserFeeds(userId).toList()

            expectThat(importedFeeds)
                .isNotEmpty()
        }
    }

    @Test
    fun `addGroup, addFeed and addFeedToGroup`() {
        val (repo, cut) = getTestSetup()
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
            val feedIdWithGroup = cut.addFeedToGroup(userId, groupId, "https://blog.jetbrains.com/feed/").valueOrNull()
            assertNotNull(feedIdWithGroup)

            val feeds = repo.getAllUserFeeds(userId).toList()
            expectThat(feeds)
                .hasSize(2)
                .map { it.feedId }
                .containsExactlyInAnyOrder(feedIdWithoutGroup, feedIdWithGroup)

            val feedItemsWithoutGroup = repo.getAllFeedItems(feedIdWithoutGroup).toList()
            expectThat(feedItemsWithoutGroup)
                .isNotEmpty()

            val feedItemsWithGroup = repo.getAllFeedItems(feedIdWithGroup).toList()
            expectThat(feedItemsWithGroup)
                .isNotEmpty()

            expectThat(feedItemsWithoutGroup)
                .isNotEqualTo(feedItemsWithGroup)

        }
    }

    @Test
    fun `updateGroup, updateFeed, updateFeedItem`() {
        val (repo, cut) = getTestSetup()
        val userId = UserId(1)

        runBlocking {
            val groupId = cut.addGroup(userId, "TestGroup")

            val feedIdWithoutGroup = cut.addFeed(userId, "https://blog.jetbrains.com/kotlin/feed/").valueOrNull()
            assertNotNull(feedIdWithoutGroup)
            val feedIdWithGroup = cut.addFeedToGroup(userId, groupId, "https://blog.jetbrains.com/feed/").valueOrNull()
            assertNotNull(feedIdWithGroup)

            cut.updateGroup(userId, groupId, UpdateAction.SAVE)
            val savedItems = repo.getAllUserFeedItemsOfFeed(userId, feedIdWithGroup).toList()
            expectThat(savedItems)
                .isNotEmpty()
                .map { it.isSaved }
                .doesNotContain(false)

            cut.updateFeed(userId, feedIdWithoutGroup, UpdateAction.READ)
            val readItems = repo.getAllUserFeedItemsOfFeed(userId, feedIdWithoutGroup).toList()
            expectThat(readItems)
                .isNotEmpty()
                .map { it.isRead }
                .doesNotContain(false)

            cut.updateFeedItem(userId, readItems.first().feedItem.feedItemId, UpdateAction.UNREAD)
            cut.updateFeedItem(userId, readItems.first().feedItem.feedItemId, UpdateAction.SAVE)
            val unreadItem =
                repo.getUserFeedItem(userId, readItems.first().feedItem.feedId, readItems.first().feedItem.feedItemId)
            expectThat(unreadItem)
                .isNotNull()
                .and {
                    get { isRead }.isEqualTo(false)
                    get { isSaved }.isEqualTo(true)
                }
        }
    }


    private fun getTestSetup(): Pair<FeedRepository, FeedControlServiceImpl> {
        val factory = H2ConnectionFactory.inMemory(UUID.randomUUID().toString())
        val repo = SpringR2DBCFeedRepository(factory)

        val fetcher = RomeFeedFetcherServiceImpl(WebClient.create())

        val importer = RomeOPMLFeedImportServiceImpl()

        val cut = FeedControlServiceImpl(repo, fetcher, importer)
        return repo to cut
    }
}