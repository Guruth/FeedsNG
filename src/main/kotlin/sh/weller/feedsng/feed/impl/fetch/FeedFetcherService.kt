package sh.weller.feedsng.feed.impl.fetch

import kotlinx.coroutines.flow.Flow
import sh.weller.feedsng.common.Result
import sh.weller.feedsng.feed.FeedData
import sh.weller.feedsng.feed.FeedItemData

// TODO: Return typed error -> XML Parse Error / HTTP Error
interface FeedFetcherService {
    suspend fun fetchFeedDetails(feedUrl: String): Result<FeedDetails, String>
}

interface FeedDetails {
    val feedData: FeedData
    val feedItemData: Flow<FeedItemData>
}
