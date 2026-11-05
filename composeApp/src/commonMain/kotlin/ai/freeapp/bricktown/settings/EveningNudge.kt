package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.streakOf
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * What the 20:00 notification says (4b). The frame's "Brick Town" and "now"
 * line is the system's own app name and time, so it is not part of the text.
 */
data class EveningNudge(val title: String, val body: String, val streak: Int)

const val NUDGE_ACTION_ZERO_DAY = "€0 day"
const val NUDGE_ACTION_ADD_BRICK = "Add a brick"

/**
 * The nudge for [today], or null when today is already logged (a brick or a
 * gold mark) or there is no town yet. The platform asks this at the moment the
 * notification is due, so a day logged at 19:59 never gets one.
 */
fun eveningNudgeFor(snapshot: TownSnapshot?, today: LocalDate): EveningNudge? {
    if (snapshot == null) return null
    val streak = streakOf(snapshot, today)
    if (streak.loggedToday) return null
    return nudgeText(streak.current)
}

/**
 * The nudge for a day that has not come. A streak number could be stale by
 * then, so the sentence is 4b's with the number dropped: "your streak is safe"
 * (ours).
 */
fun futureEveningNudge(): EveningNudge = nudgeText(null)

private fun nudgeText(streak: Int?): EveningNudge {
    val keep = when {
        streak == null -> "your streak is safe"
        streak > 0 -> "your $streak-day streak is safe"
        else -> "your first streak day is safe"
    }
    return EveningNudge(
        title = "No bricks today yet",
        body = "Spent nothing? Tap “$NUDGE_ACTION_ZERO_DAY” and $keep. Otherwise, one brick keeps it alive.",
        streak = streak ?: 0,
    )
}

/**
 * The nudge's "€0 day" button, for both platforms: marks today gold unless a
 * brick logged since the nudge rang already keeps the day alive. Returns false
 * when there is no town to write to.
 */
suspend fun markZeroDay(gateway: TownGateway, clock: TownClock): Boolean {
    val town = gateway.town.first() ?: return false
    val today = clock.today(town.town.settings)
    if (eveningNudgeFor(town, today) != null) gateway.markGoldDay(today)
    return true
}
