package ai.freeapp.bricktown

import ai.freeapp.bricktown.auth.AuthResponse
import ai.freeapp.bricktown.domain.TownSnapshot
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.assertEquals

/** The one tower every test town starts with. */
class TestTown(val townId: UUID, val foodId: UUID, val snapshot: TownSnapshot)

private val NOW = "2026-11-01T10:00:00Z"

suspend fun ServerScope.createTown(
    owner: AuthResponse,
    townId: UUID = UUID.randomUUID(),
    foodId: UUID = UUID.randomUUID(),
): TestTown {
    val response = client.post("/town") {
        bearerAuth(owner.accessToken)
        json(
            """{"id":"$townId","settings":{"monthlyBudgetCents":100000,"timeZone":"Europe/Bucharest"},""" +
                """"categories":[{"id":"$foodId","name":"Food","short":"Food","colorIndex":0,"position":0}]}""",
        )
    }
    assertEquals(HttpStatusCode.OK, response.status, response.toString())
    return TestTown(townId, foodId, response.body())
}

suspend fun ServerScope.putBrick(
    who: AuthResponse,
    id: UUID,
    categoryId: UUID,
    cents: Long = 1000,
    date: String = "2026-11-01",
    shared: Boolean = false,
): HttpResponse = client.put("/town/expenses/$id") {
    bearerAuth(who.accessToken)
    json(
        """{"id":"$id","categoryId":"$categoryId","amountCents":$cents,"date":"$date","shared":$shared,""" +
            """"paidBy":"ignored","createdAt":"$NOW"}""",
    )
}

suspend fun ServerScope.snapshot(who: AuthResponse): TownSnapshot {
    val response = client.get("/town") { bearerAuth(who.accessToken) }
    assertEquals(HttpStatusCode.OK, response.status)
    return response.body()
}

suspend fun ServerScope.inviteCode(owner: AuthResponse, canEdit: Boolean = true, canManage: Boolean = false): String {
    val response = client.post("/town/invites") {
        bearerAuth(owner.accessToken)
        json("""{"canEdit":$canEdit,"canManage":$canManage}""")
    }
    assertEquals(HttpStatusCode.OK, response.status, response.toString())
    return response.body<ai.freeapp.bricktown.domain.Invite>().code
}

suspend fun ServerScope.accept(who: AuthResponse, code: String, color: Int = 1, initial: String = "A"): HttpResponse =
    client.post("/invites/$code/accept") {
        bearerAuth(who.accessToken)
        json("""{"colorIndex":$color,"initial":"$initial"}""")
    }

/** Owner "Ada" and member "Ana" in one town. */
class Pair2(val owner: AuthResponse, val ana: AuthResponse, val town: TestTown)

suspend fun ServerScope.twoPeople(canEdit: Boolean = false, canManage: Boolean = false, tag: String = ""): Pair2 {
    val owner = register(email = "ada$tag@example.org", name = "Ada")
    val ana = register(email = "ana$tag@example.org", name = "Ana")
    val town = createTown(owner)
    val code = inviteCode(owner, canEdit, canManage)
    assertEquals(HttpStatusCode.OK, accept(ana, code).status)
    return Pair2(owner, ana, town)
}

suspend fun ServerScope.deleteBrick(who: AuthResponse, id: UUID): HttpResponse =
    client.delete("/town/expenses/$id") { bearerAuth(who.accessToken) }
