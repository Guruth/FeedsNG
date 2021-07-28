package sh.weller.feedsng.feed.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.TickerMode
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Service
import sh.weller.feedsng.common.onFailure
import sh.weller.feedsng.feed.api.provided.Feed
import sh.weller.feedsng.feed.api.required.FeedFetcherService
import sh.weller.feedsng.feed.api.required.FeedRepository
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@Service
@EnableConfigurationProperties(value = [FeedUpdateConfiguration::class])
@ObsoleteCoroutinesApi
@OptIn(ExperimentalTime::class)
class FeedUpdateServiceImpl(
    private val feedRepository: FeedRepository,
    private val feedFetcherService: FeedFetcherService,
    private val feedUpdateConfiguration: FeedUpdateConfiguration
) : SmartLifecycle {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    // This will change in future: https://github.com/Kotlin/kotlinx.coroutines/issues/540
    private lateinit var updateTicker: ReceiveChannel<Unit>
    private var isStarted = false

    override fun start() {
        logger.info("Starting to update feeds every ${feedUpdateConfiguration.updateInterval}ms in ${feedUpdateConfiguration.initialDelay}ms")
        updateTicker = ticker(
            delayMillis = feedUpdateConfiguration.updateInterval,
            initialDelayMillis = feedUpdateConfiguration.initialDelay,
            mode = TickerMode.FIXED_PERIOD
        )
        startUpdatingFeeds()
        isStarted = true
    }

    override fun stop() {
        updateTicker.cancel()
        coroutineScope.cancel("Stopping feed updates")
        isStarted = false
    }

    override fun isRunning(): Boolean = isStarted

    private fun startUpdatingFeeds() {
        coroutineScope.launch {
            for (tick in updateTicker) {
                logger.info("Starting to update all feeds.")
                val updateDuration = measureTime {
                    feedRepository
                        .getAllFeeds()
                        .toList().map {
                            async {
                                updateFeed(it)
                            }
                        }
                        .awaitAll()
                }
                logger.info("Finished all updating all feeds in took: ${updateDuration.inWholeSeconds} seconds")
            }
        }
    }

    private suspend fun updateFeed(feed: Feed) {
        logger.info("Updating Feed ${feed.feedId} - ${feed.feedData.name} - ${feed.feedData.feedUrl}")
        val feedDetails = feedFetcherService
            .fetchFeedDetails(feed.feedData.feedUrl)
            .onFailure {
                logger.error("Could not update feed ${feed.feedId} - ${feed.feedData.feedUrl}. Reason ${it.reason}")
                return
            }

        runCatching {
            feedRepository.insertFeedItemsIfNotExist(feed.feedId, feedDetails.feedItemData).collect()
            feedRepository.setFeedLastRefreshedTimestamp(feed.feedId)
        }.onFailure {
            logger.error("Could not update feed ${feed.feedId} - ${feed.feedData.feedUrl}.", it)
        }
    }


    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FeedUpdateServiceImpl::class.java)
    }

}

@ConfigurationProperties("feedsng.update")
@ConstructorBinding
@OptIn(ExperimentalTime::class)
data class FeedUpdateConfiguration(
    val initialDelay: Long = Duration.minutes(5).inWholeMilliseconds,
    val updateInterval: Long = Duration.minutes(10).inWholeMilliseconds
)