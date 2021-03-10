package sh.weller.feedsng.feed.impl.import

interface FeedImportService {
    fun importFrom(content: String): FeedImport?
}

data class FeedImport(
    val feedUrls: List<String>,
    val feedGroupImport: List<FeedGroupImport>
)

data class FeedGroupImport(
    val name: String,
    val feedUrls: List<String>
)