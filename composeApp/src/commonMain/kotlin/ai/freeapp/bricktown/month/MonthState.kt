package ai.freeapp.bricktown.month

import ai.freeapp.bricktown.brick.amountText
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.Expense
import ai.freeapp.bricktown.domain.GhostBill
import ai.freeapp.bricktown.domain.PennyMood
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.bricksFor
import ai.freeapp.bricktown.domain.formatWhole
import ai.freeapp.bricktown.domain.summarize
import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

@Immutable
data class MemberUi(val initial: String, val colorIndex: Int)

/** One brick in a tower's sheet. */
@Immutable
data class BrickRowUi(
    val id: String,
    val dateTile: String,
    val note: String,
    val monthly: Boolean,
    /** The partner's initial when they paid, else null. */
    val partnerInitial: String?,
    val partnerColorIndex: Int,
    val amountText: String,
    val refund: Boolean,
)

/** A bill pencilled in for this month, not yet a brick. */
@Immutable
data class GhostUi(
    val seriesKey: String,
    val categoryId: String,
    val title: String,
    val amountCents: Long,
    val amountText: String,
    val dueDate: LocalDate,
    val dueText: String,
)

@Immutable
data class TowerUi(
    val id: String,
    val name: String,
    val label: String,
    val colorIndex: Int,
    val bricks: Int,
    val capBricks: Int,
    val overCapBricks: Int,
    val ghostBricks: Int,
    val capCents: Long?,
    val totalText: String,
    val rows: List<BrickRowUi>,
    val ghosts: List<GhostUi>,
    /** "Over by €48" when above the cap, else null. */
    val overText: String?,
)

@Immutable
data class MonthUiState(
    val ready: Boolean = false,
    val currency: Currency = Currency.EUR,
    val monthLine: String = "",
    val pennyLine: String = "",
    val mood: PennyMood = PennyMood.NEUTRAL,
    val members: List<MemberUi> = emptyList(),
    val activity: String? = null,
    val spentText: String = "",
    val billsLine: String = "",
    val todayAmountText: String = "",
    val todayOfText: String = "",
    val monthLeftText: String = "",
    val fresh: Boolean = false,
    val freshHint: String = "",
    val ghostExplainerTitle: String = "",
    val ghostExplainerBody: String = "",
    val brickHeight: Int = 13,
    val towers: List<TowerUi> = emptyList(),
    /** The tower whose sheet is open, by id. */
    val openTowerId: String? = null,
    /** The pencilled bill whose small sheet is open, by series key. */
    val openGhostKey: String? = null,
    /** The bounce-in has played once in this ViewModel's life; coming back to the tab shows the towers still. */
    val towersPlayed: Boolean = false,
) {
    val openTower: TowerUi? get() = towers.firstOrNull { it.id == openTowerId }
    val openGhost: GhostUi? get() = openTower?.ghosts?.firstOrNull { it.seriesKey == openGhostKey }
}


private val MonthShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

internal fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${MonthShort[date.monthNumber - 1]}"

private fun monthTitle(month: Month): String = month.name.lowercase().replaceFirstChar { it.uppercase() }

private fun ghostTitle(ghost: GhostBill, categoryName: String): String = ghost.note.ifBlank { categoryName }

/** Pure mapping from the synced town to what Month draws. The money and brick maths stay in the domain. */
internal fun monthUiState(town: TownSnapshot, today: LocalDate): MonthUiState {
    val s = summarize(town, today)
    val currency = town.town.settings.currency
    val members = town.members.sortedBy { it.joinedAt }
    val names = town.categories.associate { it.id to it.name }
    val towers = s.towers.map { t ->
        val over = t.overCapCents
        val capBricks = if (t.bricks > 0) t.capBricks ?: 0 else 0
        val bricks = t.bricks
        val ghostCents = t.ghostBills.sumOf { it.amountCents }
        val rows = town.expenses
            .filter { it.categoryId == t.category.id && it.date in s.month }
            .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })
            .map { e ->
                val payer = members.firstOrNull { it.userId == e.paidBy }
                val partner = if (e.paidBy != town.viewerId) payer else null
                BrickRowUi(
                    id = e.id,
                    dateTile = shortDate(e.date),
                    note = e.note.ifBlank { t.category.name },
                    monthly = e.recurring,
                    partnerInitial = partner?.initial,
                    partnerColorIndex = partner?.colorIndex ?: 0,
                    amountText = (if (e.refund) "−" else "") + amountText(e.amountCents, currency),
                    refund = e.refund,
                )
            }
        TowerUi(
            id = t.category.id,
            name = t.category.name,
            label = t.category.short,
            colorIndex = t.category.colorIndex,
            bricks = bricks,
            capBricks = capBricks,
            overCapBricks = if (over != null && capBricks > 0) (bricks - capBricks).coerceAtLeast(0) else 0,
            ghostBricks = bricksFor(ghostCents),
            capCents = t.category.capCents,
            totalText = formatWhole(t.totalCents, currency),
            rows = rows,
            ghosts = t.ghostBills.map { g ->
                GhostUi(
                    seriesKey = g.seriesKey,
                    categoryId = g.categoryId,
                    title = ghostTitle(g, names[g.categoryId].orEmpty()),
                    amountCents = g.amountCents,
                    amountText = amountText(g.amountCents, currency),
                    dueDate = g.dueDate,
                    dueText = "due ${shortDate(g.dueDate)}",
                )
            },
            overText = over?.let { "Over by ${amountText(it, currency)}" },
        )
    }
    val partner = s.partnerLatest
    val partnerMember = partner?.let { p -> members.firstOrNull { it.userId == p.paidBy } }
    val activity = partner?.let { p ->
        val who = partnerMember?.name ?: "Partner"
        val what = p.note.ifBlank { names[p.categoryId].orEmpty() }
        "$who added $what · ${amountText(p.amountCents, currency)}"
    }
    val fresh = s.fresh
    val daily = formatWhole(s.dailyBudgetCents, currency)
    val line = if (s.todayLeftCents >= 0) {
        "${amountText(s.todayLeftCents, currency)} left today · bills don't count"
    } else {
        "Over for today. Tomorrow resets."
    }
    return MonthUiState(
        ready = true,
        currency = currency,
        monthLine = "${monthTitle(s.month.name)} · day ${s.dayNumber}",
        pennyLine = if (fresh) "Fresh town. $daily a day to play with." else line,
        mood = s.mood,
        members = members.map { MemberUi(it.initial, it.colorIndex) },
        activity = activity,
        spentText = formatWhole(s.spentCents, currency),
        billsLine = "${formatWhole(s.billsCents + if (fresh) s.pencilledCents else 0L, currency)} bills" +
            (if (fresh) " pencilled in" else "") + " · ${formatWhole(s.movingCents, currency)} moving",
        todayAmountText = amountText(s.todayLeftCents, currency),
        todayOfText = if (fresh) "of $daily\ntoday" else "of $daily today",
        monthLeftText = formatWhole(s.monthLeftCents, currency),
        fresh = fresh,
        freshHint = "Lay the first brick of ${monthTitle(s.month.name)} with +",
        ghostExplainerTitle = "Ghost bricks are bills on their way",
        ghostExplainerBody = ghostExplainer(towers),
        brickHeight = s.brickHeight,
        towers = towers,
    )
}

/** "Electricity, Metro pass and 5 subscriptions land on their usual days. Tap one to confirm or skip." */
internal fun ghostExplainer(towers: List<TowerUi>): String {
    val parts = mutableListOf<String>()
    towers.forEach { t ->
        when {
            t.ghosts.size >= 3 -> parts += "${t.ghosts.size} ${t.name.lowercase()}"
            else -> parts += t.ghosts.map { it.title }
        }
    }
    val names = when {
        parts.isEmpty() -> "Bills"
        parts.size == 1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
    return "$names land on their usual days. Tap one to confirm or skip."
}
