package ai.freeapp.bricktown.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTest {
    private val minus = "−"

    @Test fun wholeGroupsThousandsWithASpace() {
        assertEquals("€1 312", formatWhole(131_200, Currency.EUR))
        assertEquals("€0", formatWhole(0, Currency.EUR))
        assertEquals("€1 234 567", formatWhole(123_456_700, Currency.EUR))
        assertEquals("\$12", formatWhole(1_200, Currency.USD))
    }

    @Test fun wholeRoundsHalfUp() {
        assertEquals("€2", formatWhole(150, Currency.EUR))
        assertEquals("€1", formatWhole(149, Currency.EUR))
        assertEquals("€134", formatWhole(13_350, Currency.EUR))
    }

    @Test fun ronIsWrittenAfterTheNumber() {
        assertEquals("312 lei", formatWhole(31_200, Currency.RON))
        assertEquals("1 312 lei", formatWhole(131_200, Currency.RON))
        assertEquals("70.10 lei", formatExact(7_010, Currency.RON))
        assertEquals("${minus}143 lei", formatSigned(-14_300, Currency.RON))
    }

    @Test fun exactAlwaysShowsTwoDecimals() {
        assertEquals("€70.10", formatExact(7_010, Currency.EUR))
        assertEquals("€14.00", formatExact(1_400, Currency.EUR))
        assertEquals("€0.05", formatExact(5, Currency.EUR))
        assertEquals("${minus}€89.00", formatExact(-8_900, Currency.EUR))
    }

    @Test fun signedUsesARealMinusAndNoSignForZero() {
        assertEquals("${minus}€143", formatSigned(-14_300, Currency.EUR))
        assertEquals("+€24", formatSigned(2_400, Currency.EUR))
        assertEquals("€0", formatSigned(0, Currency.EUR))
        assertEquals("€0", formatSigned(20, Currency.EUR))
    }

    @Test fun numpadRules() {
        var draft = ""
        for (k in "12.5") draft = numpadPress(draft, k)
        assertEquals("12.5", draft)
        assertEquals("12.57", numpadPress(draft, '7'))
        assertEquals("12.57", numpadPress("12.57", '1'))
        assertEquals("12.5", numpadPress("12.5", '.'))
        assertEquals("12.", numpadPress("12.5", NUMPAD_DELETE))
        assertEquals("", numpadPress("", NUMPAD_DELETE))
    }

    @Test fun numpadLeadingZeroAndLength() {
        assertEquals("5", numpadPress("0", '5'))
        assertEquals("0", numpadPress("0", '0'))
        assertEquals("0.", numpadPress("0", '.'))
        assertEquals("0.", numpadPress("", '.'))
        assertEquals("0.5", numpadPress("0.", '5'))
        assertEquals("1234567", numpadPress("123456", '7'))
        assertEquals("1234567", numpadPress("1234567", '8'))
        assertEquals("1234567", numpadPress("1234567", '.'))
        assertEquals("12345.6", numpadPress("12345.", '6'))
    }

    @Test fun draftToCents() {
        assertEquals(1_250L, draftToCents("12.5"))
        assertEquals(1_205L, draftToCents("12.05"))
        assertEquals(1_200L, draftToCents("12."))
        assertEquals(50L, draftToCents("0.5"))
        assertEquals(0L, draftToCents(""))
        assertEquals(0L, draftToCents("."))
    }
}
