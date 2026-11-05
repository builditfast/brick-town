package ai.freeapp.bricktown

import ai.freeapp.bricktown.wire.AgentSummary
import ai.freeapp.bricktown.wire.agentSummaryOf
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Alone on purpose: it calls `agentSummaryOf`, which is a TODO until the domain
 * work lands, so it fails with NotImplementedError until then and nothing else
 * in the server suite depends on it.
 */
class TownSummaryTest {

    @Test
    fun `summary equals agentSummaryOf on the same data and defaults to today in the town zone`() = serverTest {
        val owner = register()
        val town = createTown(owner)
        putBrick(owner, UUID.randomUUID(), town.foodId, cents = 1250, date = "2026-11-01")
        val snapshot = snapshot(owner)

        val expected = agentSummaryOf(snapshot, LocalDate.parse("2026-11-02"))
        val asked = client.get("/town/summary?date=2026-11-02") { bearerAuth(owner.accessToken) }
        assertEquals(HttpStatusCode.OK, asked.status)
        assertEquals(expected, asked.body<AgentSummary>())

        val today = Clock.System.todayIn(TimeZone.of("Europe/Bucharest"))
        val defaulted = client.get("/town/summary") { bearerAuth(owner.accessToken) }
        assertEquals(agentSummaryOf(snapshot, today), defaulted.body<AgentSummary>())
    }
}
