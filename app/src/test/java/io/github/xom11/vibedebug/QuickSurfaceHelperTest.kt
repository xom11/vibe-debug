package io.github.xom11.vibedebug

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper
import io.github.xom11.vibedebug.shortcut.QuickTileType
import io.github.xom11.vibedebug.shortcut.TileAddOutcome
import io.github.xom11.vibedebug.tile.UsbDebugTileService
import io.github.xom11.vibedebug.widget.UsbDebugWidgetProvider
import io.github.xom11.vibedebug.widget.WidgetLayoutTier
import io.github.xom11.vibedebug.widget.WidgetToggleReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
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

    @Test
    fun `QuickTileType registers distinct service and widget providers for USB and Wireless`() {
        val entries = QuickTileType.entries
        assertEquals(2, entries.size)
        assertEquals(2, entries.map { it.serviceClass }.toSet().size)

        val state = QuickSurfaceHelper.readSurfaceState(context, AppPreferences(context))
        assertFalse(state.isHomeWidgetAdded)
    }

    @Test
    fun `UsbDebugWidgetProvider resolveTier maps dimensions to all 7 responsive tiers`() {
        assertEquals(WidgetLayoutTier.ICON_1X1, UsbDebugWidgetProvider.resolveTier(widthDp = 50, heightDp = 50))
        assertEquals(WidgetLayoutTier.TALL_1X2, UsbDebugWidgetProvider.resolveTier(widthDp = 56, heightDp = 130))
        assertEquals(WidgetLayoutTier.COMPACT_2X1, UsbDebugWidgetProvider.resolveTier(widthDp = 113, heightDp = 75))
        assertEquals(WidgetLayoutTier.WIDE_3X1, UsbDebugWidgetProvider.resolveTier(widthDp = 180, heightDp = 75))
        assertEquals(WidgetLayoutTier.STRIP_4X1, UsbDebugWidgetProvider.resolveTier(widthDp = 250, heightDp = 75))
        assertEquals(WidgetLayoutTier.SQUARE_2X2, UsbDebugWidgetProvider.resolveTier(widthDp = 113, heightDp = 155))
        assertEquals(WidgetLayoutTier.EXPANDED_4X2, UsbDebugWidgetProvider.resolveTier(widthDp = 250, heightDp = 155))
    }

    @Test
    @Config(sdk = [29])
    fun `UsbDebugWidgetProvider falls back to Bundle size options on API 29 across icon compact and wide tiers`() {
        val provider = UsbDebugWidgetProvider()
        val container = FrameLayout(context)

        val iconOptions = android.os.Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 50)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 50)
        }
        val inflatedIcon = provider.buildRemoteViews(context, appWidgetId = 1, options = iconOptions)
            .apply(context, container)
        assertTrue(inflatedIcon.findViewById<View>(R.id.widget_tile_box) != null)
        assertTrue(inflatedIcon.findViewById<View>(R.id.btn_hub_usb) == null)

        val compactOptions = android.os.Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 120)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 56)
        }
        val inflatedCompact = provider.buildRemoteViews(context, appWidgetId = 2, options = compactOptions)
            .apply(context, container)
        assertTrue(inflatedCompact.findViewById<View>(R.id.btn_hub_usb) != null)
        assertTrue(inflatedCompact.findViewById<View>(R.id.label_hub_usb) == null)
        assertEquals(View.GONE, inflatedCompact.findViewById<View>(R.id.btn_hub_wireless).visibility)

        val wideOptions = android.os.Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 56)
        }
        val inflatedWide = provider.buildRemoteViews(context, appWidgetId = 3, options = wideOptions)
            .apply(context, container)
        assertEquals(View.VISIBLE, inflatedWide.findViewById<View>(R.id.btn_hub_usb).visibility)
        assertTrue(inflatedWide.findViewById<TextView>(R.id.label_hub_usb) != null)
        assertEquals(View.GONE, inflatedWide.findViewById<View>(R.id.btn_hub_wireless).visibility)
    }

    @Test
    fun `UsbDebugWidgetProvider inflates all 7 tiers across Mode A Mode B AutoOff KeepAwake and blocked states`() {
        val provider = UsbDebugWidgetProvider()
        val container = FrameLayout(context)

        // Mode A default across all 7 tiers
        WidgetLayoutTier.entries.forEachIndexed { idx, tier ->
            val view = provider.buildRemoteViewsForTier(context, appWidgetId = 50 + idx, tier = tier)
                .apply(context, container)
            assertTrue(view != null)
        }

        // Mode B with active USB + Auto-Off deadline
        Shadows.shadowOf(context as Application)
            .grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)
        val prefs = AppPreferences(context)
        prefs.autoOffTargetAtMs = System.currentTimeMillis() + 30 * 60_000L

        val responsiveApplied = provider.buildRemoteViews(context, appWidgetId = 60).apply(context, container)
        assertTrue(responsiveApplied != null)

        val expanded = provider.buildRemoteViewsForTier(context, appWidgetId = 61, tier = WidgetLayoutTier.EXPANDED_4X2)
            .apply(context, container)
        val modeBadge = expanded.findViewById<TextView>(R.id.hub_mode_badge)
        val headerSub = expanded.findViewById<TextView>(R.id.hub_header_subtitle)
        assertEquals(context.getString(R.string.widget_mode_one_tap), modeBadge.text.toString())
        assertTrue(headerSub.text.toString().isNotBlank())

        // Policy-blocked state
        val userManager = context.getSystemService(android.os.UserManager::class.java)
        Shadows.shadowOf(userManager).setUserRestriction(
            android.os.Process.myUserHandle(),
            android.os.UserManager.DISALLOW_DEBUGGING_FEATURES,
            true
        )
        val blockedExpanded = provider.buildRemoteViewsForTier(context, appWidgetId = 62, tier = WidgetLayoutTier.EXPANDED_4X2)
            .apply(context, container)
        assertEquals(
            context.getString(R.string.widget_mode_blocked),
            blockedExpanded.findViewById<TextView>(R.id.hub_mode_badge).text.toString()
        )
    }

    @Test
    fun `widget toggle syncKeepAwakeWithUsb and refresh execute only through non-exported WidgetToggleReceiver`() {
        val pm = context.packageManager
        val receiverInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getReceiverInfo(
                ComponentName(context, WidgetToggleReceiver::class.java),
                PackageManager.ComponentInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getReceiverInfo(ComponentName(context, WidgetToggleReceiver::class.java), 0)
        }
        assertFalse(receiverInfo.exported)

        val providerReceiverInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getReceiverInfo(
                ComponentName(context, UsbDebugWidgetProvider::class.java),
                PackageManager.ComponentInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getReceiverInfo(ComponentName(context, UsbDebugWidgetProvider::class.java), 0)
        }
        assertTrue(providerReceiverInfo.exported)

        Shadows.shadowOf(context as Application)
            .grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
        AppPreferences(context).keepAwakeOnUsb = true

        // External broadcasts targeting exported UsbDebugWidgetProvider must not toggle privileged settings.
        UsbDebugWidgetProvider().onReceive(context, Intent(UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE))
        assertEquals(0, Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0))
        assertEquals(0, Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))

        // Internal broadcast targeting the non-exported WidgetToggleReceiver performs the 1-tap toggle and Keep Awake sync.
        WidgetToggleReceiver().onReceive(context, Intent(UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE))
        assertEquals(1, Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0))
        assertEquals(1, Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
        assertEquals(7, Settings.Global.getInt(context.contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0))

        // Refresh broadcast resyncs surfaces cleanly without disabling active ADB.
        WidgetToggleReceiver().onReceive(context, Intent(UsbDebugWidgetProvider.ACTION_WIDGET_REFRESH))
        assertEquals(1, Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0))

        // Turning USB Debugging OFF via widget also resets Stay Awake.
        WidgetToggleReceiver().onReceive(context, Intent(UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE))
        assertEquals(0, Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0))
        assertEquals(0, Settings.Global.getInt(context.contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0))
    }

    @Test
    fun `unified widget keeps adb_wifi_enabled off and shows Connect Wi-Fi state when Wi-Fi is disabled`() {
        Shadows.shadowOf(context as Application)
            .grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
        Settings.Global.putInt(context.contentResolver, Settings.Global.WIFI_ON, 0)
        Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 0)

        WidgetToggleReceiver().onReceive(context, Intent(UsbDebugWidgetProvider.ACTION_WIDGET_TOGGLE_WIRELESS))
        assertEquals(0, Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0))

        val container = FrameLayout(context)
        val square = UsbDebugWidgetProvider()
            .buildRemoteViewsForTier(context, appWidgetId = 90, tier = WidgetLayoutTier.SQUARE_2X2)
            .apply(context, container)
        assertEquals(
            context.getString(R.string.widget_state_need_wifi),
            square.findViewById<TextView>(R.id.state_hub_wireless).text.toString()
        )

        val expanded = UsbDebugWidgetProvider()
            .buildRemoteViewsForTier(context, appWidgetId = 91, tier = WidgetLayoutTier.EXPANDED_4X2)
            .apply(context, container)
        assertEquals(
            context.getString(R.string.tile_subtitle_need_wifi),
            expanded.findViewById<TextView>(R.id.state_hub_wireless).text.toString()
        )
    }
}
