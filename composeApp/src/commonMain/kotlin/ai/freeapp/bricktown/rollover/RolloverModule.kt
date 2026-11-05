package ai.freeapp.bricktown.rollover

import ai.freeapp.bricktown.core.time.TownClock
import kotlinx.datetime.LocalDate
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The month-end check `Start` asks, plus the trophy screen's ViewModel. */
val rolloverModule: Module = module {
    single<RolloverGate> { MonthRolloverGate(get(), get<TownClock>(), SettingsPayMonthSeenStore()) }
    viewModel { params -> RolloverViewModel(params.get<LocalDate>(), get(), get<TownClock>()) }
}
