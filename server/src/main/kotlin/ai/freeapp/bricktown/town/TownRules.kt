package ai.freeapp.bricktown.town

import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.TownSettings
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** A refusal with the sentence the person sees. Thrown inside a transaction, so it rolls the write back. */
class TownProblem(val status: HttpStatusCode, message: String) : RuntimeException(message)

fun problem(status: HttpStatusCode, message: String): Nothing = throw TownProblem(status, message)

/** The hard limits of section 5.2, in one place. */
object TownRules {
    const val MAX_MEMBERS = 2
    const val MAX_TOWERS = 12
    const val MAX_TOWER_NAME = 24
    const val MAX_SHORT_NAME = 5
    const val MAX_COLOR_INDEX = 7
    const val MAX_NOTE = 80
    const val MIN_AMOUNT_CENTS = 1L
    const val MAX_AMOUNT_CENTS = 999_999_900L
    const val MAX_SERIES_KEY = 120
    val EARLIEST_DATE = LocalDate(2000, 1, 1)

    fun zoneOf(name: String): TimeZone =
        runCatching { TimeZone.of(name) }.getOrNull()
            ?: problem(HttpStatusCode.BadRequest, "That time zone is not recognised.")

    /** The last day a brick may carry: tomorrow where the town is. */
    fun latestDate(zone: TimeZone): LocalDate =
        Clock.System.todayIn(zone).plus(DatePeriod(days = 1))

    fun today(zone: TimeZone): LocalDate = Clock.System.todayIn(zone)

    fun checkDate(date: LocalDate, zone: TimeZone) {
        if (date < EARLIEST_DATE || date > latestDate(zone)) {
            problem(HttpStatusCode.BadRequest, "That date is outside what a town can hold.")
        }
    }

    fun checkAmount(cents: Long) {
        if (cents < MIN_AMOUNT_CENTS || cents > MAX_AMOUNT_CENTS) {
            problem(HttpStatusCode.BadRequest, "An amount must be between 0.01 and 9 999 999.00.")
        }
    }

    fun checkNote(note: String) {
        if (note.length > MAX_NOTE) problem(HttpStatusCode.BadRequest, "A note can be at most $MAX_NOTE characters.")
    }

    fun checkColor(index: Int) {
        if (index !in 0..MAX_COLOR_INDEX) problem(HttpStatusCode.BadRequest, "Pick one of the colours on offer.")
    }

    fun checkSettings(settings: TownSettings) {
        if (settings.monthlyBudgetCents < 0 || settings.monthlyBudgetCents > MAX_AMOUNT_CENTS) {
            problem(HttpStatusCode.BadRequest, "The monthly budget must be between 0 and 9 999 999.00.")
        }
        if (settings.payday !in 1..31) problem(HttpStatusCode.BadRequest, "Payday must be a day from 1 to 31.")
        zoneOf(settings.timeZone)
    }

    /** The tower as it is stored: trimmed names, limits checked. */
    fun cleanCategory(category: Category): Category {
        val name = category.name.trim()
        val short = category.short.trim()
        if (name.length !in 1..MAX_TOWER_NAME) {
            problem(HttpStatusCode.BadRequest, "A tower name must be 1 to $MAX_TOWER_NAME characters.")
        }
        if (short.length !in 1..MAX_SHORT_NAME) {
            problem(HttpStatusCode.BadRequest, "A tower's short name must be 1 to $MAX_SHORT_NAME characters.")
        }
        checkColor(category.colorIndex)
        val cap = category.capCents
        if (cap != null && (cap < 0 || cap > MAX_AMOUNT_CENTS)) {
            problem(HttpStatusCode.BadRequest, "A cap must be between 0 and 9 999 999.00.")
        }
        return category.copy(name = name, short = short)
    }

    fun cleanInitial(raw: String): String {
        val letter = raw.trim()
        if (letter.length != 1 || !letter.first().isLetter()) {
            problem(HttpStatusCode.BadRequest, "An initial is one letter.")
        }
        return letter.first().uppercase()
    }
}
