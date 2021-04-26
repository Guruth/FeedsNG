package sh.weller.feedsng.database.postgres

import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import org.testcontainers.containers.PostgreSQLContainer
import sh.weller.feedsng.database.AbstractUserRepositoryTest

internal class PostgresUserRepositoryTest : AbstractUserRepositoryTest() {
    private val testSetup: Pair<DatabaseClient, PostgresUserRepository>

    init {
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres")
        postgresContainer.start()

        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionFactoryProvider
                .builder(
                    ConnectionFactoryOptions
                        .parse(
                            "r2dbc:postgresql://${postgresContainer.username}:${postgresContainer.password}@${postgresContainer.host}:${
                                postgresContainer.getMappedPort(5432)
                            }/${postgresContainer.databaseName}"
                        )
                )
                .build()
        )

        val client = DatabaseClient.create(factory)
        val repo = PostgresUserRepository(client)

        testSetup = Pair(client, repo)
    }


    override fun getTestSetup(): Pair<DatabaseClient, PostgresUserRepository> {
        runBlocking {
            testSetup.first.sql("TRUNCATE ACCOUNT").await()
        }

        return testSetup
    }
}