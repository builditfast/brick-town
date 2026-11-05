package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.lastCompletedWeekStart
import ai.freeapp.bricktown.domain.payMonthOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The debug-only "Test tools" group at the bottom of Settings. Only the debug
 * build binds an implementation (`composeApp/src/androidDebug`); the release
 * build and iOS bind none, so [SettingsViewModel] gets null and Settings draws
 * no group. The title and row labels live in the implementation for that
 * reason: a release build carries none of the words.
 */
interface TestTools {
    val title: String
    val rows: List<TestRow>
    suspend fun run(rowId: String): TestToolResult
}

data class TestRow(val id: String, val label: String)

sealed interface TestToolResult {
    data class Message(val text: String) : TestToolResult
    data class OpenRollover(val monthStart: LocalDate) : TestToolResult
}

/** One brick the sample-data tool will write. [seq] numbers it inside its week, from 0. */
data class SampleBrick(val date: LocalDate, val towerIndex: Int, val cents: Long, val seq: Int)

const val SAMPLE_WEEKS = 5
const val SAMPLE_BRICKS_PER_WEEK = 5

/**
 * Bricks for the [SAMPLE_WEEKS] newest completed weeks before [today]: five a
 * week on five different days, so History has a recap for each week and
 * Compare has two months to set against each other. Amounts change from week
 * to week (some weeks cheaper, so "best week" can appear). Pure: the same
 * [today] always gives the same list.
 */
fun sampleBricks(today: LocalDate, towerCount: Int): List<SampleBrick> {
    if (towerCount <= 0) return emptyList()
    val newest = lastCompletedWeekStart(today)
    return (0 until SAMPLE_WEEKS).flatMap { back ->
        val weekStart = newest.minus(back * 7, DateTimeUnit.DAY)
        (0 until SAMPLE_BRICKS_PER_WEEK).map { seq ->
            // A different spread each week: days 0,1,3,4,6 shifted by the week.
            val day = (seq * 3 + back) % 7
            val base = 450L + 1375L * ((seq + back * 2) % 5)
            val scale = if (back % 2 == 0) 100L else 135L
            SampleBrick(weekStart.plus(day, DateTimeUnit.DAY), (seq + back) % towerCount, base * scale / 100, seq)
        }
    }
}

/**
 * A UUID that depends only on the town and the brick's date and number, so a
 * second tap writes the same ids again (an update, not a copy). A brick id is
 * unique across the whole server, so the first half is the town's own; the
 * second half keeps the variant bits and holds the number and the day.
 */
@OptIn(ExperimentalUuidApi::class)
fun sampleBrickId(townId: String, brick: SampleBrick): String = Uuid.parse(townId).toLongs { townHigh, _ ->
    Uuid.fromLongs(townHigh, Long.MIN_VALUE or (brick.seq.toLong() shl 32) or brick.date.toEpochDays().toLong())
}.toString()

fun SampleBrick.toExpense(townId: String, towerId: String, note: String, paidBy: String, now: Instant) = Expense(
    id = sampleBrickId(townId, this), categoryId = towerId, amountCents = cents, date = date, note = note, paidBy = paidBy, createdAt = now,
)

/** The first day of the pay month before the one [today] is in. */
fun previousPayMonthStart(today: LocalDate, payday: Int): LocalDate =
    payMonthOf(payMonthOf(today, payday).start.minus(1, DateTimeUnit.DAY), payday).start
