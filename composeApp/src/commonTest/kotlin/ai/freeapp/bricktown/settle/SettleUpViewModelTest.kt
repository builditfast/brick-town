package ai.freeapp.bricktown.settle

import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.formatExact
import ai.freeapp.bricktown.settings.FakeTownGateway
import ai.freeapp.bricktown.settings.brick
import ai.freeapp.bricktown.settings.fixtureClock
import ai.freeapp.bricktown.settings.member
import ai.freeapp.bricktown.settings.townSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettleUpViewModelTest : ViewModelTest() {
    private fun viewModel(gateway: FakeTownGateway) = SettleUpViewModel(gateway, fixtureClock(), dispatcher)

    private val shared = listOf(
        brick("b1", "food", 10_000, shared = true),
        brick("b2", "fun", 2_000, paidBy = "ana", shared = true),
        brick("b3", "home", 9_999),
    )

    @Test
    fun theBalanceIsHalfOfWhatEachSidePaidAndPersonalBricksDoNotCount() = runTest(dispatcher) {
        val viewModel = viewModel(FakeTownGateway(townSnapshot(expenses = shared)))
        advanceUntilIdle()
        val state = viewModel.state.value

        assertEquals("Ana owes you ${formatExact(4_000, Currency.EUR)}", state.headline)
        assertEquals("2 shared bricks in November · split 50/50", state.summary)
        assertEquals(listOf("b1"), state.youPaid.map { it.id })
        assertEquals(listOf("b2"), state.partnerPaid.map { it.id })
        assertTrue(state.canSettle)
        assertTrue(state.canPing)
        assertEquals("Town with Ana", state.title)
    }

    @Test
    fun eachBrickCarriesItsTowerColourAndTheViewersHalf() = runTest(dispatcher) {
        val viewModel = viewModel(FakeTownGateway(townSnapshot(expenses = shared)))
        advanceUntilIdle()
        val state = viewModel.state.value
        val mine = state.youPaid.single()
        val hers = state.partnerPaid.single()

        assertEquals(SettleBrick("b1", "Food", "24 Nov · Food", "€100", 0, "+€50", true, partnerInitial = null, partnerColorIndex = 1), mine)
        assertEquals(
            SettleBrick("b2", "Fun", "24 Nov · Fun", "€20", 1, "−€10", false, partnerInitial = "A", partnerColorIndex = 1),
            hers,
        )
    }

    @Test
    fun aMonthlyBrickSaysSoAndASharedRefundFlipsItsHalf() = runTest(dispatcher) {
        val bricks = listOf(
            brick("bill", "home", 18_000, shared = true, recurring = true),
            brick("back", "food", 4_821, shared = true, refund = true),
        )
        val viewModel = viewModel(FakeTownGateway(townSnapshot(expenses = bricks)))
        advanceUntilIdle()
        val rows = viewModel.state.value.youPaid.associateBy { it.id }

        assertEquals("24 Nov · Home · Monthly", rows.getValue("bill").meta)
        assertEquals("+€90", rows.getValue("bill").shareText)
        assertEquals("−€48.21", rows.getValue("back").amount)
        assertEquals("−€24.10", rows.getValue("back").shareText)
        assertFalse(rows.getValue("back").shareOwedToYou)
    }

    @Test
    fun markAsSettledLeavesAllSquareAndWritesTodayOnce() = runTest(dispatcher) {
        val gateway = FakeTownGateway(townSnapshot(expenses = shared))
        val viewModel = viewModel(gateway)
        advanceUntilIdle()

        viewModel.onSettleClick()
        advanceUntilIdle()
        val state = viewModel.state.value

        assertEquals(listOf("settle:2026-11-24"), gateway.calls.filter { it.startsWith("settle") })
        assertEquals("All square", state.headline)
        assertTrue(state.allSquare)
        assertFalse(state.canSettle)
        assertFalse(state.canPing)
        assertEquals("Settled · 24 Nov", state.settleLabel)

        viewModel.onSettleClick()
        advanceUntilIdle()
        assertEquals(1, gateway.calls.count { it.startsWith("settle") })
    }

    @Test
    fun whenYouOweThereIsNoReminder() = runTest(dispatcher) {
        val gateway = FakeTownGateway(townSnapshot(expenses = listOf(brick("b1", "food", 6_000, paidBy = "ana", shared = true))))
        val viewModel = viewModel(gateway)
        advanceUntilIdle()

        assertEquals("You owe Ana ${formatExact(3_000, Currency.EUR)}", viewModel.state.value.headline)
        assertFalse(viewModel.state.value.canPing)
        viewModel.onPingClick()
        advanceUntilIdle()
    }

    @Test
    fun remindSharesTheSentenceWithTheAmount() = runTest(dispatcher) {
        val viewModel = viewModel(FakeTownGateway(townSnapshot(expenses = shared)))
        advanceUntilIdle()

        viewModel.onPingClick()

        assertEquals(
            SettleUpEffect.Ping("We are ${formatExact(4_000, Currency.EUR)} apart in Brick Town. Settle up?"),
            viewModel.effect.first(),
        )
    }

    @Test
    fun withoutAPartnerThereIsNothingToSettle() = runTest(dispatcher) {
        val viewModel = viewModel(FakeTownGateway(townSnapshot(members = listOf(member("you", "You", true, 0)), expenses = shared)))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.hasPartner)
        assertFalse(viewModel.state.value.canSettle)
    }
}
