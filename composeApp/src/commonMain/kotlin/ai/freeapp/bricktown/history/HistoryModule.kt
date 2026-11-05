package ai.freeapp.bricktown.history

import ai.freeapp.bricktown.core.time.TownClock
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The History tab (spec 5.7, task 1028). */
val historyModule: Module = module {
    viewModel { HistoryViewModel(get(), get<TownClock>()) }
}
