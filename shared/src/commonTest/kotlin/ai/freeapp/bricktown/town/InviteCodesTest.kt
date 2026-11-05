package ai.freeapp.bricktown.town

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteCodesTest {

    @Test
    fun aCodeAsTheInviteShowsItStaysTheSame() {
        assertEquals("8F2K-P3NY", InviteCodes.normalize("8F2K-P3NY"))
    }

    @Test
    fun aTypedCodeIsBroughtToTheShape() {
        assertEquals("8F2K-P3NY", InviteCodes.normalize(" 8f2k p3ny "))
        assertEquals("8F2K-P3NY", InviteCodes.normalize("8f2kp3ny"))
    }

    @Test
    fun lettersThatAreNeverInACodeAreRefused() {
        // O, I, 0 and 1 are left out of the alphabet, so a code read aloud is never ambiguous.
        assertNull(InviteCodes.normalize("8F2K-PENO"))
        assertNull(InviteCodes.normalize("8F2K-P1NY"))
        assertNull(InviteCodes.normalize("8F2K-P0NY"))
    }

    @Test
    fun theWrongLengthIsRefused() {
        assertNull(InviteCodes.normalize("8F2K-P3N"))
        assertNull(InviteCodes.normalize("8F2K-P3NYZ"))
        assertNull(InviteCodes.normalize(""))
    }
}
