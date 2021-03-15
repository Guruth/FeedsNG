package sh.weller.feedsng.common

sealed class Result<out T, out E>

class Success<out T>(val value: T) : Result<T, Nothing>()
class Failure<out E>(val reason: E) : Result<Nothing, E>()

fun <T> T.asSuccess(): Success<T> = Success(this)

fun <T, E> Result<T, E>.valueOrNull(): T? =
    when (this) {
        is Success -> this.value
        is Failure -> null
    }

fun <T> Result<T, T>.get(): T =
    when (this) {
        is Success -> this.value
        is Failure -> this.reason
    }


inline fun <T, E> Result<T, E>.onFailure(block: (Failure<E>) -> Nothing): T = when (this) {
    is Success<T> -> value
    is Failure<E> -> block(this)
}