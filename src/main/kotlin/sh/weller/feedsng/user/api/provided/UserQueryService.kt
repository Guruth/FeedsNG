package sh.weller.feedsng.user.api.provided

// TODO: Somehow integrate this with Spring ReactiveUserDetailsService
interface UserQueryService {
    fun getUserByFeverAPIKey(feverAPIKeyHash: String): User?

    fun getUserByUsername(username: String): User?
}
