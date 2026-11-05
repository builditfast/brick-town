package ai.freeapp.bricktown.auth

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The ViewModels of the launch check, Sign in and Create account. */
val authModule: Module = module {
    viewModel { StartViewModel(get(), get(), get(), get()) }
    viewModel { SignInViewModel(get()) }
    viewModel { CreateAccountViewModel(get()) }
}
