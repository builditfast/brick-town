package ai.freeapp.bricktown

import ai.freeapp.bricktown.core.navigation.BackStack
import ai.freeapp.bricktown.di.appModule
import ai.freeapp.bricktown.di.sharedModule
import android.app.Application
import android.content.ContentProvider
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Opens every screen once on the JVM, so a screen that throws when it is first
 * drawn fails `./gradlew test` instead of crashing on the phone. Haul,
 * 2026-10-22: `Modifier.padding(start = (-10).dp)` crashed Create account, Item
 * detail and Change password on open, while the unit tests, assembleDebug and
 * kmp-doctor all passed.
 *
 * The destinations are read from [Destination]'s sealed tree, so a new screen
 * is covered with no edit here. Each one is drawn through the app's own
 * [Screen] inside [AppTheme], on the app's real Koin graph with only the server
 * and the token store swapped (see [SmokeTestSetup]). The server answers 503
 * to everything, so a screen is drawn in its loading or error state: a crash
 * that only happens with data on screen is not caught here.
 *
 * Do not edit, skip or delete this file; the benchmark verify scripts compare
 * it with the template's copy. What a new app needs for it goes in
 * SmokeTestSetup.kt.
 */
// SDK 35: Robolectric's SDK 36 needs Java 21 and this build runs on 17. The
// plain Application keeps TemplateApp from starting the real graph first.
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ScreenSmokeTest(private val destination: Destination, @Suppress("unused") private val name: String) {

    @get:Rule
    val compose = createComposeRule()

    // Started once for every screen: koin-compose keeps the first root scope it
    // sees, so a graph restarted per test is resolved through a closed scope.
    @Before
    fun startApp() {
        if (GlobalContext.getOrNull() != null) return
        val context = ApplicationProvider.getApplicationContext<Application>()
        startProviders(context)
        startKoin {
            androidContext(context)
            allowOverride(true)
            modules(sharedModule, appModule, androidPlatformModule, SmokeTestSetup.overrides)
        }
    }

    @Test
    fun opens() {
        // A screen that polls or animates forever never goes idle, so the
        // clock is stepped by hand rather than waited on.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppTheme {
                Screen(destination, BackStack(root = destination, onRemoved = {}))
            }
        }
        repeat(FRAMES) { compose.mainClock.advanceTimeByFrame() }
    }

    companion object {
        private const val FRAMES = 10

        /**
         * The phone creates every manifest <provider> before the app's
         * onCreate; Robolectric creates none. androidx.startup's provider is
         * how libraries such as the no-arg Settings get their Context.
         */
        private fun startProviders(context: Context) {
            val providers = context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS)
                .providers.orEmpty()
            for (info in providers) {
                @Suppress("UNCHECKED_CAST")
                Robolectric.buildContentProvider(Class.forName(info.name) as Class<ContentProvider>).create(info)
            }
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
        fun destinations(): List<Array<Any>> =
            screens(Destination::class).map { arrayOf(instanceOf(it), it.qualifiedName!!.removePrefix(PREFIX)) }

        private val PREFIX = Destination::class.qualifiedName!! + "."

        /** The leaves of the sealed tree: every class or object a screen can be. */
        private fun screens(type: KClass<out Destination>): List<KClass<out Destination>> =
            if (type.isSealed) type.sealedSubclasses.flatMap(::screens) else listOf(type)

        private fun instanceOf(type: KClass<out Destination>): Destination {
            type.objectInstance?.let { return it }
            val constructor = checkNotNull(type.primaryConstructor) { "${type.simpleName} has no primary constructor" }
            val args = constructor.parameters.filterNot { it.isOptional }.associateWith { parameter ->
                val kind = parameter.type.classifier as KClass<*>
                when {
                    parameter.type.isMarkedNullable -> null
                    kind in SmokeTestSetup.samples -> SmokeTestSetup.samples.getValue(kind)
                    kind == String::class -> "smoke"
                    kind == Int::class -> 1
                    kind == Long::class -> 1L
                    kind == Boolean::class -> false
                    kind.java.isEnum -> kind.java.enumConstants.first()
                    else -> error(
                        "${type.simpleName}(${parameter.name}: ${kind.simpleName}) needs a sample: " +
                            "add one to SmokeTestSetup.samples",
                    )
                }
            }
            return constructor.callBy(args)
        }
    }
}
