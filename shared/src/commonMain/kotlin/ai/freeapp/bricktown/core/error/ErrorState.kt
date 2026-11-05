package ai.freeapp.bricktown.core.error

/**
 * The complete failure vocabulary the UI is allowed to render.
 *
 * Every layer above the API client speaks in `ErrorState`, never in raw
 * `Throwable`s: a screen that has to inspect exception types has a leak in it.
 * Repositories and use cases let exceptions propagate, and the base ViewModel
 * (`tryToExecute` / `tryToCollect`) converts them exactly once, at the boundary.
 *
 * EXTENSION POINT: add a case per failure a screen must react to *differently*.
 * If two cases would render the same message and the same retry affordance,
 * they are one case.
 */
sealed interface ErrorState {

    /** The request never reached the server: no route to host, DNS, airplane mode. */
    data object NoInternet : ErrorState

    /** The request reached the server but no response arrived in time. */
    data object RequestTimeout : ErrorState

    /** 401. The refresh interceptor already tried and failed; the session is gone. */
    data object Unauthorized : ErrorState

    /** 403. Authenticated, but not allowed to do this. */
    data object Forbidden : ErrorState

    /** 404. */
    data object NotFound : ErrorState

    /** 400 / 409 / 422: the request was understood and rejected. */
    data class Validation(val message: String) : ErrorState

    /** 5xx. [status] is kept so a bug report can name the code. */
    data class ServerError(val status: Int) : ErrorState

    /** The response arrived but did not match the DTO: a client/server contract drift. */
    data class InvalidResponse(val message: String) : ErrorState

    /** Nothing above matched. Keep the message: it is the only diagnostic left. */
    data class Unknown(val message: String) : ErrorState
}
