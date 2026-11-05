package ai.freeapp.bricktown.onboarding

import ai.freeapp.bricktown.auth.AuthRepository
import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.designsystem.BACKSPACE
import ai.freeapp.bricktown.domain.Category
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.NUMPAD_DELETE
import ai.freeapp.bricktown.domain.SUGGESTED_TOWERS
import ai.freeapp.bricktown.domain.TownSettings
import ai.freeapp.bricktown.domain.draftToCents
import ai.freeapp.bricktown.domain.formatExact
import ai.freeapp.bricktown.domain.formatWhole
import ai.freeapp.bricktown.domain.numpadPress
import ai.freeapp.bricktown.domain.payMonthOf
import ai.freeapp.bricktown.domain.shortNameFor
import ai.freeapp.bricktown.wire.CreateTownRequest
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate

enum class OnboardingStep { Budget, Towers, FirstBrick, Welcome }

/** One of the ten suggested towers. [colorIndex] is its place in the grid, which is its palette index. */
@Immutable
data class TowerChoice(val name: String, val short: String, val colorIndex: Int, val picked: Boolean)

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Budget,
    /** Whole euros typed on the numpad; empty until the first key. */
    val budgetDraft: String = "",
    /** "That's about €58 a day." once there is a budget; the first-step sub line before that. */
    val dailyLine: String? = null,
    val towers: List<TowerChoice> = defaultTowers(),
    /** The euro amount of the first brick as typed; empty means no first brick. */
    val firstDraft: String = "",
    /** Index into [towers] of the tower the first brick goes on. */
    val firstTower: Int? = null,
    val summary: String = "",
    val isCreating: Boolean = false,
    val error: ErrorState? = null,
) {
    val pickedTowers: List<TowerChoice> get() = towers.filter { it.picked }
    val pickedCount: Int get() = pickedTowers.size
    val budgetCents: Long get() = draftToCents(budgetDraft)

    /** Step 1 needs a budget above zero, step 2 at least one tower. Step 3 can always go on: the brick is optional. */
    val canNext: Boolean
        get() = when (step) {
            OnboardingStep.Budget -> budgetCents > 0
            OnboardingStep.Towers -> pickedCount > 0
            OnboardingStep.FirstBrick -> !isCreating
            OnboardingStep.Welcome -> true
        }

    /** 5a draws Back on every step but Welcome. Steps 2 and 3 go a step back; step 1 signs out (see [OnboardingViewModel.onBack]). */
    val canGoBack: Boolean get() = step != OnboardingStep.Welcome && !isCreating

    /** System Back is taken over only where it moves inside the flow; on step 1 it stays the app's own. */
    val systemBackStepsBack: Boolean get() = step == OnboardingStep.Towers || step == OnboardingStep.FirstBrick
    val firstCents: Long get() = draftToCents(firstDraft)
    val firstTowerName: String? get() = firstTower?.let { towers.getOrNull(it)?.name }
}

private fun defaultTowers(): List<TowerChoice> =
    SUGGESTED_TOWERS.mapIndexed { i, (name, short) -> TowerChoice(name, short, i, picked = false) }

/** Everything the ViewModel would otherwise read from the platform, so tests can fix it. */
interface OnboardingEnvironment {
    fun newId(): String
    fun timeZoneId(): String
    fun now(): kotlinx.datetime.Instant
}

/** The one local write that makes the town. */
fun interface TownCreator {
    suspend fun create(request: CreateTownRequest, viewerId: String, viewerName: String)
}

class OnboardingViewModel(
    private val auth: AuthRepository,
    private val creator: TownCreator,
    private val clock: TownClock,
    private val env: OnboardingEnvironment,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<OnboardingUiState, Nothing>(OnboardingUiState(), dispatcher) {

    /** [key] is what the design-system numpad emits: digits, '.', or [BACKSPACE]. */
    fun onKey(pressed: Char) = updateState { s ->
        val key = if (pressed == BACKSPACE) NUMPAD_DELETE else pressed
        when (s.step) {
            OnboardingStep.Budget -> {
                if (key == '.') s else withBudget(s, numpadPress(s.budgetDraft, key))
            }
            OnboardingStep.FirstBrick -> s.copy(firstDraft = numpadPress(s.firstDraft, key), error = null)
            else -> s
        }
    }

    fun onToggleTower(index: Int) = updateState { s ->
        val towers = s.towers.mapIndexed { i, t -> if (i == index) t.copy(picked = !t.picked) else t }
        s.copy(towers = towers)
    }

    fun onPickFirstTower(index: Int) = updateState { it.copy(firstTower = index) }

    fun onNext() {
        val s = state.value
        if (!s.canNext) return
        when (s.step) {
            OnboardingStep.Budget -> updateState { it.copy(step = OnboardingStep.Towers) }
            OnboardingStep.Towers -> updateState {
                val first = it.firstTower?.takeIf { i -> it.towers[i].picked } ?: it.towers.indexOfFirst { t -> t.picked }
                it.copy(step = OnboardingStep.FirstBrick, firstTower = first)
            }
            OnboardingStep.FirstBrick -> create()
            OnboardingStep.Welcome -> Unit
        }
    }

    /**
     * Steps 2 and 3 go one step back. Nothing was written yet, so nothing is undone.
     * Step 1 has nothing before it but the Start screen, so it signs out: the session
     * ends, `redirectFor` sends the stack to Landing, and the account stays on the
     * server for the next sign-in (no town exists yet, so nothing local is lost).
     */
    fun onBack() {
        when (state.value.step) {
            OnboardingStep.Budget -> if (!state.value.isCreating) {
                tryToExecute(call = { auth.signOut() }, onSuccess = { updateState { it.copy(error = null) } }, onError = { e -> updateState { it.copy(error = e) } })
            }
            OnboardingStep.Towers -> updateState { it.copy(step = OnboardingStep.Budget, error = null) }
            OnboardingStep.FirstBrick -> updateState { it.copy(step = OnboardingStep.Towers, error = null) }
            OnboardingStep.Welcome -> Unit
        }
    }

    private fun withBudget(s: OnboardingUiState, draft: String): OnboardingUiState {
        val cents = draftToCents(draft)
        return s.copy(budgetDraft = draft, dailyLine = if (cents > 0) dailyLineFor(cents) else null)
    }

    private fun settings(cents: Long) = TownSettings(monthlyBudgetCents = cents, timeZone = env.timeZoneId())

    /** The budget over the days of this month, in whole euros, half up. */
    private fun dailyLineFor(budgetCents: Long): String {
        val settings = settings(budgetCents)
        val days = payMonthOf(clock.today(settings), settings.payday).days
        val euros = (budgetCents + 50) / 100
        val perDay = (2 * euros + days) / (2 * days)
        return "That's about ${formatWhole(perDay * 100, Currency.EUR)} a day."
    }

    private fun create() {
        val s = state.value
        val user = auth.currentUser.value ?: return updateState { it.copy(error = ErrorState.Unauthorized) }
        val settings = settings(s.budgetCents)
        val categories = s.pickedTowers.mapIndexed { position, t ->
            Category(id = env.newId(), name = t.name, short = shortNameFor(t.name), colorIndex = t.colorIndex % PALETTE_SIZE, position = position)
        }
        val firstCategory = s.firstTowerName?.let { name -> categories.firstOrNull { it.name == name } }
        val today: LocalDate = clock.today(settings)
        val firstExpense = if (s.firstCents > 0 && firstCategory != null) {
            Expense(
                id = env.newId(), categoryId = firstCategory.id, amountCents = s.firstCents, date = today,
                paidBy = user.id, createdAt = env.now(),
            )
        } else {
            null
        }
        val request = CreateTownRequest(env.newId(), settings, categories, firstExpense)
        updateState { it.copy(isCreating = true, error = null) }
        tryToExecute(
            call = { creator.create(request, user.id, user.name) },
            onSuccess = { updateState { it.copy(step = OnboardingStep.Welcome, isCreating = false, summary = summaryOf(settings, categories.size, firstExpense, firstCategory)) } },
            onError = { e -> updateState { it.copy(isCreating = false, error = e) } },
        )
    }

    private fun summaryOf(settings: TownSettings, towers: Int, first: Expense?, category: Category?): String {
        val parts = mutableListOf(
            "${formatWhole(settings.monthlyBudgetCents, settings.currency)} a month",
            if (towers == 1) "1 tower" else "$towers towers",
        )
        if (first != null && category != null) {
            val amount = if (first.amountCents % 100 == 0L) formatWhole(first.amountCents, settings.currency) else formatExact(first.amountCents, settings.currency)
            parts += "first brick: $amount on ${category.name}"
        }
        return parts.joinToString(" · ")
    }

    private companion object {
        const val PALETTE_SIZE = 8
    }
}
