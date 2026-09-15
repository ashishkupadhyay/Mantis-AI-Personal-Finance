package io.github.ashishkupadhyay.mantis.core.common.result

/**
 * Domain result type (the "Result" of doc 02 §3, named `Outcome` to avoid clashing with `kotlin.Result`).
 * Use it at repository/use-case boundaries where failure is an expected state the UI must render;
 * throw for programming errors.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): AppError? = (this as? Failure)?.error

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw AppException(error)
    }

    fun <R> map(transform: (T) -> R): Outcome<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    fun onSuccess(action: (T) -> Unit): Outcome<T> = also { if (it is Success) action(it.value) }
    fun onFailure(action: (AppError) -> Unit): Outcome<T> = also { if (it is Failure) action(it.error) }

    companion object {
        fun <T> success(value: T): Outcome<T> = Success(value)
        fun failure(error: AppError): Outcome<Nothing> = Failure(error)

        /** Runs [block], mapping any exception to [AppError.Unexpected] (or the wrapped error of an [AppException]). */
        inline fun <T> runCatching(block: () -> T): Outcome<T> = try {
            Success(block())
        } catch (e: AppException) {
            Failure(e.error)
        } catch (e: Exception) {
            Failure(AppError.Unexpected(e.message ?: e::class.simpleName ?: "error", e))
        }
    }
}

/** Carries an [AppError] across a `throw` boundary (e.g. inside a database transaction). */
class AppException(val error: AppError) : RuntimeException(error.message, error.cause)

/** Expected, user-renderable failures. Keep messages free of PII/amounts — they may be logged. */
sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    data class Validation(val field: String, override val message: String) : AppError(message)
    data class NotFound(val entity: String, val id: String) : AppError("$entity $id not found")
    data class Conflict(override val message: String) : AppError(message)
    data class Io(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
    data class Network(override val message: String, val statusCode: Int? = null, override val cause: Throwable? = null) :
        AppError(message, cause)
    data class Unauthorized(override val message: String = "Not signed in") : AppError(message)
    data class Unsupported(override val message: String) : AppError(message)
    data class Unexpected(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
}
