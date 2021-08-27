package sh.weller.feedsng.user.impl

import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.codec.Hex
import sh.weller.feedsng.common.Success
import sh.weller.feedsng.common.valueOrNull
import sh.weller.feedsng.user.api.provided.CreateUserResult
import sh.weller.feedsng.user.api.provided.UserControlService
import sh.weller.feedsng.user.api.required.UserRepository
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNotNull
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertIs

@SpringBootTest(
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///~/db/testdb"
    ]
)
internal class UserControlServiceImplTest(
    @Autowired private val repo: UserRepository,
    @Autowired private val cut: UserControlService
) {

    @Test
    fun `createUser, enableFeverAPI`() {
        runBlocking {
            val testUsername = "Foobar"
            val testPassword = "BarBazFoo"

            val createUserResult = cut.createUser(testUsername, testPassword)
            assertIs<CreateUserResult.Success>(createUserResult)
            val createdUser = repo.getByUserId(createUserResult.userId)

            expectThat(createdUser)
                .isNotNull()
                .get { userData }
                .and {
                    get { username }.isEqualTo(testUsername)
                    get { passwordHash }.isNotEqualTo(testPassword)
                }

            val generatedAPIKey = cut.enableFeverAPI(createUserResult.userId)
            expectThat(generatedAPIKey)
                .isA<Success<String>>()
            val feverApiAuth = calcMD5Hash("$testUsername:${generatedAPIKey.valueOrNull()}")

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
}