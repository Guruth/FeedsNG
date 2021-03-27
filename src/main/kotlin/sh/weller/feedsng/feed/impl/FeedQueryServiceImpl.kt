package sh.weller.feedsng.feed.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import org.springframework.stereotype.Service
import sh.weller.feedsng.feed.*
import sh.weller.feedsng.feed.impl.database.FeedRepository
import sh.weller.feedsng.user.UserId
import java.time.Instant

@Service
class FeedQueryServiceImpl(
    private val feedRepository: FeedRepository
) : FeedQueryService {
    override suspend fun getFeed(feedId: FeedId): Feed? =
        feedRepository.getFeed(feedId)

    override suspend fun getGroups(userId: UserId): Flow<Group> =
        feedRepository.getAllUserGroups(userId)

    override fun getFeeds(userId: UserId): Flow<Feed> =
        feedRepository.getAllUserFeeds(userId)

    override fun getFeedItems(
        userId: UserId,
        feedIdList: Flow<FeedId>?,
        since: Instant?,
        filter: FeedItemFilter?
    ): Flow<UserFeedItem> {
        val feedsToFetch: Flow<FeedId> = feedIdList
            ?: getFeeds(userId).map { it.feedId }

        return feedsToFetch
            .flatMapMerge { feedId ->
                feedRepository.getAllUserFeedItemsOfFeed(
                    userId,
                    feedId,
                    since,
                    filter
                )
            }
    }
}