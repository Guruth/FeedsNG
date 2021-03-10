package sh.weller.feedsng.feed.impl.fetch

import sh.weller.feedsng.common.ResultNG
import sh.weller.feedsng.feed.FeedData
import sh.weller.feedsng.feed.FeedItemData

interface FeedFetcherService {
    suspend fun getFeedData(feedUrl: String): ResultNG<FeedData, String>
    suspend fun getFeedItemData(feedUrl: String): ResultNG<List<FeedItemData>, String>
}