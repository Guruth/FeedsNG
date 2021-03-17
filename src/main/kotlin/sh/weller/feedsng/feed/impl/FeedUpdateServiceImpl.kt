package sh.weller.feedsng.feed.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.TickerMode
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.Lifecycle
import sh.weller.feedsng.common.onFailure
import sh.weller.feedsng.feed.Feed
import sh.weller.feedsng.feed.impl.database.FeedRepository
import sh.weller.feedsng.feed.impl.fetch.FeedFetcherService

@OptIn(ObsoleteCoroutinesApi::class)
class FeedUpdateServiceImpl(
    private val feedRepository: FeedRepository,
    private val feedFetcherService: FeedFetcherService,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val feedUpdateConfiguration: FeedUpdateConfiguration = FeedUpdateConfiguration(60 * 100)
) : Lifecycle {

    // This will change in future: https://github.com/Kotlin/kotlinx.coroutines/issues/540
    private var updateTicker: ReceiveChannel<Unit>? = null
    private var isStarted = false

    override fun start() {
        updateTicker = ticker(
            delayMillis = feedUpdateConfiguration.updateInterval,
            initialDelayMillis = 5000,
            mode = TickerMode.FIXED_PERIOD
        ).also {
            startUpdate(it)
            isStarted = true
        }
    }

    override fun stop() {
        updateTicker?.cancel()
        updateTicker = null
        isStarted = false
    }

    override fun isRunning(): Boolean = isStarted

    private fun startUpdate(tickerChannel: ReceiveChannel<Unit>) {
        tickerChannel
            .receiveAsFlow()
            .onEach {
                updateFeeds()
            }
            .launchIn(coroutineScope)
    }

    private suspend fun updateFeeds() {
        feedRepository
            .getAllFeeds()
            .onEach {
                updateFeed(it)
            }
            .collect()
    }

    private suspend fun updateFeed(feed: Feed) {
        val feedItemDataList = feedFetcherService
            .getFeedItemData(feed.feedData.feedUrl)
            .onFailure {
                // TODO: Store this error?
                logger.info("Could not update feed ${feed.feedId} - ${feed.feedData.name}. Reason ${it.reason}")
                return
            }

        feedRepository.insertFeedItemsIfNotExist(feed.feedId, feedItemDataList).collect()
        feedRepository.setFeedLastRefreshedTimestamp(feed.feedId)
    }


    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FeedUpdateServiceImpl::class.java)
    }

}

data class FeedUpdateConfiguration(
    val updateInterval: Long
)