package io.github.xom11.vibedebug.tile

import android.service.quicksettings.Tile
import io.github.xom11.vibedebug.R
import io.github.xom11.vibedebug.navigation.SettingsIntents

/** Quick Settings tile for Wireless Debugging (Android 11+). */
class WirelessDebugTileService : BaseDebugTileService() {

    override fun refreshTile() {
        val state = controller.readState()

        val tileState = when {
            state.blockedByPolicy || !state.supportsWirelessAdb -> Tile.STATE_UNAVAILABLE
            state.wirelessAdbEnabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        val subtitle = when {
            state.blockedByPolicy -> getString(R.string.tile_subtitle_blocked)
            !state.supportsWirelessAdb -> getString(R.string.tile_subtitle_unsupported)
            state.wirelessAdbEnabled -> getString(R.string.tile_subtitle_on)
            else -> getString(R.string.tile_subtitle_off)
        }

        renderTile(
            iconRes = R.drawable.ic_qs_wireless_debug,
            labelRes = R.string.tile_wireless_label,
            tileState = tileState,
            subtitle = subtitle
        )
    }

    override fun handleTileClick() {
        val state = controller.readState()
        if (!state.supportsWirelessAdb || state.blockedByPolicy) {
            refreshTile()
            return
        }
        if (controller.hasWritePermission()) {
            controller.toggleWirelessDebugging()
            syncAllSurfaces()
        } else {
            openSettingsOrMain(state.devOptionsEnabled, requestCode = 10) {
                SettingsIntents.buildEnableWirelessAdbIntent()
            }
        }
    }
}
