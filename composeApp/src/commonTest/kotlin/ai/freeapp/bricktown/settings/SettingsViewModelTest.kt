package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.MemoryReminderStore
import ai.freeapp.bricktown.MemoryTokenStore
import ai.freeapp.bricktown.RecordingReminderScheduler
import ai.freeapp.bricktown.ViewModelTest
import ai.freeapp.bricktown.core.auth.AuthTokens
import ai.freeapp.bricktown.core.auth.ObservableTokenStore
import ai.freeapp.bricktown.core.ui.NotificationAccess
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.domain.formatWhole
import ai.freeapp.bricktown.domain.formatWhole
import ai.freeapp.bricktown.reminders.DailyReminder
import ai.freeapp.bricktown.town.TownResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakePennyPrefs : PennyPrefs {
    override val weeklyRecap = MutableStateFlow(true)
    override val celebrations = MutableStateFlow(true)
    override fun setWeeklyRecap(on: Boolean) { weeklyRecap.value = on }
    override fun setCelebrations(on: Boolean) { celebrations.value = on }
}

class FakeNotificationAccess(var on: Boolean = true) : NotificationAccess {
    override suspend fun allowed() = on
}

class SettingsViewModelTest : ViewModelTest() {
    private val gateway = FakeTownGateway()
    private val scheduler = RecordingReminderScheduler()
    private val tokens = ObservableTokenStore(MemoryTokenStore()).apply { save(AuthTokens("a", "r")) }
    private val reminder = DailyReminder(scheduler, MemoryReminderStore(), tokens)
    private val prefs = FakePennyPrefs()
    private val recapScheduler = RecordingRecapScheduler()
    private val recap = WeeklyRecapReminder(recapScheduler, prefs, flagsWith(), tokens)
    private val access = FakeNotificationAccess()

    private fun viewModel() = SettingsViewModel(gateway, fixtureClock(), reminder, prefs, recap, access, dispatcher)

    @Test
    fun showsTheTownTheMoneyAndTheMembers() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val state = viewModel.state.value

        assertTrue(state.loaded)
        assertEquals("3 Sep", state.townSince)
        assertEquals(82, state.townDays)
        assertEquals(formatWhole(180000, Currency.EUR), state.budgetText)
        assertEquals(3, state.towerCount)
        assertEquals(1, state.capCount)
        assertEquals(listOf("You", "Ana"), state.members.map { it.name })
        assertTrue(state.isOwner)
    }

    @Test
    fun noTestToolsMeansNoGroup() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertNull(viewModel.state.value.testTools)
        viewModel.onTestToolClick("anything")
        advanceUntilIdle()
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun aTestToolRowShowsItsMessageOrOpensTheRollover() = runTest(dispatcher) {
        val tools = object : TestTools {
            override val title = "Test tools"
            override val rows = listOf(TestRow("a", "A"), TestRow("b", "B"))
            override suspend fun run(rowId: String) =
                if (rowId == "a") TestToolResult.Message("Added 25.") else TestToolResult.OpenRollover(kotlinx.datetime.LocalDate(2026, 10, 1))
        }
        val viewModel = SettingsViewModel(gateway, fixtureClock(), reminder, prefs, recap, access, dispatcher, tools)
        advanceUntilIdle()
        assertEquals(listOf("A", "B"), viewModel.state.value.testTools?.rows?.map { it.label })

        viewModel.onTestToolClick("a")
        advanceUntilIdle()
        assertEquals("Added 25.", viewModel.state.value.message)

        viewModel.onTestToolClick("b")
        assertEquals(SettingsEffect.OpenRollover(kotlinx.datetime.LocalDate(2026, 10, 1)), viewModel.effect.first())
    }

    @Test
    fun budgetNeedsSaveAndRejectsZero() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onBudgetClick()
        repeat(4) { viewModel.onBudgetKey('\b') }
        viewModel.onBudgetSave()
        advanceUntilIdle()
        assertEquals(180000, gateway.snapshot.town.settings.monthlyBudgetCents)
        assertNotNull(viewModel.state.value.message)

        viewModel.onBudgetKey('2')
        viewModel.onBudgetKey('0')
        viewModel.onBudgetKey('0')
        viewModel.onBudgetKey('0')
        viewModel.onBudgetSave()
        advanceUntilIdle()
        assertEquals(200000, gateway.snapshot.town.settings.monthlyBudgetCents)
        assertEquals(SettingsSheet.None, viewModel.state.value.sheet)
    }

    @Test
    fun paydayCurrencyAndBillsFirstAreWrittenToTheTown() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPaydayPick(28)
        viewModel.onCurrencyPick(Currency.RON)
        viewModel.onBillsFirstChange(false)
        advanceUntilIdle()

        val settings = gateway.snapshot.town.settings
        assertEquals(28, settings.payday)
        assertEquals(Currency.RON, settings.currency)
        assertFalse(settings.billsFirst)
    }

    @Test
    fun aMemberWithoutTheManageRightCannotEditTheMoney() = runTest(dispatcher) {
        val town = FakeTownGateway(townSnapshot(viewer = "ana"))
        val viewModel = SettingsViewModel(town, fixtureClock(), reminder, prefs, recap, access, dispatcher)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canManage)
        viewModel.onBudgetClick()
        viewModel.onPaydayPick(5)
        advanceUntilIdle()
        assertEquals(1, town.snapshot.town.settings.payday)
        assertEquals(SettingsSheet.None, viewModel.state.value.sheet)
    }

    @Test
    fun theOwnerGrantsEditRightsToAMember() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onMemberCanEditChange("ana", false)
        advanceUntilIdle()

        assertFalse(gateway.snapshot.members.first { it.userId == "ana" }.canEdit)
    }

    @Test
    fun theEveningNudgeAsksForPermissionThenBooksTwentyHundred() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNudgeChange(true)
        advanceUntilIdle()
        assertEquals(SettingsEffect.AskNotificationPermission, viewModel.effect.first())
        assertNull(scheduler.booked)

        viewModel.onNotificationPermissionResult(true)
        advanceUntilIdle()
        assertEquals(EveningNudgeTime, scheduler.booked)
        assertTrue(viewModel.state.value.nudgeOn)

        viewModel.onNudgeChange(false)
        advanceUntilIdle()
        assertNull(scheduler.booked)
        assertFalse(viewModel.state.value.nudgeOn)
    }

    @Test
    fun aDeniedPermissionLeavesTheNudgeOffAndSaysSo() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNotificationPermissionResult(false)
        advanceUntilIdle()

        assertNull(scheduler.booked)
        assertNotNull(viewModel.state.value.message)
    }

    @Test
    fun theWeeklyRecapSwitchAsksForPermissionAndStaysOffWhenRefused() = runTest(dispatcher) {
        prefs.setWeeklyRecap(false)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRecapChange(true)
        advanceUntilIdle()
        assertEquals(SettingsEffect.AskNotificationPermission, viewModel.effect.first())
        assertTrue(recapScheduler.booked.isEmpty())

        viewModel.onNotificationPermissionResult(false)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.recapOn)
        assertTrue(recapScheduler.booked.isEmpty())
        assertNull(scheduler.booked)
        assertNotNull(viewModel.state.value.message)

        viewModel.onRecapChange(true)
        viewModel.onNotificationPermissionResult(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.recapOn)
        assertEquals(2, recapScheduler.booked.size)
        assertNull(scheduler.booked)
    }

    @Test
    fun recapAndCelebrationsArePersisted() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRecapChange(false)
        viewModel.onCelebrationsChange(false)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.recapOn)
        assertFalse(viewModel.state.value.celebrationsOn)
    }

    @Test
    fun exportNamesTheFileByDateAndCarriesTheCsvHeader() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onExportClick()
        advanceUntilIdle()

        val effect = viewModel.effect.first() as SettingsEffect.ShareExport
        assertEquals("brick-town-2026-11-24.csv", effect.fileName)
        assertTrue(effect.csv.startsWith("date,tower,note,amount,monthly,shared,refund,paid by"))
    }

    @Test
    fun deletingTheTownIsOwnerOnlyAndAsksFirst() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDeleteTownClick()
        assertEquals(SettingsSheet.DeleteTown, viewModel.state.value.sheet)
        assertEquals(emptyList(), gateway.calls)

        viewModel.onConfirmDeleteTown()
        advanceUntilIdle()
        assertEquals(listOf("deleteTown"), gateway.calls)
    }

    @Test
    fun aFailedDeleteKeepsTheTownAndShowsTheReason() = runTest(dispatcher) {
        gateway.endResult = TownResult.Failed("Only the owner can delete the town", 403)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDeleteTownClick()
        viewModel.onConfirmDeleteTown()
        advanceUntilIdle()

        assertEquals("Only the owner can delete the town", viewModel.state.value.message)
        assertTrue(viewModel.state.value.loaded)
    }

    @Test
    fun aMemberLeavesInsteadOfDeleting() = runTest(dispatcher) {
        val town = FakeTownGateway(townSnapshot(viewer = "ana"))
        val viewModel = SettingsViewModel(town, fixtureClock(), reminder, prefs, recap, access, dispatcher)
        advanceUntilIdle()

        viewModel.onDeleteTownClick()
        assertEquals(SettingsSheet.None, viewModel.state.value.sheet)
        viewModel.onLeaveTownClick()
        assertEquals(SettingsSheet.LeaveTown, viewModel.state.value.sheet)
        viewModel.onConfirmLeaveTown()
        advanceUntilIdle()
        assertEquals(listOf("leave"), town.calls)
    }

    @Test
    fun theRecapSwitchIsOffWhileThePhoneBlocksNotifications() = runTest(dispatcher) {
        access.on = false
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(prefs.weeklyRecap.value)
        assertFalse(viewModel.state.value.recapOn)
        assertEquals("Allow notifications", viewModel.state.value.nudgeHint)
    }

    @Test
    fun notificationsTurnedOffInThePhonesSettingsShowWhenSettingsComesBack() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onNudgeChange(true)
        viewModel.onNotificationPermissionResult(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.nudgeOn)
        assertEquals("20:00 · only if the day has no bricks", viewModel.state.value.nudgeHint)

        access.on = false
        viewModel.onResume()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.nudgeOn)
        assertFalse(viewModel.state.value.recapOn)
        assertEquals("Allow notifications", viewModel.state.value.nudgeHint)
    }

    @Test
    fun allowingNotificationsFromTheNudgeRowTurnsItOn() = runTest(dispatcher) {
        access.on = false
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNudgeChange(true)
        assertEquals(SettingsEffect.AskNotificationPermission, viewModel.effect.first())
        access.on = true
        viewModel.onNotificationPermissionResult(true)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.nudgeOn)
        assertTrue(viewModel.state.value.recapOn)
        assertEquals("20:00 · only if the day has no bricks", viewModel.state.value.nudgeHint)
    }
}

class TownAgeLabelTest {
    @kotlin.test.Test
    fun theDayTheTownIsMadeReadsOneDay() {
        assertEquals("1 day", townAgeLabel(0))
        assertEquals("1 day", townAgeLabel(1))
    }

    @kotlin.test.Test
    fun laterDaysArePlural() {
        assertEquals("2 days", townAgeLabel(2))
        assertEquals("82 days", townAgeLabel(82))
    }
}
