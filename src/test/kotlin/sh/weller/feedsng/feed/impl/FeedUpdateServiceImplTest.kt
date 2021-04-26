package sh.weller.feedsng.feed.impl

import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.reactive.function.client.WebClient
import sh.weller.feedsng.common.valueOrNull
import sh.weller.feedsng.database.h2.H2FeedRepository
import sh.weller.feedsng.feed.api.required.FeedFetcherService
import sh.weller.feedsng.feed.rome.RomeFeedFetcherServiceImpl
import java.util.*
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ObsoleteCoroutinesApi::class)
internal class FeedUpdateServiceImplTest {

    @Test
    fun `Automatically updates feeds`() {
        val (cut, repo, fetcher) = getTestSetup()

        runBlocking {
            val feedDetails = fetcher.fetchFeedDetails("https://blog.jetbrains.com/kotlin/feed/")
                .valueOrNull()
            assertNotNull(feedDetails)

            val feedId = repo.insertFeed(feedDetails.feedData)
            val insertedFeed = repo.getFeed(feedId)
            assertNotNull(insertedFeed)

            delay(2000)
            cut.start()
            delay(2000)

            val updatedFeed = repo.getFeed(feedId)
            assertNotNull(updatedFeed)
            assertTrue { insertedFeed.feedData.lastUpdated.isBefore(updatedFeed.feedData.lastUpdated) }
        }

        cut.stop()
    }


    private fun getTestSetup(): Triple<FeedUpdateServiceImpl, sh.weller.feedsng.feed.api.required.FeedRepository, FeedFetcherService> {
        val factory = H2ConnectionFactory.inMemory(UUID.randomUUID().toString())
        val client = DatabaseClient.create(factory)
        val repo = H2FeedRepository(client)

        val fetcher = RomeFeedFetcherServiceImpl(WebClient.create())
        val updater = FeedUpdateServiceImpl(
            repo,
            fetcher,
            feedUpdateConfiguration = FeedUpdateConfiguration(500, 5000)
        )

        return Triple(updater, repo, fetcher)
    }
}