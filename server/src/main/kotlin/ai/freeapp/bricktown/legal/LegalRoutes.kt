package ai.freeapp.bricktown.legal

import ai.freeapp.bricktown.auth.AccountService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Public HTML pages a store listing links to: the privacy policy, the terms,
 * and account deletion for people who no longer have the app (Google Play
 * requires that web route).
 */
fun Route.legalRoutes() {
    val legal by inject<LegalInfo>()
    val accounts by inject<AccountService>()

    get("/privacy") { call.respondPage(LegalPages.privacy(legal)) }
    get("/terms") { call.respondPage(LegalPages.terms(legal)) }

    route("/account/delete") {
        get { call.respondPage(LegalPages.deleteForm(legal)) }

        post {
            val form = call.receiveParameters()
            val email = form["email"].orEmpty().trim()
            val password = form["password"].orEmpty()
            val error = when {
                email.isEmpty() || password.isEmpty() -> "Enter the email and password of the account."
                !accounts.deleteWithPassword(email, password) -> "That email and password don't match an account."
                else -> null
            }
            if (error != null) {
                call.respondPage(LegalPages.deleteForm(legal, email, error), HttpStatusCode.BadRequest)
            } else {
                call.respondPage(LegalPages.deleted(legal))
            }
        }
    }
}

private suspend fun ApplicationCall.respondPage(html: String, status: HttpStatusCode = HttpStatusCode.OK) {
    // No scripts, no framing (the deletion form must not be clickjacked), and
    // the form may only post back to this server.
    response.headers.append(
        "Content-Security-Policy",
        "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'",
    )
    respondText(html, ContentType.Text.Html.withCharset(Charsets.UTF_8), status)
}
