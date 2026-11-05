package ai.freeapp.bricktown.brick

import ai.freeapp.bricktown.AppTheme
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 6e draws the 40 dp close disc flush with the form's 18 dp side padding. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class CloseDiscTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theDiscSitsOnTheRowsEdgeAndStillTakesA48DpTouch() {
        compose.setContent {
            AppTheme {
                Box(Modifier.size(402.dp, 874.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Edit brick")
                        CloseDisc({}, size = 44.dp)
                    }
                }
            }
        }
        val close = compose.onNodeWithContentDescription("Close")
        close.assertWidthIsEqualTo(40.dp)
        close.assertLeftPositionInRootIsEqualTo(344.dp)
        close.assertTouchWidthIsEqualTo(48.dp)
    }
}
