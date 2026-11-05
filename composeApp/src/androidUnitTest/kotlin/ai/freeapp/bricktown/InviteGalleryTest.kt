package ai.freeapp.bricktown

import ai.freeapp.bricktown.invite.InviteActions
import ai.freeapp.bricktown.invite.InviteAvatar
import ai.freeapp.bricktown.invite.InviteContent
import ai.freeapp.bricktown.invite.InviteUiState
import ai.freeapp.bricktown.invite.PendingInvite
import org.junit.Test

class InviteGalleryTest : Gallery1029() {
    private val state = InviteUiState(
        loaded = true,
        members = listOf(InviteAvatar("Y", 0)),
        link = "https://bricktown.app/join/8F2K-PENNY",
        expires = "expires in 7 days",
        pending = listOf(PendingInvite("8F2K-PENNY", "Invited 2 days ago · not joined yet")),
    )

    @Test
    fun invite() = shot("invite-6g") { InviteContent(state, InviteActions()) }

    @Test
    fun inviteQr() = shot("invite-6g-qr", heightDp = 1100) { InviteContent(state.copy(showQr = true), InviteActions()) }

    @Test
    fun full() = shot("invite-6g-full") {
        InviteContent(state.copy(members = listOf(InviteAvatar("Y", 0), InviteAvatar("A", 1)), full = true, link = null, pending = emptyList()), InviteActions())
    }
}
