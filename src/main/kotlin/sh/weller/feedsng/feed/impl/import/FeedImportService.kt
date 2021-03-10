package sh.weller.feedsng.feed.impl.import

import sh.weller.feedsng.common.ResultNG

interface FeedImportService {
    fun importFrom(content: String): ResultNG<FeedImport, String>
}

data class FeedImport(
    val feedUrls: List<String>,
    val feedGroupImport: List<FeedGroupImport>
)

data class FeedGroupImport(
    val name: String,
    val feedUrls: List<String>
)