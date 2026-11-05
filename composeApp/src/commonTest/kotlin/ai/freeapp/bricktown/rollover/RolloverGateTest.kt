package ai.freeapp.bricktown.rollover

import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.month.FakeLedger
import ai.freeapp.bricktown.month.d
import ai.freeapp.bricktown.month.expense
import ai.freeapp.bricktown.month.snapshot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FakeSeenStore : PayMonthSeenStore {
    val map = mutableMapOf<String, LocalDate>()
    var writes = 0
    override fun read(townId: String) = map[townId]
    override fun write(townId: String, monthStart: LocalDate) {
        writes++
        map[townId] = monthStart
    }
}

class RolloverGateTest {
    private var today = d(2026, 11, 10)
    private val store = FakeSeenStore()

    private fun gate(snap: TownSnapshot? = withOctBricks()) = MonthRolloverGate(FakeLedger(snap), TownClock { today }, store)

    private fun withOctBricks() = snapshot(listOf(expense("a", "groceries", 5_000, d(2026, 10, 5))))

    @Test
    fun freshInstallInsideFirstMonthShowsNothingAndRemembersIt() = runTest {
        val g = gate()
        assertNull(g.due())
        assertEquals(d(2026, 11, 1), store.map["t1"])
    }

    @Test
    fun showsOnceWhenTheMonthEnds() = runTest {
        val g = gate()
        today = d(2026, 10, 20)
        assertNull(g.due())
        today = d(2026, 11, 2)
        assertEquals(d(2026, 10, 1), g.due())
        assertNull(g.due())
        assertEquals(1, store.writes - 1)
    }

    @Test
    fun anotherTownStartsFresh() = runTest {
        store.map["other"] = d(2026, 10, 1)
        assertNull(gate().due())
    }

    @Test
    fun aClockThatWentBackDoesNotWrite() = runTest {
        store.map["t1"] = d(2026, 12, 1)
        assertNull(gate().due())
        assertEquals(d(2026, 12, 1), store.map["t1"])
        assertEquals(0, store.writes)
    }

    @Test
    fun anEmptyMonthHasNoTrophy() = runTest {
        store.map["t1"] = d(2026, 10, 1)
        assertNull(gate(snapshot()).due())
        assertEquals(d(2026, 11, 1), store.map["t1"])
    }

    @Test
    fun noTownMeansNothingDue() = runTest {
        assertNull(gate(null).due())
    }
}
