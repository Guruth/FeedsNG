package sh.weller.feedsng.feed.impl.import.impl

import com.rometools.opml.feed.opml.Opml
import com.rometools.rome.io.ParsingFeedException
import com.rometools.rome.io.WireFeedInput
import sh.weller.feedsng.common.Failure
import sh.weller.feedsng.common.ResultNG
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.feed.impl.import.FeedGroupImport
import sh.weller.feedsng.feed.impl.import.FeedImport
import sh.weller.feedsng.feed.impl.import.FeedImportService
import java.io.Reader

class RomeOPMLFeedImportServiceImpl : FeedImportService {

    override fun importFrom(content: String): ResultNG<FeedImport, String> =
        content.reader().toFeedImport()

    private fun Reader.toFeedImport(): ResultNG<FeedImport, String> {
        return try {
            val outlines = (WireFeedInput().build(this) as Opml).outlines
            val feedURLs = mutableListOf<String>()
            val feedGroups = mutableListOf<FeedGroupImport>()

            outlines
                .forEach { outline ->
                    if (outline.children.isNullOrEmpty()) {
                        feedURLs.add(outline.xmlUrl)
                    } else {
                        val feedGroupURLs = mutableListOf<String>()
                        outline.children.forEach { feedGroupURLs.add(it.xmlUrl) }
                        feedGroups.add(FeedGroupImport(outline.title, feedGroupURLs))
                    }
                }
            Success(FeedImport(feedURLs, feedGroups))
        } catch (e: ParsingFeedException) {
            Failure("Unable to parse file. ${e.message}")
        }
    }
}
