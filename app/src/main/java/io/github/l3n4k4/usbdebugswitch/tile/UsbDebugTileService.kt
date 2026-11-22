package io.github.l3n4k4.usbdebugswitch.tile

import android.service.quicksettings.Tile
import io.github.l3n4k4.usbdebugswitch.R
import io.github.l3n4k4.usbdebugswitch.navigation.SettingsIntents

/** Quick Settings tile for USB Debugging with policy-block and auto-off countdown awareness. */
class UsbDebugTileService : BaseDebugTileService() {

    override fun refreshTile() {
        val state = controller.readState()
        val remainingMinutes = if (state.adbEnabled) createAutoOffManager().remainingMinutes() else null

        val tileState = when {
            state.blockedByPolicy -> Tile.STATE_UNAVAILABLE
            state.adbEnabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        val subtitle = when {
            state.blockedByPolicy -> getString(R.string.tile_subtitle_blocked)
            remainingMinutes != null -> getString(R.string.tile_subtitle_auto_off_in, remainingMinutes)
            state.adbEnabled -> getString(R.string.tile_subtitle_on)
            else -> getString(R.string.tile_subtitle_off)
        }

        renderTile(
            iconRes = R.drawable.ic_qs_usb_debug,
            labelRes = R.string.tile_label,
            tileState = tileState,
            subtitle = subtitle
        )
    }

    override fun handleTileClick() {
        val state = controller.readState()
        if (state.blockedByPolicy) {
            refreshTile()
            return
        }
        if (controller.hasWritePermission()) {
            if (controller.toggleUsbDebugging()) {
                createAutoOffManager().scheduleIfEnabled(controller.readState().adbEnabled)
            }
            syncAllSurfaces()
        } else {
            openSettingsOrMain(state.devOptionsEnabled, requestCode = 0) {
                SettingsIntents.buildEnableAdbIntent()
            }
        }
    }
}
