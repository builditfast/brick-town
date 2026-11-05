package ai.freeapp.bricktown

import ai.freeapp.bricktown.compare.CompareContent
import ai.freeapp.bricktown.compare.compareUiState
import ai.freeapp.bricktown.designsystem.BarTab
import ai.freeapp.bricktown.domain.badgesOf
import ai.freeapp.bricktown.history.HistoryActions
import ai.freeapp.bricktown.history.HistoryContent
import ai.freeapp.bricktown.history.historyUiState
import ai.freeapp.bricktown.home.HomeFeedback
import ai.freeapp.bricktown.home.HomeScaffold
import ai.freeapp.bricktown.month.FakeLedger
import ai.freeapp.bricktown.month.NOV_24
import ai.freeapp.bricktown.month.d
import ai.freeapp.bricktown.month.fixture5b
import ai.freeapp.bricktown.recap.RecapContent
import ai.freeapp.bricktown.recap.recapUiState
import ai.freeapp.bricktown.rollover.RolloverContent
import ai.freeapp.bricktown.rollover.rolloverUiState
import ai.freeapp.bricktown.trail.TrailContent
import ai.freeapp.bricktown.trail.trailUiState
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** History, Compare, Trail, Recap and Rollover against the handoff, written to build/gallery/1028. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class HistoryGalleryTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/gallery/1028").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppTheme {
                KoinApplication(application = { modules(module { single { HomeFeedback(FakeLedger(null)) { true } } }) }) {
                    Box(Modifier.size(402.dp, 874.dp)) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(3000)
        capture(name)
    }

    private val none = {}
    private val town = fixture5b()
    private val dec2 = d(2026, 12, 2)
    private val actions = HistoryActions({}, {}, {}, {}, {}, {})

    private fun tab(selected: BarTab, content: @Composable () -> Unit) = @Composable {
        HomeScaffold(selected, none, none, none, none, none, none) { content() }
    }

    @Test
    fun history() = shot("history", tab(BarTab.History) { HistoryContent(historyUiState(town, NOV_24, ""), actions) })

    @Test
    fun historySearch() = shot("history-search", tab(BarTab.History) { HistoryContent(historyUiState(town, NOV_24, "lidl"), actions) })

    @Test
    fun historyMonths() = shot(
        "history-months",
        tab(BarTab.History) { HistoryContent(historyUiState(town, dec2, "").copy(monthsOpen = true), actions) },
    )

    @Test
    fun compare() = shot("compare", tab(BarTab.Compare) { CompareContent(compareUiState(town, NOV_24)) })

    @Test
    fun trail() = shot("trail", tab(BarTab.Trail) { TrailContent(trailUiState(badgesOf(town, NOV_24))) })

    @Test
    fun recap5d() = shot("5d-weekly-recap", { RecapContent(recapUiState(town, d(2026, 11, 16)), none, none) })

    @Test
    fun rollover5c() = shot("5c-month-rollover", { RolloverContent(rolloverUiState(town, d(2026, 11, 1), dec2), none) })
}
