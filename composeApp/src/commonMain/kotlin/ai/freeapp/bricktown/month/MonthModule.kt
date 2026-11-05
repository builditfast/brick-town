package ai.freeapp.bricktown.month

import ai.freeapp.bricktown.core.time.TownClock
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val monthModule: Module = module {
    viewModel { params -> MonthViewModel(params.getOrNull<String>(), get(), get<TownClock>(), get()) }
}
