package sh.weller.feedsng.user.impl

import io.r2dbc.h2.H2ConnectionFactory
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.security.crypto.codec.Hex
import sh.weller.feedsng.database.h2.H2UserRepository
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNotNull
import java.security.MessageDigest
import java.util.*
import kotlin.test.Test


internal class UserControlServiceImplTest {

    @Test
    fun `createUser, enableFeverAPI`() {
        val (repo, cut) = getTestSetup()

        runBlocking {
            val testUsername = "Foobar"
            val testPassword = "BarBaz"

            val userId = cut.createUser(testUsername, testPassword)
            val createdUser = repo.getByUserId(userId)

            expectThat(createdUser)
                .isNotNull()
                .get { userData }
                .and {
                    get { username }.isEqualTo(testUsername)
                    get { passwordHash }.isNotEqualTo(testPassword)
                }

            val generatedAPIKey = cut.enableFeverAPI(userId)
            val feverApiAuth = calcMD5Hash("$testUsername:$generatedAPIKey")

            val storedUser = repo.getFeverAPIAuthentication(feverApiAuth)
            expectThat(storedUser)
                .isNotNull()
                .get { userData }
                .and {
                    get { username }.isEqualTo(testUsername)
                    get { passwordHash }.isNotEqualTo(testPassword)
                }
        }
    }

    private fun calcMD5Hash(value: String): String {
        val md5Digest = MessageDigest.getInstance("MD5")
        md5Digest.update(value.toByteArray())
        val md5Bytes = md5Digest.digest()
        return Hex.encode(md5Bytes).concatToString()
    }

    private fun getTestSetup(): Pair<sh.weller.feedsng.user.api.required.UserRepository, UserControlServiceImpl> {
        val factory = H2ConnectionFactory.inMemory(UUID.randomUUID().toString())
        val client = DatabaseClient.create(factory)
        val repo = H2UserRepository(client)

        val cut = UserControlServiceImpl(repo)
        return repo to cut
    }
}