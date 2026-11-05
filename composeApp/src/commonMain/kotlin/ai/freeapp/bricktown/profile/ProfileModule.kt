package ai.freeapp.bricktown.profile

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The ViewModels of Settings' ACCOUNT group and Change password. */
val profileModule: Module = module {
    viewModel { ProfileViewModel(get()) }
    viewModel { ChangePasswordViewModel(get()) }
}
