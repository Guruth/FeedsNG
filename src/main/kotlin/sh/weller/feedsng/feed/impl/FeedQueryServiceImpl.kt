package sh.weller.feedsng.feed.impl

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.weller.feedsng.feed.api.provided.*
import sh.weller.feedsng.feed.api.required.FeedRepository
import sh.weller.feedsng.user.api.provided.UserId
import java.time.Instant

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
        return feedRepository.getAllUserFeeds(userId)
    }

    @OptIn(FlowPreview::class)
    override suspend fun getFeedItems(
        userId: UserId,
        feedIdList: Flow<FeedId>?,
        filter: FeedItemFilter?,
        since: Instant?
    ): Flow<UserFeedItem> {
        val feedsToFetch: Flow<FeedId> = feedIdList
            ?: getFeeds(userId).map { it.feedId }

        return feedsToFetch
            .onEach { feedId ->
                logger.info("Getting feed items of feed $feedId user $userId and filter $filter since $since of user $userId")
            }
            .flatMapMerge { feedId ->
                feedRepository.getAllUserFeedItemsOfFeed(
                    userId,
                    feedId,
                    filter,
                    since
                )
            }
    }

    @OptIn(FlowPreview::class)
    override suspend fun getFeedItemsIds(
        userId: UserId,
        feedIdList: Flow<FeedId>?,
        filter: FeedItemFilter?,
        since: Instant?
    ): Flow<FeedItemId> {
        val feedsToFetch: Flow<FeedId> = feedIdList
            ?: getFeeds(userId).map { it.feedId }

        return feedsToFetch
            .onEach { feedId ->
                logger.info("Getting feed item ids of feed $feedId user $userId and filter $filter since $since of user $userId")
            }
            .flatMapMerge { feedId ->
                feedRepository.getAllUserFeedItemIdsOfFeed(
                    userId,
                    feedId,
                    filter,
                    since
                )
            }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(FeedQueryServiceImpl::class.java)
    }
}