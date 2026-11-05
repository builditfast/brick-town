package ai.freeapp.bricktown

import ai.freeapp.bricktown.designsystem.BarTab
import ai.freeapp.bricktown.designsystem.BottomBar
import ai.freeapp.bricktown.designsystem.BottomSheet
import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.Chip
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.HeroAmount
import ai.freeapp.bricktown.designsystem.Numpad
import ai.freeapp.bricktown.designsystem.Penny
import ai.freeapp.bricktown.designsystem.PennyMood
import ai.freeapp.bricktown.designsystem.Toast
import ai.freeapp.bricktown.designsystem.Toggle
import ai.freeapp.bricktown.designsystem.TowerChart
import ai.freeapp.bricktown.designsystem.TowerColumn
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the design system pieces on a 402 x 874 dp phone and writes PNGs to composeApp/build/gallery
 * for a side by side look against design/handoff/screenshots. The clock is stepped by hand so the
 * bounce-in stagger has finished and no infinite animation keeps the test busy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w402dp-h874dp-xhdpi")
class DesignGalleryTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        // captureToImage waits for a real draw pass, which never comes under Robolectric's paused looper
        // (it times out even on an empty Box), so the content view is drawn straight into a bitmap.
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/gallery").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String, settleMillis: Long = 3000, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { AppTheme { Box(Modifier.size(402.dp, 874.dp)) { content() } } }
        compose.mainClock.advanceTimeBy(settleMillis)
        capture(name)
    }

    private val towers = listOf(
        TowerColumn("Food", 0, 14, contentDescription = "Food, 14 bricks"),
        TowerColumn("Out", 1, 12, capBricks = 10, overCapBricks = 2, contentDescription = "Out, 12 bricks, 2 over the cap"),
        TowerColumn("Home", 2, 11, contentDescription = "Home, 11 bricks"),
        TowerColumn("Shop", 3, 9, contentDescription = "Shop, 9 bricks"),
        TowerColumn("Move", 4, 4, contentDescription = "Move, 4 bricks"),
        TowerColumn("Subs", 5, 3, contentDescription = "Subs, 3 bricks"),
    )

    @Test
    fun month() = shot("month") {
        World(WorldKind.Pink, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Penny(PennyMood.Smile, size = 52.dp)
                    Text("November, day 24")
                }
                Box(Modifier.padding(top = 120.dp).weight(1f)) {
                    TowerChart(towers, brickHeight = 15, onColumnClick = {}, modifier = Modifier.padding(horizontal = 18.dp))
                }
                BottomBar(BarTab.Month, {}, {})
            }
        }
    }

    @Test
    fun pennyMoods() = shot("penny") {
        World(WorldKind.Sky, Modifier.fillMaxSize()) {
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PennyMood.entries.forEach { Penny(it, size = 90.dp) }
            }
        }
    }

    @Test
    fun numpad() = shot("numpad") {
        World(WorldKind.Pink, Modifier.fillMaxSize()) {
            Column(Modifier.padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HeroAmount("€12.50", Modifier.fillMaxSize().weight(1f))
                Numpad({}, Modifier.padding(bottom = 40.dp))
            }
        }
    }

    @Test
    fun bottomBar() = shot("bottombar") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { BottomBar(BarTab.History, {}, {}) }
    }

    @Test
    fun toast() = shot("toast") {
        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.BottomCenter) {
            Toast("Deleted Wolt, 14 EUR", onTimeout = {}, onUndo = {})
        }
    }

    @Test
    fun sheet() = shot("sheet") {
        World(WorldKind.Pink, Modifier.fillMaxSize()) {
            BottomSheet(onDismiss = {}) {
                Text("Food, 14 bricks", Modifier.padding(18.dp))
                repeat(12) { Text("Row $it", Modifier.padding(18.dp)) }
            }
        }
    }

    @Test
    fun chips() = shot("chips") {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("None", selected = true, onClick = {})
                Chip("150", selected = false, onClick = {})
                Chip("250", selected = false, onClick = {})
                Chip("400", selected = false, onClick = {})
            }
            Toggle(checked = true, onCheckedChange = {}, contentDescription = "On")
            Toggle(checked = false, onCheckedChange = {}, contentDescription = "Off")
            ChunkyButton("Add a brick", onClick = {}, kind = ButtonKind.Gold)
            ChunkyButton("Continue", onClick = {}, kind = ButtonKind.Primary)
            ChunkyButton("Disabled", onClick = {}, enabled = false)
        }
    }

    private fun world(kind: WorldKind) = shot("world-${kind.name.lowercase()}") { World(kind, Modifier.fillMaxSize()) }

    @Test
    fun worldPink() = world(WorldKind.Pink)

    @Test
    fun worldSky() = world(WorldKind.Sky)

    @Test
    fun worldGold() = world(WorldKind.Gold)

    @Test
    fun worldNight() = world(WorldKind.Night)
}
