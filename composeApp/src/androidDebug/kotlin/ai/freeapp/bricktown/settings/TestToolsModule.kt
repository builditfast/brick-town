package ai.freeapp.bricktown.settings

import ai.freeapp.bricktown.core.time.TownClock
import ai.freeapp.bricktown.domain.TownSettings
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import org.koin.core.module.Module
import org.koin.dsl.module

val testToolsModule: Module = module {
    single<TestTools> { DebugTestTools(get(), get()) }
}

/** The two debug rows. Compiled into debug builds only. */
class DebugTestTools(private val gateway: TownGateway, private val clock: TownClock) : TestTools {
    override val title = "Test tools"
    override val rows = listOf(
        TestRow(ADD_SAMPLES, "Add sample bricks for the past 5 weeks"),
        TestRow(SHOW_ROLLOVER, "Show last month's done screen"),
    )

    override suspend fun run(rowId: String): TestToolResult {
        val town = gateway.town.first() ?: return TestToolResult.Message("Make a town first.")
        val settings: TownSettings = town.town.settings
        val today = clock.today(settings)
        return when (rowId) {
            ADD_SAMPLES -> addSamples(town.town.id, town.categories.sortedBy { it.position }.map { it.id }, today, town.viewerId, town.expenses.map { it.id }.toSet())
            SHOW_ROLLOVER -> TestToolResult.OpenRollover(previousPayMonthStart(today, settings.payday))
            else -> TestToolResult.Message("Unknown test tool.")
        }
    }

    // Ids come from the town and the brick's date and number, so a second tap rewrites
    // the same rows: bricks already in the town are skipped, and the answer says so.
    private suspend fun addSamples(townId: String, towerIds: List<String>, today: kotlinx.datetime.LocalDate, viewerId: String, existing: Set<String>): TestToolResult {
        if (towerIds.isEmpty()) return TestToolResult.Message("Add a tower first.")
        val fresh = sampleBricks(today, towerIds.size).filter { sampleBrickId(townId, it) !in existing }
        if (fresh.isEmpty()) return TestToolResult.Message("The sample bricks are already in your town.")
        val now = Clock.System.now()
        fresh.forEach { gateway.putExpense(it.toExpense(townId, towerIds[it.towerIndex], "Sample brick", viewerId, now)) }
        return TestToolResult.Message("Added ${fresh.size} sample bricks.")
    }

    private companion object {
        const val ADD_SAMPLES = "add-samples"
        const val SHOW_ROLLOVER = "show-rollover"
    }
}
