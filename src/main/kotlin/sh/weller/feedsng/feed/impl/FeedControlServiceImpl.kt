package sh.weller.feedsng.feed.impl

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.weller.feedsng.common.*
import sh.weller.feedsng.feed.*
import sh.weller.feedsng.feed.impl.database.FeedRepository
import sh.weller.feedsng.feed.impl.fetch.FeedFetcherService
import sh.weller.feedsng.feed.impl.import.FeedImportService
import sh.weller.feedsng.user.UserId

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
    ): Result<List<Pair<String, String>>, String> {
        logger.info("Importing feeds from file for $userId")
        val importData = feedImportService
            .importFrom(fileContent)
            .onFailure { return it }

        val failedImports = mutableListOf<Pair<String, String>>()

        val feedMap = importData
            .allDistinctFeedURLs()
            .mapNotNull { feedURL ->
                val feedId = getFeedByURLOrFetchAndInsert(feedURL)
                    .onFailure { failure ->
                        failedImports.add(feedURL to failure.reason)
                        return@mapNotNull null
                    }
                return@mapNotNull feedRepository.getFeed(feedId)
            }
            .groupBy { it.feedData.feedUrl }

        importData.feedUrls
            .mapNotNull { feedMap[it]?.firstOrNull() }
            .forEach { feed ->
                feedRepository.addFeedToUser(userId, feed.feedId)
            }

        importData.feedGroupImport
            .forEach { groupImport ->
                val groupId = feedRepository
                    .insertUserGroup(userId, GroupData(groupImport.name, emptyList()))
                groupImport.feedUrls
                    .mapNotNull { feedMap[it]?.firstOrNull() }
                    .forEach { feed ->
                        feedRepository.addFeedToUserGroup(groupId, feed.feedId)

                    }
            }

        return failedImports.asSuccess()
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

    override suspend fun updateGroup(userId: UserId, groupId: GroupId, action: UpdateAction) {
        logger.info("Updating $groupId with $action for $userId")
        feedRepository.getAllUserGroups(userId)
            .filter { it.groupId == groupId }
            .map { flowOf(*it.groupData.feeds.toTypedArray()) }
            .flattenConcat()
            .onEach {
                updateFeed(userId, it, action)
            }
            .collect()
    }

    override suspend fun updateFeed(userId: UserId, feedId: FeedId, action: UpdateAction) {
        logger.info("Updating $feedId with $action for $userId")
        val feedItemFlow = feedRepository.getAllFeedItemIds(feedId)
        feedRepository.updateUserFeedItem(userId, feedItemFlow, action)
    }

    override suspend fun updateFeedItem(userId: UserId, feedItemId: FeedItemId, action: UpdateAction) {
        logger.info("Updating $feedItemId with $action for $userId")
        feedRepository.updateUserFeedItem(userId, flowOf(feedItemId), action)
    }

    private suspend fun getFeedByURLOrFetchAndInsert(feedUrl: String): Result<FeedId, String> {
        val feed = feedRepository.getFeedWithFeedURL(feedUrl)
        if (feed != null) {
            return feed.feedId.asSuccess()
        }

        val feedData = feedFetcherService
            .getFeedData(feedUrl)
            .onFailure { return it }
        val feedId = feedRepository.insertFeed(feedData)

        val feedItems = feedFetcherService
            .getFeedItemData(feedUrl)
            .onFailure { return it }
        feedRepository
            .insertFeedItemsIfNotExist(feedId, feedItems)
            .collect()

        return feedId.asSuccess()
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FeedControlServiceImpl::class.java)
    }
}
