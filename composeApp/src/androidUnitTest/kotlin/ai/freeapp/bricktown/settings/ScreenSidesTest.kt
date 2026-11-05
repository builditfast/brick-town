package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.AppTheme
import ai.freeapp.bricktown.invite.InviteActions
import ai.freeapp.bricktown.invite.InviteContent
import ai.freeapp.bricktown.invite.InviteUiState
import ai.freeapp.bricktown.settle.SettleUpActions
import ai.freeapp.bricktown.settle.SettleUpContent
import ai.freeapp.bricktown.settle.SettleUpUiState
import ai.freeapp.bricktown.towers.TowersActions
import ai.freeapp.bricktown.towers.TowersContent
import ai.freeapp.bricktown.towers.TowersUiState
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 6a, 6b, 6f and 6g lay their content 18 dp from the screen's sides: the round Back disc starts there. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class ScreenSidesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun backStartsAtTheFramesSide(content: @Composable () -> Unit) {
        compose.setContent { AppTheme { content() } }
        compose.onNodeWithContentDescription("Back").assertLeftPositionInRootIsEqualTo(18.dp)
    }

    @Test
    fun settings() = backStartsAtTheFramesSide { SettingsContent(SettingsUiState(loaded = true), SettingsActions()) }

    @Test
    fun invite() = backStartsAtTheFramesSide { InviteContent(InviteUiState(loaded = true), InviteActions()) }

    @Test
    fun towers() = backStartsAtTheFramesSide { TowersContent(TowersUiState(loaded = true), TowersActions()) }

    @Test
    fun settleUp() = backStartsAtTheFramesSide { SettleUpContent(SettleUpUiState(loaded = true), SettleUpActions()) }
}
