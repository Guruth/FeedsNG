package sh.weller.feedsng.feed.impl

import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.common.valueOrNull
import sh.weller.feedsng.database.h2.H2FeedRepository
import sh.weller.feedsng.feed.api.provided.FeedUpdateAction
import sh.weller.feedsng.feed.rome.RomeFeedFetcherServiceImpl
import sh.weller.feedsng.feed.rome.RomeOPMLFeedImportServiceImpl
import sh.weller.feedsng.user.api.provided.UserId
import strikt.api.expectThat
import strikt.assertions.*
import java.io.File
import java.util.*
import kotlin.test.Test
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
                .isA<Success<Unit>>()

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

            cut.updateGroup(userId, groupId, FeedUpdateAction.SAVE)
            val savedItems = repo.getAllUserFeedItemsOfFeed(userId, feedIdWithGroup).toList()
            expectThat(savedItems)
                .isNotEmpty()
                .map { it.isSaved }
                .doesNotContain(false)

            cut.updateFeed(userId, feedIdWithoutGroup, FeedUpdateAction.READ)
            val readItems = repo.getAllUserFeedItemsOfFeed(userId, feedIdWithoutGroup).toList()
            expectThat(readItems)
                .isNotEmpty()
                .map { it.isRead }
                .doesNotContain(false)

            cut.updateFeedItem(userId, readItems.first().feedItem.feedItemId, FeedUpdateAction.UNREAD)
            cut.updateFeedItem(userId, readItems.first().feedItem.feedItemId, FeedUpdateAction.SAVE)
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


    private fun getTestSetup(): Pair<sh.weller.feedsng.feed.api.required.FeedRepository, FeedControlServiceImpl> {
        val factory = H2ConnectionFactory.inMemory(UUID.randomUUID().toString())
        val client = DatabaseClient.create(factory)
        val repo = H2FeedRepository(client)

        val fetcher = RomeFeedFetcherServiceImpl()

        val importer = RomeOPMLFeedImportServiceImpl()

        val cut = FeedControlServiceImpl(repo, fetcher, importer)
        return repo to cut
    }
}