package ai.freeapp.bricktown.towers

import ai.freeapp.bricktown.AppTheme
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 6f puts "Done" on the content's right side, on the same line as the Back disc and the title, and splits the
 * editor's Cancel and Save tower as a browser splits two `flex` items with padding.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class TowersHeaderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun doneEndsOnTheContentsSideCentredOnTheDisc() {
        compose.setContent { AppTheme { TowersContent(TowersUiState(loaded = true, canManage = true), TowersActions()) } }
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        val done = compose.onNodeWithText("Done")
        val bounds = done.getBoundsInRoot()
        // The word itself ends there: no padding between the text and the node's edge.
        val layouts = mutableListOf<TextLayoutResult>()
        done.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val textWidth = with(compose.density) { layouts.single().size.width.toDp() }
        assertEquals(textWidth.value, (bounds.right - bounds.left).value, 0.5f)
        assertEquals(402f - 18f, bounds.right.value, 0.5f)
        assertEquals((back.top.value + back.bottom.value) / 2, (bounds.top.value + bounds.bottom.value) / 2, 0.5f)
        done.assertTouchWidthIsEqualTo(48.dp)
    }

    @Test
    fun theEditorsCancelAndSaveShareWhatIsLeftAfterTheirPaddingOneToTwo() {
        val editor = TowerEditor(id = null, name = "", colorIndex = 0, capCents = null)
        compose.setContent { AppTheme { TowersContent(TowersUiState(loaded = true, canManage = true, editor = editor), TowersActions()) } }
        val cancel = compose.onNodeWithText("Cancel").getBoundsInRoot()
        val save = compose.onNodeWithText("Save tower").getBoundsInRoot()
        // 6f: `flex:1` and `flex:2`, 8 px apart, each with 12 px padding on both sides.
        assertEquals(cancel.right.value + 8f, save.left.value, 0.5f)
        val row = save.right.value - cancel.left.value
        val left = row - 8f - 4 * 12f
        assertEquals(24f + left / 3, (cancel.right - cancel.left).value, 0.5f)
        assertEquals(24f + left * 2 / 3, (save.right - save.left).value, 0.5f)
    }
}
