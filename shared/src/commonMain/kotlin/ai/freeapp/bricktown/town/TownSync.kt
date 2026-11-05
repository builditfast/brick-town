package ai.freeapp.bricktown.town

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the town fresh while the app is on screen. The platform calls
 * [onForeground] and [onBackground] from its lifecycle; nothing here knows
 * about Android or iOS.
 *
 * While foregrounded and online it refreshes at once and then every
 * [interval]. A refresh also follows every acknowledged send (the repository
 * does that itself). Going offline pauses the loop, coming back online
 * restarts it with an immediate refresh.
 */
class TownSync(
    private val repository: TownRepository,
    private val online: Flow<Boolean>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val interval: Duration = 30.seconds,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var loop: Job? = null

    val town = repository.town
    val status: StateFlow<SyncStatus> = repository.status
    val events = repository.events

    fun onForeground() {
        loop?.cancel()
        loop = scope.launch {
            online.distinctUntilChanged().collectLatest { isOnline ->
                if (!isOnline) return@collectLatest
                while (true) {
                    repository.refreshQuietly()
                    delay(interval)
                }
            }
        }
    }

    fun onBackground() {
        loop?.cancel()
        loop = null
    }
}
