package sh.weller.feedsng.database

import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.h2.H2ConnectionOption
import org.springframework.r2dbc.core.DatabaseClient
import java.util.*

internal class H2UserRepositoryTest : AbstractUserRepositoryTest() {
    override fun getTestSetup(): Pair<DatabaseClient, UserRepository> {
        val factory = H2ConnectionFactory.inMemory(
            UUID.randomUUID().toString(),
            "test", "test",
            mapOf(Pair(H2ConnectionOption.MODE, "PostgreSQL"))
        )
        val client = DatabaseClient.create(factory)
        val repo = UserRepository(client)
        return Pair(client, repo)
    }
}