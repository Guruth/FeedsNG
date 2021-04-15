package sh.weller.feedsng.user.api.provided

inline class UserId(val id: Int) {
    init {
        require(id > 0)
    }
}

fun Int.toUserId() = UserId(this)
fun Int?.toUserId() = this?.toUserId()

data class User(
    val userId: UserId,
    val userData: UserData
)

data class UserData(
    val username: String,
    val passwordHash: String,
    val feverAPIKeyHash: String? = null,
    val isEnabled: Boolean = true,
    val isLocked: Boolean = false,
)