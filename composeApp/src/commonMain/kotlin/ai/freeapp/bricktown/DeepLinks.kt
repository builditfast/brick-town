package ai.freeapp.bricktown

import ai.freeapp.bricktown.core.navigation.BackStack
import ai.freeapp.bricktown.town.InviteCodes
import ai.freeapp.bricktown.town.JoinCodeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * The `bricktown://` links the app opens: from the join web page, the nudge
 * and recap notifications, and the widgets.
 *  - `join/CODE` opens "Join {town}?"; a code that cannot be one opens the
 *    typed-code step instead.
 *  - `add` opens Add a brick, `month` the Month tab.
 *  - `recap/YYYY-MM-DD` opens the recap of the week starting that Monday.
 */
object DeepLinks {
    const val SCHEME = "bricktown"

    /** The screen [url] asks for, or null when it is not one of ours. */
    fun parse(url: String): Destination? {
        val prefix = "$SCHEME://"
        val trimmed = url.trim()
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return null
        val path = trimmed.substring(prefix.length).substringBefore('?').substringBefore('#').trim('/')
        val parts = path.split('/')
        return when (parts.first().lowercase()) {
            "join" -> when (parts.size) {
                1 -> Destination.Join()
                2 -> Destination.Join(InviteCodes.normalize(parts[1]) ?: "")
                else -> null
            }
            "add" -> Destination.AddBrick().takeIf { parts.size == 1 }
            "month" -> Destination.Month().takeIf { parts.size == 1 }
            "recap" -> parts.getOrNull(1)?.takeIf { parts.size == 2 }?.let(::mondayOrNull)?.let(Destination::Recap)
            else -> null
        }
    }

    private fun mondayOrNull(text: String): LocalDate? =
        runCatching { LocalDate.parse(text) }.getOrNull()?.takeIf { it.dayOfWeek == DayOfWeek.MONDAY }
}

/**
 * Holds the last link the platform delivered until `App` can open it: after
 * Start has picked the first screen, and only with a session. A join link that
 * arrives signed out is kept in [JoinCodeStore] instead, so it survives
 * sign-in; every other signed-out link is dropped.
 */
class DeepLinkInbox(private val joinCodes: JoinCodeStore) {
    private val _pending = MutableStateFlow<Destination?>(null)
    val pending: StateFlow<Destination?> = _pending.asStateFlow()

    /** From Android's VIEW intent and iOS's onOpenURL. A link that is not ours is ignored. */
    fun deliver(url: String) {
        DeepLinks.parse(url)?.let { _pending.value = it }
    }

    /** The link to open now, or null; either way the inbox is empty after. */
    fun take(signedIn: Boolean): Destination? {
        val link = _pending.getAndUpdate { null } ?: return null
        if (signedIn) return link
        if (link is Destination.Join && link.code.isNotEmpty()) joinCodes.write(link.code)
        return null
    }
}

/**
 * Opens a deep link over what is on screen. Month is the tab root; a join
 * keeps the screen under it so Back returns there; everything else opens
 * above Month, so Back from it lands on the town.
 */
internal fun BackStack<Destination>.openLink(link: Destination) {
    when (link) {
        is Destination.Month -> returnTo(link)
        is Destination.Join -> push(link)
        else -> showAbove(base = Destination.Month(), destination = link)
    }
}
