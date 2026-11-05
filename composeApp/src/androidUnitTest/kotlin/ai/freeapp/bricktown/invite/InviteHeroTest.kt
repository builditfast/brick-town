package ai.freeapp.bricktown.invite

import ai.freeapp.bricktown.AppTheme
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 6g's link pill and Share link are 100% wide plus their own 14 px padding, so they reach 4 dp from the card's
 * sides while the words keep the card's 18; "Copy · Show QR" sits one 12 px gap under the button.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class InviteHeroTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = InviteUiState(
        loaded = true,
        members = listOf(InviteAvatar("Y", 0)),
        link = "https://bricktown.app/join/8F2K-PENNY",
        expires = "expires in 7 days",
    )

    private fun hero() = compose.setContent { AppTheme { InviteContent(state, InviteActions()) } }

    private fun SemanticsNodeInteraction.textHeight(): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return with(compose.density) { layouts.single().size.height.toDp().value }
    }

    @Test
    fun theButtonAndThePillReachFourFromTheCardsSides() {
        hero()
        // 18 dp screen side + 4 dp; 402 - 2 * 22 wide.
        compose.onNodeWithText("Share link").assertLeftPositionInRootIsEqualTo(22.dp).assertWidthIsEqualTo(358.dp)
        // The pill's own 14 dp padding puts the link where the card's 18 would.
        compose.onNodeWithText("bricktown.app/join/8F2K-PENNY").assertLeftPositionInRootIsEqualTo(18.dp + 4.dp + 14.dp)
    }

    @Test
    fun theWordsKeepTheCardsEighteen() {
        hero()
        for (text in listOf("One town, shared bricks", "Anyone with the link joins your town.")) {
            val bounds = compose.onNodeWithText(text, substring = true).getBoundsInRoot()
            assert(bounds.left.value >= 36f - 0.5f && bounds.right.value <= 366f + 0.5f) { "$text spans ${bounds.left} to ${bounds.right}" }
        }
    }

    @Test
    fun copyAndShowQrAreTheirWordsOnly() {
        hero()
        for (text in listOf("Copy", "Show QR")) {
            val node = compose.onNodeWithText(text)
            val bounds = node.getBoundsInRoot()
            assertEquals(text, node.textHeight(), (bounds.bottom - bounds.top).value, 0.5f)
        }
    }

    @Test
    fun theParagraphsLastLineIsNeverOneWordAlone() {
        hero()
        // 6g's paragraph has text-wrap: pretty, which ends it on "settling up." rather than "up." alone.
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Anyone with the link joins your town.", substring = true)
            .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val layout = layouts.single()
        val last = layout.layoutInput.text.text.substring(layout.getLineStart(layout.lineCount - 1)).trim().replace('\u00A0', ' ')
        assertEquals("settling up.", last)
    }
}
