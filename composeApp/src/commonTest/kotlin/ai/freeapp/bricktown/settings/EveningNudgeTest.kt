package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.domain.GoldMark
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EveningNudgeTest {

    @Test
    fun anUnloggedDayGetsTheNudgeWithTheStreakInIt() {
        val bricks = (10..23).map { brick("b$it", "food", 500, LocalDate(2026, 11, it)) }
        val nudge = assertNotNull(eveningNudgeFor(townSnapshot(expenses = bricks), fixtureToday))

        assertEquals("No bricks today yet", nudge.title)
        assertEquals(14, nudge.streak)
        assertEquals(
            "Spent nothing? Tap “€0 day” and your 14-day streak is safe. Otherwise, one brick keeps it alive.",
            nudge.body,
        )
    }

    @Test
    fun aDayWithABrickIsNotNudged() {
        assertNull(eveningNudgeFor(townSnapshot(expenses = listOf(brick("b1", "food", 500, fixtureToday))), fixtureToday))
    }

    @Test
    fun aDayWithAGoldMarkIsNotNudged() {
        assertNull(eveningNudgeFor(townSnapshot(goldMarks = listOf(GoldMark(fixtureToday, "you"))), fixtureToday))
    }

    @Test
    fun aBillOnTheDayCountsAsLoggedLikeTheStreakDoes() {
        val bill = brick("rent", "home", 50000, fixtureToday).copy(recurring = true)
        assertNull(eveningNudgeFor(townSnapshot(expenses = listOf(bill)), fixtureToday))
    }

    @Test
    fun withNoStreakTheBodyOffersAFirstDay() {
        val nudge = assertNotNull(eveningNudgeFor(townSnapshot(), fixtureToday))
        assertEquals(0, nudge.streak)
        assertEquals(
            "Spent nothing? Tap “€0 day” and your first streak day is safe. Otherwise, one brick keeps it alive.",
            nudge.body,
        )
    }

    @Test
    fun noTownMeansNoNudge() {
        assertNull(eveningNudgeFor(null, fixtureToday))
    }

    @Test
    fun aFutureDayStatesNoStreakNumber() {
        val body = futureEveningNudge().body
        assertEquals(
            "Spent nothing? Tap “€0 day” and your streak is safe. Otherwise, one brick keeps it alive.",
            body,
        )
    }

    @Test
    fun theZeroDayButtonMarksTodayGoldOnlyWhileTodayIsOpen() = kotlinx.coroutines.test.runTest {
        val gateway = FakeTownGateway()
        assertEquals(true, markZeroDay(gateway, fixtureClock()))
        assertEquals(listOf("markGoldDay:$fixtureToday"), gateway.calls)

        val logged = FakeTownGateway(townSnapshot(expenses = listOf(brick("b1", "food", 500, fixtureToday))))
        assertEquals(true, markZeroDay(logged, fixtureClock()))
        assertEquals(emptyList(), logged.calls)
        assertEquals(false, markZeroDay(FakeTownGateway(null), fixtureClock()))
    }
}
