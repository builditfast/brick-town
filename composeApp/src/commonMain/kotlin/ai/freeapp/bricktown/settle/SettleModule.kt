package ai.freeapp.bricktown.settle

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Settle up (6b). */
val settleModule: Module = module {
    viewModel { SettleUpViewModel(get(), get()) }
}
