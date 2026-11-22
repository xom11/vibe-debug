package io.github.l3n4k4.usbdebugswitch.tile

import android.service.quicksettings.Tile
import io.github.l3n4k4.usbdebugswitch.R
import io.github.l3n4k4.usbdebugswitch.navigation.SettingsIntents

/** Quick Settings tile for toggling "Stay awake while charging". */
class KeepAwakeTileService : BaseDebugTileService() {

    override fun refreshTile() {
        val state = controller.readState()

        val tileState = if (state.keepAwakeEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val subtitle = getString(
            if (state.keepAwakeEnabled) R.string.tile_subtitle_awake_charging else R.string.tile_subtitle_off
        )

        renderTile(
            iconRes = R.drawable.ic_qs_keep_awake,
            labelRes = R.string.tile_keep_awake_label,
            tileState = tileState,
            subtitle = subtitle
        )
    }

    override fun handleTileClick() {
        val state = controller.readState()
        if (controller.hasWritePermission()) {
            controller.toggleKeepAwake()
            syncAllSurfaces()
        } else {
            openSettingsOrMain(state.devOptionsEnabled, requestCode = 30) {
                SettingsIntents.buildDeveloperOptionsTopIntent()
            }
        }
    }
}
