package ai.freeapp.bricktown

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountDeletionTest {

    @Test
    fun `DELETE me removes the user, their town and their sessions`() = serverTest {
        val ada = register(email = "ada@example.org")
        val grace = register(email = "grace@example.org", name = "Grace")
        val adaId = UUID.fromString(ada.user.id)
        val graceId = UUID.fromString(grace.user.id)
        createTown(ada)
        createTown(grace)

        val deleted = client.delete("/me") { bearerAuth(ada.accessToken) }
        assertEquals(HttpStatusCode.NoContent, deleted.status)

        assertEquals(0, TestDatabase.count("select count(*) from users where id = ?", adaId))
        assertEquals(0, TestDatabase.count("select count(*) from towns where owner_id = ?", adaId))
        assertEquals(0, TestDatabase.count("select count(*) from refresh_tokens where user_id = ?", adaId))
        assertEquals(1, TestDatabase.count("select count(*) from towns where owner_id = ?", graceId))

        // The access token still has minutes to live; the account behind it is gone.
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth(ada.accessToken) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/town") { bearerAuth(ada.accessToken) }.status)
        assertEquals(HttpStatusCode.Unauthorized, refresh(ada.refreshToken).status)
        assertEquals(HttpStatusCode.OK, client.get("/me") { bearerAuth(grace.accessToken) }.status)
    }

    @Test
    fun `the web form deletes the account only with the right password`() = serverTest {
        val ada = register(email = "ada@example.org", password = "correct-horse")
        val adaId = UUID.fromString(ada.user.id)
        createTown(ada)

        val form = client.get("/account/delete")
        assertEquals(HttpStatusCode.OK, form.status)
        assertTrue(form.bodyAsText().contains("""<form method="post" action="/account/delete">"""))

        val wrong = client.submitForm(
            url = "/account/delete",
            formParameters = parameters {
                append("email", "ada@example.org")
                append("password", "wrong-horse")
            },
        )
        assertEquals(HttpStatusCode.BadRequest, wrong.status)
        val wrongPage = wrong.bodyAsText()
        assertTrue(wrongPage.contains("don&#39;t match an account"), wrongPage)
        assertTrue(wrongPage.contains("""value="ada@example.org""""))
        assertEquals(1, TestDatabase.count("select count(*) from users where id = ?", adaId))

        val tooLong = client.submitForm(
            url = "/account/delete",
            formParameters = parameters {
                append("email", "ada@example.org")
                append("password", "x".repeat(73))
            },
        )
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)

        val empty = client.submitForm(url = "/account/delete", formParameters = parameters {})
        assertEquals(HttpStatusCode.BadRequest, empty.status)

        val right = client.submitForm(
            url = "/account/delete",
            formParameters = parameters {
                append("email", "Ada@Example.org")
                append("password", "correct-horse")
            },
        )
        assertEquals(HttpStatusCode.OK, right.status)
        assertTrue(right.bodyAsText().contains("Your account has been deleted"))
        assertEquals(0, TestDatabase.count("select count(*) from users where id = ?", adaId))
        assertEquals(0, TestDatabase.count("select count(*) from towns where owner_id = ?", adaId))
        assertEquals(0, TestDatabase.count("select count(*) from refresh_tokens where user_id = ?", adaId))
    }
}
