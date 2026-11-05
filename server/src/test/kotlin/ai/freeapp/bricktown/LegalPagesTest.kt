package ai.freeapp.bricktown

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegalPagesTest {

    private val pages = listOf("/privacy", "/terms", "/account/delete")

    @Test
    fun `pages name the configured app and carry no template values`() = serverTest(
        "app.name" to "Habit Garden",
        "app.contactEmail" to "hello@habitgarden.app",
        "app.companyName" to "Garden Labs",
    ) {
        for (path in pages) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status, path)
            assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters(), path)
            val html = response.bodyAsText()
            assertTrue(html.contains("Habit Garden"), path)
            assertTrue(html.contains("hello@habitgarden.app"), path)
            assertTrue(html.contains("""<meta name="viewport""""), path)
            for (placeholder in listOf("Template values", "kmp-ktor-template", "example.com", "[", "TODO")) {
                assertFalse(html.contains(placeholder), "$path contains '$placeholder'")
            }
        }
        assertTrue(client.get("/privacy").bodyAsText().contains("""Garden Labs ("we", "us")"""))
    }

    @Test
    fun `configured values are HTML escaped`() = serverTest("app.name" to "<script>alert(1)</script>") {
        val html = client.get("/privacy").bodyAsText()
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun `unset values fall back to the template and the footer says so`() = serverTest {
        for (path in pages) {
            assertTrue(client.get(path).bodyAsText().contains("Template values"), path)
        }
    }
}
