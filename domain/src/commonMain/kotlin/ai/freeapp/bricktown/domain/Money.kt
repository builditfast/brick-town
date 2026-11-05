package ai.freeapp.bricktown.domain

/**
 * Money as the design writes it: "€1 312" (a space groups thousands), "€70.10"
 * when cents matter, "−€143" with a real minus sign, and "312 lei" for a
 * currency written after the number.
 */

/** Whole units, rounded half up: "€1 312". */
fun formatWhole(cents: Long, currency: Currency): String =
    withCurrency(wholeUnits(cents), currency, negative = wholeUnits(cents) < 0)

/** Two decimals: "€70.10". */
fun formatExact(cents: Long, currency: Currency): String {
    val abs = if (cents < 0) -cents else cents
    val body = group(abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
    return affix(body, currency, negative = cents < 0, plus = false)
}

/** Leading "−" or "+" for deltas: "−€143", "+€24". Zero has no sign. */
fun formatSigned(cents: Long, currency: Currency): String {
    val units = wholeUnits(cents)
    return affix(group(if (units < 0) -units else units), currency, negative = units < 0, plus = units > 0)
}

/** Half up on the magnitude, so -150 cents is -2 and 150 is 2. */
private fun wholeUnits(cents: Long): Long {
    val abs = if (cents < 0) -cents else cents
    val units = (abs + 50) / 100
    return if (cents < 0) -units else units
}

private fun withCurrency(units: Long, currency: Currency, negative: Boolean): String =
    affix(group(if (units < 0) -units else units), currency, negative, plus = false)

/** The minus sign is U+2212, the one the design uses, and it always leads the symbol: "−€143". */
private fun affix(body: String, currency: Currency, negative: Boolean, plus: Boolean): String {
    val sign = if (negative) "\u2212" else if (plus) "+" else ""
    return if (currency.symbolAfter) "$sign$body ${currency.symbol}" else "$sign${currency.symbol}$body"
}

/** A plain space groups thousands, as the design writes "€1 312". */
private fun group(value: Long): String {
    val digits = value.toString()
    val out = StringBuilder()
    for ((i, c) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(' ')
        out.append(c)
    }
    return out.toString()
}

/**
 * The numpad's rules: at most 7 characters, one ".", two decimals, "⌫" deletes
 * the last character, a leading "0" is replaced by the next digit. Returns the
 * new draft, or the old one when the key is refused.
 */
fun numpadPress(draft: String, key: Char): String {
    if (key == NUMPAD_DELETE) return draft.dropLast(1)
    if (key == '.') {
        if ('.' in draft) return draft
        if (draft.isEmpty()) return "0."
        return if (draft.length >= NUMPAD_MAX_LENGTH) draft else "$draft."
    }
    if (key !in '0'..'9') return draft
    if (draft == "0") return key.toString()
    if (draft.length >= NUMPAD_MAX_LENGTH) return draft
    val dot = draft.indexOf('.')
    if (dot >= 0 && draft.length - dot - 1 >= 2) return draft
    return draft + key
}

private const val NUMPAD_MAX_LENGTH = 7

const val NUMPAD_DELETE: Char = '⌫'

/** "12.5" to 1250. Blank or "." is 0. */
fun draftToCents(draft: String): Long {
    val dot = draft.indexOf('.')
    val whole = (if (dot < 0) draft else draft.substring(0, dot)).toLongOrNull() ?: 0L
    val fraction = if (dot < 0) "" else draft.substring(dot + 1).take(2).padEnd(2, '0')
    return whole * 100 + (fraction.toLongOrNull() ?: 0L)
}
