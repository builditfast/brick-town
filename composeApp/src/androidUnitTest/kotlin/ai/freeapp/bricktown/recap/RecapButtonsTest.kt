package ai.freeapp.bricktown.recap

import ai.freeapp.bricktown.AppTheme
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 5d's Share (`flex:1`) and "Nice · back to town" (`flex:2`), 8 px apart, each 14 px padding, 15 px text, radius 18
 * and a 6 px edge: a browser gives each its padding first and shares the rest 1 to 2.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class RecapButtonsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theButtonsShareWhatIsLeftAfterTheirPaddingOneToTwo() {
        compose.setContent { AppTheme { RecapContent(RecapUiState(ready = true), {}, {}) } }
        val share = compose.onNodeWithContentDescription("Share").getBoundsInRoot()
        val nice = compose.onNodeWithText("Nice · back to town").getBoundsInRoot()
        val content = 402f - 2 * 18f
        val left = content - 8f - 4 * 14f
        assertEquals(18f, share.left.value, 0.5f)
        assertEquals(14f * 2 + left / 3, (share.right - share.left).value, 0.5f)
        assertEquals(share.right.value + 8f, nice.left.value, 0.5f)
        assertEquals(14f * 2 + left * 2 / 3, (nice.right - nice.left).value, 0.5f)
        // Same padding, text and edge: one height for both, 14 + the 15 sp line + 14 + 6.
        assertEquals((share.bottom - share.top).value, (nice.bottom - nice.top).value, 0.5f)
        assertEquals(14f + 21f + 14f + 6f, (nice.bottom - nice.top).value, 1.5f)
    }
}
