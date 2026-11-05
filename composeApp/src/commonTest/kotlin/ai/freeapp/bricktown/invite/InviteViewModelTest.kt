package ai.freeapp.bricktown.invite

import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.domain.Invite
import ai.freeapp.bricktown.settings.FakeTownGateway
import ai.freeapp.bricktown.settings.member
import ai.freeapp.bricktown.settings.townSnapshot
import ai.freeapp.bricktown.town.TownResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InviteViewModelTest : ViewModelTest() {
    private val soloMembers = listOf(member("you", "You", true, 0))

    private fun viewModel(gateway: FakeTownGateway) = InviteViewModel(gateway, dispatcher, "https://town.test")

    private fun invite(code: String, created: String, expires: String, edit: Boolean = true, manage: Boolean = false) =
        Invite(code, Instant.parse(created), Instant.parse(expires), edit, manage)

    @Test
    fun anOwnerWithoutALinkGetsOneWithTheDefaultPermissions() = runTest(dispatcher) {
        val gateway = FakeTownGateway(townSnapshot(members = soloMembers))
        val viewModel = viewModel(gateway)
        advanceUntilIdle()

        assertEquals(1, gateway.calls.count { it == "createInvite" })
        assertEquals(true, gateway.lastInviteRequest?.canEdit)
        assertEquals(false, gateway.lastInviteRequest?.canManage)
        assertEquals("https://town.test/join/ABCD-2345", viewModel.state.value.link)
        assertEquals("expires in 7 days", viewModel.state.value.expires)
    }

    @Test
    fun aLiveInviteWithTheSamePermissionsIsReused() = runTest(dispatcher) {
        val gateway = FakeTownGateway(
            townSnapshot(members = soloMembers, invites = listOf(invite("KEEP-2222", "2026-11-23T10:00:00Z", "2026-11-30T10:00:00Z"))),
        )
        val viewModel = viewModel(gateway)
        advanceUntilIdle()

        assertTrue(gateway.calls.none { it == "createInvite" })
        assertEquals("https://town.test/join/KEEP-2222", viewModel.state.value.link)
    }

    @Test
    fun changingAPermissionAsksForAnInviteThatCarriesIt() = runTest(dispatcher) {
        val gateway = FakeTownGateway(townSnapshot(members = soloMembers))
        val viewModel = viewModel(gateway)
        advanceUntilIdle()

        viewModel.onCanManageChange(true)
        advanceUntilIdle()

        assertEquals(2, gateway.calls.count { it == "createInvite" })
        assertEquals(true, gateway.lastInviteRequest?.canManage)
    }

    @Test
    fun aFullTownShowsNoLinkAndMakesNoInvite() = runTest(dispatcher) {
        val gateway = FakeTownGateway(townSnapshot())
        val viewModel = viewModel(gateway)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.full)
        assertNull(viewModel.state.value.link)
        assertTrue(gateway.calls.none { it == "createInvite" })
    }

    @Test
    fun aMemberCannotInvite() = runTest(dispatcher) {
        val gateway = FakeTownGateway(townSnapshot(viewer = "ana", members = listOf(member("ana", "Ana", false, 1))))
        val viewModel = viewModel(gateway)
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isOwner)
        assertTrue(gateway.calls.none { it == "createInvite" })
    }

    @Test
    fun aFailedCreateShowsTheServerSentenceAndDoesNotLoop() = runTest(dispatcher) {
        val gateway = FakeTownGateway(townSnapshot(members = soloMembers))
        gateway.inviteResult = TownResult.Failed("You are offline.")
        val viewModel = viewModel(gateway)
        advanceUntilIdle()

        assertEquals("You are offline.", viewModel.state.value.message)
        assertEquals(1, gateway.calls.count { it == "createInvite" })
        assertNull(viewModel.state.value.link)

        viewModel.onShareClick()
        assertEquals("The link is not ready yet", viewModel.state.value.message)
    }

    @Test
    fun revokeRemovesTheInviteFromThePendingList() = runTest(dispatcher) {
        val gateway = FakeTownGateway(
            townSnapshot(
                members = soloMembers,
                invites = listOf(
                    invite("OLD1-2222", "2026-11-22T10:00:00Z", "2026-11-29T10:00:00Z", edit = false),
                    invite("KEEP-3333", "2026-11-23T10:00:00Z", "2026-11-30T10:00:00Z"),
                ),
            ),
        )
        val viewModel = viewModel(gateway)
        advanceUntilIdle()
        assertEquals("Invited 2 days ago · not joined yet", viewModel.state.value.pending.first { it.code == "OLD1-2222" }.age)

        viewModel.onRevokeClick("OLD1-2222")
        advanceUntilIdle()

        assertEquals(listOf("KEEP-3333"), viewModel.state.value.pending.map { it.code })
        assertEquals(listOf("KEEP-3333"), gateway.snapshot.invites.map { it.code })
    }

    @Test
    fun shareAndCopyHandTheLinkOut() = runTest(dispatcher) {
        val gateway = FakeTownGateway(townSnapshot(members = soloMembers))
        val viewModel = viewModel(gateway)
        advanceUntilIdle()

        viewModel.onShareClick()
        viewModel.onCopyClick()

        assertEquals(InviteEffect.Share("Join my town in Brick Town: https://town.test/join/ABCD-2345"), viewModel.effect.first())
    }

    @Test
    fun aJoinShowsWhileTheScreenIsOpen() = runTest(dispatcher) {
        val gateway = FakeTownGateway(
            townSnapshot(members = soloMembers, invites = listOf(invite("KEEP-2222", "2026-11-23T10:00:00Z", "2026-11-30T10:00:00Z"))),
        )
        val afterJoin = townSnapshot()
        var refreshes = 0
        val viewModel = InviteViewModel(gateway, dispatcher, "https://town.test") {
            refreshes++
            gateway.serverSends(afterJoin)
        }
        runCurrent()
        assertEquals(1, viewModel.state.value.pending.size)
        assertEquals(0, refreshes)

        advanceTimeBy(5_001)
        runCurrent()

        assertEquals(1, refreshes)
        assertTrue(viewModel.state.value.full)
        assertTrue(viewModel.state.value.pending.isEmpty())
        assertEquals(2, viewModel.state.value.members.size)
    }

    @Test
    fun theScreenDoesNotPollWithNothingPending() = runTest(dispatcher) {
        val gateway = FakeTownGateway(townSnapshot())
        var refreshes = 0
        InviteViewModel(gateway, dispatcher, "https://town.test") { refreshes++ }
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(0, refreshes)
    }
}
