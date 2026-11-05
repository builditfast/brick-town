package ai.freeapp.bricktown.widget

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetUpdaterTest {
    @Test
    fun withTheFlagOnEveryWidgetIsOffered() {
        assertTrue(receiverEnabled(widgetsOn = true, placed = 0))
        assertTrue(receiverEnabled(widgetsOn = true, placed = 2))
    }

    @Test
    fun withTheFlagOffAPlacedWidgetStaysToSaySo() {
        // Disabling a receiver deletes its placed widgets, and turning the flag on again does not bring them back.
        assertTrue(receiverEnabled(widgetsOn = false, placed = 1))
    }

    @Test
    fun withTheFlagOffAWidgetNobodyPlacedLeavesThePicker() {
        assertFalse(receiverEnabled(widgetsOn = false, placed = 0))
    }
}
