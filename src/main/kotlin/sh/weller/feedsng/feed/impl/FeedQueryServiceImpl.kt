package sh.weller.feedsng.feed.impl

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.feed.api.required.FeedRepository
import sh.weller.feedsng.user.api.provided.UserId

@Service
class FeedQueryServiceImpl(
    private val feedRepository: FeedRepository
) : FeedQueryService {
    override suspend fun getFeed(feedId: FeedId): Feed? {
        logger.info("Getting feed $feedId")
        return feedRepository.getFeed(feedId)
    }

    override suspend fun getGroups(userId: UserId): Flow<Group> {
        logger.info("Getting groups of  $userId")
        return feedRepository.getAllUserGroups(userId)
    }

    override suspend fun getFeeds(userId: UserId): Flow<Feed> {
        logger.info("Getting feeds of  $userId")
        return feedRepository.getAllFeedsOfUser(userId)
    }

    @OptIn(FlowPreview::class)
    override suspend fun getFeedItems(
        userId: UserId,
        feedIdList: List<FeedId>?,
        limit: Int?
    ): Flow<UserFeedItem> {
        logger.info("Getting UserFeedItems of feeds $feedIdList of user $userId")
        val feedsToFetch: Flow<FeedId> = feedIdList?.asFlow()
            ?: getFeeds(userId).map { it.feedId }

        return feedsToFetch
            .flatMapMerge { feedId ->
                feedRepository.getAllFeedItemsOfUser(
                    userId,
                    feedId,
                    limit
                )
            }
    }

    @OptIn(FlowPreview::class)
    override suspend fun getFeedItemsIds(
        userId: UserId,
        feedIdList: List<FeedId>?,
        filter: FeedItemFilter?
    ): Flow<FeedItemId> {
        logger.debug("Getting FeedItemIds of feeds $feedIdList with filter $filter of user $userId")
        val feedsToFetch: Flow<FeedId> = feedIdList?.asFlow()
            ?: getFeeds(userId).map { it.feedId }

        return feedsToFetch
            .flatMapMerge { feedId ->
                feedRepository.getAllFeedItemIdsOfFeed(
                    userId,
                    feedId,
                    filter
                )
            }
    }

    override suspend fun countFeedItems(userId: UserId, feedId: FeedId, filter: FeedItemFilter?): Int {
        logger.debug("Counting FeedItems of user $userId and feed $feedId with filter $filter ")
        return feedRepository.countFeedItemsOfFeedOfUser(userId, feedId, filter)
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FeedQueryServiceImpl::class.java)
    }
}