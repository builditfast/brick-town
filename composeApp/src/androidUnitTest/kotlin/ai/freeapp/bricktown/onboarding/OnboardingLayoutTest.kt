package ai.freeapp.bricktown.onboarding

import ai.freeapp.bricktown.AppTheme
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 5a draws the budget keys as a 54 dp face and the first brick's as a 50 dp face, each on a 4 dp edge,
 * and each amount in Fredoka at line height 1: 72 dp for the budget, 64 for the first brick.
 * "Back" ends on the header's 18 dp side padding. The first brick's tower chips are the form's chips
 * (12 sp Black, 8 x 12 padding, a 3 dp edge: 35 dp), 6 dp apart both ways.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class OnboardingLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: OnboardingUiState) = compose.setContent {
        AppTheme { Box(Modifier.size(402.dp, 874.dp)) { OnboardingContent(state, {}, {}, {}, {}, {}, {}, {}) } }
    }

    @Test
    fun theBudgetKeysAre58DpWithTheirEdge() {
        show(OnboardingUiState())
        compose.onNodeWithContentDescription("1").assertHeightIsEqualTo(58.dp)
    }

    @Test
    fun theFirstBrickKeysAre54DpWithTheirEdge() {
        val towers = OnboardingUiState().towers.mapIndexed { i, t -> t.copy(picked = i < 3) }
        show(OnboardingUiState(step = OnboardingStep.FirstBrick, budgetDraft = "1800", towers = towers, firstTower = 0))
        compose.onNodeWithContentDescription("1").assertHeightIsEqualTo(54.dp)
    }

    @Test
    fun theBudgetAmountIsAsTallAsItsFont() {
        show(OnboardingUiState(budgetDraft = "1800"))
        compose.onNodeWithContentDescription("€1800").assertHeightIsEqualTo(72.dp)
    }

    @Test
    fun theFirstBrickAmountIsAsTallAsItsFont() {
        val towers = OnboardingUiState().towers.mapIndexed { i, t -> t.copy(picked = i < 3) }
        show(OnboardingUiState(step = OnboardingStep.FirstBrick, budgetDraft = "1800", towers = towers, firstDraft = "12", firstTower = 0))
        compose.onNodeWithContentDescription("€12").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun backEndsOnTheSidePaddingAndStillTakesA48DpTouch() {
        show(OnboardingUiState())
        val back = compose.onNodeWithText("Back")
        val layouts = mutableListOf<TextLayoutResult>()
        back.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val wordWidth = with(compose.density) { layouts.single().size.width.toDp() }
        back.assertWidthIsEqualTo(wordWidth)
        back.assertLeftPositionInRootIsEqualTo(384.dp - wordWidth)
        back.assertTouchWidthIsEqualTo(48.dp)
    }

    @Test
    fun theFirstBrickChipsAreTheFormsChipsSixDpApart() {
        val towers = OnboardingUiState().towers.mapIndexed { i, t -> t.copy(picked = i < 8) }
        show(OnboardingUiState(step = OnboardingStep.FirstBrick, budgetDraft = "1800", towers = towers, firstTower = 0))
        val chips = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .fetchSemanticsNodes().map { with(compose.density) { it.boundsInRoot.top.toDp() to it.boundsInRoot.bottom.toDp() } }
        chips.forEach { (top, bottom) -> assertEquals(35f, (bottom - top).value, 1f) }
        val rows = chips.map { it.first }.distinct().sorted()
        assertTrue("the eight towers wrap to a second row", rows.size >= 2)
        val firstRowBottom = chips.first { it.first == rows[0] }.second
        assertEquals(6f, (rows[1] - firstRowBottom).value, 0.5f)
    }
}
