package sh.weller.feedsng.database.h2

import io.r2dbc.h2.H2ConnectionFactory
import org.springframework.r2dbc.core.DatabaseClient
import sh.weller.feedsng.database.AbstractUserRepositoryTest
import java.util.*

internal class H2UserRepositoryTest : AbstractUserRepositoryTest() {
    override fun getTestSetup(): Pair<DatabaseClient, H2UserRepository> {
        val factory = H2ConnectionFactory.inMemory(UUID.randomUUID().toString())
        val client = DatabaseClient.create(factory)
        val repo = H2UserRepository(client)
        return Pair(client, repo)
    }
}