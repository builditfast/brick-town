package ai.freeapp.bricktown.widget

import ai.freeapp.bricktown.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private const val MONTH_LINK = "bricktown://month"
private const val ADD_LINK = "bricktown://add"

private fun open(context: Context, link: String) = actionStartActivity(
    Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(context.packageName),
)

private fun GlanceModifier.opens(context: Context, link: String?) =
    if (link == null) this else clickable(open(context, link))

private fun text(color: Color, size: Int, weight: FontWeight = FontWeight.Bold) =
    TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = weight)

/**
 * The model as live state. Glance keeps a widget's session running after
 * provideGlance, and an update then only recomposes it: a model read once
 * before provideContent would stay stale until the session ends.
 */
@Composable
private fun liveModel(first: WidgetModel): WidgetModel =
    remember { WidgetSource.models() }.collectAsState(first).value

/** Frame 5e, medium pink: today's money, the month's towers, the month line. */
class BrickMonthWidget : GlanceAppWidget() {
    /** Exact, so the grid bitmap and the panel width follow the size the launcher gives. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = WidgetSource.current()
        provideContent { MonthContent(context, liveModel(first)) }
    }
}

/** 5e's panel is 150 dp; below this widget width it shares the row instead so the number keeps room. */
private val FIXED_PANEL_FROM = 300.dp
private const val BAR_COLUMNS = 6

/** 5e's bar row at the frame's size; a taller widget gives the extra height to the towers. */
private val MIN_CHART_HEIGHT = 64.dp

/** What the widget's height spends besides the bars: 16 dp outer and 10 dp panel padding, the ground line and the month line. */
private val CHART_CHROME = 32.dp + 20.dp + 13.dp + 14.dp

@Composable
private fun MonthContent(context: Context, model: WidgetModel) {
    val size = LocalSize.current
    val grid = remember(size) { gridBitmap(context, size) }
    Box(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.Pink).cornerRadius(24.dp)
            .opens(context, MONTH_LINK.takeIf { model != WidgetModel.Off }),
    ) {
        Image(ImageProvider(grid), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Row(modifier = GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(ImageProvider(R.drawable.widget_penny), null, GlanceModifier.size(26.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Text("Brick Town", style = text(WidgetColors.White, 12))
                }
                Spacer(GlanceModifier.defaultWeight())
                when (model) {
                    is WidgetModel.Ready -> {
                        // 40 as in 5e; the over-budget wording (ours) is longer, so it steps down to fit the column.
                        val headlineSize = if (model.headline.startsWith("Over")) 30 else 40
                        Text(model.headline, style = text(WidgetColors.White, headlineSize), maxLines = 1)
                        Text(model.subline, style = text(WidgetColors.White, 11))
                    }
                    WidgetModel.SignedOut -> Message("Sign in", "to see your town")
                    WidgetModel.NoTown -> Message("No town yet", "open the app to start one")
                    WidgetModel.Off -> Message("Widgets are off", "for now")
                }
            }
            if (model is WidgetModel.Ready) {
                Spacer(GlanceModifier.width(14.dp))
                val panelWidth = if (size.width >= FIXED_PANEL_FROM) GlanceModifier.width(150.dp) else GlanceModifier.defaultWeight()
                Column(
                    modifier = panelWidth.fillMaxHeight().background(WidgetColors.PinkPanel).cornerRadius(16.dp).padding(10.dp),
                    verticalAlignment = Alignment.Vertical.Bottom,
                ) {
                    val chartHeight = maxOf(size.height - CHART_CHROME, MIN_CHART_HEIGHT)
                    Row(modifier = GlanceModifier.fillMaxWidth().height(chartHeight), verticalAlignment = Alignment.Vertical.Bottom) {
                        // Six columns always, as 5e's grid: a town with fewer towers keeps the same bar width.
                        for (index in 0 until BAR_COLUMNS) {
                            if (index > 0) Spacer(GlanceModifier.width(3.dp))
                            val bar = model.bars.getOrNull(index)
                            if (bar == null) {
                                Spacer(GlanceModifier.defaultWeight())
                            } else {
                                Box(
                                    modifier = GlanceModifier.defaultWeight().height((chartHeight - 2.dp) * bar.height)
                                        .background(WidgetColors.Categories[bar.colorIndex.mod(8)]).cornerRadius(3.dp),
                                ) {}
                            }
                        }
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    // The town's ground line under the towers.
                    Box(modifier = GlanceModifier.fillMaxWidth().height(5.dp).background(WidgetColors.GroundLine).cornerRadius(3.dp)) {}
                    Spacer(GlanceModifier.height(4.dp))
                    Text(model.monthLine, style = text(WidgetColors.Ink, 10), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Message(title: String, sub: String) {
    Text(title, style = text(WidgetColors.White, 20))
    Text(sub, style = text(WidgetColors.White, 11))
}

/** Frame 5e, small gold: the day streak. */
class BrickStreakWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = WidgetSource.current()
        provideContent {
            val model = liveModel(first)
            Column(
                modifier = GlanceModifier.fillMaxSize().background(WidgetColors.Gold).cornerRadius(24.dp).padding(14.dp)
                    .opens(context, MONTH_LINK.takeIf { model != WidgetModel.Off }),
            ) {
                Image(ImageProvider(R.drawable.widget_penny_white), null, GlanceModifier.size(40.dp))
                Spacer(GlanceModifier.defaultWeight())
                when (model) {
                    is WidgetModel.Ready -> {
                        Text(model.streak.toString(), style = text(WidgetColors.Ink, 34))
                        Text(model.streakLine, style = text(WidgetColors.Ink, 11))
                    }
                    WidgetModel.SignedOut -> StreakMessage("Sign in", "to start a streak")
                    WidgetModel.NoTown -> StreakMessage("No town yet", "open the app")
                    WidgetModel.Off -> StreakMessage("Widgets are off", "for now")
                }
            }
        }
    }
}

@Composable
private fun StreakMessage(title: String, sub: String) {
    Text(title, style = text(WidgetColors.Ink, 16))
    Text(sub, style = text(WidgetColors.Ink, 11))
}

/** Frame 5e, small green: Add a brick. */
class BrickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = WidgetSource.current()
        provideContent {
            val model = liveModel(first)
            Column(
                modifier = GlanceModifier.fillMaxSize().background(WidgetColors.Green).cornerRadius(24.dp).padding(14.dp)
                    .opens(context, ADD_LINK.takeIf { model != WidgetModel.Off }),
            ) {
                Box(
                    modifier = GlanceModifier.size(44.dp).background(WidgetColors.White).cornerRadius(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(ImageProvider(R.drawable.widget_plus), null, GlanceModifier.size(24.dp))
                }
                Spacer(GlanceModifier.defaultWeight())
                if (model == WidgetModel.Off) {
                    Text("Widgets are off", style = text(WidgetColors.White, 15))
                } else {
                    Text("Add a brick", style = text(WidgetColors.White, 15))
                    Text("opens the numpad", style = text(WidgetColors.White, 11))
                }
            }
        }
    }
}

class BrickMonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BrickMonthWidget()
}

class BrickStreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BrickStreakWidget()
}

class BrickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BrickAddWidget()
}
