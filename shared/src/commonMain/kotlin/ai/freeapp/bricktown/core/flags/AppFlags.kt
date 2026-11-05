package ai.freeapp.bricktown.core.flags

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/** The owner's switches the app reads, named as in server/src/main/resources/flags.json. */
object Flag {
    const val TOWN_SHARE = "town.share"
    const val TOWN_SETTLE = "town.settle"
    const val TOWN_IMPORT = "town.import"
    const val TOWN_EXPORT = "town.export"
    const val PENNY_NUDGE = "penny.nudge"
    const val PENNY_RECAP = "penny.recap"
    const val WIDGETS = "widgets"
    const val IMPORT_PDF = "import.pdf"
}

/** Where the answer of `GET /flags` comes from (the [ai.freeapp.bricktown.api.ApiClient] in the app). */
fun interface FlagSource {
    suspend fun fetch(): Map<String, Boolean>
}

/** The last answer, kept so a cold start offline shows what the owner last switched. */
interface FlagCache {
    fun read(): Map<String, Boolean>
    fun write(flags: Map<String, Boolean>)
}

class SettingsFlagCache(private val settings: Settings = Settings()) : FlagCache {
    private val serializer = MapSerializer(String.serializer(), Boolean.serializer())

    override fun read(): Map<String, Boolean> =
        settings.getStringOrNull(KEY)?.let { raw -> runCatching { Json.decodeFromString(serializer, raw) }.getOrNull() }
            .orEmpty()

    override fun write(flags: Map<String, Boolean>) {
        settings.putString(KEY, Json.encodeToString(serializer, flags))
    }

    private companion object {
        const val KEY = "flags.cached"
    }
}

/**
 * The feature switches for this user. A flag the server never named is on:
 * the server is the gate, the app only hides what it was told is off, so a
 * failed fetch or an older server never hides a working feature.
 */
class AppFlags(
    private val source: FlagSource,
    private val cache: FlagCache,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val known = MutableStateFlow(cache.read())

    val all: StateFlow<Map<String, Boolean>> = known.asStateFlow()

    fun isOn(name: String): Boolean = known.value[name] ?: true

    fun on(name: String): Flow<Boolean> = known.map { it[name] ?: true }.distinctUntilChanged()

    /** Fetches now; a failure keeps the last answer. */
    suspend fun refresh() {
        val fresh = try {
            source.fetch()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return
        }
        cache.write(fresh)
        known.value = fresh
    }

    fun refreshInBackground() {
        scope.launch { refresh() }
    }
}
