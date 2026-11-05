package ai.freeapp.bricktown.widget

import ai.freeapp.bricktown.month.NOV_24
import ai.freeapp.bricktown.month.d
import ai.freeapp.bricktown.month.expense
import ai.freeapp.bricktown.month.logged
import ai.freeapp.bricktown.month.settings
import ai.freeapp.bricktown.month.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WidgetModelTest {
    private val town = snapshot(settings = settings(budget = 300_000L))

    @Test
    fun flagOffWinsOverEverything() {
        assertEquals(WidgetModel.Off, widgetModelOf(signedIn = true, snapshot = town, widgetsOn = false, today = NOV_24))
        assertEquals(WidgetModel.Off, widgetModelOf(signedIn = false, snapshot = null, widgetsOn = false, today = null))
    }

    @Test
    fun signedOutAndNoTown() {
        assertEquals(WidgetModel.SignedOut, widgetModelOf(false, town, true, NOV_24))
        assertEquals(WidgetModel.NoTown, widgetModelOf(true, null, true, null))
    }

    @Test
    fun readyShowsTodaysAllowanceThenLessAfterABrick() {
        val before = assertIs<WidgetModel.Ready>(widgetModelOf(true, town, true, NOV_24))
        // 3000 / 30 days = 100 a day
        assertEquals("€100", before.headline)
        assertEquals("left today of €100", before.subline)

        val withBrick = snapshot(
            expenses = listOf(expense("e1", "groceries", 4_300L, NOV_24)),
            settings = settings(budget = 300_000L),
        )
        val after = assertIs<WidgetModel.Ready>(widgetModelOf(true, withBrick, true, NOV_24))
        assertEquals("€57", after.headline)
        assertEquals("€43 · €2 957 left", after.monthLine)
        assertEquals(before.bars.size, after.bars.size)
    }

    @Test
    fun overTodaySaysSo() {
        val over = snapshot(
            expenses = listOf(expense("e1", "groceries", 15_000L, NOV_24)),
            settings = settings(budget = 300_000L),
        )
        val ready = assertIs<WidgetModel.Ready>(widgetModelOf(true, over, true, NOV_24))
        assertEquals("Over €50", ready.headline)
        assertEquals("today. Tomorrow resets.", ready.subline)
    }

    @Test
    fun streakLineAsksToLogTodayUntilLogged() {
        val yesterday = snapshot(expenses = logged(d(2026, 11, 22), d(2026, 11, 23)))
        val open = assertIs<WidgetModel.Ready>(widgetModelOf(true, yesterday, true, NOV_24))
        assertEquals(2, open.streak)
        assertEquals("day streak · log today", open.streakLine)

        val done = snapshot(expenses = logged(d(2026, 11, 23), NOV_24))
        val logged = assertIs<WidgetModel.Ready>(widgetModelOf(true, done, true, NOV_24))
        assertEquals(2, logged.streak)
        assertEquals("day streak", logged.streakLine)

        val none = assertIs<WidgetModel.Ready>(widgetModelOf(true, town, true, NOV_24))
        assertEquals(0, none.streak)
        assertEquals("log today to start a streak", none.streakLine)
    }

    @Test
    fun barsScaleToTheTallestTower() {
        val bricks = snapshot(
            expenses = listOf(
                expense("a", "groceries", 10_000L, NOV_24),
                expense("b", "transport", 2_500L, NOV_24),
            ),
        )
        val ready = assertIs<WidgetModel.Ready>(widgetModelOf(true, bricks, true, NOV_24))
        assertTrue(ready.bars.all { it.height in 0.08f..1f })
        assertEquals(1f, ready.bars.first().height)
        assertTrue(ready.bars.size <= 6)
    }
}
