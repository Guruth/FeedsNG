package sh.weller.feedsng.user

@JvmInline
value class UserId(val id: Int) {
    init {
        require(id > 0)
    }
}
