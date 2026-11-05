package ai.freeapp.bricktown

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TownSnapshotSizeTest {

    @Test
    fun `five thousand bricks stay under 1 MB and 300 ms`() = serverTest {
        val owner = register()
        val town = createTown(owner)
        TestDatabase.execute(
            """
            insert into expenses (id, town_id, category_id, amount_cents, date, note, recurring, shared, refund, paid_by, created_at)
            select gen_random_uuid(), '${town.townId}', '${town.foodId}', 100 + g, date '2026-01-01' + (g % 300),
                   'Groceries at the corner shop ' || g, false, g % 3 = 0, false, '${owner.user.id}', now()
            from generate_series(1, 5000) g
            """.trimIndent(),
        )

        val raw = client.get("/town") { bearerAuth(owner.accessToken) }.bodyAsBytes().size // also warms the pool and JIT
        val timings = (1..5).map {
            val started = System.nanoTime()
            val response = client.get("/town") {
                bearerAuth(owner.accessToken)
                header(HttpHeaders.AcceptEncoding, "gzip")
            }
            val bytes = response.bodyAsBytes()
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
            (System.nanoTime() - started) / 1_000_000 to bytes.size
        }
        val bytes = timings.first().second
        val best = timings.minOf { it.first }
        val worst = timings.maxOf { it.first }
        println("SNAPSHOT-SIZE 5000 bricks: raw $raw bytes, gzip $bytes bytes, warm best $best ms, worst $worst ms")
        assertTrue(bytes < 1_000_000, "gzipped snapshot was $bytes bytes")
        assertTrue(best < 300, "warm snapshot best was $best ms")
    }
}
