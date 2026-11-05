package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.reminders.ReminderRestorer
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Settings (6a) and the town gateway the towers, invite, settle and import ViewModels share. */
val settingsModule: Module = module {
    single<TownGateway> { RepositoryTownGateway(get()) }
    single<PennyPrefs> { SettingsPennyPrefs() }
    single { WeeklyRecapReminder(get(), get(), get(), get()) } bind ReminderRestorer::class
    // A release build and iOS bind no TestTools, so the group is absent there.
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), testTools = getOrNull()) }
}
