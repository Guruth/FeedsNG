package sh.weller.feedsng.feed.rome

import com.rometools.opml.feed.opml.Opml
import com.rometools.rome.io.ParsingFeedException
import com.rometools.rome.io.WireFeedInput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import sh.weller.feedsng.common.Failure
import sh.weller.feedsng.common.Result
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.feed.api.required.FeedGroupImport
import sh.weller.feedsng.feed.api.required.FeedImport
import sh.weller.feedsng.feed.api.required.FeedImportService
import java.io.Reader

@Service
class RomeOPMLFeedImportServiceImpl : FeedImportService {

    override fun importFrom(content: String): Result<FeedImport, String> {
        logger.info("Importing OPML file")
        return content.reader().toFeedImport()
    }

    private fun Reader.toFeedImport(): Result<FeedImport, String> {
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

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(RomeOPMLFeedImportServiceImpl::class.java)
    }
}
