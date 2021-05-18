package sh.weller.feedsng.user.api.provided

interface UserQueryService {
    suspend fun getUserByFeverAPIKey(feverAPIKeyHash: String): User?
    suspend fun getUserByUsername(username: String): User?
    suspend fun getUserByUserId(userId: UserId): User?
}
