package ai.freeapp.bricktown

import ai.freeapp.bricktown.brick.BrickFormActions
import ai.freeapp.bricktown.brick.BrickFormContent
import ai.freeapp.bricktown.brick.BrickFormState
import ai.freeapp.bricktown.brick.TowerChoice
import ai.freeapp.bricktown.month.categories
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The numpad steps aside while the note has focus, so the keyboard never covers the note field. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class BrickFormKeyboardTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theNumpadLeavesWhileTheNoteIsFocusedAndReturnsOnTheAmount() {
        val choices = categories().map { TowerChoice(it.id, it.name, it.colorIndex) }
        compose.setContent {
            AppTheme {
                BrickFormContent(
                    BrickFormState(ready = true, towers = choices, selectedTowerId = "eating", draft = "12", towerBricks = 12),
                    BrickFormActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }
        compose.onNodeWithContentDescription("7").assertIsDisplayed()
        compose.onNodeWithContentDescription("Note").performClick()
        compose.onNodeWithContentDescription("7").assertDoesNotExist()
        compose.onNodeWithContentDescription("Note").assertIsDisplayed()
    }
}
