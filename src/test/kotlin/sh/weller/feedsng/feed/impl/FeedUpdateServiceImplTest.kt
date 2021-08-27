package sh.weller.feedsng.feed.impl

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import sh.weller.feedsng.common.valueOrNull
import sh.weller.feedsng.feed.api.required.FeedFetcherService
import sh.weller.feedsng.feed.api.required.FeedRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@SpringBootTest(
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///~/db/testdb",

        "feedsng.update.initial-delay=0",
        "feedsng.update.update-interval=300000"
    ]
)
internal class FeedUpdateServiceImplTest(
    @Autowired private val databaseClient: DatabaseClient,
    @Autowired private val repo: FeedRepository,
    @Autowired private val fetcher: FeedFetcherService,
    @Autowired private val cut: FeedUpdateServiceImpl,
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
    fun `Automatically updates feeds`() {
        runBlocking {
            val feedDetails = fetcher
                .fetchFeedDetails("https://blog.jetbrains.com/kotlin/feed/")
                .valueOrNull()
            assertNotNull(feedDetails)

            println(repo.getAllFeeds().toList().map { it.feedData.feedUrl })

            val feedId = repo.insertFeed(feedDetails.feedData)
            val insertedFeed = repo.getFeed(feedId)
            assertNotNull(insertedFeed)

            launch { cut.start() }
            delay(2500)

            val updatedFeed = repo.getFeed(feedId)
            assertNotNull(updatedFeed)
            assertTrue { insertedFeed.feedData.lastUpdated.isBefore(updatedFeed.feedData.lastUpdated) }

            cut.stop()
        }
    }
}