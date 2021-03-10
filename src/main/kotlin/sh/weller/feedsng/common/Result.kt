package sh.weller.feedsng.common

sealed class ResultNG<out T, out E>

class Success<out T>(val value: T) : ResultNG<T, Nothing>()
class Failure<out E>(val reason: E) : ResultNG<Nothing, E>()


