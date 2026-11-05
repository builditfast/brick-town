package ai.freeapp.bricktown

import ai.freeapp.bricktown.core.navigation.BackStack
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinksTest {

    @Test
    fun eachLinkOpensItsScreen() {
        assertEquals(Destination.Join("8F2K-P3NY"), DeepLinks.parse("bricktown://join/8F2K-P3NY"))
        assertEquals(Destination.Join("8F2K-P3NY"), DeepLinks.parse("bricktown://join/8f2kp3ny?from=web"))
        assertEquals(Destination.Join(), DeepLinks.parse("bricktown://join"))
        assertEquals(Destination.AddBrick(), DeepLinks.parse("bricktown://add"))
        assertEquals(Destination.Month(), DeepLinks.parse("BRICKTOWN://month/"))
        assertEquals(Destination.Recap(LocalDate(2026, 11, 16)), DeepLinks.parse("bricktown://recap/2026-11-16"))
    }

    @Test
    fun aCodeThatCannotBeOneOpensTheTypedCodeStep() {
        assertEquals(Destination.Join(), DeepLinks.parse("bricktown://join/NOT-ACODE1"))
    }

    @Test
    fun linksThatAreNotOursAreIgnored() {
        assertNull(DeepLinks.parse("https://example.com/join/8F2K-P3NY"))
        assertNull(DeepLinks.parse("bricktown://settings"))
        assertNull(DeepLinks.parse("bricktown://add/extra"))
        assertNull(DeepLinks.parse("bricktown://join/8F2K-P3NY/extra"))
    }

    @Test
    fun aRecapLinkNeedsAMonday() {
        // 2026-11-17 is a Tuesday: a recap week always starts on Monday.
        assertNull(DeepLinks.parse("bricktown://recap/2026-11-17"))
        assertNull(DeepLinks.parse("bricktown://recap/not-a-date"))
        assertNull(DeepLinks.parse("bricktown://recap"))
    }

    @Test
    fun aSignedInLinkIsTakenOnce() {
        val inbox = DeepLinkInbox(MemoryJoinCodeStore())
        inbox.deliver("bricktown://add")

        assertEquals(Destination.AddBrick(), inbox.take(signedIn = true))
        assertNull(inbox.take(signedIn = true))
        assertNull(inbox.pending.value)
    }

    @Test
    fun aJoinLinkOpenedSignedOutIsKeptForAfterSignIn() {
        val joinCodes = MemoryJoinCodeStore()
        val inbox = DeepLinkInbox(joinCodes)
        inbox.deliver("bricktown://join/8F2K-P3NY")

        assertNull(inbox.take(signedIn = false))
        assertEquals("8F2K-P3NY", joinCodes.read())
        assertNull(inbox.pending.value)
    }

    @Test
    fun anyOtherLinkOpenedSignedOutIsDropped() {
        val joinCodes = MemoryJoinCodeStore()
        val inbox = DeepLinkInbox(joinCodes)
        inbox.deliver("bricktown://add")

        assertNull(inbox.take(signedIn = false))
        assertNull(joinCodes.read())
        assertNull(inbox.pending.value)
    }

    @Test
    fun aLinkThatIsNotOursLeavesTheInboxAsItWas() {
        val inbox = DeepLinkInbox(MemoryJoinCodeStore())
        inbox.deliver("bricktown://month")
        inbox.deliver("https://example.com")

        assertEquals(Destination.Month(), inbox.pending.value)
    }

    private fun stackOn(vararg screens: Destination) =
        BackStack(root = screens.first(), onRemoved = {}).apply { screens.drop(1).forEach(::push) }

    @Test
    fun aLinkOpensAboveMonthSoBackLandsOnTheTown() {
        val stack = stackOn(Destination.Month(), Destination.Settings, Destination.Invite)

        stack.openLink(Destination.Recap(LocalDate(2026, 11, 16)))

        assertEquals(listOf(Destination.Month(), Destination.Recap(LocalDate(2026, 11, 16))), stack.destinations)
    }

    @Test
    fun aMonthLinkReturnsToTheTabRoot() {
        val stack = stackOn(Destination.Month(), Destination.History)

        stack.openLink(Destination.Month())

        assertEquals(listOf<Destination>(Destination.Month()), stack.destinations)
    }

    @Test
    fun aJoinLinkKeepsTheScreenUnderIt() {
        val stack = stackOn(Destination.Month(), Destination.Settings)

        stack.openLink(Destination.Join("8F2K-P3NY"))

        assertEquals(listOf(Destination.Month(), Destination.Settings, Destination.Join("8F2K-P3NY")), stack.destinations)
    }
}
