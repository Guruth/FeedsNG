package sh.weller.feedsng.feed.impl.import

import sh.weller.feedsng.common.Result

interface FeedImportService {
    fun importFrom(content: String): Result<FeedImport, String>
}

data class FeedImport(
    val feedUrls: List<String>,
    val feedGroupImport: List<FeedGroupImport>
) {
    fun allFeedURLs(): List<String> = feedUrls + feedGroupImport.flatMap { it.feedUrls }
    fun allDistinctFeedURLs() = allFeedURLs().distinct()
}

data class FeedGroupImport(
    val name: String,
    val feedUrls: List<String>
)