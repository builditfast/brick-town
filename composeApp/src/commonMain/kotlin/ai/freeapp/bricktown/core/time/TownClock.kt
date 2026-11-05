package ai.freeapp.bricktown.core.time

import ai.freeapp.bricktown.domain.TownSettings
import kotlinx.datetime.Clock
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * What day it is in the town. Every screen asks this, never `Clock.System`,
 * so both partners and the server agree on "today" and a ViewModel test can
 * pin the date.
 */
fun interface TownClock {
    fun today(settings: TownSettings): LocalDate
}

/**
 * Today in the zone the town was created in. A zone this device does not know
 * falls back to the device's own, which is the right day for nearly everyone.
 */
class SystemTownClock(private val clock: Clock = Clock.System) : TownClock {
    override fun today(settings: TownSettings): LocalDate = clock.todayIn(zoneOf(settings.timeZone))

    private fun zoneOf(id: String): TimeZone = try {
        TimeZone.of(id)
    } catch (unknown: IllegalTimeZoneException) {
        TimeZone.currentSystemDefault()
    }
}
