package sh.weller.feedsng.feed.rome

import sh.weller.feedsng.common.Failure
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.feed.api.required.FeedImport
import strikt.api.expectThat
import strikt.assertions.*
import java.io.File
import kotlin.test.Test

class RomeOPMLFeedImportServiceImplTest {

    private val testFile = File(javaClass.classLoader.getResource("ValidOPML.xml")!!.file)
    private val invalidTestFile = File(javaClass.classLoader.getResource("InvalidTestOPML.xml")!!.file)
    private val emptyTestFile = File(javaClass.classLoader.getResource("EmptyTestOPML.xml")!!.file)

    @Test
    fun `Valid OPML String returns urls`() {
        val cut = RomeOPMLFeedImportServiceImpl()

        val result = cut.importFrom(testFile.readText())

        expectThat(result)
            .isA<Success<FeedImport>>()
            .get { value }
            .and { get { feedUrls }.hasSize(1).contains("https://blog.jetbrains.com/kotlin/feed/") }
            .and {
                get { feedGroupImport }
                    .hasSize(1)
                    .first()
                    .and { get { name }.isEqualTo("Folder") }
                    .and { get { feedUrls }.hasSize(1).contains("https://blog.jetbrains.com/feed/") }
            }
    }

    @Test
    fun `Invalid OPML String returns empty list`() {
        val cut = RomeOPMLFeedImportServiceImpl()

        val result = cut.importFrom(invalidTestFile.readText())

        expectThat(result)
            .isA<Failure<String>>()
    }

    @Test
    fun `Empty OPML String returns empty list`() {
        val cut = RomeOPMLFeedImportServiceImpl()

        val result = cut.importFrom(emptyTestFile.readText())

        expectThat(result)
            .isA<Failure<String>>()
    }
}