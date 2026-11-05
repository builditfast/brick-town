package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.domain.lastCompletedWeekStart
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestToolsTest {
    private val today = LocalDate(2026, 11, 24)

    @Test
    fun spreadsFiveBricksOverEachOfTheFiveCompletedWeeks() {
        val bricks = sampleBricks(today, towerCount = 3)
        assertEquals(25, bricks.size)
        val newest = lastCompletedWeekStart(today)
        assertEquals(DayOfWeek.MONDAY, newest.dayOfWeek)
        // Every brick is in the past and no older than five weeks before the newest completed week's end.
        assertTrue(bricks.all { it.date < today })
        assertEquals(newest, bricks.maxOf { it.date }.minusDaysToMonday())
        val weeks = bricks.groupBy { b -> b.date.minusDaysToMonday() }
        assertEquals(5, weeks.size)
        assertTrue(weeks.values.all { it.size == 5 })
    }

    @Test
    fun usesEveryTowerWithinRangeAndAmountsArePositive() {
        val bricks = sampleBricks(today, towerCount = 2)
        assertTrue(bricks.all { it.towerIndex in 0..1 && it.cents > 0 })
        assertEquals(setOf(0, 1), bricks.map { it.towerIndex }.toSet())
    }

    @Test
    fun noTowersMeansNoBricks() = assertTrue(sampleBricks(today, 0).isEmpty())

    @Test
    fun theSameDayGivesTheSameBricksAndUniqueUuids() {
        val first = sampleBricks(today, 3)
        assertEquals(first, sampleBricks(today, 3))
        val ids = first.map { sampleBrickId(TOWN_A, it) }
        assertEquals(ids, sampleBricks(today, 3).map { sampleBrickId(TOWN_A, it) })
        assertEquals(ids.size, ids.toSet().size)
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        assertTrue(ids.all { uuid.matches(it) })
    }

    @Test
    fun twoTownsOnTheSameDayGetDifferentIds() {
        // A brick id is unique across the whole server, so another town's sample bricks must not reuse it.
        val bricks = sampleBricks(today, 3)
        val a = bricks.map { sampleBrickId(TOWN_A, it) }.toSet()
        val b = bricks.map { sampleBrickId(TOWN_B, it) }.toSet()
        assertTrue(a.intersect(b).isEmpty())
    }

    @Test
    fun weeksDifferSoCompareHasSomethingToShow() {
        val perWeek = sampleBricks(today, 3).groupBy { it.date.minusDaysToMonday() }.values.map { w -> w.sumOf { it.cents } }
        assertTrue(perWeek.toSet().size > 1)
    }

    @Test
    fun lastMonthIsThePayMonthBeforeThisOne() {
        // Payday 28: 24 Nov is in the month that began 28 Oct, so last month began 28 Sep.
        assertEquals(LocalDate(2026, 9, 28), previousPayMonthStart(today, 28))
        // Payday 1: 24 Nov is in November, last month began 1 Oct.
        assertEquals(LocalDate(2026, 10, 1), previousPayMonthStart(today, 1))
    }
}

private const val TOWN_A = "3f1c2a9e-7b44-4d0e-9a51-2c8e6f0b1d73"
private const val TOWN_B = "c0a8e5d2-1f36-4b7a-8e09-5d4c3b2a1f60"

private fun LocalDate.minusDaysToMonday(): LocalDate = LocalDate.fromEpochDays(toEpochDays() - (dayOfWeek.ordinal))
