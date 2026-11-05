package ai.freeapp.bricktown

import ai.freeapp.bricktown.designsystem.BrickColors
import ai.freeapp.bricktown.designsystem.ChunkyButton
import ai.freeapp.bricktown.designsystem.ButtonKind
import ai.freeapp.bricktown.designsystem.LocalBrickType
import ai.freeapp.bricktown.designsystem.World
import ai.freeapp.bricktown.designsystem.WorldKind
import ai.freeapp.bricktown.settings.eveningNudgeFor
import ai.freeapp.bricktown.settings.fixtureToday
import ai.freeapp.bricktown.settings.brick
import ai.freeapp.bricktown.settings.townSnapshot
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.junit.Test

/**
 * The nudge is a system notification, so the real thing cannot be drawn in
 * Compose. This renders the exact strings eveningNudgeFor returns on a
 * lock-screen sized card, to compare wording and action order with 4b.
 */
class EveningNudgeGalleryTest : Gallery1029() {
    @Test
    fun nudge() = shot("nudge-4b") {
        val bricks = (12..23).map { brick("b$it", "food", 500, LocalDate(2026, 11, it)) }
        val nudge = eveningNudgeFor(townSnapshot(expenses = bricks), fixtureToday)!!
        val type = LocalBrickType.current
        World(WorldKind.Night) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("20:00", style = type.title18.copy(color = BrickColors.White), modifier = Modifier.padding(top = 64.dp, bottom = 24.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(BrickColors.White).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Brick Town · now", style = type.label11.copy(color = BrickColors.Muted))
                    Text(nudge.title, style = type.body15.copy(color = BrickColors.Ink, fontWeight = FontWeight.Black))
                    Text(nudge.body, style = type.small13.copy(color = BrickColors.Ink))
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChunkyButton("€0 day", {}, Modifier.weight(1f), kind = ButtonKind.Gold)
                        ChunkyButton("Add a brick", {}, Modifier.weight(1f), kind = ButtonKind.Danger)
                    }
                }
            }
        }
    }
}
