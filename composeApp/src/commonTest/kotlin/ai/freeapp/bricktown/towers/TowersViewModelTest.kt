package ai.freeapp.bricktown.towers

import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.settings.FakeTownGateway
import ai.freeapp.bricktown.settings.brick
import ai.freeapp.bricktown.settings.fixtureClock
import ai.freeapp.bricktown.settings.member
import ai.freeapp.bricktown.settings.townSnapshot
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TowersViewModelTest : ViewModelTest() {
    private val gateway = FakeTownGateway(
        townSnapshot(
            expenses = listOf(
                brick("b1", "food", 5_000),
                brick("b2", "food", 5_000),
                brick("b3", "fun", 2_500),
            ),
        ),
    )
    private var ids = 0

    private fun viewModel() = TowersViewModel(gateway, fixtureClock(), dispatcher) { "new-${ids++}" }

    private val TownSnapshot.towerNames get() = categories.sortedBy { it.position }.map { it.name }

    @Test
    fun listsTowersWithBricksAndCap() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val rows = viewModel.state.value.rows

        assertEquals(listOf("Food", "Fun", "Home"), rows.map { it.name })
        assertEquals("4 bricks · cap €250", rows[0].meta.replace(' ', ' ').replace(' ', ' '))
        assertEquals("1 brick · no cap", rows[1].meta)
    }

    @Test
    fun nameRulesSurfaceAsCopy() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNewTowerClick()
        viewModel.onEditorSave()
        assertEquals("Give the tower a name", viewModel.state.value.editor?.error)

        viewModel.onNameChange("x".repeat(25))
        viewModel.onEditorSave()
        assertEquals("Keep it to 24 characters", viewModel.state.value.editor?.error)

        viewModel.onNameChange("  fun ")
        viewModel.onEditorSave()
        assertEquals("You already have a tower with that name", viewModel.state.value.editor?.error)
        advanceUntilIdle()
        assertTrue(gateway.calls.none { it.startsWith("putCategory") })
    }

    @Test
    fun newTowerTakesTheNextColourAndPosition() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNewTowerClick()
        assertEquals(3, viewModel.state.value.editor?.colorIndex)
        viewModel.onNameChange("Pets")
        viewModel.onCapPick(15_000L)
        viewModel.onEditorSave()
        advanceUntilIdle()

        val added = gateway.snapshot.categories.first { it.id == "new-0" }
        assertEquals("Pets", added.name)
        assertEquals(15_000L, added.capCents)
        assertEquals(3, added.position)
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun editingRenamesRecoloursAndSetsACustomCap() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onTowerClick("fun")
        viewModel.onNameChange("Going out")
        viewModel.onColorPick(5)
        viewModel.onCustomCapClick()
        viewModel.onCustomCapChange("320")
        viewModel.onEditorSave()
        advanceUntilIdle()

        val edited = gateway.snapshot.categories.first { it.id == "fun" }
        assertEquals("Going out", edited.name)
        assertEquals(5, edited.colorIndex)
        assertEquals(32_000L, edited.capCents)
    }

    @Test
    fun reorderWritesOnceWhenTheDragEnds() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onMove(0, 1)
        viewModel.onMove(1, 2)
        advanceUntilIdle()
        assertTrue(gateway.calls.none { it.startsWith("reorder") })
        assertEquals(listOf("Fun", "Home", "Food"), viewModel.state.value.rows.map { it.name })

        viewModel.onMoveEnd()
        advanceUntilIdle()
        assertEquals(listOf("Fun", "Home", "Food"), gateway.snapshot.towerNames)
    }

    @Test
    fun mergeMovesTheBricksAndTheMergedCapGoesWithIt() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onMergeClick("food")
        val prompt = assertNotNull(viewModel.state.value.prompt)
        assertEquals(listOf("Fun", "Home"), prompt.targets.map { it.name })

        viewModel.onMoveTargetPick("fun")
        advanceUntilIdle()

        val after = gateway.snapshot
        assertEquals(listOf("Fun", "Home"), after.towerNames)
        assertEquals(3, after.expenses.count { it.categoryId == "fun" })
        assertTrue(after.categories.none { it.capCents != null })
        assertNull(viewModel.state.value.prompt)
    }

    @Test
    fun deletingATowerWithBricksAsksWhereTheyGo() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDeleteClick("fun")
        assertNotNull(viewModel.state.value.prompt)
        assertTrue(gateway.calls.none { it.startsWith("deleteCategory") })

        viewModel.onPromptDismiss()
        assertNull(viewModel.state.value.prompt)
    }

    @Test
    fun deletingAnEmptyTowerGoesAtOnce() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDeleteClick("home")
        advanceUntilIdle()

        assertEquals(listOf("Food", "Fun"), gateway.snapshot.towerNames)
        assertNull(viewModel.state.value.prompt)
    }

    @Test
    fun theLastTowerCannotGo() = runTest(dispatcher) {
        val lone = FakeTownGateway(townSnapshot(categories = listOf(ai.freeapp.bricktown.settings.tower("only", "Only", 0))))
        val viewModel = TowersViewModel(lone, fixtureClock(), dispatcher) { "x" }
        advanceUntilIdle()

        viewModel.onDeleteClick("only")
        assertEquals("You need at least one tower", viewModel.state.value.message)
        advanceUntilIdle()
        assertTrue(lone.calls.none { it.startsWith("deleteCategory") })
    }

    @Test
    fun aMemberWithoutTheManageRightCannotEdit() = runTest(dispatcher) {
        val readOnly = FakeTownGateway(
            townSnapshot(viewer = "ana", members = listOf(member("you", "You", true, 0), member("ana", "Ana", false, 1))),
        )
        val viewModel = TowersViewModel(readOnly, fixtureClock(), dispatcher) { "x" }
        advanceUntilIdle()

        viewModel.onTowerClick("food")
        viewModel.onNewTowerClick()
        viewModel.onDeleteClick("home")
        viewModel.onMove(0, 1)
        advanceUntilIdle()

        assertNull(viewModel.state.value.editor)
        assertTrue(readOnly.calls.isEmpty())
    }
}
