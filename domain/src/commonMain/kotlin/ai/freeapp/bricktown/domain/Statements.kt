package ai.freeapp.bricktown.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable

/**
 * Bank statements. A CSV is read on the device. A PDF is turned into words with
 * positions on the server ([statementFromWords], in StatementPdf.kt) and the
 * same CSV reader finishes the job.
 */

@Serializable
data class StatementRow(val date: LocalDate, val merchant: String, val amountCents: Long)

/** The answer of `POST /town/import/pdf`: rows, or the reason there are none. */
@Serializable
data class PdfImportResult(val rows: List<StatementRow> = emptyList(), val failure: ImportFailure? = null)

@Serializable
enum class ImportFailure {
    /** The file is a PDF and this build or server does not read PDFs. */
    PDF_NOT_SUPPORTED,
    /** A PDF with (almost) no text layer: a picture of a statement. */
    SCANNED_PDF,
    /** A PDF with text, but no header line with a date column and an amount column. */
    NO_TABLE,
    /** A password-protected PDF. */
    PDF_LOCKED,
    /** No delimiter, no header, or binary content. */
    NOT_A_STATEMENT,
    /** A table, but no column reads as dates or no column reads as amounts. */
    NO_DATES_OR_AMOUNTS,
    /** Readable, but it holds no money going out. */
    NO_SPENDING,
}

sealed interface StatementParse {
    data class Rows(val rows: List<StatementRow>) : StatementParse
    data class Failed(val reason: ImportFailure) : StatementParse
}

/**
 * Finds the delimiter (, ; tab), the header row, the date column (ISO,
 * dd.MM.yyyy, dd/MM/yyyy, MM/dd/yyyy when no day passes 12 is ambiguous and
 * read as dd/MM), the amount column (a debit column, or one signed column where
 * negative is spending; "1.234,56" and "1,234.56" both read) and the merchant
 * column. Keeps money going out only, as positive cents.
 */
fun parseCsvStatement(text: String): StatementParse {
    val clean = text.removePrefix("\uFEFF")
    if (clean.startsWith("%PDF")) return StatementParse.Failed(ImportFailure.PDF_NOT_SUPPORTED)
    if (clean.isBlank() || looksBinary(clean)) return StatementParse.Failed(ImportFailure.NOT_A_STATEMENT)
    val delimiter = detectDelimiter(clean) ?: return StatementParse.Failed(ImportFailure.NOT_A_STATEMENT)
    val records = splitRecords(clean, delimiter).filter { r -> r.any { it.isNotBlank() } }
    if (records.size < 2) return StatementParse.Failed(ImportFailure.NOT_A_STATEMENT)

    val hasHeader = records.first().none { dateLike(it) }
    val header = if (hasHeader) records.first().map { it.trim().lowercase() } else emptyList()
    val data = if (hasHeader) records.drop(1) else records
    val width = records.maxOf { it.size }
    fun column(i: Int): List<String> = data.mapNotNull { it.getOrNull(i)?.trim()?.takeIf { c -> c.isNotEmpty() } }
    fun headerOf(i: Int): String = header.getOrNull(i) ?: ""

    val dateColumn = (0 until width).firstOrNull { i ->
        val cells = column(i)
        cells.isNotEmpty() && cells.count { dateLike(it) } * 5 >= cells.size * 4
    }
    val dateColumns = (0 until width).filter { i -> column(i).let { cells -> cells.count { dateLike(it) } * 2 > cells.size } }
    val numeric = (0 until width).filter { i ->
        val cells = column(i)
        i !in dateColumns && cells.isNotEmpty() && cells.count { parseAmountCents(it) != null } * 5 >= cells.size * 4
    }
    if (dateColumn == null || numeric.isEmpty()) return StatementParse.Failed(ImportFailure.NO_DATES_OR_AMOUNTS)

    val debitColumn = numeric.firstOrNull { h -> DEBIT_HEADERS.any { it in headerOf(h) } }
    val amountColumn = debitColumn
        ?: numeric.firstOrNull { h -> AMOUNT_HEADERS.any { it in headerOf(h) } }
        ?: numeric.firstOrNull { i -> column(i).any { (parseAmountCents(it) ?: 0L) < 0 } }
        ?: numeric.first()
    val merchantColumn = (0 until width).filter { it !in dateColumns && it !in numeric }.let { candidates ->
        candidates.firstOrNull { h -> MERCHANT_HEADERS.any { it in headerOf(h) } }
            ?: candidates.maxByOrNull { i -> column(i).sumOf { it.length } }
    }

    val slashMonthFirst = slashesAreMonthFirst(column(dateColumn))
    val rows = data.mapNotNull { record ->
        val date = parseDate(record.getOrNull(dateColumn)?.trim().orEmpty(), slashMonthFirst) ?: return@mapNotNull null
        val value = parseAmountCents(record.getOrNull(amountColumn)?.trim().orEmpty()) ?: return@mapNotNull null
        val spending = when {
            value == 0L -> return@mapNotNull null
            debitColumn != null -> if (value < 0) -value else value
            value < 0 -> -value
            else -> return@mapNotNull null
        }
        val merchant = merchantColumn?.let { record.getOrNull(it) }.orEmpty().trim().replace(Regex("\\s+"), " ")
        StatementRow(date, merchant, spending)
    }
    return if (rows.isEmpty()) StatementParse.Failed(ImportFailure.NO_SPENDING) else StatementParse.Rows(rows)
}

private val DEBIT_HEADERS = listOf("debit", "withdraw", "paid out", "money out", "spent", "outgoing")
private val AMOUNT_HEADERS = listOf("amount", "suma", "sum", "value", "valoare", "betrag", "montant")
private val MERCHANT_HEADERS = listOf(
    "description", "merchant", "details", "payee", "name", "memo", "narrative", "reference",
    "beneficiar", "descriere", "detalii", "explica", "counterparty",
)

/** NUL bytes, replacement characters or a high share of control characters: a binary file read as text. */
private fun looksBinary(text: String): Boolean {
    val sample = text.take(4096)
    val bad = sample.count { it == '\u0000' || it == '\uFFFD' || (it < ' ' && it != '\n' && it != '\r' && it != '\t') }
    return bad > 0 && bad * 100 >= sample.length || '\u0000' in sample
}

/** The delimiter that splits the first lines into the same number of cells, at least two. */
private fun detectDelimiter(text: String): Char? {
    val lines = mutableListOf<String>()
    var open = ""
    for (physical in text.lineSequence()) {
        val joined = if (open.isEmpty()) physical else "$open $physical"
        if (joined.count { it == '"' } % 2 == 1) { open = joined; continue }
        open = ""
        if (joined.isNotBlank()) lines += joined
        if (lines.size == 5) break
    }
    if (lines.isEmpty()) return null
    var best: Char? = null
    var bestScore = 0
    for (candidate in listOf('\t', ';', ',')) {
        val counts = lines.map { countOutsideQuotes(it, candidate) }
        val score = counts.min()
        if (score > bestScore) { best = candidate; bestScore = score }
    }
    return best
}

private fun countOutsideQuotes(line: String, delimiter: Char): Int {
    var quoted = false
    var count = 0
    for (c in line) {
        if (c == '"') quoted = !quoted else if (c == delimiter && !quoted) count++
    }
    return count
}

/** RFC 4180 style: a quoted field may hold the delimiter, a newline and a doubled quote. */
private fun splitRecords(text: String, delimiter: Char): List<List<String>> {
    val records = mutableListOf<List<String>>()
    var fields = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    fun endField() { fields.add(field.toString()); field.clear() }
    fun endRecord() { endField(); records.add(fields); fields = mutableListOf() }
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
            c == '"' -> quoted = !quoted
            !quoted && c == delimiter -> endField()
            !quoted && c == '\n' -> endRecord()
            !quoted && c == '\r' -> if (text.getOrNull(i + 1) != '\n') endRecord()
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
    return records
}

private val ISO_DATE = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[T ].*)?$""")
private val DMY_DATE = Regex("""^(\d{1,2})([./-])(\d{1,2})\2(\d{4})(?:[T ,].*)?$""")

internal fun dateLike(cell: String): Boolean {
    val c = cell.trim()
    return ISO_DATE.matches(c) || DMY_DATE.matches(c) || MONTH_NAME_DATE.matches(c)
}

/** "5 Oct 2026", "05 October 2026", "5 noi 2026", "5 noiembrie 2026". */
private val MONTH_NAME_DATE = Regex("""^(\d{1,2})\s+(\p{L}{3,10})\.?,?\s+(\d{4})$""")

private val MONTH_NAMES: Map<String, Int> = buildMap {
    val english = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    val romanian = listOf("ianuarie", "februarie", "martie", "aprilie", "mai", "iunie", "iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie")
    for ((i, name) in english.withIndex()) { put(name, i + 1); put(name.take(3), i + 1) }
    for ((i, name) in romanian.withIndex()) { put(name, i + 1); put(name.take(3), i + 1) }
    put("sept", 9)
}

/** Only "/" can be month first, and only when some day in the column is above 12 in the second slot and none in the first. */
private fun slashesAreMonthFirst(cells: List<String>): Boolean {
    val slashed = cells.mapNotNull { DMY_DATE.matchEntire(it.trim()) }.filter { it.groupValues[2] == "/" }
    val firstAbove12 = slashed.any { it.groupValues[1].toInt() > 12 }
    val secondAbove12 = slashed.any { it.groupValues[3].toInt() > 12 }
    return secondAbove12 && !firstAbove12
}

private fun parseDate(cell: String, slashMonthFirst: Boolean): LocalDate? {
    ISO_DATE.matchEntire(cell)?.let { m ->
        return safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }
    MONTH_NAME_DATE.matchEntire(cell)?.let { m ->
        val month = MONTH_NAMES[m.groupValues[2].lowercase()] ?: return null
        return safeDate(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
    }
    val m = DMY_DATE.matchEntire(cell) ?: return null
    val a = m.groupValues[1].toInt()
    val b = m.groupValues[3].toInt()
    val year = m.groupValues[4].toInt()
    return if (m.groupValues[2] == "/" && slashMonthFirst) safeDate(year, a, b) else safeDate(year, b, a)
}

private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
    if (month in 1..12 && day in 1..lastDayOfMonth(year, month)) LocalDate(year, month, day) else null

/**
 * Signed cents from "1.234,56", "1,234.56", "-12.5", "(12.50)", "12.50-" or
 * "€12" style cells. A lone separator followed by exactly three digits after
 * one to three leading digits is a thousands separator ("1,234"); otherwise it
 * is the decimal point.
 */
private fun parseAmountCents(cell: String): Long? {
    var t = cell.trim()
    if (t.isEmpty()) return null
    var negative = false
    if (t.startsWith("(") && t.endsWith(")")) { negative = true; t = t.substring(1, t.length - 1) }
    t = t.replace("\u2212", "-")
    for (symbol in listOf("EUR", "RON", "USD", "GBP", "lei", "\u20ac", "$", "\u00a3")) t = t.replace(symbol, "", ignoreCase = true)
    t = t.filter { !it.isWhitespace() && it != '\u00a0' && it != '\u202f' }
    if (t.startsWith("-")) { negative = !negative; t = t.substring(1) } else if (t.startsWith("+")) t = t.substring(1)
    if (t.endsWith("-")) { negative = !negative; t = t.dropLast(1) }
    if (t.isEmpty() || t.any { !it.isDigit() && it != '.' && it != ',' }) return null
    if (!t.first().isDigit() || !t.last().isDigit()) return null

    val lastDot = t.lastIndexOf('.')
    val lastComma = t.lastIndexOf(',')
    val decimalAt = when {
        lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
        lastDot >= 0 -> lone(t, '.', lastDot)
        lastComma >= 0 -> lone(t, ',', lastComma)
        else -> -1
    }
    val whole: String
    val fraction: String
    if (decimalAt < 0) {
        whole = t.filter { it.isDigit() }; fraction = ""
    } else {
        whole = t.substring(0, decimalAt).filter { it.isDigit() }
        fraction = t.substring(decimalAt + 1)
        if (fraction.any { !it.isDigit() } || fraction.length > 2 || whole.isEmpty()) return null
    }
    val cents = (whole.toLongOrNull() ?: return null) * 100 + (fraction.padEnd(2, '0').toLongOrNull() ?: 0L)
    return if (negative) -cents else cents
}

/** Index of the decimal point for a string with one kind of separator, or -1 when every one is a thousands separator. */
private fun lone(t: String, sep: Char, last: Int): Int {
    if (t.count { it == sep } > 1) return -1
    val after = t.length - last - 1
    val before = t.substring(0, last)
    return if (after == 3 && before.length in 1..3 && before != "0") -1 else last
}

/** Starts with "%PDF". */
fun looksLikePdf(bytes: ByteArray): Boolean =
    bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()

data class ImportCandidate(
    val row: StatementRow,
    /** Id of a brick with the same merchant (note, case-insensitive) and amount within ±1 day: skipped. */
    val duplicateOf: String?,
    /** From merchant keywords onto the town's towers by name; null means the row needs a category. */
    val guessedCategoryId: String?,
    /** Merchant on the recurring list (Netflix, Spotify, rent, insurance, gym, phone, ...). */
    val looksRecurring: Boolean,
)

fun reviewImport(rows: List<StatementRow>, snapshot: TownSnapshot): List<ImportCandidate> =
    rows.map { row ->
        val merchant = row.merchant.trim()
        val duplicate = snapshot.expenses.firstOrNull {
            it.note.trim().equals(merchant, ignoreCase = true) &&
                it.amountCents == row.amountCents &&
                kotlin.math.abs(it.date.daysUntil(row.date)) <= 1
        }
        val recurring = RECURRING_MERCHANTS.any { mentions(merchant, it) }
        ImportCandidate(
            row = row,
            duplicateOf = duplicate?.id,
            guessedCategoryId = guessCategory(merchant, recurring, snapshot.categories),
            looksRecurring = recurring,
        )
    }

private val RECURRING_MERCHANTS = listOf(
    "Netflix", "Spotify", "Disney", "HBO", "Apple", "iCloud", "Google One", "YouTube", "Amazon Prime",
    "rent", "chirie", "insurance", "asigurare", "gym", "World Class", "Vodafone", "Orange", "Digi",
    "Telekom", "electricity", "Enel", "E.ON", "Engie", "water", "internet",
)

/** Tower name to keywords, in the order they are tried: "Uber Eats" must reach Eating out before "Uber" reaches Transport. */
private val CATEGORY_KEYWORDS: List<Pair<String, List<String>>> = listOf(
    "Groceries" to listOf("Lidl", "Kaufland", "Carrefour", "Mega Image", "Auchan", "Penny", "Profi", "Rewe", "Aldi", "Tesco"),
    "Eating out" to listOf("Wolt", "Glovo", "Uber Eats", "Bolt Food", "Tazz", "restaurant", "cafe", "McDonald's", "KFC", "Starbucks"),
    "Transport" to listOf("Uber", "Bolt", "Lime", "OMV", "Petrom", "Mol", "Rompetrol", "metrorex", "STB", "parking"),
    "Home" to listOf("IKEA", "Dedeman", "Leroy", "Hornbach", "Emag"),
    "Shopping" to listOf("Zara", "H&M", "Decathlon", "About You", "Answear"),
)

private fun guessCategory(merchant: String, recurring: Boolean, categories: List<Category>): String? {
    val tower = CATEGORY_KEYWORDS.firstOrNull { (_, words) -> words.any { mentions(merchant, it) } }?.first
        ?: if (recurring) "Subscriptions" else null
    return tower?.let { name -> categories.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }?.id }
}

/** Keyword at a word start; short keywords (4 letters or fewer) also need a word end, so "Mol" is not "Molly". */
private fun mentions(text: String, keyword: String): Boolean {
    var from = 0
    while (true) {
        val at = text.indexOf(keyword, from, ignoreCase = true)
        if (at < 0) return false
        val startOk = at == 0 || !text[at - 1].isLetterOrDigit()
        val end = at + keyword.length
        val endOk = keyword.length > 4 || end == text.length || !text[end].isLetterOrDigit()
        if (startOk && endOk) return true
        from = at + 1
    }
}

/** Every brick as CSV (date, tower, note, amount, monthly, shared, refund, paid by), header first, oldest first. */
fun exportCsv(snapshot: TownSnapshot): String {
    val towers = snapshot.categories.associate { it.id to it.name }
    val members = snapshot.members.associate { it.userId to it.name }
    val out = StringBuilder("date,tower,note,amount,monthly,shared,refund,paid by\n")
    for (e in snapshot.expenses.sortedWith(compareBy<Expense>({ it.date }, { it.createdAt }))) {
        out.append(
            listOf(
                e.date.toString(),
                towers[e.categoryId].orEmpty(),
                e.note,
                (e.amountCents / 100).toString() + "." + (e.amountCents % 100).toString().padStart(2, '0'),
                yesNo(e.recurring),
                yesNo(e.shared),
                yesNo(e.refund),
                members[e.paidBy] ?: e.paidBy,
            ).joinToString(",") { csvField(it) },
        ).append('\n')
    }
    return out.toString()
}

private fun yesNo(value: Boolean): String = if (value) "yes" else "no"

private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
