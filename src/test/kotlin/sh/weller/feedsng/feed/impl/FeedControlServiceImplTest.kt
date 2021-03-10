package sh.weller.feedsng.feed.impl

import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.feed.impl.database.FeedRepository
import sh.weller.feedsng.feed.impl.database.impl.SpringR2DBCFeedRepository
import sh.weller.feedsng.feed.impl.fetch.impl.RomeFeedFetcherServiceImpl
import sh.weller.feedsng.feed.impl.import.impl.RomeOPMLFeedImportServiceImpl
import sh.weller.feedsng.user.UserId
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isNotEmpty
import java.io.File
import java.util.*

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


    fun getTestSetup(): Pair<FeedRepository, FeedControlServiceImpl> {
        val factory = H2ConnectionFactory.inMemory(UUID.randomUUID().toString())
        val repo = SpringR2DBCFeedRepository(factory)

        val fetcher = RomeFeedFetcherServiceImpl(WebClient.create())

        val importer = RomeOPMLFeedImportServiceImpl()

        val cut = FeedControlServiceImpl(repo, fetcher, importer)
        return repo to cut
    }
}