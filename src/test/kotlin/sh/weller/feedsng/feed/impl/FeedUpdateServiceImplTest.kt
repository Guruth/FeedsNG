package sh.weller.feedsng.feed.impl

import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import sh.weller.feedsng.common.valueOrNull
import sh.weller.feedsng.feed.impl.database.FeedRepository
import sh.weller.feedsng.feed.impl.database.impl.SpringR2DBCFeedRepository
import sh.weller.feedsng.feed.impl.fetch.FeedFetcherService
import sh.weller.feedsng.feed.impl.fetch.impl.RomeFeedFetcherServiceImpl
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class FeedUpdateServiceImplTest {

    @Test
    fun `Automatically updates feeds`() {
        val (cut, repo, fetcher) = getTestSetup()

        runBlocking {
            val feedData = fetcher.getFeedData("https://blog.jetbrains.com/kotlin/feed/")
                .valueOrNull()
            assertNotNull(feedData)

            val feedId = repo.insertFeed(feedData)
            val insertedFeed = repo.getFeed(feedId)
            assertNotNull(insertedFeed)

            cut.start()
            delay(6000)
            val updatedFeed = repo.getFeed(feedId)
            assertNotNull(updatedFeed)
            assertTrue { insertedFeed.feedData.lastUpdated.isBefore(updatedFeed.feedData.lastUpdated) }
        }

        cut.stop()
    }


    private fun getTestSetup(): Triple<FeedUpdateServiceImpl, FeedRepository, FeedFetcherService> {
        val factory = H2ConnectionFactory.inMemory(UUID.randomUUID().toString())
        val repo = SpringR2DBCFeedRepository(factory)

        val fetcher = RomeFeedFetcherServiceImpl(WebClient.create())
        val updater = FeedUpdateServiceImpl(
            repo,
            fetcher
        )

        return Triple(updater, repo, fetcher)
    }
}