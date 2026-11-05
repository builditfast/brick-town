package ai.freeapp.bricktown.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatementPdfTest {
    private val charWidth = 5f

    /** Words of a left aligned text starting at [x0]; a space is 4 wide, under the 1.3 character gap that splits cells. */
    private fun text(page: Int, y: Float, x0: Float, value: String): List<PdfWord> {
        var x = x0
        return value.split(' ').map { part ->
            val word = PdfWord(page, x, x + part.length * charWidth, y, 8f, part)
            x = word.x1 + 4f
            word
        }
    }

    /** A figure whose RIGHT edge is at [x1], as banks print them. */
    private fun figure(page: Int, y: Float, x1: Float, value: String) =
        PdfWord(page, x1 - value.length * charWidth, x1, y, 8f, value)

    private val preamble = text(1, 30f, 10f, "Account statement for November 2026 of the account holder, with the address of the customer")

    private fun header(page: Int, y: Float) =
        (if (page == 1 && y == 100f) preamble else emptyList()) + text(page, y, 10f, "Date") + text(page, y, 120f, "Details") + text(page, y, 300f, "Debit") +
            text(page, y, 360f, "Credit") + text(page, y, 430f, "Balance")

    private fun row(page: Int, y: Float, date: String, details: String, debit: String? = null, credit: String? = null, balance: String = "1.000,00"): List<PdfWord> =
        text(page, y, 10f, date) + text(page, y, 120f, details) +
            listOfNotNull(debit?.let { figure(page, y, 330f, it) }, credit?.let { figure(page, y, 395f, it) }) +
            figure(page, y, 470f, balance)

    private fun rows(words: List<PdfWord>): List<StatementRow> {
        val parsed = statementFromWords(words)
        assertTrue(parsed is StatementParse.Rows, "expected rows, got $parsed")
        return parsed.rows
    }

    private fun failure(words: List<PdfWord>): ImportFailure = (statementFromWords(words) as StatementParse.Failed).reason

    @Test fun rightAlignedFiguresUnderLeftAlignedHeaders() {
        val words = header(1, 100f) +
            row(1, 120f, "05.11.2026", "Lidl Cluj", debit = "123,45") +
            row(1, 140f, "06.11.2026", "Salary", credit = "3.000,00") +
            row(1, 160f, "07.11.2026", "Cafe Roma", debit = "12,50")
        assertEquals(
            listOf(StatementRow(d(2026, 11, 5), "Lidl Cluj", 12_345), StatementRow(d(2026, 11, 7), "Cafe Roma", 1_250)),
            rows(words),
        )
    }

    @Test fun monthNameDatesInEnglishAndRomanian() {
        val words = header(1, 100f) +
            row(1, 120f, "5 Oct 2026", "Lidl", debit = "10,00") +
            row(1, 140f, "05 October 2026", "Kaufland", debit = "20,00") +
            row(1, 160f, "5 noi 2026", "Mega Image", debit = "30,00") +
            row(1, 180f, "6 noiembrie 2026", "Profi", debit = "40,00")
        assertEquals(
            listOf(d(2026, 10, 5), d(2026, 10, 5), d(2026, 11, 5), d(2026, 11, 6)),
            rows(words).map { it.date },
        )
    }

    @Test fun datedSummaryLineIsSkipped() {
        val summary = text(1, 120f, 10f, "01.11.2026") + text(1, 120f, 270f, "Sold initial") + figure(1, 120f, 470f, "5.000,00")
        val words = header(1, 100f) + summary +
            row(1, 140f, "05.11.2026", "Lidl", debit = "10,00") +
            row(1, 160f, "06.11.2026", "Cafe", debit = "5,00")
        assertEquals(listOf(1_000L, 500L), rows(words).map { it.amountCents })
    }

    @Test fun aSecondTableAfterTheFirstIsNotRead() {
        val second = text(1, 200f, 10f, "Data") + text(1, 200f, 120f, "Sold initial") + text(1, 200f, 300f, "Rulaj debit") +
            text(1, 200f, 400f, "Rulaj credit")
        val words = header(1, 100f) +
            row(1, 120f, "05.11.2026", "Lidl", debit = "10,00") +
            row(1, 140f, "06.11.2026", "Cafe", debit = "5,00") +
            second +
            row(1, 220f, "05.11.2026", "Daily total", debit = "999,00")
        assertEquals(listOf(1_000L, 500L), rows(words).map { it.amountCents })
    }

    @Test fun aHeaderRepeatedOnPageTwoContinuesTheTable() {
        val words = header(1, 100f) +
            row(1, 120f, "05.11.2026", "Lidl", debit = "10,00") +
            row(1, 140f, "06.11.2026", "Cafe", debit = "5,00") +
            header(2, 100f) +
            row(2, 120f, "07.11.2026", "Kaufland", debit = "7,00")
        assertEquals(listOf("Lidl", "Cafe", "Kaufland"), rows(words).map { it.merchant })
    }

    @Test fun aSecondLineOfTheDescriptionJoinsTheRowAbove() {
        val words = header(1, 100f) +
            row(1, 120f, "05.11.2026", "Card payment", debit = "10,00") +
            text(1, 130f, 120f, "Lidl Cluj") +
            row(1, 160f, "06.11.2026", "Cafe", debit = "5,00")
        assertEquals(listOf("Card payment Lidl Cluj", "Cafe"), rows(words).map { it.merchant })
    }

    @Test fun aScannedPageHasNoTextLayer() {
        assertEquals(ImportFailure.SCANNED_PDF, failure(text(1, 100f, 10f, "Date Details Debit")))
        assertEquals(ImportFailure.SCANNED_PDF, failure(emptyList()))
    }

    @Test fun textWithoutAHeaderLineHasNoTable() {
        val words = (1..30).flatMap { text(1, 20f * it, 10f, "just some words in a letter") }
        assertEquals(ImportFailure.NO_TABLE, failure(words))
    }

    @Test fun aTableWithOnlyMoneyComingInHasNoSpending() {
        val words = header(1, 100f) +
            row(1, 120f, "05.11.2026", "Salary", credit = "3.000,00") +
            row(1, 140f, "06.11.2026", "Refund", credit = "10,00")
        assertEquals(ImportFailure.NO_SPENDING, failure(words))
    }

    @Test fun aQuoteInADescriptionDoesNotBreakTheRow() {
        val words = header(1, 100f) +
            row(1, 120f, "05.11.2026", "The \"Big\" Shop", debit = "10,00") +
            row(1, 140f, "06.11.2026", "Cafe", debit = "5,00")
        assertTrue(rows(words).first().merchant.contains("Big"))
        assertEquals(2, rows(words).size)
    }
}
