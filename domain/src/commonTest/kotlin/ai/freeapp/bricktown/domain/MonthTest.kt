package ai.freeapp.bricktown.domain

import ai.freeapp.bricktown.wire.agentSummaryOf
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonthTest {
    private val minus = "−"

    @Test fun bricksRoundAndClamp() {
        assertEquals(14, bricksFor(34_200))
        assertEquals(12, bricksFor(29_800))
        assertEquals(1, bricksFor(100))
        assertEquals(1, bricksFor(1_249))
        assertEquals(1, bricksFor(1_250))
        assertEquals(0, bricksFor(0))
        assertEquals(0, bricksFor(-5_000))
    }

    @Test fun brickHeightFitsTheChart() {
        assertEquals(13, brickHeight(0))
        assertEquals(13, brickHeight(13))
        assertEquals(12, brickHeight(14))
        assertEquals(6, brickHeight(20))
        assertEquals(4, brickHeight(60))
    }

    @Test fun calendarMonthWindow() {
        val m = payMonthOf(d(2026, 11, 24), 1)
        assertEquals(d(2026, 11, 1), m.start)
        assertEquals(d(2026, 11, 30), m.endInclusive)
        assertEquals(30, m.days)
        assertEquals(24, m.dayNumber(d(2026, 11, 24)))
        assertEquals(Month.NOVEMBER, m.name)
    }

    @Test fun payday28SpansTheMonthBoundary() {
        val m = payMonthOf(d(2026, 11, 30), 28)
        assertEquals(d(2026, 11, 28), m.start)
        assertEquals(d(2026, 12, 27), m.endInclusive)
        assertEquals(30, m.days)
        assertEquals(Month.DECEMBER, m.name)
        assertEquals(1, m.dayNumber(d(2026, 11, 28)))
        assertEquals(m, payMonthOf(d(2026, 12, 27), 28))
        assertEquals(d(2026, 12, 28), payMonthOf(d(2026, 12, 28), 28).start)
    }

    @Test fun payday28AcrossFebruaryAndALeapYear() {
        val jan = payMonthOf(d(2027, 1, 30), 28)
        assertEquals(d(2027, 1, 28), jan.start)
        assertEquals(d(2027, 2, 27), jan.endInclusive)
        val feb = jan.next(28)
        assertEquals(d(2027, 2, 28), feb.start)
        assertEquals(d(2027, 3, 27), feb.endInclusive)
        assertEquals(feb, feb.next(28).previous(28))
        val leap = payMonthOf(d(2028, 2, 29), 28)
        assertEquals(d(2028, 2, 28), leap.start)
        assertEquals(d(2028, 3, 27), leap.endInclusive)
        assertEquals(29, payMonthOf(d(2028, 2, 10), 1).days)
        assertEquals(28, payMonthOf(d(2027, 2, 10), 1).days)
    }

    @Test fun payday29And30FallBackInShortFebruary() {
        assertEquals(d(2027, 2, 28), payMonthOf(d(2027, 2, 28), 29).start)
        assertEquals(d(2028, 2, 29), payMonthOf(d(2028, 2, 29), 29).start)
        assertEquals(d(2027, 1, 29), payMonthOf(d(2027, 2, 27), 29).start)
    }

    @Test fun payday31InA30DayMonthStartsOnThe30th() {
        val nov = payMonthOf(d(2026, 11, 30), 31)
        assertEquals(d(2026, 11, 30), nov.start)
        assertEquals(d(2026, 12, 30), nov.endInclusive)
        val oct = payMonthOf(d(2026, 11, 29), 31)
        assertEquals(d(2026, 10, 31), oct.start)
        assertEquals(d(2026, 11, 29), oct.endInclusive)
        assertEquals(d(2026, 11, 30), oct.next(31).start)
        assertEquals(d(2026, 10, 31), nov.previous(31).start)
    }

    @Test fun yearBoundary() {
        val dec = payMonthOf(d(2026, 12, 31), 1)
        assertEquals(d(2027, 1, 1), dec.next(1).start)
        assertEquals(d(2026, 11, 1), dec.previous(1).start)
        val jan = payMonthOf(d(2027, 1, 3), 15)
        assertEquals(d(2026, 12, 15), jan.start)
        assertEquals(d(2027, 1, 14), jan.endInclusive)
    }

    @Test fun seriesKeyIgnoresCaseAndPadding() {
        assertEquals("subs|netflix", seriesKeyOf(expense("a", "subs", 1, NOV_24, "  Netflix ")))
    }

    @Test fun fiveB() {
        val s = summarize(fixture5b(), NOV_24)
        assertEquals(24, s.dayNumber)
        assertEquals(131_200, s.spentCents)
        assertEquals(30_300, s.billsCents)
        assertEquals(100_900, s.movingCents)
        assertEquals(0, s.pencilledCents)
        assertEquals(15_700, s.dailyBudgetCents)
        assertEquals(2_350, s.todaySpentCents)
        assertEquals(13_350, s.todayLeftCents)
        assertEquals(368_800, s.monthLeftCents)
        assertEquals(145_500, s.lastMonthSameDayCents)
        assertEquals(-14_300, s.aheadCents)
        assertEquals(PennyMood.SMILE, s.mood)
        assertEquals("12", s.partnerLatest?.id)

        assertEquals(listOf(34_200L, 29_800L, 27_500L, 21_400L, 9_600L, 8_700L), s.towers.map { it.totalCents })
        assertEquals(listOf(14, 12, 11, 9, 4, 3), s.towers.map { it.bricks })
        assertEquals(12, s.brickHeight)
        val eating = s.towers[1]
        assertEquals(10, eating.capBricks)
        assertEquals(4_800L, eating.overCapCents)
        assertEquals(8, s.towers[3].capBricks)
        assertEquals(1_400L, s.towers[3].overCapCents)
        assertNull(s.towers[0].capBricks)
        assertNull(s.towers[0].overCapCents)
        assertEquals(38_000L, s.towers[0].lastMonthSameDayCents)
    }

    @Test fun fiveBFormatsAsTheDesign() {
        val s = summarize(fixture5b(), NOV_24)
        assertEquals("€1 312", formatWhole(s.spentCents, Currency.EUR))
        assertEquals("€303", formatWhole(s.billsCents, Currency.EUR))
        assertEquals("€1 009", formatWhole(s.movingCents, Currency.EUR))
        assertEquals("${minus}€143", formatSigned(s.aheadCents, Currency.EUR))
        assertEquals("€3 688", formatWhole(s.monthLeftCents, Currency.EUR))
    }

    @Test fun sixC() {
        val s = summarize(fixture6c(), d(2026, 11, 28))
        assertEquals(Month.DECEMBER, s.month.name)
        assertEquals(1, s.dayNumber)
        assertEquals(0, s.spentCents)
        assertEquals(35_800, s.pencilledCents)
        assertEquals(6_000, s.dailyBudgetCents)
        assertEquals(6_000, s.todayLeftCents)
        assertEquals(144_200, s.monthLeftCents)
        assertEquals(0, s.movingCents)
        assertEquals(PennyMood.SMILE, s.mood)
        val ghosts = s.towers.flatMap { it.ghostBills }
        assertEquals(7, ghosts.size)
        assertTrue(ghosts.all { it.dueDate in s.month })
        val electricity = ghosts.single { it.note == "Electricity" }
        assertEquals(d(2026, 11, 30), electricity.dueDate)
        assertEquals("p1", electricity.sourceExpenseId)
    }

    @Test fun confirmedAndSkippedBillsAreNotGhosts() {
        val base = fixture6c()
        val confirmed = base.copy(expenses = base.expenses + expense("c1", "home", 19_000, d(2026, 11, 30), "electricity", recurring = true))
        val skipped = confirmed.copy(billSkips = listOf(BillSkip(d(2026, 11, 28), "subs|netflix")))
        val s = summarize(skipped, d(2026, 11, 30))
        assertEquals(35_800 - 19_000 - 2_500, s.pencilledCents)
        assertEquals(19_000, s.billsCents)
        // Confirmed bills lower the daily budget, pencilled ones do not.
        assertEquals(roundDivForTest(180_000 - 19_000, 30), s.dailyBudgetCents)
        // A skip for another month does not apply.
        val other = base.copy(billSkips = listOf(BillSkip(d(2026, 10, 28), "subs|netflix")))
        assertEquals(35_800, summarize(other, d(2026, 11, 28)).pencilledCents)
    }

    private fun roundDivForTest(cents: Long, days: Int): Long = ((cents / 100.0 / days) + 0.5).toLong() * 100

    @Test fun ghostDueDateIsClampedIntoTheMonth() {
        val snap = snapshot(
            settings = settings(payday = 1),
            expenses = listOf(expense("x", "subs", 1_000, d(2026, 10, 31), "Rent", recurring = true)),
        )
        val ghost = summarize(snap, d(2026, 11, 10)).towers.flatMap { it.ghostBills }.single()
        assertEquals(d(2026, 11, 30), ghost.dueDate)
    }

    @Test fun billsFirstOffDividesTheWholeBudget() {
        val s = summarize(fixture5b().let { it.copy(town = it.town.copy(settings = it.town.settings.copy(billsFirst = false))) }, NOV_24)
        assertEquals(16_700, s.dailyBudgetCents)
    }

    @Test fun refundsCanTakeATowerToZeroBricks() {
        val snap = snapshot(
            expenses = listOf(
                expense("a", "shopping", 8_900, d(2026, 11, 10), "Zara"),
                expense("b", "shopping", 8_900, d(2026, 11, 12), "Zara return", refund = true),
                expense("c", "shopping", 2_000, d(2026, 11, 13), "Return", refund = true),
            ),
        )
        val s = summarize(snap, NOV_24)
        val shopping = s.towers.single { it.category.id == "shopping" }
        assertEquals(-2_000L, shopping.totalCents)
        assertEquals(0, shopping.bricks)
        assertEquals(3, shopping.expenseCount)
        assertEquals(-2_000L, s.spentCents)
        assertNull(shopping.overCapCents)
    }

    @Test fun pennyMoods() {
        assertEquals(PennyMood.NEUTRAL, summarize(snapshot(), NOV_24).mood)
        val over = snapshot(listOf(expense("a", "groceries", 40_000, NOV_24, "Big shop")))
        assertEquals(PennyMood.FROWN, summarize(over, NOV_24).mood)
        val exact = snapshot(listOf(expense("a", "groceries", 16_700, NOV_24, "Exactly")), settings = settings(billsFirst = false))
        assertEquals(PennyMood.SMILE, summarize(exact, NOV_24).mood)
        assertNull(summarize(snapshot(listOf(expense("a", "groceries", 100, NOV_24))), NOV_24).partnerLatest)
    }

    @Test fun billsDoNotCountAgainstTodaysAllowance() {
        val snap = snapshot(listOf(expense("a", "home", 18_000, NOV_24, "Electricity", recurring = true)))
        val s = summarize(snap, NOV_24)
        assertEquals(0, s.todaySpentCents)
        assertEquals(s.dailyBudgetCents, s.todayLeftCents)
    }

    @Test fun agentSentenceUnderBudget() {
        val a = agentSummaryOf(fixture5b(), NOV_24)
        assertEquals("You have 134 euros left today and 3688 for the month.", a.sentence)
        assertEquals("November", a.monthName)
        assertEquals(24, a.dayNumber)
        assertEquals("EUR", a.currency)
        assertEquals(13_350, a.todayLeftCents)
        assertEquals(6, a.towers.size)
        assertEquals(25_000L, a.towers[1].capCents)
        assertEquals(7, a.streak)
    }

    @Test fun agentSentenceOverToday() {
        val fixture = fixture5b()
        val snap = fixture.copy(expenses = fixture.expenses + expense("big", "groceries", 14_500, NOV_24, "Big shop"))
        assertEquals("You are 12 euros over today, with 3543 euros left for the month.", agentSummaryOf(snap, NOV_24).sentence)
    }

    @Test fun agentSentenceOverTheMonth() {
        val fixture = fixture5b()
        val snap = fixture.copy(town = fixture.town.copy(settings = fixture.town.settings.copy(monthlyBudgetCents = 100_000)))
        assertEquals("You are 1 euro over today, and 312 euros over for the month.", agentSummaryOf(snap, NOV_24).sentence)
    }

    @Test fun agentSentenceSingularAndOtherCurrencies() {
        val s = snapshot(emptyList(), settings = settings(budget = 3_100, billsFirst = false, currency = Currency.RON))
        val a = agentSummaryOf(s, NOV_24)
        assertEquals("You have 1 leu left today and 31 for the month.", a.sentence)
        assertNotNull(a.date)
    }
}
