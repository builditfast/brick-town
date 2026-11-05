package ai.freeapp.bricktown.rollover

import ai.freeapp.bricktown.brick.BrickLedger
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.domain.closedMonths
import ai.freeapp.bricktown.domain.payMonthOf
import ai.freeapp.bricktown.domain.previous
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * Answers with the pay month that just ended, once. The seen month is written
 * before the answer is returned, so a second call (or a re-render) gets null.
 * A month with no bricks has no trophy and is skipped silently.
 */
class MonthRolloverGate(
    private val ledger: BrickLedger,
    private val clock: TownClock,
    private val seen: PayMonthSeenStore,
) : RolloverGate {

    override suspend fun due(): LocalDate? {
        val snapshot = ledger.town.first() ?: return null
        val settings = snapshot.town.settings
        val townId = snapshot.town.id
        val today = clock.today(settings)
        val current = payMonthOf(today, settings.payday)
        val last = seen.read(townId)
        if (last == null) {
            seen.write(townId, current.start)
            return null
        }
        if (last >= current.start) return null
        seen.write(townId, current.start)
        val ended = current.previous(settings.payday)
        return ended.start.takeIf { start -> closedMonths(snapshot, today).any { it.month.start == start } }
    }
}
