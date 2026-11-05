package ai.freeapp.bricktown.recap

import kotlinx.datetime.LocalDate
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The weekly recap (spec 5.7, task 1028). The week's Monday arrives as the one parameter. */
val recapModule: Module = module {
    viewModel { params -> RecapViewModel(params.get<LocalDate>(), get()) }
}
