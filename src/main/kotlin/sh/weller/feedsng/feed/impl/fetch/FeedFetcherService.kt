package sh.weller.feedsng.feed.impl.fetch

import kotlinx.coroutines.flow.Flow
import sh.weller.feedsng.common.Result
import sh.weller.feedsng.feed.FeedData
import sh.weller.feedsng.feed.FeedItemData

// TODO: Change return value to support getting FeedData and FeedItemData with only one request
// TODO: Return typed error -> XML Parse Error / HTTP Error
interface FeedFetcherService {
    suspend fun getFeedData(feedUrl: String): Result<FeedData, String>
    suspend fun getFeedItemData(feedUrl: String): Result<Flow<FeedItemData>, String>
}
