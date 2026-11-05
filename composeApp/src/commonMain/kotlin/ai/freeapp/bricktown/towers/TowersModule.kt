package ai.freeapp.bricktown.towers

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Your towers (6f). */
val towersModule: Module = module {
    viewModel { TowersViewModel(get(), get()) }
}
