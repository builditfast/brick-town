package ai.freeapp.bricktown

import ai.freeapp.bricktown.towers.TowerEditor
import ai.freeapp.bricktown.towers.TowerMovePrompt
import ai.freeapp.bricktown.towers.TowerRow
import ai.freeapp.bricktown.towers.TowersActions
import ai.freeapp.bricktown.towers.TowersContent
import ai.freeapp.bricktown.towers.TowersUiState
import org.junit.Test

class TowersGalleryTest : Gallery1029() {
    private val rows = listOf(
        TowerRow("g", "Groceries", 0, 14, null, 0, "14 bricks · no cap"),
        TowerRow("e", "Eating out", 1, 12, 25_000, 0, "12 bricks · cap €250"),
        TowerRow("h", "Home", 2, 11, null, 2, "11 bricks · 2 monthly bills"),
        TowerRow("t", "Transport", 4, 4, null, 0, "4 bricks"),
        TowerRow("s", "Subscriptions", 5, 3, null, 5, "3 bricks · 5 monthly bills"),
    )
    private val state = TowersUiState(loaded = true, canManage = true, rows = rows)

    @Test
    fun list() = shot("towers-6f") { TowersContent(state, TowersActions()) }

    @Test
    fun editor() = shot("towers-6f-edit") {
        TowersContent(state.copy(editor = TowerEditor("e", "Eating out", 1, 25_000)), TowersActions())
    }

    @Test
    fun mergeSheet() = shot("towers-6f-merge") {
        TowersContent(state.copy(prompt = TowerMovePrompt("e", "Eating out", 12, false, rows.filter { it.id != "e" })), TowersActions())
    }
}
