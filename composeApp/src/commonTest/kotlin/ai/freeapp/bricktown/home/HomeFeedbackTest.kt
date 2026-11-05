package ai.freeapp.bricktown.home

import ai.freeapp.bricktown.month.FakeLedger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeFeedbackTest {
    @Test
    fun anAddedBrickAsksForOneCelebration() {
        val feedback = HomeFeedback(FakeLedger(null)) { true }
        feedback.brickAdded("e1", "+1 brick")
        assertTrue(feedback.state.value.celebrate)
        assertEquals(1, feedback.state.value.burst)
    }

    @Test
    fun aPlayedCelebrationIsNotPlayedAgainOnReturnToHome() {
        val feedback = HomeFeedback(FakeLedger(null)) { true }
        feedback.brickAdded("e1", "+1 brick")
        feedback.consumeCelebration()
        assertFalse(feedback.state.value.celebrate)
        assertEquals(1, feedback.state.value.burst)
    }

    @Test
    fun celebrationsOffMeansNoConfetti() {
        val feedback = HomeFeedback(FakeLedger(null)) { false }
        feedback.brickAdded("e1", "+1 brick")
        assertFalse(feedback.state.value.celebrate)
        assertEquals(0, feedback.state.value.burst)
    }
}
