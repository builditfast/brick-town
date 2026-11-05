package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.AppTheme
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 6a, 6b, 6f and 6g draw a 40 dp Back disc on the content's side, the title 10 dp after it, and the first card
 * 12 dp under it: the column's gap and nothing more.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class RoundBackHeaderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun header() {
        compose.setContent {
            AppTheme {
                Box(Modifier.size(402.dp, 874.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RoundBackHeader("Town with Ana", onBack = {})
                        Box(Modifier.fillMaxWidth().height(100.dp).testTag("card"))
                    }
                }
            }
        }
    }

    @Test
    fun theFirstCardSitsTheColumnsGapUnderTheDisc() {
        header()
        // 8 dp above the disc (Android's top spacing), the 44 dp disc with its edge, then 12.
        compose.onNodeWithTag("card").assertTopPositionInRootIsEqualTo(8.dp + 44.dp + 12.dp)
    }

    @Test
    fun theDiscStartsAtTheScreensSideAndTheTitleTenAfterIt() {
        header()
        val back = compose.onNodeWithContentDescription("Back")
        back.assertLeftPositionInRootIsEqualTo(18.dp)
        back.assertWidthIsEqualTo(40.dp)
        // A 40 dp face still answers a 48 dp touch.
        back.assertTouchWidthIsEqualTo(48.dp)
        compose.onNodeWithText("Town with Ana").assertLeftPositionInRootIsEqualTo(18.dp + 40.dp + 10.dp)
    }
}
