package sh.weller.feedsng.feed.impl.fetcher

import sh.weller.feedsng.feed.FeedData
import sh.weller.feedsng.feed.FeedItemData

interface FeedFetcher {
    suspend fun getFeedData(feedUrl: String): FeedFetcherResult<FeedData>
    suspend fun getFeedItemData(feedUrl: String): FeedFetcherResult<List<FeedItemData>>
}

sealed class FeedFetcherResult<T> {
    data class Success<T>(val data: T) : FeedFetcherResult<T>()
    data class Error<T>(val reason: String) : FeedFetcherResult<T>()
}