package ai.freeapp.bricktown

import ai.freeapp.bricktown.town.SyncStatus
import kotlinx.datetime.LocalDate

/**
 * Every screen the app can show. `App` maps each one to its composable.
 *
 * EXTENSION POINT: add a destination per screen, then a branch in `App`'s
 * `Screen` and a `backStack.push(destination = Destination.X)` where it opens.
 * A screen someone without an account can see implements [SignedOut]; a
 * screen that reads the town implements [NeedsTown].
 */
sealed interface Destination {

    /** Screens shown without a session. Every other destination needs one. */
    sealed interface SignedOut : Destination

    /** Screens that show the town. With no town on the account they give way to [Onboarding]. */
    sealed interface NeedsTown : Destination

    /** Launch with a stored session: checks it and the town with the server, then picks the first screen. */
    data object Start : Destination

    /** The sky with Penny, "Sign in" and "Create account": the root without a session. */
    data object Landing : SignedOut
    data object SignIn : SignedOut
    data object CreateAccount : SignedOut

    /**
     * Creating a town: budget, towers, first brick, then Welcome. Not
     * [NeedsTown]: the town appears in the middle of it, and Welcome still shows.
     */
    data object Onboarding : Destination

    /** "Join {town}?" for an invite [code]; empty when the user types one. Works with or without a town. */
    data class Join(val code: String = "") : Destination

    /** The main tab. [joinedTown] is the town just joined: Month greets "Welcome to {town}." once. */
    data class Month(val joinedTown: String? = null) : NeedsTown
    data object History : NeedsTown
    data object Compare : NeedsTown
    data object Trail : NeedsTown

    /** Add a brick, with [categoryId]'s tower picked when it opens from that tower's sheet. */
    data class AddBrick(val categoryId: String? = null) : NeedsTown
    data class EditBrick(val expenseId: String) : NeedsTown

    /** The weekly recap of the ISO week starting [weekStart] (a Monday). */
    data class Recap(val weekStart: LocalDate) : NeedsTown

    /** The trophy for the pay month that started on [monthStart], shown once when it ends. */
    data class Rollover(val monthStart: LocalDate) : NeedsTown

    data object Settings : NeedsTown
    data object ManageTowers : NeedsTown
    data object Invite : NeedsTown
    data object SettleUp : NeedsTown
    data object Import : NeedsTown

    data object ChangePassword : Destination
}

/**
 * Where the stack goes with this screen on top, or null when it stays. `App`
 * resets the whole stack to the answer whenever the session or the town
 * changes.
 *  - A session that ended leaves every screen that needs one for [Destination.Landing].
 *  - A session that started leaves the signed-out screens for [Destination.Start].
 *  - An account with no town (none yet, left, or deleted by its owner) leaves
 *    every town screen for [Destination.Onboarding].
 */
fun Destination.redirectFor(signedIn: Boolean, town: SyncStatus): Destination? = when {
    !signedIn && this !is Destination.SignedOut -> Destination.Landing
    signedIn && this is Destination.SignedOut -> Destination.Start
    signedIn && this is Destination.NeedsTown && town.meansNoTown -> Destination.Onboarding
    else -> null
}

private val SyncStatus.meansNoTown: Boolean
    get() = this == SyncStatus.NoTown || this == SyncStatus.TownDeleted
