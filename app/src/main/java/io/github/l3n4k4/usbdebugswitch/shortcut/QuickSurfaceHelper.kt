package io.github.l3n4k4.usbdebugswitch.shortcut

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import io.github.l3n4k4.usbdebugswitch.R
import io.github.l3n4k4.usbdebugswitch.tile.UsbDebugTileService
import io.github.l3n4k4.usbdebugswitch.tile.WirelessDebugTileService
import io.github.l3n4k4.usbdebugswitch.widget.UsbDebugWidgetProvider

sealed interface TileAddOutcome {
    data class Added(val tileLabel: String) : TileAddOutcome
    data class AlreadyAdded(val tileLabel: String) : TileAddOutcome
    data object Dismissed : TileAddOutcome
    data class NeedsManualInstructions(val tileLabel: String) : TileAddOutcome
}

object QuickSurfaceHelper {

    const val PINNED_USB_SHORTCUT_ID = "pin_usb_debug"

    internal const val RESULT_TILE_NOT_ADDED = 0
    internal const val RESULT_TILE_ALREADY_ADDED = 1
    internal const val RESULT_TILE_ADDED = 2

    fun requestAddUsbTile(
        context: Context,
        onResult: (TileAddOutcome) -> Unit
    ) {
        requestAddTileInternal(
            context = context,
            tileClass = UsbDebugTileService::class.java,
            labelRes = R.string.tile_label,
            iconRes = R.drawable.ic_qs_usb_debug,
            onResult = onResult
        )
    }

    fun requestAddWirelessTile(
        context: Context,
        onResult: (TileAddOutcome) -> Unit
    ) {
        requestAddTileInternal(
            context = context,
            tileClass = WirelessDebugTileService::class.java,
            labelRes = R.string.tile_wireless_label,
            iconRes = R.drawable.ic_qs_wireless_debug,
            onResult = onResult
        )
    }

    internal fun mapTileAddResultCode(resultCode: Int, tileLabel: String): TileAddOutcome {
        return when (resultCode) {
            RESULT_TILE_ADDED -> TileAddOutcome.Added(tileLabel)
            RESULT_TILE_ALREADY_ADDED -> TileAddOutcome.AlreadyAdded(tileLabel)
            RESULT_TILE_NOT_ADDED -> TileAddOutcome.Dismissed
            else -> TileAddOutcome.NeedsManualInstructions(tileLabel)
        }
    }

    private fun requestAddTileInternal(
        context: Context,
        tileClass: Class<*>,
        @StringRes labelRes: Int,
        @DrawableRes iconRes: Int,
        onResult: (TileAddOutcome) -> Unit
    ) {
        val label = context.getString(labelRes)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(TileAddOutcome.NeedsManualInstructions(label))
            return
        }

        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onResult(TileAddOutcome.NeedsManualInstructions(label))
            return
        }

        try {
            statusBarManager.requestAddTileService(
                ComponentName(context, tileClass),
                label,
                Icon.createWithResource(context, iconRes),
                ContextCompat.getMainExecutor(context)
            ) { code ->
                onResult(mapTileAddResultCode(code, label))
            }
        } catch (_: Exception) {
            onResult(TileAddOutcome.NeedsManualInstructions(label))
        }
    }

    fun requestPinUsbWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return runCatching {
            manager.requestPinAppWidget(
                ComponentName(context, UsbDebugWidgetProvider::class.java),
                null,
                null
            )
        }.getOrDefault(false)
    }

    fun requestPinUsbShortcut(context: Context): Boolean {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!shortcutManager.isRequestPinShortcutSupported) return false

        val shortcutInfo = ShortcutInfo.Builder(context, PINNED_USB_SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.shortcut_usb_short))
            .setLongLabel(context.getString(R.string.shortcut_usb_long))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_usb))
            .setIntent(
                Intent(context, ShortcutDispatchActivity::class.java).apply {
                    action = ShortcutDispatchActivity.ACTION_SHORTCUT_USB
                }
            )
            .build()

        return runCatching {
            shortcutManager.requestPinShortcut(shortcutInfo, null)
        }.getOrDefault(false)
    }
}
