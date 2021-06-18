package sh.weller.feedsng.feed.impl

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
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
        logger.trace("Getting feed $feedId")
        return feedRepository.getFeed(feedId)
    }

    override suspend fun getGroups(userId: UserId): Flow<Group> {
        logger.trace("Getting groups of  $userId")
        return feedRepository.getAllUserGroups(userId)
    }

    override suspend fun getFeeds(userId: UserId): Flow<Feed> {
        logger.trace("Getting feeds of  $userId")
        return feedRepository.getAllFeedsOfUser(userId)
    }

    override suspend fun getFeedIds(userId: UserId): Flow<FeedId> {
        logger.trace("Getting feedIds of  $userId")
        return feedRepository.getAllFeedIdsOfUser(userId)
    }

    @OptIn(FlowPreview::class)
    override suspend fun getFeedItems(
        userId: UserId,
        feedIdList: List<FeedId>?,
        feedItemIdFilter: FeedItemIdFilter?,
        limit: Int?,
        offset: Int
    ): Flow<UserFeedItem> {
        logger.trace("Getting $limit with offset $offset UserFeedItems of feeds $feedIdList with feedItemFilter $feedItemIdFilter of user $userId")
        val feedsToFetch = if (feedIdList.isNullOrEmpty()) {
            getFeeds(userId).map { it.feedId }.toList()
        } else {
            feedIdList
        }

        return feedRepository.getAllFeedItemsOfUser(
            userId,
            feedsToFetch,
            feedItemIdFilter,
            limit,
            offset
        )
    }

    @OptIn(FlowPreview::class)
    override suspend fun getFeedItemsIds(
        userId: UserId,
        feedIdList: List<FeedId>?,
        filter: FeedItemFilter?
    ): Flow<FeedItemId> {
        logger.trace("Getting FeedItemIds of feeds $feedIdList with filter $filter of user $userId")
        val feedsToFetch: List<FeedId> = feedIdList
            ?: getFeeds(userId).map { it.feedId }.toList()

        return feedRepository.getAllFeedItemIdsOfFeed(
            userId,
            feedsToFetch,
            filter
        )
    }

    override suspend fun countFeedItems(userId: UserId, feedId: FeedId, filter: FeedItemFilter?): Int {
        logger.trace("Counting FeedItems of user $userId and feed $feedId with filter $filter ")
        return feedRepository.countFeedItemsOfFeedOfUser(userId, feedId, filter)
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FeedQueryServiceImpl::class.java)
    }
}