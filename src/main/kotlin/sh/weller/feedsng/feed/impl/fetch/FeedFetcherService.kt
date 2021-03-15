package sh.weller.feedsng.feed.impl.fetch

import sh.weller.feedsng.common.Result
import sh.weller.feedsng.feed.FeedData
import sh.weller.feedsng.feed.FeedItemData

interface FeedFetcherService {
    suspend fun getFeedData(feedUrl: String): Result<FeedData, String>
    suspend fun getFeedItemData(feedUrl: String): Result<List<FeedItemData>, String>
}
