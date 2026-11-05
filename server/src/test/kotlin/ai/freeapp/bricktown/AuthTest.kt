package ai.freeapp.bricktown

import ai.freeapp.bricktown.auth.AuthResponse
import ai.freeapp.bricktown.auth.AuthTokens
import ai.freeapp.bricktown.auth.UserDto
import ai.freeapp.bricktown.core.ErrorResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthTest {

    @Test
    fun `register signs the user in and GET me returns the profile`() = serverTest {
        assertEquals(HttpStatusCode.OK, client.get("/health").status)

        val auth = register(email = " Ada@Example.ORG ", name = "Ada")
        assertEquals("ada@example.org", auth.user.email)

        val me = client.get("/me") { bearerAuth(auth.accessToken) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(UserDto(auth.user.id, "ada@example.org", "Ada"), me.body<UserDto>())
    }

    @Test
    fun `a taken email is 409 whatever its case`() = serverTest {
        register(email = "ada@example.org")
        val again = client.post("/auth/register") {
            json("""{"email":"ADA@example.org","password":"another-pass","name":"Other"}""")
        }
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertTrue(again.body<ErrorResponse>().error.isNotBlank())
    }

    @Test
    fun `invalid input is 400 with an error body`() = serverTest {
        val bodies = listOf(
            """{"email":"ada@example.org","password":"1234567","name":"Ada"}""",
            """{"email":"not-an-email","password":"correct-horse","name":"Ada"}""",
            """{"email":"ada@example.org","password":"correct-horse","name":"  "}""",
            """{"email":"ada@example.org"""",
        )
        for (body in bodies) {
            val response = client.post("/auth/register") { json(body) }
            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            assertTrue(response.body<ErrorResponse>().error.isNotBlank(), body)
        }
    }

    @Test
    fun `a body that is not JSON is 415, not a server error`() = serverTest {
        val response = client.post("/auth/login") {
            contentType(ContentType.Text.Plain)
            setBody("""{"email":"ada@example.org","password":"correct-horse"}""")
        }
        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertTrue(response.body<ErrorResponse>().error.isNotBlank())
    }

    @Test
    fun `a wrong password or an unknown email is 401`() = serverTest {
        register(email = "ada@example.org", password = "correct-horse")
        assertEquals(HttpStatusCode.Unauthorized, login("ada@example.org", "wrong-horse").status)
        assertEquals(HttpStatusCode.Unauthorized, login("nobody@example.org", "correct-horse").status)
        assertEquals(HttpStatusCode.OK, login("ADA@example.org", "correct-horse").status)
    }

    @Test
    fun `a password past bcrypt's 72 bytes is a wrong password, not a server error`() = serverTest {
        val longest = "é".repeat(36)
        val tooLong = "é".repeat(37)
        val auth = register(email = "ada@example.org", password = longest)
        assertEquals(HttpStatusCode.OK, login("ada@example.org", longest).status)
        assertEquals(HttpStatusCode.Unauthorized, login("ada@example.org", tooLong).status)
        assertEquals(HttpStatusCode.Unauthorized, login("nobody@example.org", tooLong).status)

        val change = client.post("/me/password") {
            bearerAuth(auth.accessToken)
            json("""{"currentPassword":"$tooLong","newPassword":"battery-staple"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, change.status)
    }

    @Test
    fun `a signed-out session does not outlive its 30 days when its user never returns`() = serverTest {
        val ada = register(email = "ada@example.org")
        val adaId = UUID.fromString(ada.user.id)
        client.post("/auth/logout") { json("""{"refreshToken":"${ada.refreshToken}"}""") }
        // Thirty days on, the signed-out row has expired and Ada has not come back.
        TestDatabase.update("update refresh_tokens set expires_at = now() - interval '1 second' where user_id = ?", adaId)

        register(email = "grace@example.org", name = "Grace")

        assertEquals(0, TestDatabase.count("select count(*) from refresh_tokens where user_id = ?", adaId))
        assertEquals(1, TestDatabase.count("select count(*) from users where id = ?", adaId))
    }

    @Test
    fun `refresh rotates the token and the spent one is refused`() = serverTest {
        val first = register()

        val rotated = refresh(first.refreshToken)
        assertEquals(HttpStatusCode.OK, rotated.status)
        assertEquals(setOf("accessToken", "refreshToken"), rotated.body<JsonObject>().keys)
        val second = rotated.body<AuthTokens>()
        assertNotEquals(first.refreshToken, second.refreshToken)
        assertEquals(HttpStatusCode.OK, client.get("/me") { bearerAuth(second.accessToken) }.status)

        assertEquals(HttpStatusCode.Unauthorized, refresh(first.refreshToken).status)
        assertEquals(HttpStatusCode.OK, refresh(second.refreshToken).status)
        assertEquals(HttpStatusCode.Unauthorized, refresh("made-up-token").status)
    }

    @Test
    fun `logout revokes the refresh token`() = serverTest {
        val auth = register()
        val logout = client.post("/auth/logout") { json("""{"refreshToken":"${auth.refreshToken}"}""") }
        assertEquals(HttpStatusCode.NoContent, logout.status)
        assertEquals(HttpStatusCode.Unauthorized, refresh(auth.refreshToken).status)

        val again = client.post("/auth/logout") { json("""{"refreshToken":"${auth.refreshToken}"}""") }
        assertEquals(HttpStatusCode.NoContent, again.status)
    }

    @Test
    fun `changing the password keeps this session and ends the others`() = serverTest {
        val phone = register(email = "ada@example.org", password = "correct-horse")
        val tablet = login("ada@example.org", "correct-horse").body<AuthResponse>()

        val wrong = client.post("/me/password") {
            bearerAuth(phone.accessToken)
            json("""{"currentPassword":"not-my-password","newPassword":"battery-staple"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, wrong.status)

        val tooShort = client.post("/me/password") {
            bearerAuth(phone.accessToken)
            json("""{"currentPassword":"correct-horse","newPassword":"short"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, tooShort.status)

        val changed = client.post("/me/password") {
            bearerAuth(phone.accessToken)
            json("""{"currentPassword":"correct-horse","newPassword":"battery-staple"}""")
        }
        assertEquals(HttpStatusCode.NoContent, changed.status)

        assertEquals(HttpStatusCode.Unauthorized, refresh(tablet.refreshToken).status)
        assertEquals(HttpStatusCode.OK, refresh(phone.refreshToken).status)
        assertEquals(HttpStatusCode.Unauthorized, login("ada@example.org", "correct-horse").status)
        assertEquals(HttpStatusCode.OK, login("ada@example.org", "battery-staple").status)
    }

    @Test
    fun `signed-in routes need a valid access token`() = serverTest {
        val auth = register()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/town").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth("not-a-jwt") }.status)

        val refreshAsAccess = client.get("/me") { bearerAuth(auth.refreshToken) }
        assertEquals(HttpStatusCode.Unauthorized, refreshAsAccess.status)
        assertTrue(refreshAsAccess.bodyAsText().contains("\"error\""))
    }
}
