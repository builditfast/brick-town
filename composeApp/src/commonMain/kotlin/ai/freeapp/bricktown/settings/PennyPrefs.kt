package ai.freeapp.bricktown.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The two Penny switches that are phone-only: nothing about them goes to the town. */
interface PennyPrefs {
    val weeklyRecap: StateFlow<Boolean>
    val celebrations: StateFlow<Boolean>
    fun setWeeklyRecap(on: Boolean)
    fun setCelebrations(on: Boolean)
}

class SettingsPennyPrefs(private val settings: Settings = Settings()) : PennyPrefs {
    private val recap = MutableStateFlow(settings.getBoolean(RECAP_KEY, true))
    private val confetti = MutableStateFlow(settings.getBoolean(CELEBRATIONS_KEY, true))

    override val weeklyRecap: StateFlow<Boolean> = recap.asStateFlow()
    override val celebrations: StateFlow<Boolean> = confetti.asStateFlow()

    override fun setWeeklyRecap(on: Boolean) {
        settings.putBoolean(RECAP_KEY, on)
        recap.value = on
    }

    override fun setCelebrations(on: Boolean) {
        settings.putBoolean(CELEBRATIONS_KEY, on)
        confetti.value = on
    }

    private companion object {
        const val RECAP_KEY = "penny_weekly_recap"
        const val CELEBRATIONS_KEY = "penny_celebrations"
    }
}
