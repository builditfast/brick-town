package ai.freeapp.bricktown.core.sync

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Whether the phone has a network, as the system sees it: Android's
 * `registerDefaultNetworkCallback` (needs ACCESS_NETWORK_STATE in the
 * manifest), iOS's `NWPathMonitor`. The Android actual takes a Context and is
 * registered by `TemplateApp`; the iOS one by `initKoinIos`.
 *
 * "Online" says nothing about the server. It only wakes [OutboxDrainer] the
 * moment the network returns; the drainer's timer covers a server that was
 * down while the network stayed up.
 */
expect class ConnectivityMonitor {
    /** Emits the current state on collection, then every change. */
    val isOnline: Flow<Boolean>
}

/**
 * Ticks every time the server answers a request with a 2xx: proof that it can
 * be reached again, so queued writes should go now. `ApiClient.defaultClient`
 * calls [markAnswered] from its response validator.
 */
class ServerReachability {
    private val _answered = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val answered: Flow<Unit> = _answered.asSharedFlow()

    fun markAnswered() {
        _answered.tryEmit(Unit)
    }
}
