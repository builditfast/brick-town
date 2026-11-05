package ai.freeapp.bricktown.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Keeps the home-screen widgets in step with the town. A brick added, undone
 * or synced changes the model, which redraws all three; a new day is covered
 * by updatePeriodMillis (30 minutes). With the `widgets` flag off a widget
 * kind nobody placed leaves the launcher's picker; a placed one stays and says
 * the widgets are off, and shows the numbers again when the flag is back on.
 */
class WidgetUpdater(private val context: Context) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            WidgetSource.models().debounce(DEBOUNCE_MILLIS).collect { model ->
                setReceiversEnabled(model != WidgetModel.Off)
                BrickMonthWidget().updateAll(context)
                BrickStreakWidget().updateAll(context)
                BrickAddWidget().updateAll(context)
            }
        }
    }

    private fun setReceiversEnabled(widgetsOn: Boolean) {
        val widgets = AppWidgetManager.getInstance(context)
        listOf(
            BrickMonthWidgetReceiver::class.java,
            BrickStreakWidgetReceiver::class.java,
            BrickAddWidgetReceiver::class.java,
        ).forEach { receiver ->
            val component = ComponentName(context, receiver)
            val enabled = receiverEnabled(widgetsOn, placed = widgets.getAppWidgetIds(component).size)
            val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
    }
}

/** Android deletes a disabled receiver's placed widgets for good, so only an unplaced kind is switched off. */
internal fun receiverEnabled(widgetsOn: Boolean, placed: Int): Boolean = widgetsOn || placed > 0
