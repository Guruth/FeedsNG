package sh.weller.feedsng.database

import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import sh.weller.feedsng.user.api.provided.UserData
import sh.weller.feedsng.user.api.required.UserRepository
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import kotlin.test.Test


internal abstract class AbstractUserRepositoryTest {

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

    abstract fun getTestSetup(): Pair<DatabaseClient, UserRepository>
}