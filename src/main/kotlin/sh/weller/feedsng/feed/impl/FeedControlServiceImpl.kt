package sh.weller.feedsng.feed.impl

import sh.weller.feedsng.common.Failure
import sh.weller.feedsng.common.ResultNG
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.feed.*
import sh.weller.feedsng.feed.impl.database.FeedRepository
import sh.weller.feedsng.feed.impl.fetch.FeedFetcherService
import sh.weller.feedsng.feed.impl.import.FeedImportService
import sh.weller.feedsng.user.UserId

class FeedControlServiceImpl(
    private val feedRepository: FeedRepository,
    private val feedFetcherService: FeedFetcherService,
    private val feedImportService: FeedImportService
) : FeedControlService {

    override suspend fun importFromOPML(
        userId: UserId,
        fileContent: String
    ): ResultNG<List<Pair<String, String>>, String> {
        val importResult = feedImportService.importFrom(fileContent)
        if (importResult is Failure) {
            return importResult
        }
        val import = (importResult as Success).value

        val failedImports = mutableListOf<Pair<String, String>>()

        val feedsToImport = import.feedUrls + import.feedGroupImport.flatMap { it.feedUrls }
        val feedMap = feedsToImport
            .distinct()
            .mapNotNull {
                val feedDetails = feedRepository.getFeedWithFeedURL(it)
                if (feedDetails != null) {
                    return@mapNotNull feedDetails
                }

                val fetchedFeedDetails = feedFetcherService.getFeedData(it)
                if (fetchedFeedDetails is Failure) {
                    failedImports.add(it to fetchedFeedDetails.reason)
                    return@mapNotNull null
                } else {
                    val feedData = (fetchedFeedDetails as Success<FeedData>).value
                    val feedId = feedRepository.insertFeed(feedData)
                    return@mapNotNull feedRepository.getFeed(feedId)
                }
            }
            .groupBy { it.feedData.feedUrl }

        import.feedUrls
            .forEach { feedUrl ->
                val feed: Feed? = feedMap[feedUrl]?.firstOrNull()
                if (feed != null) {
                    feedRepository.addFeedToUser(userId, feed.feedId)
                }
            }

        import.feedGroupImport
            .forEach { groupImport ->
                val groupId = feedRepository
                    .insertUserGroup(userId, GroupData(groupImport.name, emptyList()))
                groupImport.feedUrls
                    .forEach { feedUrl ->
                        val feed: Feed? = feedMap[feedUrl]?.firstOrNull()
                        if (feed != null) {
                            feedRepository.addFeedToUserGroup(groupId, feed.feedId)
                        }
                    }
            }

        return Success(failedImports)
    }

    override fun addGroup(userId: UserId, groupName: String): GroupId {
        TODO("Not yet implemented")
    }

    override fun addFeedToGroup(userId: UserId, groupId: GroupId, feedUrl: String): ResultNG<FeedId, String> {
        TODO("Not yet implemented")
    }

    override fun addFeed(userId: UserId, feedUrl: String): ResultNG<FeedId, String> {
        TODO("Not yet implemented")
    }

    override fun updateFeedGroup(userId: UserId, groupId: GroupId, action: UpdateAction) {
        TODO("Not yet implemented")
    }

    override fun updateFeed(userId: UserId, feedId: FeedId, action: UpdateAction) {
        TODO("Not yet implemented")
    }

    override fun updateFeedItem(userId: UserId, feedItemId: FeedItemId, action: UpdateAction) {
        TODO("Not yet implemented")
    }
}