package ai.freeapp.bricktown.di

import ai.freeapp.bricktown.DeepLinkInbox
import ai.freeapp.bricktown.auth.authModule
import ai.freeapp.bricktown.brick.brickModule
import ai.freeapp.bricktown.compare.compareModule
import ai.freeapp.bricktown.core.time.SystemTownClock
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.history.historyModule
import ai.freeapp.bricktown.home.homeModule
import ai.freeapp.bricktown.importing.importingModule
import ai.freeapp.bricktown.invite.inviteModule
import ai.freeapp.bricktown.join.joinModule
import ai.freeapp.bricktown.month.monthModule
import ai.freeapp.bricktown.onboarding.onboardingModule
import ai.freeapp.bricktown.profile.profileModule
import ai.freeapp.bricktown.recap.recapModule
import ai.freeapp.bricktown.rollover.rolloverModule
import ai.freeapp.bricktown.settings.settingsModule
import ai.freeapp.bricktown.settle.settleModule
import ai.freeapp.bricktown.towers.towersModule
import ai.freeapp.bricktown.trail.trailModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * UI-layer Koin module: ViewModels live here, repositories live in
 * `sharedModule` (:shared), which knows nothing about the UI.
 *
 * Each screen folder keeps its own module (`month/MonthModule.kt`), so the
 * screen tasks never edit the same file. EXTENSION POINT: a new screen folder
 * adds its module to `includes` below; a new ViewModel goes in its folder's
 * module, not here.
 */
val appModule: Module = module {
    includes(
        authModule,
        onboardingModule,
        joinModule,
        homeModule,
        monthModule,
        brickModule,
        historyModule,
        compareModule,
        trailModule,
        recapModule,
        rolloverModule,
        settingsModule,
        towersModule,
        inviteModule,
        settleModule,
        importingModule,
        profileModule,
    )
    single { DeepLinkInbox(get()) }
    single<TownClock> { SystemTownClock() }
}
