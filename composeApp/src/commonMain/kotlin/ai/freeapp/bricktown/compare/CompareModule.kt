package ai.freeapp.bricktown.compare

import ai.freeapp.bricktown.core.time.TownClock
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The Compare tab (spec 5.7, task 1028). */
val compareModule: Module = module {
    viewModel { CompareViewModel(get(), get<TownClock>()) }
}
