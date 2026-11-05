package ai.freeapp.bricktown

import ai.freeapp.bricktown.api.ApiClient
import ai.freeapp.bricktown.core.auth.AuthTokens
import ai.freeapp.bricktown.core.auth.ObservableTokenStore
import ai.freeapp.bricktown.core.auth.TokenStore
import ai.freeapp.bricktown.core.sync.ServerReachability
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.reflect.KClass

/**
 * What [ScreenSmokeTest] swaps out of the app's Koin graph: only what cannot
 * run on the JVM. Never fake a ViewModel, a repository or a screen here, or
 * the test no longer draws what the phone draws.
 *
 * EXTENSION POINT: a platform piece a new app adds that needs a device goes in
 * [overrides], and a destination argument of a type the test cannot invent
 * goes in [samples].
 */
object SmokeTestSetup {

    val overrides = module {
        // The Android Keystore does not exist on the JVM. A stored session
        // means every signed-in screen draws as it would for a real user.
        single { ObservableTokenStore(SignedInTokenStore()) } bind TokenStore::class
        single {
            ApiClient(
                ApiClient.defaultClient(
                    tokens = get<TokenStore>(),
                    engine = MockEngine { respond("", HttpStatusCode.ServiceUnavailable) },
                    reachability = get<ServerReachability>(),
                ),
                baseUrl = "http://smoke.test",
            )
        }
    }

    // Recap and Rollover take a date: a Monday, so a recap week is a real one.
    val samples: Map<KClass<*>, Any> = mapOf(LocalDate::class to LocalDate(2026, 11, 16))
}

private class SignedInTokenStore : TokenStore {
    private var tokens: AuthTokens? = AuthTokens(accessToken = "smoke-access", refreshToken = "smoke-refresh")
    override fun read() = tokens
    override fun save(tokens: AuthTokens) {
        this.tokens = tokens
    }
    override fun clear() {
        tokens = null
    }
}
