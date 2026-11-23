package io.github.xom11.vibedebug.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import io.github.xom11.vibedebug.MainActivity
import io.github.xom11.vibedebug.R
import io.github.xom11.vibedebug.data.AndroidSettingsGateway
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.data.AutoOffManager
import io.github.xom11.vibedebug.data.DevSettingsController
import io.github.xom11.vibedebug.data.WirelessSwitchResult
import io.github.xom11.vibedebug.model.DevState
import io.github.xom11.vibedebug.rules.AppWatcherService
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper
import io.github.xom11.vibedebug.shortcut.ShortcutDispatchActivity
import java.text.DateFormat
import java.util.Date

@RequiresApi(Build.VERSION_CODES.S)
internal object Api31WidgetViews {
    fun responsive(viewsBySize: Map<SizeF, RemoteViews>): RemoteViews = RemoteViews(viewsBySize)
}

enum class WidgetLayoutTier {
    ICON_1X1,
    TALL_1X2,
    COMPACT_2X1,
    WIDE_3X1,
    STRIP_4X1,
    SQUARE_2X2,
    EXPANDED_4X2,
}

private data class HubControlSpec(
    val slot: Int,
    @IdRes val buttonId: Int,
    @IdRes val iconViewId: Int,
    @IdRes val labelViewId: Int,
    @IdRes val stateViewId: Int,
    @StringRes val labelRes: Int,
    val active: Boolean,
    val canOneTap: Boolean,
    val blockedByPolicy: Boolean,
    val wifiRequiredWarning: Boolean = false,
    val toggleAction: String,
    val modeAAction: String,
)

/**
 * Single unified multi-size Home Screen widget adapting across 1x1, 2x1, 4x1, 2x2, and 4x2
 * for USB Debugging and Wireless Debugging.
 */
class UsbDebugWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        AppWatcherService.sync(context.applicationContext ?: context)
        for (widgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(widgetId, buildRemoteViews(context, widgetId))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetId, newOptions))
    }

    internal fun handleToggle(context: Context) {
        val appContext = context.applicationContext ?: context
        val controller = DevSettingsController(AndroidSettingsGateway(appContext))
        val state = controller.readState()
        if (controller.hasWritePermission() && !state.blockedByPolicy) {
            if (controller.toggleUsbDebugging()) {
                val prefs = AppPreferences(appContext)
                prefs.clearRuleHold()
                AutoOffManager(appContext, prefs).scheduleIfEnabled(controller.readState().adbEnabled)
                controller.syncKeepAwakeWithUsb(prefs.keepAwakeOnUsb)
            }
        }
        QuickSurfaceHelper.syncAllSurfaces(appContext)
    }

    internal fun updateAllOfType(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, UsbDebugWidgetProvider::class.java))
        for (id in ids) {
            manager.updateAppWidget(id, buildRemoteViews(context, id))
        }
    }

    internal fun buildRemoteViews(
        context: Context,
        appWidgetId: Int,
        options: Bundle? = null,
    ): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return Api31WidgetViews.responsive(
                mapOf(
                    SizeF(40f, 40f) to buildRemoteViewsForTier(context, appWidgetId, WidgetLayoutTier.ICON_1X1),
                    SizeF(40f, 115f) to buildRemoteViewsForTier(context, appWidgetId, WidgetLayoutTier.TALL_1X2),
                    SizeF(95f, 48f) to buildRemoteViewsForTier(context, appWidgetId, WidgetLayoutTier.COMPACT_2X1),
                    SizeF(170f, 48f) to buildRemoteViewsForTier(context, appWidgetId, WidgetLayoutTier.WIDE_3X1),
                    SizeF(235f, 48f) to buildRemoteViewsForTier(context, appWidgetId, WidgetLayoutTier.STRIP_4X1),
                    SizeF(95f, 115f) to buildRemoteViewsForTier(context, appWidgetId, WidgetLayoutTier.SQUARE_2X2),
                    SizeF(220f, 115f) to buildRemoteViewsForTier(context, appWidgetId, WidgetLayoutTier.EXPANDED_4X2),
                )
            )
        }
        val resolvedOptions = options
            ?: runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId) }
                .getOrNull()
        val minWidth = resolvedOptions?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 120) ?: 120
        val minHeight = resolvedOptions?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 52) ?: 52
        return buildRemoteViewsForTier(context, appWidgetId, resolveTier(minWidth, minHeight))
    }

    internal fun buildRemoteViewsForTier(
        context: Context,
        appWidgetId: Int,
        tier: WidgetLayoutTier,
    ): RemoteViews {
        val appContext = context.applicationContext ?: context
        val controller = DevSettingsController(AndroidSettingsGateway(appContext))
        val state = controller.readState()
        val privileged = controller.hasWritePermission()
        val usbActive = state.adbEnabled && !state.blockedByPolicy

        if (tier == WidgetLayoutTier.ICON_1X1) {
            return buildIconTier(context, appWidgetId, state, privileged, usbActive)
        }

        val wirelessActive = state.supportsWirelessAdb && state.wirelessAdbEnabled && !state.blockedByPolicy
        val wirelessNeedWifi = !wirelessActive && privileged && !state.blockedByPolicy &&
            state.supportsWirelessAdb && !state.isWifiEnabled

        val layoutRes = when (tier) {
            WidgetLayoutTier.TALL_1X2 -> R.layout.widget_usb_hub_1x2
            WidgetLayoutTier.COMPACT_2X1 -> R.layout.widget_usb_hub_2x1_pill
            WidgetLayoutTier.WIDE_3X1 -> R.layout.widget_usb_hub_3x1_pill
            WidgetLayoutTier.STRIP_4X1 -> R.layout.widget_control_hub_strip
            WidgetLayoutTier.SQUARE_2X2 -> R.layout.widget_control_hub_square
            WidgetLayoutTier.EXPANDED_4X2 -> R.layout.widget_control_hub_expanded
            WidgetLayoutTier.ICON_1X1 -> R.layout.widget_usb_debug
        }

        val views = RemoteViews(context.packageName, layoutRes)
        views.setInt(
            R.id.widget_hub_root,
            "setBackgroundResource",
            if (usbActive) R.drawable.bg_widget_card_active else R.drawable.bg_widget_card
        )
        views.setViewVisibility(
            R.id.btn_hub_wireless,
            if (state.supportsWirelessAdb) View.VISIBLE else View.GONE
        )

        val includeLabel = tier != WidgetLayoutTier.COMPACT_2X1 && tier != WidgetLayoutTier.TALL_1X2
        val compactLabels = tier == WidgetLayoutTier.COMPACT_2X1 ||
            tier == WidgetLayoutTier.TALL_1X2 ||
            tier == WidgetLayoutTier.WIDE_3X1 ||
            tier == WidgetLayoutTier.SQUARE_2X2
        val includeStateLabel = tier == WidgetLayoutTier.STRIP_4X1 ||
            tier == WidgetLayoutTier.SQUARE_2X2 ||
            tier == WidgetLayoutTier.EXPANDED_4X2
        val useShortWifiWarning = tier != WidgetLayoutTier.EXPANDED_4X2
        val controls = listOf(
            HubControlSpec(
                slot = 1,
                buttonId = R.id.btn_hub_usb,
                iconViewId = R.id.icon_hub_usb,
                labelViewId = R.id.label_hub_usb,
                stateViewId = R.id.state_hub_usb,
                labelRes = if (compactLabels) R.string.widget_btn_usb else R.string.widget_short_usb,
                active = usbActive,
                canOneTap = privileged && !state.blockedByPolicy,
                blockedByPolicy = state.blockedByPolicy,
                toggleAction = ACTION_WIDGET_TOGGLE,
                modeAAction = ShortcutDispatchActivity.ACTION_SHORTCUT_USB,
            ),
            HubControlSpec(
                slot = 2,
                buttonId = R.id.btn_hub_wireless,
                iconViewId = R.id.icon_hub_wireless,
                labelViewId = R.id.label_hub_wireless,
                stateViewId = R.id.state_hub_wireless,
                labelRes = if (compactLabels) R.string.widget_btn_wireless else R.string.widget_short_wireless,
                active = wirelessActive,
                canOneTap = privileged && !state.blockedByPolicy && state.supportsWirelessAdb,
                blockedByPolicy = state.blockedByPolicy,
                wifiRequiredWarning = wirelessNeedWifi,
                toggleAction = ACTION_WIDGET_TOGGLE_WIRELESS,
                modeAAction = ShortcutDispatchActivity.ACTION_SHORTCUT_WIRELESS,
            ),
        )

        controls.forEach { spec ->
            bindControl(
                context = context,
                appWidgetId = appWidgetId,
                views = views,
                spec = spec,
                includeLabel = includeLabel,
                includeStateLabel = includeStateLabel,
                useShortWifiWarning = useShortWifiWarning,
            )
        }

        if (tier == WidgetLayoutTier.EXPANDED_4X2) {
            bindExpandedHeader(context, appContext, appWidgetId, views, state, privileged, usbActive)
        }

        return views
    }

    private fun buildIconTier(
        context: Context,
        appWidgetId: Int,
        state: DevState,
        privileged: Boolean,
        usbActive: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_usb_debug)
        views.setInt(
            R.id.widget_tile_box,
            "setBackgroundResource",
            if (usbActive) R.drawable.bg_widget_icon_on else R.drawable.bg_widget_icon_off
        )
        views.setImageViewResource(R.id.widget_icon, R.drawable.ic_qs_usb_debug)
        views.setInt(
            R.id.widget_icon,
            "setColorFilter",
            if (usbActive) COLOR_ON else COLOR_OFF
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val stateWord = context.getString(if (usbActive) R.string.widget_state_on else R.string.widget_state_off)
            views.setContentDescription(R.id.widget_root, "${context.getString(R.string.widget_label)}: $stateWord")
        }
        val tapIntent = buildActionPendingIntent(
            context = context,
            requestCode = appWidgetId * 16 + 1,
            canOneTap = privileged && !state.blockedByPolicy,
            blockedByPolicy = state.blockedByPolicy,
            toggleAction = ACTION_WIDGET_TOGGLE,
            modeAAction = ShortcutDispatchActivity.ACTION_SHORTCUT_USB,
        )
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent)
        return views
    }

    private fun bindControl(
        context: Context,
        appWidgetId: Int,
        views: RemoteViews,
        spec: HubControlSpec,
        includeLabel: Boolean,
        includeStateLabel: Boolean,
        useShortWifiWarning: Boolean,
    ) {
        val label = context.getString(spec.labelRes)
        val stateText = when {
            spec.active -> context.getString(R.string.widget_state_on)
            spec.wifiRequiredWarning -> context.getString(
                if (useShortWifiWarning) R.string.widget_state_need_wifi else R.string.tile_subtitle_need_wifi
            )
            else -> context.getString(R.string.widget_state_off)
        }
        val fgColor = if (spec.active) COLOR_ON else COLOR_OFF

        views.setInt(
            spec.buttonId,
            "setBackgroundResource",
            if (spec.active) R.drawable.bg_widget_btn_on else R.drawable.bg_widget_btn_off
        )
        views.setInt(spec.iconViewId, "setColorFilter", fgColor)
        if (includeLabel) {
            views.setTextViewText(spec.labelViewId, label)
            views.setTextColor(spec.labelViewId, fgColor)
        }

        if (includeStateLabel) {
            views.setTextViewText(spec.stateViewId, stateText)
            val stateColor = when {
                spec.active -> COLOR_ON
                spec.wifiRequiredWarning -> COLOR_AMBER
                else -> COLOR_MUTED
            }
            views.setTextColor(spec.stateViewId, stateColor)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setContentDescription(spec.buttonId, "$label: $stateText")
        }

        val pendingIntent = buildActionPendingIntent(
            context = context,
            requestCode = appWidgetId * 16 + spec.slot,
            canOneTap = spec.canOneTap,
            blockedByPolicy = spec.blockedByPolicy,
            toggleAction = spec.toggleAction,
            modeAAction = spec.modeAAction,
        )
        views.setOnClickPendingIntent(spec.buttonId, pendingIntent)
    }

    private fun buildActionPendingIntent(
        context: Context,
        requestCode: Int,
        canOneTap: Boolean,
        blockedByPolicy: Boolean,
        toggleAction: String,
        modeAAction: String,
    ): PendingIntent = if (canOneTap) {
        val toggleIntent = Intent(context, WidgetToggleReceiver::class.java).apply {
            action = toggleAction
        }
        PendingIntent.getBroadcast(
            context,
            requestCode,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    } else {
        val openIntent = if (blockedByPolicy) {
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else {
            Intent(context, ShortcutDispatchActivity::class.java).apply {
                action = modeAAction
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun bindExpandedHeader(
        context: Context,
        appContext: Context,
        appWidgetId: Int,
        views: RemoteViews,
        state: DevState,
        privileged: Boolean,
        usbActive: Boolean,
    ) {
        val usbStateWord = context.getString(if (usbActive) R.string.widget_state_on else R.string.widget_state_off)
        views.setTextViewText(
            R.id.hub_header_title,
            context.getString(R.string.widget_hub_usb_status, usbStateWord)
        )

        val nowMs = System.currentTimeMillis()
        val prefs = AppPreferences(appContext)
        val autoOffTargetAtMs = prefs.autoOffTargetAtMs
        val subtitleText = when {
            state.blockedByPolicy -> context.getString(R.string.usb_sub_blocked)
            privileged && usbActive && autoOffTargetAtMs > nowMs -> {
                val formatted = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(autoOffTargetAtMs))
                context.getString(R.string.widget_hub_sub_auto_off, formatted)
            }
            privileged && usbActive && prefs.keepAwakeOnUsb ->
                context.getString(R.string.usb_awake_sub_active)
            privileged -> context.getString(R.string.widget_hub_sub_ready)
            !state.devOptionsEnabled -> context.getString(R.string.widget_sub_dev_unlock)
            else -> context.getString(R.string.widget_hub_sub_mode_a)
        }
        views.setTextViewText(R.id.hub_header_subtitle, subtitleText)

        val (badgeTextRes, badgeColor) = when {
            state.blockedByPolicy -> R.string.widget_mode_blocked to COLOR_CORAL
            privileged -> R.string.widget_mode_one_tap to COLOR_MINT
            else -> R.string.widget_mode_settings to COLOR_AMBER
        }
        views.setTextViewText(R.id.hub_mode_badge, context.getString(badgeTextRes))
        views.setTextColor(R.id.hub_mode_badge, badgeColor)

        val refreshIntent = Intent(context, WidgetToggleReceiver::class.java).apply {
            action = ACTION_WIDGET_REFRESH
        }
        views.setOnClickPendingIntent(
            R.id.btn_hub_refresh,
            PendingIntent.getBroadcast(
                context,
                appWidgetId * 16 + 5,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        views.setOnClickPendingIntent(
            R.id.hub_header_click,
            PendingIntent.getActivity(
                context,
                appWidgetId * 16 + 6,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    companion object {
        const val ACTION_WIDGET_TOGGLE = "io.github.xom11.vibedebug.ACTION_WIDGET_TOGGLE"
        const val ACTION_WIDGET_TOGGLE_WIRELESS = "io.github.xom11.vibedebug.ACTION_WIDGET_TOGGLE_WIRELESS"
        const val ACTION_WIDGET_REFRESH = "io.github.xom11.vibedebug.ACTION_WIDGET_REFRESH"

        private const val COLOR_ON = 0xFF0B1F14.toInt()
        private const val COLOR_OFF = 0xFFE9EDF0.toInt()
        private const val COLOR_MUTED = 0xFF9BA6AF.toInt()
        private const val COLOR_MINT = 0xFF7EE2A8.toInt()
        private const val COLOR_AMBER = 0xFFF4B860.toInt()
        private const val COLOR_CORAL = 0xFFF28B82.toInt()

        internal fun resolveTier(widthDp: Int, heightDp: Int): WidgetLayoutTier = when {
            widthDp < 95 && heightDp < 115 -> WidgetLayoutTier.ICON_1X1
            widthDp < 95 -> WidgetLayoutTier.TALL_1X2
            heightDp >= 115 && widthDp >= 220 -> WidgetLayoutTier.EXPANDED_4X2
            heightDp >= 115 -> WidgetLayoutTier.SQUARE_2X2
            widthDp >= 235 -> WidgetLayoutTier.STRIP_4X1
            widthDp >= 170 -> WidgetLayoutTier.WIDE_3X1
            else -> WidgetLayoutTier.COMPACT_2X1
        }

        fun updateAllWidgets(context: Context) {
            UsbDebugWidgetProvider().updateAllOfType(context)
        }
    }
}

/** Internal non-exported receiver executing privileged widget 1-tap toggles safely. */
class WidgetToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE -> UsbDebugWidgetProvider().handleToggle(context)
            UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE_WIRELESS -> handleWirelessToggle(context)
            UsbDebugWidgetProvider.ACTION_WIDGET_REFRESH -> {
                val appContext = context.applicationContext ?: context
                val prefs = AppPreferences(appContext)
                val controller = DevSettingsController(AndroidSettingsGateway(appContext))
                AutoOffManager(appContext, prefs).resync(controller.readState().adbEnabled)
                controller.syncKeepAwakeWithUsb(prefs.keepAwakeOnUsb)
                QuickSurfaceHelper.syncAllSurfaces(appContext)
            }
        }
    }

    private fun handleWirelessToggle(context: Context) {
        val appContext = context.applicationContext ?: context
        val controller = DevSettingsController(AndroidSettingsGateway(appContext))
        val state = controller.readState()
        if (!controller.hasWritePermission() || state.blockedByPolicy || !state.supportsWirelessAdb) {
            QuickSurfaceHelper.syncAllSurfaces(appContext)
            return
        }
        AppPreferences(appContext).clearRuleHold()
        if (state.wirelessAdbEnabled) {
            controller.setWirelessDebugging(false)
            QuickSurfaceHelper.syncAllSurfaces(appContext)
            return
        }
        when (controller.setWirelessDebuggingWithResult(true)) {
            WirelessSwitchResult.WIFI_NOT_CONNECTED -> {
                QuickSurfaceHelper.syncAllSurfaces(appContext)
                QuickSurfaceHelper.showMainToast(appContext, R.string.wireless_err_need_wifi)
            }
            WirelessSwitchResult.UPDATED -> {
                QuickSurfaceHelper.syncAllSurfaces(appContext)
                val pendingResult = runCatching { goAsync() }.getOrNull()
                Handler(Looper.getMainLooper()).postDelayed(
                    {
                        try {
                            if (!controller.readState().wirelessAdbEnabled) {
                                QuickSurfaceHelper.syncAllSurfaces(appContext)
                                QuickSurfaceHelper.showMainToast(appContext, R.string.wireless_err_need_wifi)
                            }
                        } finally {
                            runCatching { pendingResult?.finish() }
                        }
                    },
                    500L
                )
            }
            WirelessSwitchResult.UNAVAILABLE -> QuickSurfaceHelper.syncAllSurfaces(appContext)
        }
    }
}
