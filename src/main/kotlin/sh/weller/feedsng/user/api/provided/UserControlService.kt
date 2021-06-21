package sh.weller.feedsng.user.api.provided

import sh.weller.feedsng.common.Result

interface UserControlService {
    suspend fun createUser(username: String, password: String): CreateUserResult
    suspend fun createUser(username: String, password: String, inviteCode: String): CreateUserResult

    suspend fun enableFeverAPI(userId: UserId): Result<String, String>

    suspend fun createInviteCode(userId: UserId): String?
}

sealed class CreateUserResult {
    data class Success(val userId: UserId) : CreateUserResult()
    object UsernameAlreadyExist : CreateUserResult()
    object PasswordNotValid : CreateUserResult()
    object InviteCodeInvalid : CreateUserResult()
}