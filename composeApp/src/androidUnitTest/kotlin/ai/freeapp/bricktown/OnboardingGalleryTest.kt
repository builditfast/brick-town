package ai.freeapp.bricktown

import ai.freeapp.bricktown.auth.CreateAccountContent
import ai.freeapp.bricktown.auth.CreateAccountUiState
import ai.freeapp.bricktown.auth.LandingScreen
import ai.freeapp.bricktown.auth.SignInContent
import ai.freeapp.bricktown.auth.SignInUiState
import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.join.JoinContent
import ai.freeapp.bricktown.join.JoinStep
import ai.freeapp.bricktown.join.JoinUiState
import ai.freeapp.bricktown.onboarding.OnboardingContent
import ai.freeapp.bricktown.onboarding.OnboardingStep
import ai.freeapp.bricktown.onboarding.OnboardingUiState
import ai.freeapp.bricktown.wire.InvitePreview
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
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Task 1026: each onboarding step, Landing, Sign in, Create account and Join, drawn from fixture
 * state on a 402 x 874 dp phone. PNGs land in composeApp/build/gallery/1026 for a side by side look
 * against design/handoff/screenshots/5a-onboarding.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class OnboardingGalleryTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppTheme { Box(Modifier.size(402.dp, 874.dp)) { content() } } }
        compose.mainClock.advanceTimeBy(3000)
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/gallery/1026").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun onboarding(name: String, state: OnboardingUiState) = shot("onboarding-$name") {
        OnboardingContent(state, {}, {}, {}, {}, {}, {}, {})
    }

    private val picked = listOf(0, 1, 2, 5)
    private val towers = OnboardingUiState().towers.mapIndexed { i, t -> t.copy(picked = i in picked) }

    @Test
    fun step1Empty() = onboarding("1-empty", OnboardingUiState())

    @Test
    fun step1Budget() = onboarding("1-budget", OnboardingUiState(budgetDraft = "1800", dailyLine = "That's about €60 a day."))

    @Test
    fun step2Towers() = onboarding("2-towers", OnboardingUiState(step = OnboardingStep.Towers, budgetDraft = "1800", towers = towers))

    @Test
    fun step3FirstBrick() = onboarding(
        "3-first-brick",
        OnboardingUiState(step = OnboardingStep.FirstBrick, budgetDraft = "1800", towers = towers, firstDraft = "12", firstTower = 0),
    )

    @Test
    fun welcome() = onboarding(
        "4-welcome",
        OnboardingUiState(
            step = OnboardingStep.Welcome, budgetDraft = "1800", towers = towers, firstDraft = "12", firstTower = 0,
            summary = "€1 800 a month · 4 towers · first brick: €12 on Groceries",
        ),
    )

    @Test
    fun landing() = shot("landing") { LandingScreen({}, {}) }

    @Test
    fun signIn() = shot("signin") { SignInContent(SignInUiState(email = "ana@example.com"), {}, {}, {}, {}) }

    @Test
    fun signInError() = shot("signin-error") {
        SignInContent(SignInUiState(email = "ana@example.com", password = "x", error = ErrorState.Validation("Wrong email or password.")), {}, {}, {}, {})
    }

    @Test
    fun createAccount() = shot("create-account") { CreateAccountContent(CreateAccountUiState(name = "Ana"), {}, {}, {}, {}, {}) }

    private val invite = InvitePreview(
        code = "8F2K-P3NY",
        townName = "Emanuel's town",
        ownerName = "Emanuel",
        categories = listOf(
            Category("c1", "Groceries", "Food", 0, position = 0),
            Category("c2", "Eating out", "Out", 1, position = 1),
            Category("c3", "Home", "Home", 2, position = 2),
            Category("c4", "Subscriptions", "Subs", 5, position = 3),
        ),
        takenColors = listOf(0),
        expiresAt = Instant.parse("2026-11-11T10:00:00Z"),
    )

    private fun join(name: String, state: JoinUiState) = shot(name) { JoinContent(state, {}, {}, {}, {}, {}, {}, {}) }

    @Test
    fun joinCode() = join("join-code", JoinUiState(codeInput = "8F2K-P3NY"))

    @Test
    fun joinPreview() = join("join-preview", JoinUiState(step = JoinStep.Preview, preview = invite, colorIndex = 1, initial = "A"))

    @Test
    fun joinPick() = join("join-pick", JoinUiState(step = JoinStep.Pick, preview = invite, colorIndex = 1, initial = "A"))
}
