package ai.freeapp.bricktown

import ai.freeapp.bricktown.brick.BrickFormActions
import ai.freeapp.bricktown.brick.BrickFormContent
import ai.freeapp.bricktown.brick.BrickFormState
import ai.freeapp.bricktown.brick.TowerChoice
import ai.freeapp.bricktown.designsystem.BarTab
import ai.freeapp.bricktown.home.HomeFeedback
import ai.freeapp.bricktown.home.HomeScaffold
import ai.freeapp.bricktown.home.AddMenu
import ai.freeapp.bricktown.month.FakeLedger
import ai.freeapp.bricktown.month.MonthActions
import ai.freeapp.bricktown.month.MonthContent
import ai.freeapp.bricktown.month.MonthUiState
import ai.freeapp.bricktown.month.NOV_24
import ai.freeapp.bricktown.month.categories
import ai.freeapp.bricktown.month.d
import ai.freeapp.bricktown.month.fixture5b
import ai.freeapp.bricktown.month.fixture6c
import ai.freeapp.bricktown.month.monthUiState
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

/** Month, its sheets, the plus menu, Add and Refund against the handoff PNGs (5b, 6c, 6e), written to build/gallery/1027. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class MonthGalleryTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/gallery/1027").apply { mkdirs() }
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
    private val monthActions = MonthActions(none, {}, none, { _, _ -> }, {}, {}, {}, {}, none, none, none)

    private fun month(state: MonthUiState, pulseAdd: Boolean = false) = @Composable {
        HomeScaffold(BarTab.Month, none, none, none, none, none, none, pulseAdd = pulseAdd) { MonthContent(state, monthActions) }
    }

    @Test
    fun month5b() = shot("5b-month", month(monthUiState(fixture5b(), NOV_24)))

    @Test
    fun categorySheet() {
        val s = monthUiState(fixture5b(), NOV_24)
        shot("5b-category-sheet", month(s.copy(openTowerId = "eating")))
    }

    @Test
    fun fresh6c() = shot("6c-fresh-month", month(monthUiState(fixture6c(), d(2026, 11, 28)), pulseAdd = true))

    @Test
    fun plusMenu() = shot("5b-plus-menu") {
        HomeScaffold(BarTab.Month, none, none, none, none, none, none) { MonthContent(monthUiState(fixture5b(), NOV_24), monthActions) }
        AddMenu({}, {}, {})
    }

    private val formActions = BrickFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val choices = categories().map { TowerChoice(it.id, it.name, it.colorIndex) }

    @Test
    fun add() = shot("add-brick") {
        BrickFormContent(BrickFormState(ready = true, towers = choices, selectedTowerId = "eating", draft = "12", towerBricks = 12), formActions)
    }

    @Test
    fun refund6e() = shot("6e-refund") {
        BrickFormContent(
            BrickFormState(isEdit = true, ready = true, towers = choices, selectedTowerId = "shopping", draft = "89", refund = true, towerBricks = 9),
            formActions,
        )
    }
}
