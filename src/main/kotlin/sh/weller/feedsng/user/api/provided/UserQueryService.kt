package sh.weller.feedsng.user.api.provided

// TODO: Somehow integrate this with Spring ReactiveUserDetailsService
interface UserQueryService {
    suspend fun getUserByFeverAPIKey(feverAPIKeyHash: String): User?

    suspend fun getUserByUsername(username: String): User?
}
