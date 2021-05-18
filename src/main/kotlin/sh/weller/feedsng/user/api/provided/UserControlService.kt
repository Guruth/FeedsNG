package sh.weller.feedsng.user.api.provided

import sh.weller.feedsng.common.Result

interface UserControlService {
    suspend fun createUser(username: String, password: String): UserId
    suspend fun enableFeverAPI(userId: UserId): Result<String, String>
}