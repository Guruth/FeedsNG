package sh.weller.feedsng.feed.impl

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Service
import sh.weller.feedsng.common.onFailure
import sh.weller.feedsng.feed.api.provided.Feed
import sh.weller.feedsng.feed.api.required.FeedFetcherService
import sh.weller.feedsng.feed.api.required.FeedRepository
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@Service
@EnableConfigurationProperties(value = [FeedUpdateConfiguration::class])
class FeedUpdateServiceImpl(
    private val feedRepository: FeedRepository,
    private val feedFetcherService: FeedFetcherService,
    private val feedUpdateConfiguration: FeedUpdateConfiguration
) : SmartLifecycle {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private lateinit var updateTimer: Timer
    private var isStarted = false

    override fun start() {
        logger.info("Starting to update feeds every ${feedUpdateConfiguration.updateInterval} in ${feedUpdateConfiguration.initialDelay}")

        updateTimer = fixedRateTimer(
            initialDelay = feedUpdateConfiguration.initialDelay.toMillis(),
            period = feedUpdateConfiguration.updateInterval.toMillis()
        ) {
            updateFeeds()
        }
        isStarted = true
    }

    override fun stop() {
        updateTimer.cancel()
        coroutineScope.cancel("Stopping feed updates")
        isStarted = false
    }

    override fun isRunning(): Boolean = isStarted

    @OptIn(ExperimentalTime::class)
    private fun updateFeeds() {
        coroutineScope.launch {
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
data class FeedUpdateConfiguration(
    @DurationUnit(ChronoUnit.SECONDS)
    val initialDelay: Duration = Duration.ofSeconds(30),
    @DurationUnit(ChronoUnit.MINUTES)
    val updateInterval: Duration = Duration.ofMinutes(5)
)