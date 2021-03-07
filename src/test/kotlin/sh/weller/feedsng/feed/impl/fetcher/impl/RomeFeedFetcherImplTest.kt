package sh.weller.feedsng.feed.impl.fetcher.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import sh.weller.feedsng.feed.FeedData
import sh.weller.feedsng.feed.FeedItemData
import sh.weller.feedsng.feed.impl.fetcher.FeedFetcherResult
import strikt.api.expectThat
import strikt.assertions.containsIgnoringCase
import strikt.assertions.isA
import strikt.assertions.isNotBlank
import strikt.assertions.isNotEmpty

internal class RomeFeedFetcherImplTest {

    @Test
    fun `get kotlin blog feed works`() {
        val cut = RomeFeedFetcherImpl(WebClient.create())
        runBlocking {
            val feedData = cut.getFeedData("https://blog.jetbrains.com/kotlin/feed/")
            expectThat(feedData)
                .isA<FeedFetcherResult.Success<FeedData>>()
                .get { data }
                .and {
                    get { name }
                        .containsIgnoringCase("kotlin")
                }

            val feedItemDataList = cut.getFeedItemData("https://blog.jetbrains.com/kotlin/feed/")
            expectThat(feedItemDataList)
                .isA<FeedFetcherResult.Success<List<FeedItemData>>>()
                .get { data }
                .isNotEmpty()
        }
    }

    @Test
    fun `get invalid feed url returns error`() {
        val cut = RomeFeedFetcherImpl(WebClient.create())
        runBlocking {
            val feedData = cut.getFeedData("https://example.com")
            expectThat(feedData)
                .isA<FeedFetcherResult.Error<FeedData>>()
                .get { reason }
                .isNotBlank()
        }
    }

    @Test
    fun `get not existing feed url returns error`() {
        val cut = RomeFeedFetcherImpl(WebClient.create())
        runBlocking {
            val feedData = cut.getFeedData("https://127.0.0.1:9999/rss")
            expectThat(feedData)
                .isA<FeedFetcherResult.Error<FeedData>>()
                .get { reason }
                .isNotBlank()
        }
    }
}