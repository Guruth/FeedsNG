package sh.weller.feedsng.user

interface UserService {
    fun addUser(): UserId

    fun getUser(id: UserId): User
}

inline class UserId(val id: Int) {
    init {
        require(id > 0)
    }
}

data class User(
    val name: String
)