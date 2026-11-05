package ai.freeapp.bricktown.town

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

/** The page an invite link opens on a phone without the app, and in any browser. Public. */
fun Route.joinRoutes() {
    val repo by inject<TownRepository>()

    get("/join/{code}") {
        val code = InviteCodes.normalize(call.parameters["code"].orEmpty())
        val town = code?.let { repo.joinPageTown(it) }
        call.response.headers.append(
            "Content-Security-Policy",
            "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'",
        )
        if (code == null || town == null) {
            call.respondText(JoinPage.expired(), ContentType.Text.Html.withCharset(Charsets.UTF_8), HttpStatusCode.NotFound)
        } else {
            call.respondText(JoinPage.open(town, code), ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }
    }
}

object JoinPage {
    fun open(townName: String, code: String): String = page(
        title = "Join ${townName.html()} in Brick Town",
        body = """
<h1>Join ${townName.html()} in Brick Town</h1>
<p><a class="button" href="bricktown://join/${code.html()}">Open in Brick Town</a></p>
<p>No app yet, or the button does nothing? Open Brick Town and type this code.</p>
<p class="code">${code.html()}</p>
""",
    )

    fun expired(): String = page(
        title = "This invite has expired",
        body = """
<h1>That invite has expired</h1>
<p>Ask for a new link.</p>
""",
    )

    private fun page(title: String, body: String) = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$title</title>
<style>
body{font-family:system-ui,sans-serif;max-width:28rem;margin:3rem auto;padding:0 1.25rem;text-align:center;color:#2b2118;background:#fbf6ef}
h1{font-size:1.5rem}
.button{display:inline-block;padding:.9rem 1.6rem;border-radius:1.5rem;background:#e8743b;color:#fff;text-decoration:none;font-weight:600}
.code{font-size:2.5rem;letter-spacing:.15em;font-weight:700;font-family:ui-monospace,monospace}
</style>
</head>
<body>
$body</body>
</html>
"""

    private fun String.html(): String = buildString(length) {
        for (c in this@html) append(
            when (c) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> c
            },
        )
    }
}
