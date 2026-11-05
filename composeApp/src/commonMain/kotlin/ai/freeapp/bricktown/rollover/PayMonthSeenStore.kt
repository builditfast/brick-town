package ai.freeapp.bricktown.rollover

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate

/** The last pay month this device has seen for a town, so the trophy shows once per month. */
interface PayMonthSeenStore {
    fun read(townId: String): LocalDate?
    fun write(townId: String, monthStart: LocalDate)
}

class SettingsPayMonthSeenStore(private val settings: Settings = Settings()) : PayMonthSeenStore {
    override fun read(townId: String): LocalDate? =
        settings.getStringOrNull(key(townId))?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override fun write(townId: String, monthStart: LocalDate) {
        settings.putString(key(townId), monthStart.toString())
    }

    private fun key(townId: String) = "rollover.seen.$townId"
}
