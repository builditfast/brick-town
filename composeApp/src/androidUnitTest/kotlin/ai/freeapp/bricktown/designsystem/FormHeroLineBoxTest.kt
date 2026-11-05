package ai.freeapp.bricktown.designsystem

import ai.freeapp.bricktown.AppTheme
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The form's amount is Fredoka 64 at CSS line height 1 (5a, 6e): its row is 64 dp, not the font's own 78. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class FormHeroLineBoxTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theAmountRowIsAsTallAsItsFontSize() {
        compose.setContent { AppTheme { FormHero(sign = "-", symbol = "€", symbolAfter = false, digits = "89", spoken = "minus 89 euros") } }
        compose.onNodeWithContentDescription("minus 89 euros").assertHeightIsEqualTo(64.dp)
    }
}
