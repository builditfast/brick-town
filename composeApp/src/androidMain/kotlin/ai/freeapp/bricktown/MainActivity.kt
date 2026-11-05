package ai.freeapp.bricktown

import ai.freeapp.bricktown.core.auth.SecureTokenStoreFactory
import ai.freeapp.bricktown.core.sync.ConnectivityMonitor
import ai.freeapp.bricktown.core.ui.AndroidNotificationAccess
import ai.freeapp.bricktown.core.ui.NotificationAccess
import ai.freeapp.bricktown.db.DatabaseDriverFactory
import ai.freeapp.bricktown.di.appModule
import ai.freeapp.bricktown.di.initKoin
import ai.freeapp.bricktown.reminders.AndroidRecapScheduler
import ai.freeapp.bricktown.reminders.AndroidReminderScheduler
import ai.freeapp.bricktown.reminders.ReminderScheduler
import ai.freeapp.bricktown.settings.RecapScheduler
import ai.freeapp.bricktown.settings.testToolsModule
import ai.freeapp.bricktown.widget.WidgetUpdater
import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Runs before any activity or receiver, so the boot and reminder receivers
 * find Koin started and the outbox drainer running, with no screen open.
 */
class TemplateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@TemplateApp)
            modules(appModule, androidPlatformModule)
        }
        WidgetUpdater(this).start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}

/**
 * The pieces that need a Context. Named, so the screen smoke test starts the
 * same graph the app does.
 */
val androidPlatformModule = module {
    single { DatabaseDriverFactory(get()) }
    single { SecureTokenStoreFactory(get()) }
    single { ConnectivityMonitor(get()) }
    single<ReminderScheduler> { AndroidReminderScheduler(get()) }
    single<RecapScheduler> { AndroidRecapScheduler(get()) }
    single<NotificationAccess> { AndroidNotificationAccess(get()) }
    // Debug binds the Test tools, release binds nothing (src/androidDebug, src/androidRelease).
    includes(testToolsModule)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The template draws a light theme only. The default SystemBarStyle.auto
        // follows the phone instead, and in dark mode paints white bar icons
        // over the light screen. An app that adds a dark theme switches to auto.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A restored activity (rotation, process restore) carries the link it
        // was opened with, and that link was handled already.
        if (savedInstanceState == null) deliverLink(intent)
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverLink(intent)
    }

    /** Hands a bricktown:// link to the inbox; App opens it once Start has picked the first screen. */
    private fun deliverLink(intent: Intent) {
        // Reopened from Recents, Android replays the intent the task started with.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val url = intent.data?.toString() ?: return
        get<DeepLinkInbox>().deliver(url)
    }
}
