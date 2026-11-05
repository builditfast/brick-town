package ai.freeapp.bricktown.trail

import ai.freeapp.bricktown.core.time.TownClock
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The Trail tab (spec 5.7, task 1028). */
val trailModule: Module = module {
    viewModel { TrailViewModel(get(), get<TownClock>()) }
}
