package sh.weller.feedsng.user.api.required

import sh.weller.feedsng.user.api.provided.User
import sh.weller.feedsng.user.api.provided.UserData
import sh.weller.feedsng.user.api.provided.UserId

interface UserRepository {
    suspend fun insertUser(userData: UserData): UserId

    suspend fun getByUserId(userId: UserId): User?
    suspend fun getByUsername(username: String): User?

    suspend fun getFeverAPIAuthentication(feverAPIKeyHash: String): User?
    suspend fun setFeverAPIAuthentication(userId: UserId, feverAPIKeyHash: String)
}