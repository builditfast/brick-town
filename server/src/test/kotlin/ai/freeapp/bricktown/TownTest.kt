package ai.freeapp.bricktown

import ai.freeapp.bricktown.core.ErrorResponse
import ai.freeapp.bricktown.domain.MemberRole
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TownTest {

    @AfterTest
    fun clearFlags() = TestFlags.clear()

    @Test
    fun `create is idempotent by id and a second town is refused`() = serverTest {
        val ada = register(name = "Ada Lovelace")
        val first = createTown(ada)
        assertEquals("Ada's town", first.snapshot.town.name)
        assertEquals(MemberRole.OWNER, first.snapshot.members.single().role)
        assertEquals(ada.user.id, first.snapshot.viewerId)

        val replay = createTown(ada, townId = first.townId, foodId = first.foodId)
        assertEquals(first.snapshot.town.id, replay.snapshot.town.id)
        assertEquals(1, TestDatabase.count("select count(*) from towns where owner_id = ?", UUID.fromString(ada.user.id)))

        val other = client.post("/town") {
            bearerAuth(ada.accessToken)
            json(
                """{"id":"${UUID.randomUUID()}","settings":{"monthlyBudgetCents":1,"timeZone":"UTC"},""" +
                    """"categories":[{"id":"${UUID.randomUUID()}","name":"Rent","short":"Rent","colorIndex":1,"position":0}]}""",
            )
        }
        assertEquals(HttpStatusCode.Conflict, other.status)
    }

    @Test
    fun `a user with no town gets 404 and the snapshot carries no emails`() = serverTest {
        val ada = register()
        val missing = client.get("/town") { bearerAuth(ada.accessToken) }
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("You are not in a town yet.", missing.body<ErrorResponse>().error)

        val pair = twoPeople(tag = "2")
        val raw = client.get("/town") { bearerAuth(pair.owner.accessToken) }.bodyAsText()
        assertFalse(raw.contains("@"), raw)
    }

    @Test
    fun `a member edits another member's brick only with canEdit`() = serverTest {
        val locked = twoPeople(canEdit = false)
        val brick = UUID.randomUUID()
        assertEquals(HttpStatusCode.OK, putBrick(locked.owner, brick, locked.town.foodId).status)

        val refused = putBrick(locked.ana, brick, locked.town.foodId, cents = 2000)
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals(HttpStatusCode.Forbidden, deleteBrick(locked.ana, brick).status)

        val open = twoPeople(canEdit = true, tag = "2")
        val other = UUID.randomUUID()
        assertEquals(HttpStatusCode.OK, putBrick(open.owner, other, open.town.foodId).status)
        assertEquals(HttpStatusCode.OK, putBrick(open.ana, other, open.town.foodId, cents = 2000).status)
        assertEquals(2000L, snapshot(open.owner).expenses.single().amountCents)
    }

    @Test
    fun `an update keeps paidBy and createdAt and an insert sets them`() = serverTest {
        val pair = twoPeople(canEdit = true)
        val brick = UUID.randomUUID()
        val created = putBrick(pair.ana, brick, pair.town.foodId).body<ai.freeapp.bricktown.domain.Expense>()
        assertEquals(pair.ana.user.id, created.paidBy)

        val edited = putBrick(pair.owner, brick, pair.town.foodId, cents = 5000).body<ai.freeapp.bricktown.domain.Expense>()
        assertEquals(pair.ana.user.id, edited.paidBy)
        assertEquals(created.createdAt, edited.createdAt)
        assertEquals(5000L, edited.amountCents)
    }

    @Test
    fun `a member cannot change settings without canManage`() = serverTest {
        val denied = twoPeople(canManage = false)
        val body = """{"monthlyBudgetCents":250000,"timeZone":"Europe/Bucharest"}"""
        val refused = client.put("/town/settings") { bearerAuth(denied.ana.accessToken); json(body) }
        assertEquals(HttpStatusCode.Forbidden, refused.status)

        val allowed = twoPeople(canManage = true, tag = "2")
        val ok = client.put("/town/settings") { bearerAuth(allowed.ana.accessToken); json(body) }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals(250000L, snapshot(allowed.owner).town.settings.monthlyBudgetCents)
    }

    @Test
    fun `a full town refuses a third person`() = serverTest {
        val pair = twoPeople()
        val full = client.post("/town/invites") { bearerAuth(pair.owner.accessToken); json("{}") }
        assertEquals(HttpStatusCode.Conflict, full.status)
        assertEquals("Your town is full.", full.body<ErrorResponse>().error)
    }

    @Test
    fun `a second person cannot join once the town has two people`() = serverTest {
        val owner = register(email = "ada@example.org", name = "Ada")
        val ana = register(email = "ana@example.org", name = "Ana")
        val ben = register(email = "ben@example.org", name = "Ben")
        createTown(owner)
        val first = inviteCode(owner)
        val second = inviteCode(owner)
        assertEquals(HttpStatusCode.OK, accept(ana, first).status)
        val refused = accept(ben, second, color = 2, initial = "B")
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertEquals("This town already has two people.", refused.body<ErrorResponse>().error)
    }

    @Test
    fun `a user with a town cannot accept an invite`() = serverTest {
        val owner = register(email = "ada@example.org", name = "Ada")
        val ben = register(email = "ben@example.org", name = "Ben")
        createTown(owner)
        createTown(ben)
        val refused = accept(ben, inviteCode(owner))
        assertEquals(HttpStatusCode.Conflict, refused.status)
        assertEquals("You already have a town. Leave it first.", refused.body<ErrorResponse>().error)
    }

    @Test
    fun `an expired or revoked invite answers 404 and a used one stops working`() = serverTest {
        val owner = register(email = "ada@example.org", name = "Ada")
        val ana = register(email = "ana@example.org", name = "Ana")
        createTown(owner)

        val expired = inviteCode(owner)
        TestDatabase.execute("update invites set expires_at = now() - interval '1 minute'")
        val gone = client.get("/invites/$expired") { bearerAuth(ana.accessToken) }
        assertEquals(HttpStatusCode.NotFound, gone.status)
        assertEquals("That invite has expired or was revoked.", gone.body<ErrorResponse>().error)
        assertEquals(HttpStatusCode.NotFound, accept(ana, expired).status)

        val revoked = inviteCode(owner)
        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/town/invites/$revoked") { bearerAuth(owner.accessToken) }.status,
        )
        assertEquals(HttpStatusCode.NotFound, client.get("/invites/$revoked") { bearerAuth(ana.accessToken) }.status)

        val live = inviteCode(owner)
        val preview = client.get("/invites/$live") { bearerAuth(ana.accessToken) }
        assertEquals(HttpStatusCode.OK, preview.status)
        assertEquals("Ada's town", preview.body<ai.freeapp.bricktown.wire.InvitePreview>().townName)
        assertEquals(HttpStatusCode.OK, accept(ana, live).status)
        assertEquals(HttpStatusCode.NotFound, client.get("/invites/$live") { bearerAuth(ana.accessToken) }.status)
    }

    @Test
    fun `leave removes the leaver's bricks only`() = serverTest {
        val pair = twoPeople(canEdit = true)
        putBrick(pair.owner, UUID.randomUUID(), pair.town.foodId)
        putBrick(pair.ana, UUID.randomUUID(), pair.town.foodId)
        putBrick(pair.ana, UUID.randomUUID(), pair.town.foodId, date = "2026-11-02")
        client.put("/town/gold/2026-11-03") { bearerAuth(pair.ana.accessToken) }

        val left = client.post("/town/leave") { bearerAuth(pair.ana.accessToken) }
        assertEquals(HttpStatusCode.NoContent, left.status)

        val after = snapshot(pair.owner)
        assertEquals(1, after.expenses.size)
        assertEquals(pair.owner.user.id, after.expenses.single().paidBy)
        assertEquals(1, after.members.size)
        assertTrue(after.goldMarks.isEmpty())
        assertEquals(HttpStatusCode.NotFound, client.get("/town") { bearerAuth(pair.ana.accessToken) }.status)
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/town/leave") { bearerAuth(pair.owner.accessToken) }.status,
        )
    }

    @Test
    fun `the owner's DELETE me removes the town and Ana's next read is 404`() = serverTest {
        val pair = twoPeople(canEdit = true)
        putBrick(pair.ana, UUID.randomUUID(), pair.town.foodId)
        val townId = pair.town.townId

        assertEquals(HttpStatusCode.NoContent, client.delete("/me") { bearerAuth(pair.owner.accessToken) }.status)

        assertEquals(0, TestDatabase.count("select count(*) from towns where id = ?", townId))
        assertEquals(0, TestDatabase.count("select count(*) from town_members where town_id = ?", townId))
        assertEquals(0, TestDatabase.count("select count(*) from categories where town_id = ?", townId))
        assertEquals(0, TestDatabase.count("select count(*) from expenses where town_id = ?", townId))
        assertEquals(HttpStatusCode.NotFound, client.get("/town") { bearerAuth(pair.ana.accessToken) }.status)
    }

    @Test
    fun `Ana's DELETE me removes her bricks and member row and leaves the town`() = serverTest {
        val pair = twoPeople(canEdit = true)
        putBrick(pair.owner, UUID.randomUUID(), pair.town.foodId)
        putBrick(pair.ana, UUID.randomUUID(), pair.town.foodId)

        assertEquals(HttpStatusCode.NoContent, client.delete("/me") { bearerAuth(pair.ana.accessToken) }.status)

        val anaId = UUID.fromString(pair.ana.user.id)
        assertEquals(0, TestDatabase.count("select count(*) from town_members where user_id = ?", anaId))
        assertEquals(0, TestDatabase.count("select count(*) from expenses where paid_by = ?", anaId))
        val after = snapshot(pair.owner)
        assertEquals(1, after.expenses.size)
        assertEquals(1, after.members.size)
    }

    @Test
    fun `switching town_share off answers 403 on invites`() = serverTest {
        val owner = register()
        createTown(owner)
        TestFlags.write("""{"town.share": {"on": false}}""")
        val refused = client.post("/town/invites") { bearerAuth(owner.accessToken); json("{}") }
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals("Sharing a town is switched off.", refused.body<ErrorResponse>().error)
    }

    @Test
    fun `settle stamps unsettled shared bricks up to the date and answers the snapshot`() = serverTest {
        val pair = twoPeople(canEdit = true)
        val sharedOld = UUID.randomUUID()
        val sharedNew = UUID.randomUUID()
        val solo = UUID.randomUUID()
        putBrick(pair.owner, sharedOld, pair.town.foodId, date = "2026-10-20", shared = true)
        putBrick(pair.owner, sharedNew, pair.town.foodId, date = "2026-11-02", shared = true)
        putBrick(pair.owner, solo, pair.town.foodId, date = "2026-10-20")

        val settled = client.post("/town/settle") { bearerAuth(pair.ana.accessToken); json("""{"upTo":"2026-10-31"}""") }
        assertEquals(HttpStatusCode.OK, settled.status)
        val byId = settled.body<ai.freeapp.bricktown.domain.TownSnapshot>().expenses.associateBy { it.id }
        assertTrue(byId.getValue(sharedOld.toString()).settledAt != null)
        assertNull(byId.getValue(sharedNew.toString()).settledAt)
        assertNull(byId.getValue(solo.toString()).settledAt)

        TestFlags.write("""{"town.settle": {"on": false}}""")
        val off = client.post("/town/settle") { bearerAuth(pair.ana.accessToken); json("""{"upTo":"2026-10-31"}""") }
        assertEquals(HttpStatusCode.Forbidden, off.status)
        assertEquals("Settling up is switched off.", off.body<ErrorResponse>().error)
    }

    @Test
    fun `deleting a tower moves or refuses its bricks and the last tower stays`() = serverTest {
        val owner = register()
        val town = createTown(owner)
        val rent = UUID.randomUUID()
        val put = client.put("/town/categories/$rent") {
            bearerAuth(owner.accessToken)
            json("""{"id":"$rent","name":"Rent","short":"Rent","colorIndex":1,"position":1}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)
        val dup = client.put("/town/categories/${UUID.randomUUID()}") {
            bearerAuth(owner.accessToken)
            json("""{"id":"x","name":"  food ","short":"F","colorIndex":2,"position":2}""")
        }
        assertEquals(HttpStatusCode.Conflict, dup.status)
        assertEquals("You already have a tower with that name.", dup.body<ErrorResponse>().error)

        val brick = UUID.randomUUID()
        putBrick(owner, brick, rent)
        val noTarget = client.delete("/town/categories/$rent") { bearerAuth(owner.accessToken) }
        assertEquals(HttpStatusCode.Conflict, noTarget.status)
        assertEquals("Choose where its bricks go.", noTarget.body<ErrorResponse>().error)

        val merged = client.delete("/town/categories/$rent?mergeInto=${town.foodId}") { bearerAuth(owner.accessToken) }
        assertEquals(HttpStatusCode.NoContent, merged.status)
        assertEquals(town.foodId.toString(), snapshot(owner).expenses.single().categoryId)

        val last = client.delete("/town/categories/${town.foodId}") { bearerAuth(owner.accessToken) }
        assertEquals(HttpStatusCode.Conflict, last.status)
        assertEquals("A town needs at least one tower.", last.body<ErrorResponse>().error)

        assertEquals(
            HttpStatusCode.NoContent,
            client.delete("/town/categories/${UUID.randomUUID()}") { bearerAuth(owner.accessToken) }.status,
        )
    }

    @Test
    fun `hard limits are refused with a sentence and a missing delete is 204`() = serverTest {
        val owner = register()
        val town = createTown(owner)
        assertEquals(HttpStatusCode.NoContent, deleteBrick(owner, UUID.randomUUID()).status)

        assertEquals(HttpStatusCode.BadRequest, putBrick(owner, UUID.randomUUID(), town.foodId, cents = 0).status)
        assertEquals(HttpStatusCode.BadRequest, putBrick(owner, UUID.randomUUID(), town.foodId, cents = 999_999_901).status)
        assertEquals(HttpStatusCode.BadRequest, putBrick(owner, UUID.randomUUID(), town.foodId, date = "1999-12-31").status)
        assertEquals(HttpStatusCode.BadRequest, putBrick(owner, UUID.randomUUID(), town.foodId, date = "2999-01-01").status)
        assertEquals(HttpStatusCode.OK, putBrick(owner, UUID.randomUUID(), town.foodId, cents = 999_999_900).status)

        val longNote = "n".repeat(81)
        val tooLong = client.put("/town/expenses/${UUID.randomUUID()}") {
            bearerAuth(owner.accessToken)
            json(
                """{"id":"x","categoryId":"${town.foodId}","amountCents":100,"date":"2026-11-01","note":"$longNote",""" +
                    """"paidBy":"x","createdAt":"2026-11-01T10:00:00Z"}""",
            )
        }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)

        repeat(11) {
            val id = UUID.randomUUID()
            val ok = client.put("/town/categories/$id") {
                bearerAuth(owner.accessToken)
                json("""{"id":"$id","name":"T$it","short":"T","colorIndex":${it % 8},"position":${it + 1}}""")
            }
            assertEquals(HttpStatusCode.OK, ok.status)
        }
        val thirteenth = UUID.randomUUID()
        val over = client.put("/town/categories/$thirteenth") {
            bearerAuth(owner.accessToken)
            json("""{"id":"$thirteenth","name":"Extra","short":"E","colorIndex":0,"position":12}""")
        }
        assertEquals(HttpStatusCode.Conflict, over.status)
    }

    @Test
    fun `the join page is public, escapes the town name and shows the code`() = serverTest {
        val owner = register(name = "<b>Ada</b>")
        createTown(owner)
        val code = inviteCode(owner)

        val page = client.get("/join/$code")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains("Join &lt;b&gt;Ada&lt;/b&gt;&#39;s town in Brick Town"), html)
        assertTrue(html.contains("bricktown://join/$code"))
        assertTrue(html.contains("Open in Brick Town"))
        assertFalse(html.contains("<b>Ada"))

        assertEquals(HttpStatusCode.NotFound, client.get("/join/ZZZZ-ZZZZ").status)
    }

    @Test
    fun `member colours stay unique and permissions belong to the owner`() = serverTest {
        val pair = twoPeople()
        val ownerId = pair.owner.user.id
        val taken = client.put("/town/members/${pair.ana.user.id}") {
            bearerAuth(pair.ana.accessToken)
            json("""{"colorIndex":0}""")
        }
        assertEquals(HttpStatusCode.Conflict, taken.status)

        val grab = client.put("/town/members/${pair.ana.user.id}") {
            bearerAuth(pair.ana.accessToken)
            json("""{"canManage":true}""")
        }
        assertEquals(HttpStatusCode.Forbidden, grab.status)

        val meddle = client.put("/town/members/$ownerId") {
            bearerAuth(pair.ana.accessToken)
            json("""{"initial":"Z"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, meddle.status)

        val grant = client.put("/town/members/${pair.ana.user.id}") {
            bearerAuth(pair.owner.accessToken)
            json("""{"canEdit":true,"canManage":true}""")
        }
        assertEquals(HttpStatusCode.OK, grant.status)
        val mine = client.put("/town/members/${pair.ana.user.id}") {
            bearerAuth(pair.ana.accessToken)
            json("""{"colorIndex":5,"initial":"q"}""")
        }
        assertEquals(HttpStatusCode.OK, mine.status)
        assertEquals("Q", mine.body<ai.freeapp.bricktown.domain.Member>().initial)
    }
}
