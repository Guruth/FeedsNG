package sh.weller.feedsng.feed.impl.fetch.impl

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange
import sh.weller.feedsng.common.Failure
import sh.weller.feedsng.common.Result
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.feed.FeedData
import sh.weller.feedsng.feed.FeedItemData
import sh.weller.feedsng.feed.impl.fetch.FeedFetcherService
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.time.Instant

@Service
class RomeFeedFetcherServiceImpl(
    private val client: WebClient = WebClient.create()
) : FeedFetcherService {

    override suspend fun getFeedData(feedUrl: String): Result<FeedData, String> {
        logger.info("Fetching FeedData for $feedUrl")
        return this.getSyndFeedMapping(feedUrl) {
            it.toFeedData(feedUrl)
        }
    }

    override suspend fun getFeedItemData(feedUrl: String): Result<Flow<FeedItemData>, String> {
        logger.info("Fetching FeedItemData for $feedUrl")
        return this.getSyndFeedMapping(feedUrl)
        {
            it.toFeedItemData()
        }
    }

    private suspend fun <T> getSyndFeedMapping(
        feedUrl: String,
        mappingFunction: (SyndFeed) -> T
    ): Result<T, String> {
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
                        Success(mappedData)
                    } else {
                        Failure(it.statusCode().reasonPhrase)
                    }
                }
        } catch (e: Exception) {
            Failure(e.message ?: "Unknown Error")
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

    private fun SyndFeed.toFeedItemData(): Flow<FeedItemData> = flow {
        for (entry in this@toFeedItemData.entries) {
            emit(
                FeedItemData(
                    title = entry.getFeedItemTitle(),
                    author = entry.author,
                    html = entry.getFeedItemDescription(),
                    url = entry.uri,
                    created = entry.getFeedItemCreatedTimestamp()
                )
            )
        }
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

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(RomeFeedFetcherServiceImpl::class.java)
    }
}
