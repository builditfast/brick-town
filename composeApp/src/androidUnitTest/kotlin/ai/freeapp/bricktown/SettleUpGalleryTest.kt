package ai.freeapp.bricktown

import ai.freeapp.bricktown.settle.SettleBrick
import ai.freeapp.bricktown.settle.SettleUpActions
import ai.freeapp.bricktown.settle.SettleUpContent
import ai.freeapp.bricktown.settle.SettleUpUiState
import org.junit.Test
import org.robolectric.annotation.Config

/** The rows of handoff frame 6b. Tower colour 0 is Groceries, 1 Eating out, 2 Home; member colour 1 is Ana's purple. */
class SettleUpGalleryTest : Gallery1029() {
    private fun hers(id: String, title: String, meta: String, amount: String, colorIndex: Int, share: String) =
        SettleBrick(id, title, meta, amount, colorIndex, share, shareOwedToYou = false, partnerInitial = "A", partnerColorIndex = 1)

    private val state = SettleUpUiState(
        loaded = true,
        hasPartner = true,
        title = "Town with Ana",
        youInitial = "Y",
        youColorIndex = 0,
        partnerName = "Ana",
        partnerInitial = "A",
        partnerColorIndex = 1,
        headline = "Ana owes you €70.10",
        allSquare = false,
        summary = "5 shared bricks in November · split 50/50",
        youPaid = listOf(
            SettleBrick("1", "Electricity", "2 Nov · Home · Monthly", "€180", 2, "+€90", shareOwedToYou = true),
            SettleBrick("18", "Cleaning", "19 Nov · Home", "€50", 2, "+€25", shareOwedToYou = true),
        ),
        partnerPaid = listOf(
            hers("2", "Albert Heijn", "22 Nov · Groceries", "€48.20", 0, "−€24.10"),
            hers("12", "Wolt", "23 Nov · Eating out", "€14", 1, "−€7"),
            hers("9", "Wolt", "16 Nov · Eating out", "€27.60", 1, "−€13.80"),
        ),
        canSettle = true,
        canPing = true,
    )

    @Test
    fun owed() = shot("settle-6b") { SettleUpContent(state, SettleUpActions()) }

    /** The same screen scrolled to its end: the hint card under the last brick. */
    @Test
    @Config(qualifiers = "w402dp-h1180dp-xhdpi")
    fun owedFullLength() = shot("settle-6b-full", heightDp = 1180) { SettleUpContent(state, SettleUpActions()) }

    @Test
    fun square() = shot("settle-6b-square") {
        SettleUpContent(
            state.copy(headline = "All square", allSquare = true, summary = "No shared bricks to settle", youPaid = emptyList(), partnerPaid = emptyList(), canSettle = false, canPing = false, settleLabel = "Settled · 24 Nov"),
            SettleUpActions(),
        )
    }
}
