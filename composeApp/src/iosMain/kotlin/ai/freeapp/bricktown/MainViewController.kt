package ai.freeapp.bricktown

import ai.freeapp.bricktown.di.appModule
import ai.freeapp.bricktown.di.initKoinIos
import ai.freeapp.bricktown.reminders.iosRemindersModule
import ai.freeapp.bricktown.reminders.registerNotificationActions
import androidx.compose.ui.window.ComposeUIViewController
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

/**
 * The iOS entrypoints, the counterpart of `TemplateApp` + `MainActivity` in
 * androidMain. Swift sees them as `MainViewControllerKt.startKoinIos()`,
 * `MainViewControllerKt.MainViewController()` and
 * `MainViewControllerKt.deliverLink(url:)`.
 */

/** Call once from the @main App's init, before the first view is built. */
fun startKoinIos() {
    initKoinIos(appModule, iosRemindersModule)
    registerNotificationActions()
}

fun MainViewController(): UIViewController = ComposeUIViewController { App() }

/**
 * Hands a bricktown:// link from SwiftUI's `onOpenURL` to the inbox; App
 * opens it once Start has picked the first screen. Koin is started by then:
 * the App's init runs before any URL reaches a scene.
 */
fun deliverLink(url: String) {
    KoinPlatform.getKoin().get<DeepLinkInbox>().deliver(url)
}
