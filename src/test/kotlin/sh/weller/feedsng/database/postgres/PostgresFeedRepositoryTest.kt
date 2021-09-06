package sh.weller.feedsng.database.postgres

import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.PostgreSQLContainer
import sh.weller.feedsng.database.AbstractFeedRepositoryTest
import sh.weller.feedsng.database.FlywayContextInitializer


@SpringBootTest
@ContextConfiguration(
    initializers = [
        PostgresFeedRepositoryTest.PostgresInitializer::class,
        FlywayContextInitializer::class
    ]
)
@EnabledOnOs(OS.LINUX)
internal class PostgresFeedRepositoryTest(
    @Autowired databaseClient: DatabaseClient,
    @Autowired feedRepository: PostgresFeedRepository
) : AbstractFeedRepositoryTest(databaseClient, feedRepository) {

    companion object {
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres").apply {
            start()
        }
    }

    class PostgresInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            TestPropertyValues.of(
                "spring.r2dbc.url=r2dbc:postgresql://${postgresContainer.host}:${postgresContainer.getMappedPort(5432)}/${postgresContainer.databaseName}",
                "spring.r2dbc.username=${postgresContainer.username}",
                "spring.r2dbc.password=${postgresContainer.password}"
            ).applyTo(applicationContext.environment)
        }
    }
}