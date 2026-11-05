package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.core.error.ErrorState
import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.core.ui.BaseViewModel
import ai.freeapp.bricktown.core.ui.NotificationAccess
import ai.freeapp.bricktown.core.ui.toMessage
import ai.freeapp.bricktown.designsystem.BACKSPACE
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.NUMPAD_DELETE
import ai.freeapp.bricktown.domain.MemberRole
import ai.freeapp.bricktown.domain.TownSnapshot
import ai.freeapp.bricktown.domain.closedMonths
import ai.freeapp.bricktown.domain.draftToCents
import ai.freeapp.bricktown.domain.exportCsv
import ai.freeapp.bricktown.domain.formatWhole
import ai.freeapp.bricktown.domain.numpadPress
import ai.freeapp.bricktown.domain.streakOf
import ai.freeapp.bricktown.domain.summarize
import ai.freeapp.bricktown.reminders.DailyReminder
import ai.freeapp.bricktown.reminders.ReminderTime
import ai.freeapp.bricktown.town.TownResult
import ai.freeapp.bricktown.wire.MemberUpdate
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

@Immutable
data class MemberRow(
    val userId: String,
    val name: String,
    val initial: String,
    val colorIndex: Int,
    val owner: Boolean,
    val isYou: Boolean,
    val canEdit: Boolean,
)

/** What is open above Settings. */
@Immutable
sealed interface SettingsSheet {
    data object None : SettingsSheet
    data class Budget(val draft: String) : SettingsSheet
    data object Payday : SettingsSheet
    data object Currency : SettingsSheet
    data object DeleteTown : SettingsSheet
    data object LeaveTown : SettingsSheet
}

@Immutable
data class TestToolsUi(val title: String, val rows: List<TestRow>)

@Immutable
data class SettingsUiState(
    val loaded: Boolean = false,
    val townSince: String = "",
    val townDays: Int = 0,
    val trophies: Int = 0,
    val budgetCents: Long = 0,
    val dailyCents: Long = 0,
    val payday: Int = 1,
    val currency: Currency = Currency.EUR,
    val billsFirst: Boolean = true,
    /** The viewer may change the budget, payday, currency and bills first. */
    val canManage: Boolean = false,
    val isOwner: Boolean = false,
    val towerColors: List<Int> = emptyList(),
    val towerCount: Int = 0,
    val capCount: Int = 0,
    val members: List<MemberRow> = emptyList(),
    /** The nudge and the recap switches read on only while the phone lets a notification show. */
    val nudgeOn: Boolean = false,
    val recapOn: Boolean = true,
    val notificationsAllowed: Boolean = true,
    val celebrationsOn: Boolean = true,
    val freezesLeft: Int = 0,
    /** Debug builds only: the Test tools group. Null in release, so no group. */
    val testTools: TestToolsUi? = null,
    val sheet: SettingsSheet = SettingsSheet.None,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val budgetText: String get() = formatWhole(budgetCents, currency)
    val dailyText: String get() = formatWhole(dailyCents, currency)

    /** The other member of a two-person town; null while the viewer is alone. Settle up is with them. */
    val partnerName: String? get() = members.firstOrNull { !it.isYou }?.name

    /** Spec F12: while the phone blocks notifications the nudge row asks for them instead. */
    val nudgeHint: String get() = if (notificationsAllowed) "20:00 · only if the day has no bricks" else "Allow notifications"
}

sealed interface SettingsEffect {
    data object AskNotificationPermission : SettingsEffect
    /** Hand [csv] to the share sheet as [fileName]. */
    data class ShareExport(val fileName: String, val csv: String) : SettingsEffect
    /** The Test tools row that shows last month's done screen. */
    data class OpenRollover(val monthStart: LocalDate) : SettingsEffect
}

val EveningNudgeTime = ReminderTime(20, 0)

/**
 * Settings (6a). Every edit is a local-first write through [TownGateway]; the
 * numpad draft for the budget stays here until Save so a half-typed number
 * never reaches the town. Permission checks are the town's own: a member
 * without the manage right sees the money rows read only.
 */
class SettingsViewModel(
    private val gateway: TownGateway,
    private val clock: TownClock,
    private val reminder: DailyReminder,
    private val prefs: PennyPrefs,
    private val recap: WeeklyRecapReminder,
    private val access: NotificationAccess,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val testTools: TestTools? = null,
) : BaseViewModel<SettingsUiState, SettingsEffect>(SettingsUiState(testTools = testTools?.let { TestToolsUi(it.title, it.rows) }), dispatcher) {

    private var snapshot: TownSnapshot? = null
    private val notificationsAllowed = MutableStateFlow(true)

    init {
        tryToCollect(
            flow = {
                combine(gateway.town, reminder.time, prefs.weeklyRecap, prefs.celebrations, notificationsAllowed) { a, b, c, d, e ->
                    Inputs(a, b != null && e, c && e, d, e)
                }
            },
            onEach = ::render,
            onError = { fail(it) },
        )
        onResume()
    }

    private class Inputs(val town: TownSnapshot?, val nudge: Boolean, val recap: Boolean, val celebrations: Boolean, val allowed: Boolean)

    /** Asks the phone again: the user may have changed notifications in its settings while away. */
    fun onResume() {
        tryToExecute(call = { access.allowed() }, onSuccess = { notificationsAllowed.value = it }, onError = ::fail)
    }

    private fun render(input: Inputs) {
        val town = input.town
        snapshot = town
        if (town == null) {
            updateState {
                it.copy(nudgeOn = input.nudge, recapOn = input.recap, celebrationsOn = input.celebrations, notificationsAllowed = input.allowed)
            }
            return
        }
        val settings = town.town.settings
        val today = clock.today(settings)
        val viewer = town.members.firstOrNull { it.userId == town.viewerId }
        val isOwner = viewer?.role == MemberRole.OWNER
        val zone = runCatching { TimeZone.of(settings.timeZone) }.getOrDefault(TimeZone.UTC)
        val since = town.town.createdAt.toLocalDateTime(zone).date
        val summary = summarize(town, today)
        updateState {
            it.copy(
                loaded = true,
                townSince = shortDate(since),
                townDays = since.daysUntil(today).coerceAtLeast(0),
                trophies = closedMonths(town, today).count { trophy -> trophy.underBudget },
                budgetCents = settings.monthlyBudgetCents,
                dailyCents = summary.dailyBudgetCents,
                payday = settings.payday,
                currency = settings.currency,
                billsFirst = settings.billsFirst,
                canManage = isOwner || viewer?.canManage == true,
                isOwner = isOwner,
                towerColors = town.categories.sortedBy { c -> c.position }.map { c -> c.colorIndex },
                towerCount = town.categories.size,
                capCount = town.categories.count { c -> c.capCents != null },
                members = town.members.map { m ->
                    MemberRow(m.userId, m.name, m.initial, m.colorIndex, m.role == MemberRole.OWNER, m.userId == town.viewerId, m.canEdit)
                },
                nudgeOn = input.nudge,
                recapOn = input.recap,
                celebrationsOn = input.celebrations,
                notificationsAllowed = input.allowed,
                freezesLeft = streakOf(town, today).freezesLeft,
            )
        }
    }

    fun onBudgetClick() {
        if (!state.value.canManage) return
        updateState { it.copy(sheet = SettingsSheet.Budget(draftFor(it.budgetCents))) }
    }

    fun onBudgetKey(key: Char) {
        val sheet = state.value.sheet as? SettingsSheet.Budget ?: return
        updateState { it.copy(sheet = SettingsSheet.Budget(numpadPress(sheet.draft, if (key == BACKSPACE) NUMPAD_DELETE else key))) }
    }

    fun onBudgetSave() {
        val sheet = state.value.sheet as? SettingsSheet.Budget ?: return
        val cents = draftToCents(sheet.draft)
        if (cents <= 0) {
            updateState { it.copy(message = "Pick a budget above zero.") }
            return
        }
        changeSettings { it.copy(monthlyBudgetCents = cents) }
        onDismissSheet()
    }

    fun onPaydayClick() {
        if (state.value.canManage) updateState { it.copy(sheet = SettingsSheet.Payday) }
    }

    fun onPaydayPick(day: Int) {
        if (day !in 1..31) return
        changeSettings { it.copy(payday = day) }
        onDismissSheet()
    }

    fun onCurrencyClick() {
        if (state.value.canManage) updateState { it.copy(sheet = SettingsSheet.Currency) }
    }

    fun onCurrencyPick(currency: Currency) {
        changeSettings { it.copy(currency = currency) }
        onDismissSheet()
    }

    fun onBillsFirstChange(on: Boolean) = changeSettings { it.copy(billsFirst = on) }

    /** Only the owner sets what a member may do, so the toggle is hidden for everyone else. */
    fun onMemberCanEditChange(userId: String, on: Boolean) {
        if (!state.value.isOwner) return
        launchWrite { gateway.updateMember(userId, MemberUpdate(canEdit = on)) }
    }

    private var asking: Notice? = null

    private enum class Notice { Nudge, Recap }

    fun onNudgeChange(on: Boolean) {
        if (on) askPermissionFor(Notice.Nudge) else reminder.turnOff()
    }

    private fun askPermissionFor(notice: Notice) {
        asking = notice
        sendEffect(SettingsEffect.AskNotificationPermission)
    }

    /** The answer to whichever switch asked: the nudge or the weekly recap. A refusal leaves that switch off. */
    fun onNotificationPermissionResult(granted: Boolean) {
        val notice = asking
        asking = null
        if (granted) {
            when (notice) {
                Notice.Recap -> recap.turnOn()
                else -> reminder.turnOn(EveningNudgeTime)
            }
        } else {
            updateState { it.copy(message = "Notifications are off for Brick Town. Turn them on in your phone's settings.") }
        }
        onResume()
    }

    fun onRecapChange(on: Boolean) {
        if (on) askPermissionFor(Notice.Recap) else recap.turnOff()
    }

    fun onCelebrationsChange(on: Boolean) = prefs.setCelebrations(on)

    fun onTestToolClick(rowId: String) {
        val tools = testTools ?: return
        tryToExecute(
            call = { tools.run(rowId) },
            onSuccess = { result ->
                when (result) {
                    is TestToolResult.Message -> updateState { it.copy(message = result.text) }
                    is TestToolResult.OpenRollover -> sendEffect(SettingsEffect.OpenRollover(result.monthStart))
                }
            },
            onError = ::fail,
        )
    }

    fun onExportClick() {
        val town = snapshot ?: return
        val today = clock.today(town.town.settings)
        sendEffect(SettingsEffect.ShareExport("brick-town-$today.csv", exportCsv(town)))
    }

    fun onDeleteTownClick() {
        if (state.value.isOwner) updateState { it.copy(sheet = SettingsSheet.DeleteTown) }
    }

    fun onLeaveTownClick() {
        if (!state.value.isOwner) updateState { it.copy(sheet = SettingsSheet.LeaveTown) }
    }

    fun onConfirmDeleteTown() = endTown { gateway.deleteTown() }

    fun onConfirmLeaveTown() = endTown { gateway.leave() }

    fun onDismissSheet() = updateState { it.copy(sheet = SettingsSheet.None) }

    fun onMessageShown() = updateState { it.copy(message = null) }

    private fun endTown(call: suspend () -> TownResult<Unit>) {
        updateState { it.copy(busy = true) }
        tryToExecute(
            call = call,
            onSuccess = { result ->
                when (result) {
                    // The town row is gone, so the app's redirect moves the user to Start by itself.
                    is TownResult.Ok -> updateState { it.copy(busy = false, sheet = SettingsSheet.None) }
                    is TownResult.Failed -> updateState { it.copy(busy = false, sheet = SettingsSheet.None, message = result.message) }
                }
            },
            onError = { error -> updateState { it.copy(busy = false, sheet = SettingsSheet.None, message = error.toMessage()) } },
        )
    }

    private fun changeSettings(edit: (ai.freeapp.bricktown.domain.TownSettings) -> ai.freeapp.bricktown.domain.TownSettings) {
        if (!state.value.canManage) return
        launchWrite {
            val latest = gateway.town.first()?.town?.settings ?: return@launchWrite
            gateway.putSettings(edit(latest))
        }
    }

    private fun launchWrite(write: suspend () -> Unit) {
        tryToExecute(write, onSuccess = ::written, onError = ::fail)
    }

    private fun written(ignored: Unit) = Unit

    private fun fail(error: ErrorState) = updateState { it.copy(message = error.toMessage()) }

    private fun draftFor(cents: Long): String = if (cents % 100L == 0L) (cents / 100).toString() else "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    private fun shortDate(date: LocalDate): String = "${date.dayOfMonth} ${monthShort(date.month)}"

    private fun monthShort(month: Month): String = month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
}
