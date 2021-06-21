package sh.weller.feedsng.database

import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import sh.weller.feedsng.user.api.provided.UserData
import sh.weller.feedsng.user.api.provided.UserId
import sh.weller.feedsng.user.api.required.UserRepository
import strikt.api.expectThat
import strikt.assertions.*
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

    @Test
    fun `insertInviteCode, inviteCodeUnused and setInviteCodeUsed`() {
        val (_, repo) = getTestSetup()

        runBlocking {
            val issuingUser = UserId(1)
            val usingUser = UserId(2)
            val inviteCode = "ABCDEF"

            repo.insertInviteCode(issuingUser, inviteCode)

            val unusedCode = repo.isInviteCodeUsed(inviteCode)
            expectThat(unusedCode)
                .isFalse()

            repo.setInviteCodeUsed(usingUser, inviteCode)

            val usedCode = repo.isInviteCodeUsed(inviteCode)
            expectThat(usedCode)
                .isTrue()
        }
    }

    abstract fun getTestSetup(): Pair<DatabaseClient, UserRepository>
}