package sh.weller.feedsng.feed.rome

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange
import reactor.netty.http.client.HttpClient
import sh.weller.feedsng.common.Failure
import sh.weller.feedsng.common.Result
import sh.weller.feedsng.common.asSuccess
import sh.weller.feedsng.common.onFailure
import sh.weller.feedsng.feed.api.provided.FeedData
import sh.weller.feedsng.feed.api.provided.FeedItemData
import sh.weller.feedsng.feed.api.required.FeedDetails
import sh.weller.feedsng.feed.api.required.FeedFetcherService
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class RomeFeedFetcherServiceImpl(
    private val client: WebClient = WebClient
        .builder()
        .clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create()
                    .followRedirect(true)
                    .responseTimeout(Duration.of(10, ChronoUnit.SECONDS))
            )
        )
        .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
        .build()
) : FeedFetcherService {

    override suspend fun fetchFeedDetails(feedUrl: String): Result<FeedDetails, String> {
        val syndFeed = getFeedBytes(feedUrl)
            .onFailure { return it }
            .toSyndFeed(feedUrl)
            .onFailure { return it }

        return RomeFeedDetails(feedUrl, syndFeed).asSuccess()
    }

    suspend fun getFeedBytes(feedUrl: String): Result<ByteArray, String> {
        return try {
            client
                .get()
                .uri(feedUrl)
                .awaitExchange {
                    if (it.statusCode().isError) {
                        logger.error("Could not fetch feed at $feedUrl: ${it.statusCode().reasonPhrase}")
                        return@awaitExchange Failure(it.statusCode().reasonPhrase)
                    }
                    return@awaitExchange it.awaitBody<ByteArray>().asSuccess()
                }
        } catch (e: Exception) {
            logger.error("Could not fetch feed $feedUrl: ${e.message}")
            Failure(e.message ?: "Unknown Error")
        }
    }

    private fun ByteArray.toSyndFeed(feedUrl: String): Result<SyndFeed, String> {
        return try {
            val feedInput = SyndFeedInput().apply {
                isAllowDoctypes = true
                xmlHealerOn = true
            }
            feedInput.build(InputStreamReader(ByteArrayInputStream(this))).asSuccess()
        } catch (e: Exception) {
            logger.error("Could not parse feed of $feedUrl: ${e.message}")
            Failure(e.message ?: "Unknown Error")
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(RomeFeedFetcherServiceImpl::class.java)
    }

}

private class RomeFeedDetails(
    private val feedUrl: String,
    private val syndFeed: SyndFeed
) : FeedDetails {

    override val feedData: FeedData
        get() = FeedData(
            name = syndFeed.title,
            description = syndFeed.description ?: "",
            feedUrl = feedUrl,
            siteUrl = syndFeed.link,
            lastUpdated = syndFeed.getFeedUpdatedTimestamp(),
        )

    override val feedItemData: Flow<FeedItemData>
        get() = flow {
            for (entry in syndFeed.entries) {
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

}
