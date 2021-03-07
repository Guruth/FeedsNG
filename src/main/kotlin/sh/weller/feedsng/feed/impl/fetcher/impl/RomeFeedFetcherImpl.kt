package sh.weller.feedsng.feed.impl.fetcher.impl

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange
import sh.weller.feedsng.feed.FeedData
import sh.weller.feedsng.feed.FeedItemData
import sh.weller.feedsng.feed.impl.fetcher.FeedFetcher
import sh.weller.feedsng.feed.impl.fetcher.FeedFetcherResult
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.time.Instant

class RomeFeedFetcherImpl(
    private val client: WebClient
) : FeedFetcher {

    override suspend fun getFeedData(feedUrl: String): FeedFetcherResult<FeedData> =
        this.getSyndFeedMapping(feedUrl) {
            it.toFeedData(feedUrl)
        }

    override suspend fun getFeedItemData(feedUrl: String): FeedFetcherResult<List<FeedItemData>> =
        this.getSyndFeedMapping(feedUrl) {
            it.toFeedItemData()
        }

    private suspend fun <T> getSyndFeedMapping(
        feedUrl: String,
        mappingFunction: (SyndFeed) -> T
    ): FeedFetcherResult<T> {
        return try {
            client
                .get()
                .uri(feedUrl)
                .awaitExchange {
                    return@awaitExchange if (it.statusCode().is2xxSuccessful) {
                        val rawResponse = it.awaitBody<ByteArray>()
                        val feedInput = SyndFeedInput()
                        val syndFeed = feedInput.build(InputStreamReader(ByteArrayInputStream(rawResponse)))
                        val mappedData = mappingFunction(syndFeed)
                        FeedFetcherResult.Success(mappedData)
                    } else {
                        FeedFetcherResult.Error(it.statusCode().reasonPhrase)
                    }
                }
        } catch (e: Exception) {
            FeedFetcherResult.Error(e.message ?: "Unknown Error")
        }
    }

    private fun SyndFeed.toFeedData(feedUrl: String): FeedData =
        FeedData(
            name = this.title,
            description = this.description,
            feedUrl = feedUrl,
            siteUrl = this.link,
            lastUpdated = this.getFeedUpdatedTimestamp(),
        )

    private fun SyndFeed.toFeedItemData(): List<FeedItemData> =
        this.entries
            .map {
                FeedItemData(
                    title = it.getFeedItemTitle(),
                    author = it.author,
                    html = it.getFeedItemDescription(),
                    url = it.uri,
                    created = it.getFeedItemCreatedTimestamp()
                )
            }

    private fun SyndFeed.getFeedUpdatedTimestamp(): Instant =
        this.publishedDate?.toInstant() ?: Instant.now()

    private fun SyndEntry.getFeedItemTitle(): String =
        if (this.title.length > 120) {
            this.title.split(" ").take(8).joinToString(" ") + "..."
        } else {
            this.title
        }

    private fun SyndEntry.getFeedItemDescription(): String =
        this.description?.value ?: ""

    private fun SyndEntry.getFeedItemCreatedTimestamp(): Instant =
        this.publishedDate?.toInstant() ?: this.updatedDate?.toInstant() ?: Instant.now()

}
