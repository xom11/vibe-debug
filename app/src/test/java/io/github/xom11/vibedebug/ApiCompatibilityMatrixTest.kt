package io.github.xom11.vibedebug

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import io.github.xom11.vibedebug.data.AndroidSettingsGateway
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.data.AutoOffManager
import io.github.xom11.vibedebug.data.DevSettingsController
import io.github.xom11.vibedebug.navigation.SettingsIntents
import io.github.xom11.vibedebug.rules.AppWatcherService
import io.github.xom11.vibedebug.rules.BootCompletedReceiver
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper
import io.github.xom11.vibedebug.shortcut.TileAddOutcome
import io.github.xom11.vibedebug.widget.UsbDebugWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 29, 31, 33, 34, 35])
class ApiCompatibilityMatrixTest {

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
    fun `settings gateway supportsWirelessAdb and observer lifecycle behave cleanly on every API`() {
        val gateway = AndroidSettingsGateway(context)
        assertEquals(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R, gateway.supportsWirelessAdb)

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {}
        gateway.registerObserver(observer)
        val state = DevSettingsController(gateway).readState()
        assertEquals(gateway.supportsWirelessAdb, state.supportsWirelessAdb)
        gateway.unregisterObserver(observer)
    }

    @Test
    fun `quick surfaces and tile requests follow API level contracts without throwing`() {
        val initial = QuickSurfaceHelper.readSurfaceState(context)
        assertFalse(initial.isHomeWidgetAdded)
        QuickSurfaceHelper.syncAllSurfaces(context)

        var capturedOutcome: TileAddOutcome? = null
        QuickSurfaceHelper.requestAddUsbTile(context) { outcome ->
            capturedOutcome = outcome
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            assertTrue(
                "Expected manual fallback on API ${Build.VERSION.SDK_INT}",
                capturedOutcome is TileAddOutcome.NeedsManualInstructions
            )
        }
        assertFalse(SettingsIntents.hasMiuiPermissionEditor(context))
    }

    @Test
    fun `widget builds and inflates in both Mode A and Mode B across API 26 to 35`() {
        val provider = UsbDebugWidgetProvider()
        val container = FrameLayout(context)
        val sizes = listOf(110 to 40, 220 to 48, 220 to 110, 320 to 120)

        sizes.forEachIndexed { idx, (w, h) ->
            val opts = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
            }
            val views = provider.buildRemoteViews(context, appWidgetId = idx + 1, options = opts)
            assertNotNull(views.apply(context, container))
        }

        Shadows.shadowOf(context as Application)
            .grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS)
        Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)
        AppPreferences(context).autoOffTargetAtMs = System.currentTimeMillis() + 30 * 60_000L

        assertNotNull(provider.buildRemoteViews(context, appWidgetId = 20).apply(context, container))
    }

    @Test
    fun `autoOffManager alarm and boot receiver run safely across API 26 to 35`() {
        val prefs = AppPreferences(context).apply {
            autoOffEnabled = true
            autoOffMinutes = 30
        }
        val manager = AutoOffManager(context, prefs)
        manager.start(30)

        assertTrue(prefs.autoOffTargetAtMs > System.currentTimeMillis())
        val remaining = manager.remainingMinutes()
        assertNotNull(remaining)
        assertTrue(remaining!! in 29..30)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertNotNull(Shadows.shadowOf(alarmManager).nextScheduledAlarm)

        manager.cancel()
        assertEquals(0L, prefs.autoOffTargetAtMs)

        assertFalse(AppWatcherService.shouldRun(context))
        BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }
}
