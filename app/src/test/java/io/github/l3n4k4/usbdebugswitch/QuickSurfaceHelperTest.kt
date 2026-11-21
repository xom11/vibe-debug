package io.github.l3n4k4.usbdebugswitch

import io.github.l3n4k4.usbdebugswitch.shortcut.QuickSurfaceHelper
import io.github.l3n4k4.usbdebugswitch.shortcut.TileAddOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSurfaceHelperTest {

    @Test
    fun `mapTileAddResultCode maps standard StatusBarManager codes accurately`() {
        assertEquals(
            TileAddOutcome.Added("USB Debug"),
            QuickSurfaceHelper.mapTileAddResultCode(QuickSurfaceHelper.RESULT_TILE_ADDED, "USB Debug")
        )
        assertEquals(
            TileAddOutcome.AlreadyAdded("USB Debug"),
            QuickSurfaceHelper.mapTileAddResultCode(QuickSurfaceHelper.RESULT_TILE_ALREADY_ADDED, "USB Debug")
        )
        assertEquals(
            TileAddOutcome.Dismissed,
            QuickSurfaceHelper.mapTileAddResultCode(QuickSurfaceHelper.RESULT_TILE_NOT_ADDED, "USB Debug")
        )
        assertEquals(
            TileAddOutcome.NeedsManualInstructions("USB Debug"),
            QuickSurfaceHelper.mapTileAddResultCode(1004, "USB Debug")
        )
    }
}
