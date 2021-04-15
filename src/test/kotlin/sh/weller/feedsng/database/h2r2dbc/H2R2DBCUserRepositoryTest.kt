package sh.weller.feedsng.database.h2r2dbc

import io.r2dbc.h2.H2ConnectionFactory
import io.r2dbc.spi.Row
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.flow
import sh.weller.feedsng.user.api.provided.UserData
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.util.*
import kotlin.test.Test


internal class H2R2DBCUserRepositoryTest {

    @Test
    fun `init`() {
        val (client, _) = getTestSetup()

        runBlocking {
            val mappedList = mutableListOf<String>()
            client
                .sql("SHOW TABLES")
                .map<String> { row: Row -> row.getReified("TABLE_NAME") }
                .flow()
                .toCollection(mappedList)

            expectThat(mappedList)
                .contains("USER")
        }
    }

    @Test
    fun `insertUser, getByUsername, getByUserId`() {
        val (_, repo) = getTestSetup()

        runBlocking {
            val testUserData = UserData("test", "123")
            val createdUserId = repo.insertUser(testUserData)

            val userByUsername = repo.getByUsername(testUserData.username)
            expectThat(userByUsername)
                .isNotNull()
                .and {
                    get { userId }.isEqualTo(createdUserId)
                    get { userData }
                        .and {
                            get { username }.isEqualTo(testUserData.username)
                            get { passwordHash }.isEqualTo(testUserData.passwordHash)
                            get { feverAPIKeyHash }.isNull()
                        }
                }

            val userByUserId = repo.getByUserId(createdUserId)
            expectThat(userByUserId)
                .isNotNull()
                .isEqualTo(userByUsername)
        }
    }

    @Test
    fun `insertUser, setFeverAPIKeyHash, getByFeverAPIKey`() {
        val (_, repo) = getTestSetup()

        runBlocking {
            val testUserData = UserData("test", "123")
            val createdUserId = repo.insertUser(testUserData)

            val feverAPIKey = "123"
            repo.setFeverAPIAuthentication(createdUserId, feverAPIKey)
            val updatedUser = repo.getFeverAPIAuthentication(feverAPIKey)
            expectThat(updatedUser)
                .isNotNull()
                .and {
                    get { userId }.isEqualTo(createdUserId)
                    get { userData }
                        .and {
                            get { username }.isEqualTo(testUserData.username)
                            get { passwordHash }.isEqualTo(testUserData.passwordHash)
                            get { feverAPIKeyHash }.isNotNull().isEqualTo(feverAPIKey)
                        }
                }
        }
    }

    private fun getTestSetup(): Pair<DatabaseClient, H2R2DBCUserRepository> {
        val factory = H2ConnectionFactory.inMemory(UUID.randomUUID().toString())
        val client = DatabaseClient.create(factory)
        val repo = H2R2DBCUserRepository(client)
        return Pair(client, repo)
    }
}