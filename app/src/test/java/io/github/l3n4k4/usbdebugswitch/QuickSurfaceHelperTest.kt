package io.github.l3n4k4.usbdebugswitch

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.l3n4k4.usbdebugswitch.data.AppPreferences
import io.github.l3n4k4.usbdebugswitch.shortcut.QuickSurfaceHelper
import io.github.l3n4k4.usbdebugswitch.shortcut.QuickTileType
import io.github.l3n4k4.usbdebugswitch.shortcut.TileAddOutcome
import io.github.l3n4k4.usbdebugswitch.tile.UsbDebugTileService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickSurfaceHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("usb_debug_switch_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

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
            TileAddOutcome.Added("USB Debug"),
            QuickSurfaceHelper.mapTileAddResultCode(
                QuickSurfaceHelper.RESULT_TILE_ALREADY_ADDED,
                "USB Debug",
                wasReEnabled = true
            )
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

    @Test
    fun `isTileSpecInCsv and removeTileSpecFromCsv match custom and bare component specs`() {
        val component = ComponentName(context, UsbDebugTileService::class.java)
        val full = component.flattenToString()
        val short = component.flattenToShortString()

        assertTrue(QuickSurfaceHelper.isTileSpecInCsv("wifi,custom($short),bt", component))
        assertTrue(QuickSurfaceHelper.isTileSpecInCsv("wifi,custom($full),bt", component))
        assertTrue(QuickSurfaceHelper.isTileSpecInCsv("wifi,$short,bt", component))
        assertTrue(QuickSurfaceHelper.isTileSpecInCsv("wifi,$full,bt", component))
        assertFalse(QuickSurfaceHelper.isTileSpecInCsv("wifi,bt,flashlight", component))

        assertEquals(
            "wifi,bt,flashlight",
            QuickSurfaceHelper.removeTileSpecFromCsv("wifi, custom($short), bt, flashlight", component)
        )
        assertEquals(
            "wifi,flashlight",
            QuickSurfaceHelper.removeTileSpecFromCsv("wifi, custom($full), flashlight", component)
        )
    }

    @Test
    fun `removeQsTile disables tile component clears preference and updates readSurfaceState`() {
        val prefs = AppPreferences(context)
        val component = ComponentName(context, UsbDebugTileService::class.java)
        val short = component.flattenToShortString()
        prefs.setQsTileAdded(QuickTileType.USB_DEBUG.prefKey, true)
        Settings.Secure.putString(context.contentResolver, "sysui_qs_tiles", "wifi,custom($short),bt")

        val initialState = QuickSurfaceHelper.readSurfaceState(context, prefs)
        assertTrue(QuickTileType.USB_DEBUG in initialState.addedTiles)
        assertTrue(QuickSurfaceHelper.isTileComponentEnabled(context, UsbDebugTileService::class.java))

        val removedLabel = QuickSurfaceHelper.removeQsTile(context, QuickTileType.USB_DEBUG)
        assertEquals(context.getString(R.string.tile_label), removedLabel)
        assertFalse(AppPreferences(context).isQsTileAdded(QuickTileType.USB_DEBUG.prefKey))
        assertFalse(QuickSurfaceHelper.isTileComponentEnabled(context, UsbDebugTileService::class.java))
        assertEquals("wifi,bt", Settings.Secure.getString(context.contentResolver, "sysui_qs_tiles"))

        val updatedState = QuickSurfaceHelper.readSurfaceState(context, AppPreferences(context))
        assertFalse(QuickTileType.USB_DEBUG in updatedState.addedTiles)
    }
}
