package ai.freeapp.bricktown.core.ui

import ai.freeapp.bricktown.core.error.ErrorState

/**
 * The one place an [ErrorState] becomes words on screen. A [ErrorState.Validation]
 * carries the server's own sentence (or a ViewModel's), so it is shown as is.
 */
fun ErrorState.toMessage(): String = when (this) {
    ErrorState.NoInternet -> "No connection. Check your internet and try again."
    ErrorState.RequestTimeout -> "The server took too long to answer. Try again."
    ErrorState.Unauthorized -> "Your session ended. Sign in again."
    ErrorState.Forbidden -> "You are not allowed to do that."
    ErrorState.NotFound -> "That no longer exists."
    is ErrorState.Validation -> message
    is ErrorState.ServerError -> "Something went wrong on our side ($status). Try again in a moment."
    is ErrorState.InvalidResponse -> "The app and the server disagree. Update the app and try again."
    is ErrorState.Unknown -> "Something went wrong. Try again."
}
