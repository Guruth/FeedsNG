package sh.weller.feedsng.database

import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import kotlin.test.Ignore

@Ignore("For local tests only")
internal class PostgresUserRepositoryTest : AbstractUserRepositoryTest() {
    private val testSetup: Pair<DatabaseClient, UserRepository>

    init {
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionFactoryProvider
                .builder(ConnectionFactoryOptions.parse("r2dbc:postgresql://guruth@localhost:5432/feeds-ng"))
                .build()
        )

        val client = DatabaseClient.create(factory)
        val repo = UserRepository(client)

        testSetup = Pair(client, repo)
    }


    override fun getTestSetup(): Pair<DatabaseClient, UserRepository> {
        runBlocking {
            testSetup.first.sql("TRUNCATE ACCOUNT").await()
        }

        return testSetup
    }
}