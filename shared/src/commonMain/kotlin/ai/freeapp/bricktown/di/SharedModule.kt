package ai.freeapp.bricktown.di

import ai.freeapp.bricktown.api.ApiClient
import ai.freeapp.bricktown.auth.AuthRepository
import ai.freeapp.bricktown.auth.AuthRepositoryImpl
import ai.freeapp.bricktown.core.auth.ObservableTokenStore
import ai.freeapp.bricktown.core.auth.SecureTokenStoreFactory
import ai.freeapp.bricktown.core.auth.TokenStore
import ai.freeapp.bricktown.core.flags.AppFlags
import ai.freeapp.bricktown.core.flags.SettingsFlagCache
import ai.freeapp.bricktown.core.sync.ConnectivityMonitor
import ai.freeapp.bricktown.core.sync.Outbox
import ai.freeapp.bricktown.core.sync.OutboxDrainer
import ai.freeapp.bricktown.core.sync.OutboxSender
import ai.freeapp.bricktown.core.sync.ServerReachability
import ai.freeapp.bricktown.core.sync.SqlOutboxStore
import ai.freeapp.bricktown.db.AppDatabase
import ai.freeapp.bricktown.db.DatabaseDriverFactory
import ai.freeapp.bricktown.reminders.DailyReminder
import ai.freeapp.bricktown.reminders.ReminderRestorer
import ai.freeapp.bricktown.reminders.ReminderScheduler
import ai.freeapp.bricktown.reminders.SettingsReminderStore
import ai.freeapp.bricktown.town.JoinCodeStore
import ai.freeapp.bricktown.town.SettingsJoinCodeStore
import ai.freeapp.bricktown.town.TownLoader
import ai.freeapp.bricktown.town.TownRepository
import ai.freeapp.bricktown.town.TownSync
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Koin module shared across Android + iOS.
 *
 * EXTENSION POINT: register new repositories / use-cases here.
 * Per-platform setup ([DatabaseDriverFactory], [SecureTokenStoreFactory] and
 * [ConnectivityMonitor], which need a Context on Android, and the
 * [ReminderScheduler]) is registered by the platform entrypoints:
 *   - `TemplateApp` in :composeApp androidMain, through initKoin
 *   - `initKoinIos` in :shared iosMain
 */
val sharedModule: Module = module {
    single { AppDatabase(get<DatabaseDriverFactory>().createDriver()) }
    // One store, bound under both types: the HTTP client clears it when the
    // server refuses a refresh, and the UI must hear that through the same
    // instance's signedIn flow.
    single { ObservableTokenStore(get<SecureTokenStoreFactory>().create()) } bind TokenStore::class
    single { ServerReachability() }
    // Drop the `tokens` argument in an app with no login: the client then
    // sends no Authorization header and installs no 401 interceptor.
    // The type arguments are explicit on purpose: the parameters are nullable,
    // and a bare get() there resolves against a nullable expected type, which
    // Koin reports only at first resolution, not at compile time.
    single { ApiClient(ApiClient.defaultClient(tokens = get<TokenStore>(), reachability = get<ServerReachability>())) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    // The outbox looks its senders up when it sends, not when it is built, so
    // a repository can both need the outbox and be one of its senders.
    single { Outbox(SqlOutboxStore(get()), get()) { getAll<OutboxSender>() } }
    single {
        OutboxDrainer(
            outbox = get(),
            online = get<ConnectivityMonitor>().isOnline,
            serverAnswered = get<ServerReachability>().answered,
            signedIn = get<ObservableTokenStore>().signedIn,
        )
    }
    // EXTENSION POINT: a repository whose writes go through the outbox is
    // bound as an OutboxSender too, or its queued writes are never sent.
    single { TownRepository(get(), get(), get(), get()) } binds arrayOf(OutboxSender::class, TownLoader::class)
    // The UI calls onForeground and onBackground from its lifecycle.
    single { TownSync(get(), get<ConnectivityMonitor>().isOnline) }
    single { DailyReminder(get(), SettingsReminderStore(), get()) } bind ReminderRestorer::class
    single<JoinCodeStore> { SettingsJoinCodeStore() }
    single { AppFlags(source = get<ApiClient>()::getFlags, cache = SettingsFlagCache()) }
}

/**
 * Starts Koin with the shared module plus any extra platform modules, then
 * the work that runs with no screen open. Called by `TemplateApp.onCreate`
 * (Android) and `initKoinIos` (iOS).
 */
fun initKoin(extra: KoinAppDeclaration? = null) {
    val app = startKoin {
        extra?.invoke(this)
        modules(sharedModule)
    }
    startBackgroundWork(app.koin)
}

/**
 * On Android this runs inside `TemplateApp.onCreate`, so also for a process
 * the system starts only to deliver the boot or reminder broadcast.
 *
 * EXTENSION POINT: start new background pieces here. AuthRepository is
 * resolved first so its wipe is registered before anything can end a session.
 */
fun startBackgroundWork(koin: Koin) {
    koin.get<AuthRepository>()
    koin.getAll<ReminderRestorer>().forEach { it.restore() }
    koin.get<OutboxDrainer>().start()
}
