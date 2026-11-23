package io.github.xom11.vibedebug.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import io.github.xom11.vibedebug.MainActivity
import io.github.xom11.vibedebug.R
import io.github.xom11.vibedebug.data.AndroidSettingsGateway
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.data.AutoOffManager
import io.github.xom11.vibedebug.data.DevSettingsController
import io.github.xom11.vibedebug.model.DevState
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper

/** Shared base for home screen 1x1 icon-style developer control widgets. */
abstract class BaseDebugWidgetProvider : AppWidgetProvider() {

    @get:LayoutRes
    protected abstract val layoutRes: Int

    @get:DrawableRes
    protected abstract val iconRes: Int

    @get:StringRes
    protected abstract val titleRes: Int

    @get:StringRes
    protected abstract val shortTitleRes: Int

    protected abstract val toggleAction: String

    protected abstract fun isOn(state: DevState): Boolean

    protected abstract fun canOneTap(state: DevState, privileged: Boolean): Boolean

    protected abstract fun performToggle(appContext: Context, controller: DevSettingsController)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(widgetId, buildRemoteViews(context, widgetId))
        }
    }

    internal fun handleToggle(context: Context) {
        val appContext = context.applicationContext ?: context
        val controller = DevSettingsController(AndroidSettingsGateway(appContext))
        val state = controller.readState()
        if (canOneTap(state, controller.hasWritePermission())) {
            performToggle(appContext, controller)
        }
        QuickSurfaceHelper.syncAllSurfaces(appContext)
    }

    internal fun updateAllOfType(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, this::class.java))
        for (id in ids) {
            manager.updateAppWidget(id, buildRemoteViews(context, id))
        }
    }

    internal fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val appContext = context.applicationContext ?: context
        val controller = DevSettingsController(AndroidSettingsGateway(appContext))
        val state = controller.readState()
        val privileged = controller.hasWritePermission()
        val active = isOn(state)
        val oneTapReady = canOneTap(state, privileged)

        val views = RemoteViews(context.packageName, layoutRes)
        views.setInt(
            R.id.widget_tile_box,
            "setBackgroundResource",
            if (active) R.drawable.bg_widget_icon_on else R.drawable.bg_widget_icon_off
        )
        views.setImageViewResource(R.id.widget_icon, iconRes)
        views.setInt(
            R.id.widget_icon,
            "setColorFilter",
            if (active) COLOR_ICON_ON else COLOR_ICON_OFF
        )
        views.setTextViewText(R.id.widget_title, context.getString(shortTitleRes))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setContentDescription(
                R.id.widget_root,
                "${context.getString(titleRes)}: ${context.getString(if (active) R.string.widget_state_on else R.string.widget_state_off)}"
            )
        }

        val tapPendingIntent = if (oneTapReady) {
            val toggleIntent = Intent(context, WidgetToggleReceiver::class.java).apply {
                action = toggleAction
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
        return views
    }

    companion object {
        private const val COLOR_ICON_ON = 0xFF0B1F14.toInt()
        private const val COLOR_ICON_OFF = 0xFFE9EDF0.toInt()
    }
}

/** Home screen 1x1 icon widget for checking and toggling USB Debugging. */
class UsbDebugWidgetProvider : BaseDebugWidgetProvider() {

    override val layoutRes: Int = R.layout.widget_usb_debug
    override val iconRes: Int = R.drawable.ic_qs_usb_debug
    override val titleRes: Int = R.string.widget_label
    override val shortTitleRes: Int = R.string.widget_short_usb
    override val toggleAction: String = ACTION_WIDGET_TOGGLE

    override fun isOn(state: DevState): Boolean = state.adbEnabled && !state.blockedByPolicy

    override fun canOneTap(state: DevState, privileged: Boolean): Boolean =
        privileged && !state.blockedByPolicy

    override fun performToggle(appContext: Context, controller: DevSettingsController) {
        if (controller.toggleUsbDebugging()) {
            AutoOffManager(appContext, AppPreferences(appContext))
                .scheduleIfEnabled(controller.readState().adbEnabled)
        }
    }

    companion object {
        const val ACTION_WIDGET_TOGGLE = "io.github.xom11.vibedebug.ACTION_WIDGET_TOGGLE"
        const val ACTION_WIDGET_TOGGLE_WIRELESS = "io.github.xom11.vibedebug.ACTION_WIDGET_TOGGLE_WIRELESS"
        const val ACTION_WIDGET_TOGGLE_DEV_OPTIONS = "io.github.xom11.vibedebug.ACTION_WIDGET_TOGGLE_DEV_OPTIONS"
        const val ACTION_WIDGET_TOGGLE_KEEP_AWAKE = "io.github.xom11.vibedebug.ACTION_WIDGET_TOGGLE_KEEP_AWAKE"

        fun updateAllWidgets(context: Context) {
            listOf(
                UsbDebugWidgetProvider(),
                WirelessDebugWidgetProvider(),
                DevOptionsWidgetProvider(),
                KeepAwakeWidgetProvider(),
            ).forEach { it.updateAllOfType(context) }
        }
    }
}

/** Home screen 1x1 icon widget for checking and toggling Wireless Debugging. */
class WirelessDebugWidgetProvider : BaseDebugWidgetProvider() {

    override val layoutRes: Int = R.layout.widget_wireless_debug
    override val iconRes: Int = R.drawable.ic_qs_wireless_debug
    override val titleRes: Int = R.string.tile_wireless_label
    override val shortTitleRes: Int = R.string.widget_short_wireless
    override val toggleAction: String = UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE_WIRELESS

    override fun isOn(state: DevState): Boolean =
        state.supportsWirelessAdb && state.wirelessAdbEnabled && !state.blockedByPolicy

    override fun canOneTap(state: DevState, privileged: Boolean): Boolean =
        privileged && !state.blockedByPolicy && state.supportsWirelessAdb

    override fun performToggle(appContext: Context, controller: DevSettingsController) {
        controller.toggleWirelessDebugging()
    }
}

/** Home screen 1x1 icon widget for showing or hiding Developer Options. */
class DevOptionsWidgetProvider : BaseDebugWidgetProvider() {

    override val layoutRes: Int = R.layout.widget_dev_options
    override val iconRes: Int = R.drawable.ic_qs_dev_options
    override val titleRes: Int = R.string.tile_dev_options_label
    override val shortTitleRes: Int = R.string.widget_short_dev
    override val toggleAction: String = UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE_DEV_OPTIONS

    override fun isOn(state: DevState): Boolean = state.devOptionsEnabled && !state.blockedByPolicy

    override fun canOneTap(state: DevState, privileged: Boolean): Boolean =
        privileged && !state.blockedByPolicy

    override fun performToggle(appContext: Context, controller: DevSettingsController) {
        if (controller.toggleDeveloperOptions() && !controller.readState().devOptionsEnabled) {
            AutoOffManager(appContext, AppPreferences(appContext)).cancel()
        }
    }
}

/** Home screen 1x1 icon widget for toggling Keep Awake while plugged in. */
class KeepAwakeWidgetProvider : BaseDebugWidgetProvider() {

    override val layoutRes: Int = R.layout.widget_keep_awake
    override val iconRes: Int = R.drawable.ic_qs_keep_awake
    override val titleRes: Int = R.string.tile_keep_awake_label
    override val shortTitleRes: Int = R.string.widget_short_awake
    override val toggleAction: String = UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE_KEEP_AWAKE

    override fun isOn(state: DevState): Boolean = state.keepAwakeEnabled

    override fun canOneTap(state: DevState, privileged: Boolean): Boolean = privileged

    override fun performToggle(appContext: Context, controller: DevSettingsController) {
        controller.toggleKeepAwake()
    }
}

/** Internal non-exported receiver executing privileged widget 1-tap toggles safely. */
class WidgetToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE -> UsbDebugWidgetProvider().handleToggle(context)
            UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE_WIRELESS -> WirelessDebugWidgetProvider().handleToggle(context)
            UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE_DEV_OPTIONS -> DevOptionsWidgetProvider().handleToggle(context)
            UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE_KEEP_AWAKE -> KeepAwakeWidgetProvider().handleToggle(context)
        }
    }
}
