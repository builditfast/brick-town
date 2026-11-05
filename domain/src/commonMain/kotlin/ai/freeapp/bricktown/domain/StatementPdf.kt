package ai.freeapp.bricktown.domain

import kotlin.math.abs
import kotlin.math.max

/**
 * One word of a PDF page with its place: x0 and x1 are the left and right edge, y the
 * baseline distance from the top of the page, height the glyph height. The server reads
 * these out of the file; everything after that is plain logic in this file.
 */
data class PdfWord(val page: Int, val x0: Float, val x1: Float, val y: Float, val height: Float, val text: String)

/** Fewer words than this on the whole file means there is no text layer: a scanned page. */
private const val MIN_WORDS = 20

/**
 * Rebuilds the payments table of a bank statement from positioned words and hands it to
 * [parseCsvStatement] as tab separated text.
 *
 * Lines are words with the same y. A cell ends at a gap wider than 1.3 average character
 * widths. The first line whose first cell is a date header and that has an amount header
 * gives the columns. Figures are right aligned under left aligned headers, so a figure
 * belongs to the last column whose header starts left of the figure's right edge; text
 * belongs to the column whose header midpoints enclose its left edge.
 * A dated line with text in a money column is a summary line ("Sold initial ...") and is
 * skipped. The table ends at any other header line (a daily balance summary), the same
 * header repeated on the next page is skipped, and a text-only line right under a row
 * continues that row.
 */
fun statementFromWords(words: List<PdfWord>): StatementParse {
    if (words.size < MIN_WORDS) return StatementParse.Failed(ImportFailure.SCANNED_PDF)
    val lines = groupLines(words)
    val tsv = tableAsTsv(lines) ?: return StatementParse.Failed(ImportFailure.NO_TABLE)
    if (tsv.rowCount == 0) return StatementParse.Failed(ImportFailure.NO_SPENDING)
    return parseCsvStatement(tsv.text)
}

private class Cell(val x0: Float, val x1: Float, val text: String)

private class Line(val page: Int, val y: Float, val height: Float, val cells: List<Cell>) {
    val text: String get() = cells.joinToString(" ") { it.text }
}

private class Tsv(val text: String, val rowCount: Int)

private val DATE_HEADER = Regex(
    "^(date|data|datum|fecha|booking|data inregistrare|data tranzactiei|transaction date)",
    RegexOption.IGNORE_CASE,
)
private val AMOUNT_HEADER = Regex(
    "(debit|credit|amount|suma|sum[aă]|money out|paid out|withdrawal|plati|incasari|betrag)",
    RegexOption.IGNORE_CASE,
)
private val MONEY_HEADER = Regex(
    "(debit|credit|amount|suma|sum[aă]|money|paid|withdrawal|plati|incasari|betrag|sold|balance|incoming|outgoing)",
    RegexOption.IGNORE_CASE,
)
private val FIGURE = Regex(
    """^[+-]?[€$£]?\s?[+-]?\d{1,3}([.,\s]\d{3})*([.,]\d{2})?$|^[+-]?[€$£]?\d+([.,]\d{2})$""",
)

private fun isHeaderLine(line: Line, moneyPattern: Regex): Boolean =
    line.cells.size >= 2 && DATE_HEADER.containsMatchIn(line.cells[0].text) &&
        line.cells.any { moneyPattern.containsMatchIn(it.text) }

private fun groupLines(words: List<PdfWord>): List<Line> {
    val sorted = words.filter { it.text.isNotBlank() }
        .sortedWith(compareBy<PdfWord>({ it.page }, { it.y }, { it.x0 }))
    val groups = mutableListOf<MutableList<PdfWord>>()
    for (w in sorted) {
        val last = groups.lastOrNull()
        if (last != null && last[0].page == w.page && abs(last[0].y - w.y) <= max(2f, w.height * 0.4f)) last += w
        else groups += mutableListOf(w)
    }
    return groups.map { group ->
        group.sortBy { it.x0 }
        val charWidth = group.map { (it.x1 - it.x0) / max(1, it.text.length) }.average().toFloat()
        val cells = mutableListOf<Cell>()
        var x0 = group[0].x0
        var x1 = group[0].x1
        val text = StringBuilder(group[0].text)
        for (w in group.drop(1)) {
            if (w.x0 - x1 < charWidth * 1.3f) {
                text.append(' ').append(w.text)
                x1 = w.x1
            } else {
                cells += Cell(x0, x1, text.toString())
                x0 = w.x0; x1 = w.x1
                text.clear(); text.append(w.text)
            }
        }
        cells += Cell(x0, x1, text.toString())
        Line(group[0].page, group[0].y, group[0].height, cells)
    }
}

private fun tableAsTsv(lines: List<Line>): Tsv? {
    val headerAt = lines.indexOfFirst { isHeaderLine(it, AMOUNT_HEADER) }
    if (headerAt < 0) return null
    val header = lines[headerAt].cells
    val headerText = lines[headerAt].text
    val n = header.size
    val lower = FloatArray(n) { if (it == 0) -Float.MAX_VALUE else (header[it - 1].x1 + header[it].x0) / 2 }
    val upper = FloatArray(n) { if (it == n - 1) Float.MAX_VALUE else (header[it].x1 + header[it + 1].x0) / 2 }

    val rows = mutableListOf<Array<String>>()
    var previous: Line? = null
    for (line in lines.drop(headerAt + 1)) {
        if (line.text == headerText) { previous = null; continue }
        if (isHeaderLine(line, MONEY_HEADER)) break

        val row = Array(n) { "" }
        for (cell in line.cells) {
            var column = 0
            if (FIGURE.matches(cell.text)) {
                for (k in 0 until n) if (cell.x1 > header[k].x0 + 1) column = k
            } else {
                for (k in 0 until n) if (cell.x0 >= lower[k] && cell.x0 < upper[k]) column = k
            }
            row[column] = if (row[column].isEmpty()) cell.text else row[column] + " " + cell.text
        }

        val dated = dateLike(row[0])
        val labelInMoney = (0 until n).any { k ->
            row[k].isNotEmpty() && MONEY_HEADER.containsMatchIn(header[k].text) && !FIGURE.matches(row[k])
        }
        if (dated && labelInMoney) { previous = null; continue }
        if (dated) { rows += row; previous = line; continue }

        var onlyText = true
        var textColumn = -1
        for (k in 0 until n) if (row[k].isNotEmpty()) {
            if (k == 0 || FIGURE.matches(row[k])) onlyText = false
            textColumn = k
        }
        val above = previous
        if (above != null && onlyText && textColumn > 0 && line.page == above.page && line.y - above.y < line.height * 2.6f) {
            val last = rows.last()
            last[textColumn] = last[textColumn] + " " + row[textColumn]
            previous = line
        } else previous = null
    }

    val out = StringBuilder(header.joinToString("\t") { tsvField(it.text) }).append('\n')
    for (row in rows) out.append(row.joinToString("\t") { tsvField(it) }).append('\n')
    return Tsv(out.toString(), rows.size)
}

/** The CSV reader treats a double quote as quoting, so a quote inside a cell is doubled and the cell wrapped. */
private fun tsvField(text: String): String {
    val clean = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
    return if ('"' in clean) "\"" + clean.replace("\"", "\"\"") + "\"" else clean
}
