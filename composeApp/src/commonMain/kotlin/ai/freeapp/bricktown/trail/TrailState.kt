package ai.freeapp.bricktown.trail

import ai.freeapp.bricktown.domain.BadgeState
import androidx.compose.runtime.Immutable

enum class TrailNodeKind { Earned, Next, Locked }

@Immutable
data class TrailNodeUi(val id: String, val title: String, val subtitle: String, val kind: TrailNodeKind)

@Immutable
data class TrailUiState(
    val ready: Boolean = false,
    val penny: String = "",
    val nodes: List<TrailNodeUi> = emptyList(),
)

/** The first badge without an earn date is the next one; every later one is locked. */
internal fun trailUiState(badges: List<BadgeState>): TrailUiState {
    val nextIndex = badges.indexOfFirst { it.earnedOn == null }
    val earned = badges.count { it.earnedOn != null }
    val nextProgress = badges.getOrNull(nextIndex)?.progress
    val line = buildString {
        append("Your trail · $earned of ${badges.size}")
        when {
            nextIndex < 0 -> append(" · every badge is yours")
            nextProgress != null -> append(" · next one is $nextProgress")
        }
    }
    return TrailUiState(
        ready = true,
        penny = line,
        nodes = badges.mapIndexed { i, b ->
            TrailNodeUi(
                id = b.id.name,
                title = b.id.title,
                subtitle = b.subtitle,
                kind = when {
                    b.earnedOn != null -> TrailNodeKind.Earned
                    i == nextIndex -> TrailNodeKind.Next
                    else -> TrailNodeKind.Locked
                },
            )
        },
    )
}
