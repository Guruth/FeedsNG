package sh.weller.feedsng.database.postgres

import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import sh.weller.feedsng.database.AbstractFeedRepositoryTest
import kotlin.test.Ignore

@Ignore("For local tests only")
internal class PostgresFeedRepositoryTest : AbstractFeedRepositoryTest() {
    private val testSetup: Pair<DatabaseClient, PostgresFeedRepository>

    init {
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionFactoryProvider
                .builder(ConnectionFactoryOptions.parse("r2dbc:postgresql://guruth@localhost:5432/feedsng"))
                .build()
        )

        val client = DatabaseClient.create(factory)
        val repo = PostgresFeedRepository(client)

        testSetup = Pair(client, repo)
    }


    override fun getTestSetup(): Pair<DatabaseClient, PostgresFeedRepository> {
        runBlocking {
            testSetup.first.sql("TRUNCATE FEED").await()
            testSetup.first.sql("TRUNCATE FEED_ITEM").await()
            testSetup.first.sql("TRUNCATE USER_GROUP").await()
            testSetup.first.sql("TRUNCATE USER_GROUP_FEED").await()
            testSetup.first.sql("TRUNCATE USER_FEED").await()
            testSetup.first.sql("TRUNCATE USER_FEED_ITEM").await()
        }

        return testSetup
    }
}