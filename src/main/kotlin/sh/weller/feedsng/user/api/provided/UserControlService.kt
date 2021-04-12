package sh.weller.feedsng.user.api.provided

interface UserControlService {

    suspend fun createUser()

    suspend fun enableFeverAPI(userId: UserId)

}