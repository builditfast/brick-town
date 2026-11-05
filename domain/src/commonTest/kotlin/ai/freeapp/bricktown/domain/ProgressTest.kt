package ai.freeapp.bricktown.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressTest {
    private fun run(from: LocalDate, days: Int): List<LocalDate> = (0 until days).map { from.plusDaysForTest(it) }

    private fun LocalDate.plusDaysForTest(n: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() + n)

    private fun badge(snap: TownSnapshot, id: BadgeId, today: LocalDate = NOV_24) =
        badgesOf(snap, today).single { it.id == id }

    @Test fun streakCountsConsecutiveDaysEndingToday() {
        val s = streakOf(snapshot(logged(*run(d(2026, 11, 18), 7).toTypedArray())), NOV_24)
        assertEquals(7, s.current)
        assertTrue(s.loggedToday)
        assertTrue(s.frozenDays.isEmpty())
    }

    @Test fun anOpenTodayDoesNotBreakTheStreak() {
        val s = streakOf(snapshot(logged(*run(d(2026, 11, 18), 6).toTypedArray())), NOV_24)
        assertEquals(6, s.current)
        assertEquals(false, s.loggedToday)
    }

    @Test fun aSingleMissedDayIsBridgedByTheMonthlyFreeze() {
        val days = run(d(2026, 11, 10), 5) + run(d(2026, 11, 16), 9)
        val s = streakOf(snapshot(logged(*days.toTypedArray())), NOV_24)
        assertEquals(14, s.current)
        assertEquals(setOf(d(2026, 11, 15)), s.frozenDays)
        assertEquals(0, s.freezesLeft)
    }

    @Test fun twoMissedDaysBreakTheStreak() {
        val days = run(d(2026, 11, 10), 5) + run(d(2026, 11, 17), 8)
        val s = streakOf(snapshot(logged(*days.toTypedArray())), NOV_24)
        assertEquals(8, s.current)
        assertTrue(s.frozenDays.isEmpty())
    }

    @Test fun theSecondGapInOnePayMonthIsNotFrozen() {
        val days = run(d(2026, 11, 1), 5) + run(d(2026, 11, 7), 5) + run(d(2026, 11, 13), 12)
        val s = streakOf(snapshot(logged(*days.toTypedArray())), NOV_24)
        assertEquals(12, s.current)
        assertEquals(setOf(d(2026, 11, 6)), s.frozenDays)
    }

    @Test fun aGoldMarkIsALoggedDay() {
        val snap = snapshot(logged(d(2026, 11, 22), d(2026, 11, 24)), goldMarks = gold(d(2026, 11, 23)))
        assertEquals(3, streakOf(snap, NOV_24).current)
        assertEquals(setOf(d(2026, 11, 23)), goldDays(snap))
    }

    @Test fun aGoldMarkWithASpendingBrickIsNotGold() {
        val snap = snapshot(logged(d(2026, 11, 23)), goldMarks = gold(d(2026, 11, 23)))
        assertTrue(goldDays(snap).isEmpty())
    }

    @Test fun badgesComeInTrailOrder() {
        assertEquals(BadgeId.entries, badgesOf(snapshot(), NOV_24).map { it.id })
    }

    @Test fun firstWeekEarnedOnTheSeventhLoggedDay() {
        val earned = badge(snapshot(logged(*run(d(2026, 11, 10), 7).toTypedArray())), BadgeId.FIRST_WEEK)
        assertEquals(d(2026, 11, 16), earned.earnedOn)
        assertEquals("Earned 16 Nov", earned.subtitle)
        val notYet = badge(snapshot(logged(*run(d(2026, 11, 10), 6).toTypedArray())), BadgeId.FIRST_WEEK)
        assertNull(notYet.earnedOn)
        assertEquals("1 logged days away", notYet.progress)
    }

    @Test fun noSpendWeekendNeedsBothDays() {
        val both = snapshot(logged(d(2026, 11, 20)), goldMarks = gold(d(2026, 11, 21), d(2026, 11, 22)))
        assertEquals(d(2026, 11, 22), badge(both, BadgeId.NO_SPEND_WEEKEND).earnedOn)
        val one = snapshot(logged(d(2026, 11, 20)), goldMarks = gold(d(2026, 11, 21)))
        assertNull(badge(one, BadgeId.NO_SPEND_WEEKEND).earnedOn)
    }

    @Test fun underBudgetThreeDaysInARow() {
        val ok = snapshot(logged(*run(d(2026, 11, 20), 4).toTypedArray()))
        assertEquals(d(2026, 11, 22), badge(ok, BadgeId.UNDER_BUDGET_X3).earnedOn)
        val broken = snapshot(
            listOf(expense("big", "groceries", 40_000, d(2026, 11, 21), "Big")) +
                logged(d(2026, 11, 20), d(2026, 11, 22), d(2026, 11, 23)),
        )
        assertNull(badge(broken, BadgeId.UNDER_BUDGET_X3).earnedOn)
    }

    @Test fun streak12AndStreak30() {
        val twelve = snapshot(logged(*run(d(2026, 11, 13), 12).toTypedArray()))
        assertEquals(d(2026, 11, 24), badge(twelve, BadgeId.STREAK_12).earnedOn)
        assertNull(badge(twelve, BadgeId.STREAK_30).earnedOn)
        val eleven = snapshot(logged(*run(d(2026, 11, 14), 11).toTypedArray()))
        assertNull(badge(eleven, BadgeId.STREAK_12).earnedOn)
        val thirty = snapshot(logged(*run(d(2026, 10, 26), 30).toTypedArray()))
        assertNotNull(badge(thirty, BadgeId.STREAK_30).earnedOn)
    }

    @Test fun leakPluggedWhenACappedTowerComesBackUnderItsCap() {
        fun town(novEating: Long) = snapshot(
            listOf(
                expense("o", "eating", 27_000, d(2026, 10, 10), "Restaurant"),
                expense("n", "eating", novEating, d(2026, 11, 10), "Restaurant"),
            ),
        )
        assertEquals(d(2026, 11, 30), badge(town(20_000), BadgeId.LEAK_PLUGGED, d(2026, 12, 5)).earnedOn)
        assertNull(badge(town(26_000), BadgeId.LEAK_PLUGGED, d(2026, 12, 5)).earnedOn)
    }

    @Test fun monthUnderBudgetNeedsAFinishedMonth() {
        val under = snapshot(listOf(expense("o", "groceries", 100_000, d(2026, 10, 10), "Lidl")))
        assertEquals(d(2026, 10, 31), badge(under, BadgeId.MONTH_UNDER_BUDGET).earnedOn)
        val over = snapshot(listOf(expense("o", "groceries", 600_000, d(2026, 10, 10), "Lidl")))
        assertNull(badge(over, BadgeId.MONTH_UNDER_BUDGET).earnedOn)
        val running = snapshot(listOf(expense("o", "groceries", 100_000, d(2026, 11, 10), "Lidl")))
        assertNull(badge(running, BadgeId.MONTH_UNDER_BUDGET).earnedOn)
    }

    @Test fun zeroDeliveryWeekAfterTheFirstDelivery() {
        val clean = snapshot(listOf(expense("w", "eating", 2_000, d(2026, 11, 3), "Wolt")))
        assertEquals(d(2026, 11, 15), badge(clean, BadgeId.ZERO_DELIVERY_WEEK).earnedOn)
        val weekly = snapshot(
            listOf(d(2026, 11, 3), d(2026, 11, 10), d(2026, 11, 17)).mapIndexed { i, day ->
                expense("w$i", "eating", 2_000, day, "Glovo")
            },
        )
        assertNull(badge(weekly, BadgeId.ZERO_DELIVERY_WEEK).earnedOn)
    }

    @Test fun onlyTheFirstUnearnedBadgeShowsProgress() {
        val badges = badgesOf(snapshot(), NOV_24)
        assertEquals(1, badges.count { it.progress != null })
        assertNotNull(badges.first().progress)
    }

    @Test fun lastCompletedWeekIsTheWeekBeforeThisMonday() {
        assertEquals(d(2026, 11, 16), lastCompletedWeekStart(NOV_24))
        assertEquals(d(2026, 11, 16), lastCompletedWeekStart(d(2026, 11, 23)))
        assertEquals(d(2026, 11, 16), lastCompletedWeekStart(d(2026, 11, 29)))
    }

    @Test fun weekRecapOnTheFiveBTown() {
        val r = weekRecap(fixture5b(), d(2026, 11, 16))
        assertEquals(47, r.weekNumber)
        assertEquals(d(2026, 11, 22), r.end)
        assertEquals(7, r.days.size)
        assertEquals(d(2026, 11, 16), r.days.first().date)
        assertEquals(35_100, r.spentCents)
        assertEquals(36_850, r.previousWeekCents)
        assertEquals(35_100, r.days.sumOf { it.movingCents })
        assertEquals("groceries", r.biggestTowerId)
        assertEquals(setOf("2", "9"), r.partnerBricks.map { it.id }.toSet())
        assertEquals(0, r.goldDays)
    }

    @Test fun theFirstWeekOfAPayMonthHasNothingToBeatSoItIsNotBest() {
        val snap = snapshot(listOf(expense("a", "groceries", 19_100, d(2026, 11, 4))), serverTime = instantOf(d(2026, 11, 9)))
        val r = weekRecap(snap, d(2026, 11, 2))
        assertEquals(false, r.bestWeekOfMonth)
        assertTrue(r.pennyLine.startsWith("A heavier week than the one before."))
    }

    @Test fun aWeekCheaperThanEveryEndedWeekOfItsMonthIsBest() {
        val bricks = listOf(expense("a", "groceries", 10_000, d(2026, 11, 4)), expense("b", "groceries", 5_000, d(2026, 11, 11)))
        val r = weekRecap(snapshot(bricks, serverTime = instantOf(d(2026, 11, 16))), d(2026, 11, 9))
        assertTrue(r.bestWeekOfMonth)
        assertTrue(r.pennyLine.startsWith("Your best week this month."))
    }

    @Test fun isoWeekNumberAcrossTheYearBoundary() {
        assertEquals(53, weekRecap(snapshot(), d(2026, 12, 28)).weekNumber)
        assertEquals(1, weekRecap(snapshot(), d(2027, 1, 4)).weekNumber)
        assertEquals(1, weekRecap(snapshot(), d(2024, 12, 30)).weekNumber)
    }

    @Test fun closedMonthsHoldOctober() {
        val oct = closedMonths(fixture5b(), NOV_24).single { it.month.start == d(2026, 10, 1) }
        assertEquals(145_500, oct.spentCents)
        assertEquals(30_300, oct.billsCents)
        assertTrue(oct.underBudget)
        assertTrue(closedMonths(fixture5b(), NOV_24).none { it.month.start == d(2026, 11, 1) })
        assertTrue(closedMonths(snapshot(), NOV_24).isEmpty())
    }

    @Test fun settleOnTheSixBRows() {
        val s = settleStateOf(fixture5b(), NOV_24)
        assertEquals(7_010, s.balanceCents)
        assertEquals("Ana", s.partner?.name)
        assertEquals(listOf("18", "1"), s.youPaid.map { it.id })
        assertEquals(setOf("2", "9", "12"), s.partnerPaid.map { it.id }.toSet())
        assertEquals("€70.10", formatExact(s.balanceCents, Currency.EUR))
    }

    @Test fun settledBricksDropOut() {
        val base = fixture5b()
        val settled = base.copy(expenses = base.expenses.map { if (it.shared) it.copy(settledAt = instantOf(NOV_24)) else it })
        val s = settleStateOf(settled, NOV_24)
        assertEquals(0, s.balanceCents)
        assertTrue(s.youPaid.isEmpty() && s.partnerPaid.isEmpty())
        assertEquals(instantOf(NOV_24), s.lastSettledAt)
    }

    @Test fun nobodyToSettleWithMeansZero() {
        val s = settleStateOf(snapshot(members = MEMBERS.take(1)), NOV_24)
        assertEquals(0, s.balanceCents)
        assertNull(s.partner)
    }

    @Test fun eachSixBRowShowsTheViewersHalfAndTheyAddUpToTheBalance() {
        val s = settleStateOf(fixture5b(), NOV_24)
        val shares = (s.youPaid + s.partnerPaid).associate { it.id to settleShareCents(it, YOU) }
        assertEquals(mapOf("18" to 2_500L, "1" to 9_000L, "2" to -2_410L, "12" to -700L, "9" to -1_380L), shares)
        assertEquals(s.balanceCents, shares.values.sum())
    }

    @Test fun aSharedRefundFlipsTheShareAndOddCentsRoundTowardZero() {
        val refund = expense("r", "shopping", 1_001, NOV_24, shared = true, refund = true)
        assertEquals(-500, settleShareCents(refund, YOU))
        assertEquals(500, settleShareCents(refund.copy(paidBy = ANA), YOU))
    }

    @Test fun theViewerOwesWhenThePartnerPaidMore() {
        val snap = snapshot(listOf(expense("a", "groceries", 10_000, NOV_24, "Shop", shared = true, paidBy = ANA)))
        assertEquals(-5_000, settleStateOf(snap, NOV_24).balanceCents)
    }
}
