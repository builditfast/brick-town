package ai.freeapp.bricktown.core.error

import ai.freeapp.bricktown.api.ApiException
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * The single place a `Throwable` becomes an [ErrorState].
 *
 * Called from the base ViewModel, never from a screen. Cancellation is not a
 * failure and must be rethrown by the caller before this function is reached.
 */
fun Throwable.toErrorState(): ErrorState = when (this) {
    // Before ResponseException, which it extends: it carries the server's own sentence.
    is ApiException -> response.status.value.toErrorState(serverMessage)
    is ResponseException -> response.status.value.toErrorState(message)
    is HttpRequestTimeoutException -> ErrorState.RequestTimeout
    is NoTransformationFoundException -> ErrorState.InvalidResponse(shortMessage())
    is SerializationException -> ErrorState.InvalidResponse(shortMessage())
    is IOException -> ErrorState.NoInternet
    else -> ErrorState.Unknown(shortMessage())
}

/**
 * HTTP status to [ErrorState].
 *
 * EXTENSION POINT: an API that returns business codes in the body (beep-beep
 * used "1013"-style codes) should parse them in its repository, throw a typed
 * domain exception, and get a branch in [toErrorState] above. Do not widen this
 * function: it only knows about transport-level status.
 */
private fun Int.toErrorState(detail: String?): ErrorState = when (this) {
    401 -> ErrorState.Unauthorized
    403 -> ErrorState.Forbidden
    404 -> ErrorState.NotFound
    400, 409, 422 -> ErrorState.Validation(detail ?: "Request rejected ($this)")
    in 500..599 -> ErrorState.ServerError(this)
    else -> ErrorState.Unknown(detail ?: "HTTP $this")
}

/** Ktor exception messages can carry a whole response body; keep them loggable. */
private fun Throwable.shortMessage(): String =
    (message ?: this::class.simpleName ?: "Unknown error").take(300)
