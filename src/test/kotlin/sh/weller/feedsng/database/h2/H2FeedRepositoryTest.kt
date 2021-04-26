package sh.weller.feedsng.database.h2

import io.r2dbc.h2.H2ConnectionFactory
import org.springframework.r2dbc.core.DatabaseClient
import sh.weller.feedsng.database.AbstractFeedRepositoryTest
import java.util.*

internal class H2FeedRepositoryTest : AbstractFeedRepositoryTest() {
    override fun getTestSetup(): Pair<DatabaseClient, H2FeedRepository> {
        val factory = H2ConnectionFactory.inMemory(UUID.randomUUID().toString())
        val client = DatabaseClient.create(factory)
        val repo = H2FeedRepository(client)
        return Pair(client, repo)
    }

}