package ai.freeapp.bricktown

import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.domain.Currency
import ai.freeapp.bricktown.auth.User
import ai.freeapp.bricktown.profile.AccountContent
import ai.freeapp.bricktown.profile.ProfileUiState
import ai.freeapp.bricktown.settings.MemberRow
import ai.freeapp.bricktown.settings.SettingsActions
import ai.freeapp.bricktown.settings.SettingsContent
import ai.freeapp.bricktown.settings.SettingsSheet
import ai.freeapp.bricktown.settings.SettingsUiState
import org.junit.Test

class SettingsGalleryTest : Gallery1029() {
    private val state = SettingsUiState(
        loaded = true, townSince = "3 Sep", townDays = 82, trophies = 2,
        budgetCents = 180000, dailyCents = 6000, payday = 28, currency = Currency.EUR, billsFirst = true,
        canManage = true, isOwner = true, towerColors = listOf(0, 1, 2, 3, 4, 5), towerCount = 6, capCount = 2,
        members = listOf(MemberRow("you", "You", "Y", 0, owner = true, isYou = true, canEdit = true), MemberRow("ana", "Ana", "A", 1, owner = false, isYou = false, canEdit = true)),
        nudgeOn = true, recapOn = true, celebrationsOn = true, freezesLeft = 1,
    )

    private val account: @androidx.compose.runtime.Composable () -> Unit = {
        AccountContent(ProfileUiState(user = User("u1", "emanuel@example.com", "Emanuel")), {}, {}, {}, {})
    }

    @Test
    fun settingsTop() = shot("settings-6a") { SettingsContent(state, SettingsActions(), account = account) }

    @Test
    fun settingsBudgetSheet() = shot("settings-6a-budget") {
        SettingsContent(state.copy(sheet = SettingsSheet.Budget("1800")), SettingsActions())
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w402dp-h1900dp-xhdpi")
    fun settingsFull() = shot("settings-6a-full", heightDp = 1900) { SettingsContent(state, SettingsActions(), account = account) }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w402dp-h1900dp-xhdpi")
    fun settingsDebugTestTools() = shot("settings-6a-test-tools", heightDp = 1900) {
        SettingsContent(
            state.copy(testTools = ai.freeapp.bricktown.settings.TestToolsUi("Test tools", listOf(
                ai.freeapp.bricktown.settings.TestRow("a", "Add sample bricks for the past 5 weeks"),
                ai.freeapp.bricktown.settings.TestRow("b", "Show last month's done screen"),
            ))),
            SettingsActions(), account = account,
        )
    }

    @Test
    fun settingsDayOne() = shot("settings-6a-day1") { SettingsContent(state.copy(townDays = 0, townSince = "24 Nov", trophies = 0), SettingsActions(), account = account) }
}
