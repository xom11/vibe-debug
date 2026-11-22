package io.github.l3n4k4.usbdebugswitch.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.l3n4k4.usbdebugswitch.MainActivity
import io.github.l3n4k4.usbdebugswitch.R
import io.github.l3n4k4.usbdebugswitch.data.AndroidSettingsGateway
import io.github.l3n4k4.usbdebugswitch.data.AppPreferences
import io.github.l3n4k4.usbdebugswitch.data.AutoOffManager
import io.github.l3n4k4.usbdebugswitch.data.DevSettingsController
import io.github.l3n4k4.usbdebugswitch.shortcut.QuickSurfaceHelper

/** Home screen 2x1 widget for checking and toggling USB Debugging. */
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
            val appContext = context.applicationContext ?: context
            val controller = DevSettingsController(AndroidSettingsGateway(appContext))
            val state = controller.readState()
            if (!state.blockedByPolicy && controller.hasWritePermission()) {
                if (controller.toggleUsbDebugging()) {
                    AutoOffManager(appContext, AppPreferences(appContext))
                        .scheduleIfEnabled(controller.readState().adbEnabled)
                }
            }
            QuickSurfaceHelper.syncAllSurfaces(appContext)
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
            val appContext = context.applicationContext ?: context
            val controller = DevSettingsController(AndroidSettingsGateway(appContext))
            val state = controller.readState()
            val privileged = controller.hasWritePermission()
            val canOneTap = privileged && !state.blockedByPolicy

            val views = RemoteViews(context.packageName, R.layout.widget_usb_debug)
            views.setTextViewText(
                R.id.widget_title,
                context.getString(R.string.widget_label)
            )
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
                    when {
                        state.blockedByPolicy -> R.string.tile_subtitle_blocked
                        privileged -> R.string.widget_hint_one_tap
                        else -> R.string.widget_hint_open
                    }
                )
            )

            val tapPendingIntent = if (canOneTap) {
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
