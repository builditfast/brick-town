package ai.freeapp.bricktown.importing

import ai.freeapp.bricktown.api.ApiClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Importing a bank statement (spec 5.8, task 1029; PDF reading, task 1032). */
val importingModule: Module = module {
    single<PdfStatementImporter> { ApiPdfStatementImporter(get<ApiClient>()) }
    viewModel { ImportViewModel(get(), get(), get()) }
}
