package sh.weller.feedsng.feed.impl

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.weller.feedsng.common.*
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.feed.api.required.FeedFetcherService
import sh.weller.feedsng.feed.api.required.FeedImportService
import sh.weller.feedsng.feed.api.required.FeedRepository
import sh.weller.feedsng.user.api.provided.UserId
import java.time.Instant

@OptIn(FlowPreview::class)
@Service
class FeedControlServiceImpl(
    private val feedRepository: FeedRepository,
    private val feedFetcherService: FeedFetcherService,
    private val feedImportService: FeedImportService
) : FeedControlService {

    override suspend fun importFromOPML(
        userId: UserId,
        fileContent: String
    ): Result<Unit, List<String>> = coroutineScope {
        logger.info("Importing feeds for $userId")
        val importData = feedImportService
            .importFrom(fileContent)
            .onFailure { failure -> return@coroutineScope failure.map { listOf(it) } }

        val feedImports = importData
            .allDistinctFeedURLs()
            .map { async { getFeedByURLOrFetchAndInsert(it) } }
            .awaitAll()

        logger.debug("Added ${feedImports.size} feeds for $userId.")

        importData
            .feedUrls
            .map {
                async {
                    val feed = feedRepository.getFeedWithFeedURL(it)
                    if (feed != null) {
                        feedRepository.addFeedToUser(userId, feed.feedId)
                    }
                }
            }
            .awaitAll()

        logger.debug("Added imported feeds to $userId.")

        importData
            .feedGroupImport
            .flatMap {
                val groupId = feedRepository
                    .insertUserGroup(userId, GroupData(it.name, emptyList()))
                return@flatMap it.feedUrls
                    .map {
                        async {
                            val feed = feedRepository.getFeedWithFeedURL(it)
                            if (feed != null) {
                                feedRepository.addFeedToUserGroup(groupId, feed.feedId)
                            }
                        }
                    }
            }.awaitAll()
        logger.debug("Added imported feeds to groups of $userId.")

        val failedImports = feedImports
            .filterIsInstance<Failure<String>>()
            .map {
                logger.info("Failed to import feed for user $userId: ${it.reason}")
                it.reason
            }

        if (failedImports.isEmpty()) {
            return@coroutineScope Success(Unit)
        } else {
            return@coroutineScope failedImports.asFailure()
        }
    }

    override suspend fun addGroup(userId: UserId, groupName: String): GroupId {
        logger.info("Adding group $groupName for $userId")
        return feedRepository.insertUserGroup(userId, GroupData(groupName, emptyList()))
    }

    override suspend fun addFeedToGroup(userId: UserId, groupId: GroupId, feedUrl: String): Result<FeedId, String> {
        logger.info("Adding $feedUrl to $groupId for $userId")
        val feedId = getFeedByURLOrFetchAndInsert(feedUrl)
            .onFailure { return it }

        feedRepository.addFeedToUserGroup(groupId, feedId)
        return feedId.asSuccess()
    }

    override suspend fun addFeed(userId: UserId, feedUrl: String): Result<FeedId, String> {
        logger.info("Adding $feedUrl for $userId")
        val feedId = getFeedByURLOrFetchAndInsert(feedUrl)
            .onFailure { return it }

        feedRepository.addFeedToUser(userId, feedId)
        return feedId.asSuccess()
    }

    override suspend fun updateGroup(userId: UserId, groupId: GroupId, action: FeedUpdateAction, before: Instant?) {
        logger.info("Updating $groupId with $action for $userId")
        feedRepository.getAllUserGroups(userId)
            .filter { it.groupId == groupId }
            .map { flowOf(*it.groupData.feeds.toTypedArray()) }
            .flattenConcat()
            .onEach {
                updateFeed(userId, it, action, before)
            }
            .collect()
    }

    override suspend fun updateFeed(userId: UserId, feedId: FeedId, action: FeedUpdateAction, before: Instant?) {
        logger.info("Updating $feedId with $action for $userId before $before")
        val feedItemFlow = feedRepository.getAllFeedItemIds(feedId = feedId, before = before)
        logger.debug("Fetched all feedIds of $feedId to update them with $action for $userId before $before")
        feedRepository.updateUserFeedItem(userId, feedItemFlow, action)
        logger.debug("Finished updating $feedId with $action for $userId before $before")
    }

    override suspend fun updateFeedItem(
        userId: UserId,
        feedItemId: FeedItemId,
        action: FeedUpdateAction
    ) {
        logger.info("Updating $feedItemId with $action for $userId")
        feedRepository.updateUserFeedItem(userId, flowOf(feedItemId), action)
    }

    private suspend fun getFeedByURLOrFetchAndInsert(feedUrl: String): Result<FeedId, String> {
        val feed = feedRepository.getFeedWithFeedURL(feedUrl)
        if (feed != null) {
            return feed.feedId.asSuccess()
        }

        val feedDetails = feedFetcherService
            .fetchFeedDetails(feedUrl)
            .onFailure { failure -> return failure.map { "Failed to import $feedUrl: $it" } }
        val feedId = feedRepository.insertFeed(feedDetails.feedData)

        feedRepository
            .insertFeedItemsIfNotExist(feedId, feedDetails.feedItemData)
            .collect()

        return feedId.asSuccess()
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FeedControlServiceImpl::class.java)
    }
}
