package io.github.l3n4k4.usbdebugswitch.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import io.github.l3n4k4.usbdebugswitch.MainActivity
import io.github.l3n4k4.usbdebugswitch.R
import io.github.l3n4k4.usbdebugswitch.data.AndroidSettingsGateway
import io.github.l3n4k4.usbdebugswitch.data.DevSettingsController
import io.github.l3n4k4.usbdebugswitch.tile.UsbDebugTileService

class UsbDebugWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_TOGGLE) {
            val controller = DevSettingsController(AndroidSettingsGateway(context.applicationContext))
            if (controller.hasWritePermission()) {
                controller.toggleUsbDebugging()
                updateAllWidgets(context.applicationContext)
                runCatching {
                    TileService.requestListeningState(
                        context.applicationContext,
                        ComponentName(context.applicationContext, UsbDebugTileService::class.java)
                    )
                }
            } else {
                updateAllWidgets(context.applicationContext)
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_TOGGLE = "io.github.l3n4k4.usbdebugswitch.ACTION_WIDGET_TOGGLE"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UsbDebugWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    updateSingleWidget(context, manager, id)
                }
            }
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val controller = DevSettingsController(AndroidSettingsGateway(context.applicationContext))
            val state = controller.readState()
            val privileged = controller.hasWritePermission()

            val views = RemoteViews(context.packageName, R.layout.widget_usb_debug)
            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (state.adbEnabled) R.drawable.bg_widget_pill_on else R.drawable.bg_widget_pill_off
            )
            views.setInt(
                R.id.widget_badge,
                "setBackgroundResource",
                if (state.adbEnabled) R.drawable.bg_widget_badge_on else R.drawable.bg_widget_badge_off
            )
            views.setTextViewText(
                R.id.widget_badge,
                context.getString(
                    if (state.adbEnabled) R.string.widget_state_on else R.string.widget_state_off
                )
            )
            views.setTextViewText(
                R.id.widget_subtitle,
                context.getString(
                    if (privileged) R.string.widget_hint_one_tap else R.string.widget_hint_open
                )
            )

            val tapPendingIntent = if (privileged) {
                val toggleIntent = Intent(context, UsbDebugWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_TOGGLE
                }
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            views.setOnClickPendingIntent(R.id.widget_root, tapPendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
