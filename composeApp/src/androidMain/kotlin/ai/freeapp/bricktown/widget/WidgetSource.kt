package ai.freeapp.bricktown.widget

import ai.freeapp.bricktown.auth.AuthRepository
import ai.freeapp.bricktown.core.flags.AppFlags
import ai.freeapp.bricktown.core.flags.Flag
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.town.TownRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.koin.mp.KoinPlatform

/**
 * The widgets' door to the app's graph. A widget receiver can run in a fresh
 * process, but TemplateApp.onCreate starts Koin before any receiver runs.
 */
internal object WidgetSource {
    fun models(): Flow<WidgetModel> {
        val koin = KoinPlatform.getKoin()
        val clock = koin.get<TownClock>()
        val flags = koin.get<AppFlags>()
        return combine(
            koin.get<TownRepository>().town,
            koin.get<AuthRepository>().isSignedIn,
            flags.on(Flag.WIDGETS),
        ) { snapshot, signedIn, on ->
            widgetModelOf(signedIn, snapshot, on, snapshot?.let { clock.today(it.town.settings) })
        }.distinctUntilChanged()
    }

    suspend fun current(): WidgetModel = models().first()
}
