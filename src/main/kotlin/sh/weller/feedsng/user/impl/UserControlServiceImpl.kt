package sh.weller.feedsng.user.impl

import org.springframework.security.crypto.codec.Hex
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sh.weller.feedsng.common.Failure
import sh.weller.feedsng.common.Result
import sh.weller.feedsng.common.asSuccess
import sh.weller.feedsng.user.api.provided.CreateUserResult
import sh.weller.feedsng.user.api.provided.UserControlService
import sh.weller.feedsng.user.api.provided.UserData
import sh.weller.feedsng.user.api.provided.UserId
import sh.weller.feedsng.user.api.required.UserRepository
import java.security.MessageDigest

@Service
class UserControlServiceImpl(
    private val repository: UserRepository
) : UserControlService {
    private val passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    override suspend fun createUser(username: String, password: String): CreateUserResult {
        if (isUsernameInvalid(username)) {
            return CreateUserResult.UsernameInvalid
        }
        if (isPasswordInvalid(password)) {
            return CreateUserResult.PasswordNotValid
        }
        val existingUser = repository.getByUsername(username)
        if (existingUser != null) {
            return CreateUserResult.UsernameAlreadyExist
        }

        val userId = repository.insertUser(UserData(username, passwordEncoder.encode(password)))
        return CreateUserResult.Success(userId)
    }

    @Transactional
    override suspend fun createUser(username: String, password: String, inviteCode: String): CreateUserResult {
        if (isUsernameInvalid(username)) {
            return CreateUserResult.UsernameInvalid
        }
        if (isPasswordInvalid(password)) {
            return CreateUserResult.PasswordNotValid
        }
        val existingUser = repository.getByUsername(username)
        if (existingUser != null) {
            return CreateUserResult.UsernameAlreadyExist
        }
        if (repository.isInviteCodeUsed(inviteCode)) {
            return CreateUserResult.InviteCodeInvalid
        }

        val userId = repository.insertUser(UserData(username, passwordEncoder.encode(password)))
        repository.setInviteCodeUsed(userId, inviteCode)

        return CreateUserResult.Success(userId)
    }

    private fun isPasswordInvalid(password: String): Boolean {
        if (password.isBlank()) {
            return true
        } else if (password.length < 8) {
            return true
        }
        return false
    }

    private fun isUsernameInvalid(username: String): Boolean {
        if (username.isBlank()) {
            return true
        } else if (username.length < 4) {
            return true
        }
        return false
    }

    override suspend fun enableFeverAPI(userId: UserId): Result<String, String> {
        val user = repository.getByUserId(userId)
        requireNotNull(user) // This shouldn't be the case ever...
        if (user.isDisabled()) {
            return Failure("Fever API Key can not be generated: User is not enabled or locked.")
        }

        val generatedAPIKey = generateRandomString()
        val feverAPIAuthentication = getFeverAPIAuthentication(user.userData.username, generatedAPIKey)
        repository.setFeverAPIAuthentication(userId, feverAPIAuthentication)

        return generatedAPIKey.asSuccess()
    }

    private fun getFeverAPIAuthentication(username: String, apiKey: String): String {
        val md5Digest = MessageDigest.getInstance("MD5")
        md5Digest.update("$username:$apiKey".toByteArray())
        val md5Bytes = md5Digest.digest()
        return Hex.encode(md5Bytes).concatToString()
    }


    override suspend fun createInviteCode(userId: UserId): String? {
        val inviteCode = generateRandomString(8)
        // TODO: Limit number of invite codes?
        repository.insertInviteCode(userId, inviteCode)
        return inviteCode
    }

    companion object {
        private fun generateRandomString(length: Int = 18): String = (0 until length)
            .map { (('A'..'Z') + ('a'..'z') + ('0'..'9')).random() }
            .joinToString("")
    }
}

