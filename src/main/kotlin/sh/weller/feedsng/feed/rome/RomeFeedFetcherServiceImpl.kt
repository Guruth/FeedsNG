package sh.weller.feedsng.feed.rome

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntityFlux
import reactor.netty.http.client.HttpClient
import sh.weller.feedsng.common.Failure
import sh.weller.feedsng.common.Result
import sh.weller.feedsng.common.asSuccess
import sh.weller.feedsng.common.onFailure
import sh.weller.feedsng.feed.api.provided.FeedData
import sh.weller.feedsng.feed.api.provided.FeedItemData
import sh.weller.feedsng.feed.api.required.FeedDetails
import sh.weller.feedsng.feed.api.required.FeedFetcherService
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.Charset
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class RomeFeedFetcherServiceImpl : FeedFetcherService {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val client: WebClient = WebClient
        .builder()
        .clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create()
                    .followRedirect(true)
                    .responseTimeout(Duration.of(10, ChronoUnit.SECONDS))
            )
        )
        .build()


    private val feedBuilder = SyndFeedInput()
        .apply {
            isAllowDoctypes = true
            xmlHealerOn = true
            isPreserveWireFeed = false
        }

    override suspend fun fetchFeedDetails(feedUrl: String): Result<FeedDetails, String> {
        val syndFeed = getFeedBytes(feedUrl)
            .onFailure { return it }
            .toSyndFeed(feedUrl)
            .onFailure { return it }

        return RomeFeedDetails(feedUrl, syndFeed).asSuccess()
    }

    private suspend fun getFeedBytes(feedUrl: String): Result<WebclientResponse, String> {
        return try {
            val responseEntity = client
                .get()
                .uri(feedUrl)
                .retrieve()
                .toEntityFlux<DataBuffer>()
                .awaitSingle()

            if (responseEntity.statusCode.is2xxSuccessful) {
                val body = responseEntity.body
                    ?: return Failure("Empty response")
                return WebclientResponse(
                    body.asFlow(),
                    responseEntity.headers.contentType?.charset
                ).asSuccess()
            }
            return Failure("Could not fetch feed $feedUrl: ${responseEntity.statusCode}")
        } catch (e: Exception) {
            logger.error("Could not fetch feed $feedUrl: ${e.message}")
            Failure(e.message ?: "Unknown Error")
        }
    }

    private suspend fun WebclientResponse.toSyndFeed(feedUrl: String): Result<SyndFeed, String> {
        val pipedInputStream = PipedInputStream()
        val pipedOutputStream: PipedOutputStream = withContext(Dispatchers.IO) {
            runCatching {
                PipedOutputStream(pipedInputStream)
            }.getOrNull()
        } ?: return Failure("Could not created PipedOutputStream for URL $feedUrl")

        this@toSyndFeed.body
            .onEach {
                try {
                    it.asInputStream().copyTo(pipedOutputStream)
                } finally {
                    // Make sure the databuffer is released
                    DataBufferUtils.release(it)
                }
            }
            .catch { logger.error("Error during databuffer copy for URL $feedUrl: ${it.message}") }
            .onCompletion {
                withContext(Dispatchers.IO) {
                    runCatching {
                        pipedOutputStream.close()
                    }.onFailure {
                        logger.error("Could not close PipedOutputStream for URL $feedUrl")
                    }
                }
            }
            .launchIn(scope)

        return try {
            InputStreamReader(pipedInputStream, this@toSyndFeed.charset ?: Charsets.UTF_8)
                .use {
                    withContext(Dispatchers.IO) {
                        feedBuilder.build(it).asSuccess()
                    }
                }

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
                        url = entry.link,
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


private data class WebclientResponse(
    val body: Flow<DataBuffer>,
    val charset: Charset?
)