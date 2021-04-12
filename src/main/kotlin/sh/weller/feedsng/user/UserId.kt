package sh.weller.feedsng.user

inline class UserId(val id: Int) {
    init {
        require(id > 0)
    }
}
