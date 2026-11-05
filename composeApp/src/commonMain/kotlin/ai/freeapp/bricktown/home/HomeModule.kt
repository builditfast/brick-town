package ai.freeapp.bricktown.home

import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.settings.PennyPrefs
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The app-wide feedback holder (toast, confetti, Penny's jump) and the tab frame's ViewModel. */
val homeModule: Module = module {
    single<CelebrationSetting> {
        val prefs = get<PennyPrefs>()
        CelebrationSetting { prefs.celebrations.value }
    }
    single { HomeFeedback(get(), get()) }
    viewModel { HomeViewModel(get(), get<TownClock>()) }
}
