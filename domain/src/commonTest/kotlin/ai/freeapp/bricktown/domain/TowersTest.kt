package ai.freeapp.bricktown.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TowersTest {
    private val cats = categories()

    @Test fun nameIsRequired() {
        assertEquals(TowerProblem.NameRequired, checkTowerName("   ", cats, null))
        assertEquals(TowerProblem.NameRequired, checkTowerName("", cats, null))
    }

    @Test fun nameOverTheLimitIsTooLongNotMissing() {
        assertEquals(null, checkTowerName("a".repeat(MAX_TOWER_NAME), cats, null))
        assertEquals(TowerProblem.NameTooLong, checkTowerName("a".repeat(MAX_TOWER_NAME + 1), cats, null))
    }

    @Test fun nameMustBeUniqueIgnoringCaseAndSpaces() {
        assertEquals(TowerProblem.NameTaken, checkTowerName(" groceries ", cats, null))
        assertNull(checkTowerName("Pets", cats, null))
    }

    @Test fun renamingATowerToItsOwnNameIsFine() {
        assertNull(checkTowerName("Groceries", cats, "groceries"))
        assertEquals(TowerProblem.NameTaken, checkTowerName("Home", cats, "groceries"))
    }

    @Test fun deleteRules() {
        assertEquals(TowerProblem.MergeIntoItself, checkTowerDelete("home", "home", cats))
        assertEquals(TowerProblem.LastTower, checkTowerDelete("home", null, cats.take(1)))
        assertNull(checkTowerDelete("home", "groceries", cats))
        assertNull(checkTowerDelete("home", null, cats))
    }

    @Test fun nextColorWrapsAtEight() {
        assertEquals(6, nextColorIndex(cats))
        assertEquals(0, nextColorIndex(cats + cats.take(2)))
    }

    @Test fun searchMatchesNoteOrTowerNameNewestFirst() {
        val snap = fixture5b()
        val lidl = searchBricks(snap, "lidl")
        assertTrue(lidl.isNotEmpty() && lidl.all { it.note.contains("Lidl", ignoreCase = true) })
        assertEquals(lidl.sortedByDescending { it.date }.map { it.date }, lidl.map { it.date })
        assertEquals(31 + 13, searchBricks(snap, "").size)
        val subs = searchBricks(snap, "subscr")
        assertEquals(10, subs.size)
        assertTrue(searchBricks(snap, "zzz").isEmpty())
    }

    @Test fun shortNamesAreFiveCharactersAtMost() {
        assertTrue(shortNameFor("Pets and vets").length <= 5)
        assertEquals("Pets", shortNameFor("Pets"))
    }
}
