package ai.freeapp.bricktown.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatementsTest {
    private fun rows(text: String): List<StatementRow> =
        (parseCsvStatement(text) as StatementParse.Rows).rows

    private fun failure(text: String): ImportFailure =
        (parseCsvStatement(text) as StatementParse.Failed).reason

    @Test fun semicolonFileWithEuropeanNumbers() {
        val csv = "Date;Description;Debit;Credit\n24.11.2026;Lidl;1.234,56;\n23.11.2026;Salary;;3.000,00\n22.11.2026;Cafe;12,50;\n"
        assertEquals(
            listOf(
                StatementRow(d(2026, 11, 24), "Lidl", 123_456),
                StatementRow(d(2026, 11, 22), "Cafe", 1_250),
            ),
            rows(csv),
        )
    }

    @Test fun commaFileWithSignedAmounts() {
        val csv = "Date,Description,Amount\n2026-11-24,Lidl,-12.50\n2026-11-23,Salary,2500.00\n2026-11-22,Kaufland,\"-1,234.56\"\n"
        assertEquals(
            listOf(StatementRow(d(2026, 11, 24), "Lidl", 1_250), StatementRow(d(2026, 11, 22), "Kaufland", 123_456)),
            rows(csv),
        )
    }

    @Test fun bomAndQuotedDelimiter() {
        val csv = "﻿Date,Description,Amount\n2026-11-24,\"Shop, the big one\",-10.00\n"
        assertEquals(listOf(StatementRow(d(2026, 11, 24), "Shop, the big one", 1_000)), rows(csv))
    }

    @Test fun quotedNewlineStaysInOneRecord() {
        val csv = "Date,Description,Amount\n2026-11-24,\"Line one\nline two\",-10.00\n2026-11-23,Lidl,-5.00\n"
        assertEquals(2, rows(csv).size)
    }

    @Test fun ambiguousSlashDatesReadAsDayFirst() {
        assertEquals(d(2026, 4, 3), rows("Date,Description,Amount\n03/04/2026,Lidl,-5.00\n").single().date)
        assertEquals(d(2026, 4, 25), rows("Date,Description,Amount\n04/25/2026,Lidl,-5.00\n").single().date)
        assertEquals(d(2026, 4, 25), rows("Date,Description,Amount\n25/04/2026,Lidl,-5.00\n").single().date)
    }

    @Test fun pdfIsRefused() {
        assertEquals(ImportFailure.PDF_NOT_SUPPORTED, failure("%PDF-1.7\n1 0 obj"))
        assertTrue(looksLikePdf("%PDF-1.4".encodeToByteArray()))
        assertFalse(looksLikePdf("Date,Amount".encodeToByteArray()))
        assertFalse(looksLikePdf(ByteArray(0)))
    }

    @Test fun failureReasons() {
        assertEquals(ImportFailure.NOT_A_STATEMENT, failure("hello world"))
        assertEquals(ImportFailure.NOT_A_STATEMENT, failure(""))
        assertEquals(ImportFailure.NOT_A_STATEMENT, failure("\u0000\u0001\u0002\u0003,\u0000\u0001"))
        assertEquals(ImportFailure.NO_DATES_OR_AMOUNTS, failure("Name,Note\nfoo,bar\nbaz,qux\n"))
        assertEquals(ImportFailure.NO_SPENDING, failure("Date,Description,Amount\n2026-11-24,Salary,2500.00\n"))
    }

    private fun townWithLidl() = snapshot(listOf(expense("e1", "groceries", 1_250, d(2026, 11, 20), "Lidl")))

    @Test fun duplicatesWithinOneDayAreSkippedAtTwoDaysKept() {
        val candidates = reviewImport(
            listOf(
                StatementRow(d(2026, 11, 19), "lidl", 1_250),
                StatementRow(d(2026, 11, 20), "Lidl", 1_250),
                StatementRow(d(2026, 11, 21), "Lidl", 1_250),
                StatementRow(d(2026, 11, 22), "Lidl", 1_250),
                StatementRow(d(2026, 11, 20), "Lidl", 1_251),
            ),
            townWithLidl(),
        )
        assertEquals(listOf("e1", "e1", "e1", null, null), candidates.map { it.duplicateOf })
    }

    @Test fun categoryGuessesAndRecurringFlags() {
        val c = reviewImport(
            listOf(
                StatementRow(NOV_24, "KAUFLAND 123", 5_000),
                StatementRow(NOV_24, "Bolt Food order", 2_000),
                StatementRow(NOV_24, "Bolt ride", 800),
                StatementRow(NOV_24, "Molly's bar", 900),
                StatementRow(NOV_24, "Netflix.com", 1_599),
                StatementRow(NOV_24, "Zara Home", 4_000),
            ),
            snapshot(),
        )
        assertEquals("groceries", c[0].guessedCategoryId)
        assertEquals("eating", c[1].guessedCategoryId)
        assertEquals("transport", c[2].guessedCategoryId)
        assertNull(c[3].guessedCategoryId)
        assertEquals("subs", c[4].guessedCategoryId)
        assertTrue(c[4].looksRecurring)
        assertFalse(c[0].looksRecurring)
        assertEquals("shopping", c[5].guessedCategoryId)
    }

    @Test fun guessIsNullWhenTheTowerDoesNotExist() {
        val noGroceries = snapshot(categories = categories().filter { it.id != "groceries" })
        assertNull(reviewImport(listOf(StatementRow(NOV_24, "Lidl", 100)), noGroceries).single().guessedCategoryId)
    }

    @Test fun exportHasAHeaderAndOldestFirst() {
        val snap = snapshot(
            listOf(
                expense("b", "eating", 1_400, d(2026, 11, 23), "Wolt, again", shared = true, paidBy = ANA),
                expense("a", "subs", 2_500, d(2026, 11, 1), "Netflix", recurring = true),
                expense("c", "shopping", 8_900, d(2026, 11, 24), "Zara return", refund = true),
            ),
        )
        val lines = exportCsv(snap).trim().lines()
        assertEquals("date,tower,note,amount,monthly,shared,refund,paid by", lines[0])
        assertEquals(4, lines.size)
        assertTrue(lines[1].startsWith("2026-11-01,"))
        assertTrue(lines[1].contains("Netflix") && lines[1].contains("25.00"))
        assertTrue(lines[2].contains("\"Wolt, again\""))
        assertTrue(lines[3].startsWith("2026-11-24,"))
    }

    @Test fun exportRoundTripsThroughTheParser() {
        val text = exportCsv(fixture5b())
        assertTrue(text.startsWith("date,tower,note,amount"))
        assertEquals(44, text.trim().lines().size - 1)
    }

    @Test fun monthNameDatesRead() {
        val csv = "Date,Description,Amount\n5 Oct 2026,Lidl,-5.00\n06 October 2026,Cafe,-6.00\n5 noi 2026,Profi,-7.00\n7 noiembrie 2026,Mega,-8.00\n"
        assertEquals(listOf(d(2026, 10, 5), d(2026, 10, 6), d(2026, 11, 5), d(2026, 11, 7)), rows(csv).map { it.date })
    }

    @Test fun aSecondDateColumnIsNeverTheAmount() {
        val csv = "Booking;Value date;Description;Amount\n05.11.2026;06.11.2026;Lidl;-12,50\n07.11.2026;08.11.2026;Cafe;-3,00\n"
        assertEquals(
            listOf(StatementRow(d(2026, 11, 5), "Lidl", 1_250), StatementRow(d(2026, 11, 7), "Cafe", 300)),
            rows(csv),
        )
    }
}
