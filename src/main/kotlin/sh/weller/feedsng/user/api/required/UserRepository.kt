package sh.weller.feedsng.user.api.required

import sh.weller.feedsng.user.api.provided.User
import sh.weller.feedsng.user.api.provided.UserData
import sh.weller.feedsng.user.api.provided.UserId

interface UserRepository {
    suspend fun getByUsername(username: String): User?
    suspend fun getByFeverAPIKey(feverAPIKeyHash: String): User?

    suspend fun insertUser(userData: UserData): UserId
}