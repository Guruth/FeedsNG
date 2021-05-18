package sh.weller.feedsng.user.impl

import org.springframework.stereotype.Service
import sh.weller.feedsng.user.api.provided.User
import sh.weller.feedsng.user.api.provided.UserId
import sh.weller.feedsng.user.api.provided.UserQueryService
import sh.weller.feedsng.user.api.required.UserRepository

@Service
class UserQueryServiceImpl(
    private val userRepository: UserRepository
) : UserQueryService {

    override suspend fun getUserByFeverAPIKey(feverAPIKeyHash: String): User? =
        userRepository.getFeverAPIAuthentication(feverAPIKeyHash)

    override suspend fun getUserByUsername(username: String): User? =
        userRepository.getByUsername(username)

    override suspend fun getUserByUserId(userId: UserId): User? =
        userRepository.getByUserId(userId)
}