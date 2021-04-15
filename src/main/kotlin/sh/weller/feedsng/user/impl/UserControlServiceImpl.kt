package sh.weller.feedsng.user.impl

import org.springframework.security.crypto.codec.Hex
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import sh.weller.feedsng.user.api.provided.UserControlService
import sh.weller.feedsng.user.api.provided.UserData
import sh.weller.feedsng.user.api.provided.UserId
import sh.weller.feedsng.user.api.required.UserRepository
import java.security.MessageDigest

@Service
class UserControlServiceImpl(
    private val repository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : UserControlService {

    override suspend fun createUser(username: String, password: String): UserId =
        repository.insertUser(UserData(username, passwordEncoder.encode(password)))

    override suspend fun enableFeverAPI(userId: UserId): String {
        val user = repository.getByUserId(userId)
        requireNotNull(user) // This shouldn't be the case ever...

        val generatedAPIKey = generateAPIKey()
        val feverAPIAuthentication = getFeverAPIAuthentication(user.userData.username, generatedAPIKey)
        repository.setFeverAPIAuthentication(userId, feverAPIAuthentication)

        return generatedAPIKey
    }

    private fun getFeverAPIAuthentication(username: String, apiKey: String): String {
        val md5Digest = MessageDigest.getInstance("MD5")
        md5Digest.update("$username:$apiKey".toByteArray())
        val md5Bytes = md5Digest.digest()
        return Hex.encode(md5Bytes).concatToString()
    }

    companion object {
        private fun generateAPIKey(): String = (1..18)
            .map { (('A'..'Z') + ('a'..'z') + ('0'..'9')).random() }
            .joinToString("")
    }
}