package ai.freeapp.bricktown.brick

import ai.freeapp.bricktown.core.time.TownClock
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The ledger seam and the one ViewModel behind Add brick and Edit brick. */
val brickModule: Module = module {
    single<BrickLedger> { RepositoryBrickLedger(get()) }
    viewModel { params -> BrickFormViewModel(params.get<BrickFormArgs>(), get(), get<TownClock>(), get(), ::newBrickId) }
}
