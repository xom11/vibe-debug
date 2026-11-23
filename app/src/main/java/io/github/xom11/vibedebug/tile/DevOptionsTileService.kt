package io.github.xom11.vibedebug.tile

import android.service.quicksettings.Tile
import io.github.xom11.vibedebug.R
import io.github.xom11.vibedebug.navigation.SettingsIntents

/** Quick Settings tile for toggling the master Developer Options switch. */
class DevOptionsTileService : BaseDebugTileService() {

    override fun refreshTile() {
        val state = controller.readState()

        val tileState = when {
            state.blockedByPolicy -> Tile.STATE_UNAVAILABLE
            state.devOptionsEnabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        val subtitle = when {
            state.blockedByPolicy -> getString(R.string.tile_subtitle_blocked)
            state.devOptionsEnabled -> getString(R.string.tile_subtitle_on)
            else -> getString(R.string.tile_subtitle_off)
        }

        renderTile(
            iconRes = R.drawable.ic_qs_dev_options,
            labelRes = R.string.tile_dev_options_label,
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
            if (controller.toggleDeveloperOptions() && !controller.readState().devOptionsEnabled) {
                createAutoOffManager().cancel()
            }
            syncAllSurfaces()
        } else {
            openSettingsOrMain(state.devOptionsEnabled, requestCode = 20) {
                SettingsIntents.buildDeveloperOptionsTopIntent()
            }
        }
    }
}
