package sh.weller.feedsng.user.api.provided

interface UserControlService {
    suspend fun createUser(username: String, password: String): UserId
    suspend fun enableFeverAPI(userId: UserId): String
}