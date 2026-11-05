package ai.freeapp.bricktown.town

import kotlinx.coroutines.flow.StateFlow

/**
 * What app start and navigation need from [TownRepository]: whether this
 * account has a town, and a way to ask the server again. An interface so the
 * app's ViewModel tests can drive it without a database.
 */
interface TownLoader {
    val status: StateFlow<SyncStatus>

    /** Asks the server for the town. A 404 turns [status] into NoTown or TownDeleted; other failures throw. */
    suspend fun refresh()
}
