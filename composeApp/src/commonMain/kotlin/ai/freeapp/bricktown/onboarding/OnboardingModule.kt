package ai.freeapp.bricktown.onboarding

import ai.freeapp.bricktown.town.TownRepository

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The device's own answers: a random UUID, the zone id, the instant now. */
internal class DeviceOnboardingEnvironment : OnboardingEnvironment {
    @OptIn(ExperimentalUuidApi::class)
    override fun newId(): String = Uuid.random().toString()
    override fun timeZoneId(): String = TimeZone.currentSystemDefault().id
    override fun now(): Instant = Clock.System.now()
}

val onboardingModule: Module = module {
    single<OnboardingEnvironment> { DeviceOnboardingEnvironment() }
    factory { TownCreator { request, id, name -> get<TownRepository>().createTown(request, id, name) } }
    viewModel { OnboardingViewModel(get(), get(), get(), get()) }
}
