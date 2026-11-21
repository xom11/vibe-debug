package io.github.l3n4k4.usbdebugswitch.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.l3n4k4.usbdebugswitch.MainActivity
import io.github.l3n4k4.usbdebugswitch.R
import io.github.l3n4k4.usbdebugswitch.data.AndroidSettingsGateway
import io.github.l3n4k4.usbdebugswitch.data.DevSettingsController
import io.github.l3n4k4.usbdebugswitch.navigation.SettingsIntents
import io.github.l3n4k4.usbdebugswitch.widget.UsbDebugWidgetProvider

class WirelessDebugTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        unlockAndRun {
            val controller = DevSettingsController(AndroidSettingsGateway(applicationContext))
            if (controller.hasWritePermission()) {
                controller.toggleWirelessDebugging()
                refreshTile()
                UsbDebugWidgetProvider.updateAllWidgets(applicationContext)
                runCatching {
                    requestListeningState(
                        applicationContext,
                        ComponentName(applicationContext, UsbDebugTileService::class.java)
                    )
                }
            } else {
                val state = controller.readState()
                val targetIntent = if (state.devOptionsEnabled) {
                    SettingsIntents.buildEnableWirelessAdbIntent().apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                } else {
                    Intent(applicationContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                }
                launchFromTileAndCollapse(targetIntent)
            }
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_wireless_debug)
        tile.label = getString(R.string.tile_wireless_label)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            tile.state = Tile.STATE_UNAVAILABLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.tile_subtitle_unsupported)
            }
        } else {
            val state = DevSettingsController(AndroidSettingsGateway(applicationContext)).readState()
            tile.state = if (state.wirelessAdbEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(
                    if (state.wirelessAdbEnabled) R.string.tile_subtitle_on else R.string.tile_subtitle_off
                )
            }
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun launchFromTileAndCollapse(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    10,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                startActivityAndCollapse(intent)
            }
        } catch (_: Exception) {
            val fallback = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingFallback = PendingIntent.getActivity(
                    this,
                    11,
                    fallback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingFallback)
            } else {
                startActivityAndCollapse(fallback)
            }
        }
    }
}
