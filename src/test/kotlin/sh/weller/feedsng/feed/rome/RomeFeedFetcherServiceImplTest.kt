package sh.weller.feedsng.feed.rome

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.web.reactive.function.client.WebClient
import sh.weller.feedsng.common.Failure
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.feed.api.required.FeedDetails
import strikt.api.expectThat
import strikt.assertions.containsIgnoringCase
import strikt.assertions.isA
import strikt.assertions.isNotBlank
import strikt.assertions.isNotEmpty
import kotlin.test.Test

internal class RomeFeedFetcherServiceImplTest {

    @Test
    fun `get kotlin blog feed works`() {
        val cut = RomeFeedFetcherServiceImpl(WebClient.create())
        runBlocking {
            val feedDetails = cut.fetchFeedDetails("https://blog.jetbrains.com/kotlin/feed/")
            expectThat(feedDetails)
                .isA<Success<FeedDetails>>()
                .get { value }
                .and {
                    get { feedData.name }
                        .containsIgnoringCase("kotlin")

                    get { runBlocking { feedItemData.toList() } }
                        .isNotEmpty()
                }
        }
    }

    @Test
    fun `get invalid feed url returns error`() {
        val cut = RomeFeedFetcherServiceImpl(WebClient.create())
        runBlocking {
            val feedData = cut.fetchFeedDetails("https://example.com")
            expectThat(feedData)
                .isA<Failure<String>>()
                .get { reason }
                .isNotBlank()
        }
    }

    @Test
    fun `get not existing feed url returns error`() {
        val cut = RomeFeedFetcherServiceImpl(WebClient.create())
        runBlocking {
            val feedData = cut.fetchFeedDetails("https://127.0.0.1:9999/rss")
            expectThat(feedData)
                .isA<Failure<String>>()
                .get { reason }
                .isNotBlank()
        }
    }
}