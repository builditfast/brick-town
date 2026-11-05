package ai.freeapp.bricktown.di

import ai.freeapp.bricktown.core.auth.SecureTokenStoreFactory
import ai.freeapp.bricktown.core.sync.ConnectivityMonitor
import ai.freeapp.bricktown.db.DatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Starts Koin on iOS with the platform bindings plus [extra]. `:composeApp`
 * passes its UI module from `startKoinIos()`, which the @main App's init calls.
 *
 * The reminder schedulers need the town, so `:composeApp` binds them (`IosReminders`).
 *
 * EXTENSION POINT: add iOS-only Koin bindings here.
 */
fun initKoinIos(vararg extra: Module) = initKoin {
    modules(
        module {
            single { DatabaseDriverFactory() }
            single { SecureTokenStoreFactory() }
            single { ConnectivityMonitor() }
        },
        *extra,
    )
}
