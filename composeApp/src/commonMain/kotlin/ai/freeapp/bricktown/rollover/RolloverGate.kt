package ai.freeapp.bricktown.rollover

import kotlinx.datetime.LocalDate

/**
 * Whether a pay month ended since this phone last showed its trophy, and the
 * day that month started. Start asks it once the town is known, so the
 * rollover screen (5c) comes before Month on the first open after payday.
 */
fun interface RolloverGate {
    suspend fun due(): LocalDate?
}
