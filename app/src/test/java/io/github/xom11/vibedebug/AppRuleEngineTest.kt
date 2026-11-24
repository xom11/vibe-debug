package io.github.xom11.vibedebug

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.data.AppRuleMode
import io.github.xom11.vibedebug.data.DevSettingsController
import io.github.xom11.vibedebug.data.SettingsGateway
import io.github.xom11.vibedebug.rules.AppRuleEngine
import io.github.xom11.vibedebug.rules.AppWatcherService
import io.github.xom11.vibedebug.rules.ForegroundAppDetector
import io.github.xom11.vibedebug.shortcut.SafeLaunchHelper
import io.github.xom11.vibedebug.shortcut.SafeLaunchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppRuleEngineTest {

    private class FakeSettingsGateway(
        override var supportsWirelessAdb: Boolean = true,
        var hasPermission: Boolean = true,
        var devOptions: Boolean = true,
        var adb: Boolean = true,
        var wirelessAdb: Boolean = true,
        var keepAwake: Boolean = false,
        var blockedByPolicy: Boolean = false,
        var wifiEnabled: Boolean = true,
    ) : SettingsGateway {
        override fun hasWriteSecureSettings(): Boolean = hasPermission
        override fun isDevOptionsEnabled(): Boolean = devOptions
        override fun isAdbEnabled(): Boolean = adb
        override fun isWirelessAdbEnabled(): Boolean = wirelessAdb
        override fun isKeepAwakeEnabled(): Boolean = keepAwake
        override fun isWifiEnabled(): Boolean = wifiEnabled
        override fun isDebuggingBlockedByPolicy(): Boolean = blockedByPolicy

        override fun putDevOptionsEnabled(enabled: Boolean): Boolean {
            if (!hasPermission) return false
            devOptions = enabled
            return true
        }

        override fun putAdbEnabled(enabled: Boolean): Boolean {
            if (!hasPermission) return false
            adb = enabled
            return true
        }

        override fun putWirelessAdbEnabled(enabled: Boolean): Boolean {
            if (!hasPermission || !supportsWirelessAdb) return false
            wirelessAdb = enabled
            return true
        }

        override fun putKeepAwakeEnabled(enabled: Boolean): Boolean {
            if (!hasPermission) return false
            keepAwake = enabled
            return true
        }
    }

    private class FakeForegroundDetector(
        var usageAccess: Boolean = true,
        var screenInteractive: Boolean = true,
    ) : ForegroundAppDetector {
        val eventTimeline = mutableListOf<Pair<Long, String>>()

        fun emit(timestampMs: Long, packageName: String) {
            eventTimeline += timestampMs to packageName
        }

        override fun hasUsageAccess(): Boolean = usageAccess

        override fun isScreenInteractive(): Boolean = screenInteractive

        override fun queryLatestForegroundPackage(
            fromMs: Long,
            toMs: Long,
            transientPackages: Set<String>
        ): Pair<String?, Long> {
            val windowEvents = eventTimeline.filter { (ts, _) -> ts in fromMs..toMs }
            val maxTs = windowEvents.maxOfOrNull { it.first } ?: 0L
            val latest = windowEvents.lastOrNull { (_, pkg) -> pkg !in transientPackages }?.second
            return latest to maxTs
        }
    }

    private lateinit var context: Context
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("usb_debug_switch_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = AppPreferences(context)
    }

    @Test
    fun `DEV_OFF rule disables dev options and debugging on entry then restores after 8s exit delay`() {
        val gateway = FakeSettingsGateway(devOptions = true, adb = true, wirelessAdb = true)
        val controller = DevSettingsController(gateway)
        val detector = FakeForegroundDetector()
        var surfaceSyncs = 0
        val engine = AppRuleEngine(prefs, controller, detector, onSurfaceSync = { surfaceSyncs++ })

        prefs.addSafeLaunchPackage("com.vietinbank.ipay")
        prefs.setAppRuleMode("com.vietinbank.ipay", AppRuleMode.DEV_OFF)
        prefs.autoAppRulesEnabled = true
        prefs.autoRestoreOnExit = true

        detector.emit(100_000L, "com.android.launcher")
        assertTrue(engine.poll(nowMs = 100_100L))
        assertTrue(gateway.devOptions && gateway.adb && gateway.wirelessAdb)

        // User taps regular VietinBank icon -> engine turns everything off & holds state.
        detector.emit(101_000L, "com.vietinbank.ipay")
        assertTrue(engine.poll(nowMs = 101_200L))
        assertFalse(gateway.devOptions)
        assertFalse(gateway.adb)
        assertFalse(gateway.wirelessAdb)
        assertEquals("com.vietinbank.ipay", prefs.heldRulePackage)
        assertTrue(prefs.heldDevOptions && prefs.heldUsbDebug && prefs.heldWirelessDebug)
        assertEquals(1, surfaceSyncs)

        // User leaves to home screen; should stay off before 8 seconds pass.
        detector.emit(105_000L, "com.android.launcher")
        assertTrue(engine.poll(nowMs = 105_200L))
        assertTrue(engine.poll(nowMs = 112_900L))
        assertFalse(gateway.devOptions)
        assertFalse(gateway.adb)

        // Once 8_000 ms have passed since starting the countdown, flags restore cleanly.
        assertTrue(engine.poll(nowMs = 113_300L))
        assertTrue(gateway.devOptions)
        assertTrue(gateway.adb)
        assertTrue(gateway.wirelessAdb)
        assertEquals("", prefs.heldRulePackage)
        assertFalse(prefs.heldDevOptions)
        assertEquals(2, surfaceSyncs)
    }

    @Test
    fun `USB_OFF rule keeps developer options enabled while toggling USB and wireless debugging`() {
        val gateway = FakeSettingsGateway(devOptions = true, adb = true, wirelessAdb = false)
        val controller = DevSettingsController(gateway)
        val detector = FakeForegroundDetector()
        val engine = AppRuleEngine(prefs, controller, detector)

        prefs.addSafeLaunchPackage("com.VCB")
        prefs.setAppRuleMode("com.VCB", AppRuleMode.USB_OFF)
        prefs.autoAppRulesEnabled = true
        prefs.autoRestoreOnExit = true

        detector.emit(200_000L, "com.VCB")
        assertTrue(engine.poll(nowMs = 200_100L))
        assertTrue(gateway.devOptions)
        assertFalse(gateway.adb)
        assertFalse(gateway.wirelessAdb)
        assertFalse(prefs.heldDevOptions)
        assertTrue(prefs.heldUsbDebug)
        assertFalse(prefs.heldWirelessDebug)

        detector.emit(202_000L, "com.android.launcher")
        assertTrue(engine.poll(nowMs = 202_100L))
        assertTrue(engine.poll(nowMs = 210_200L))
        assertTrue(gateway.devOptions)
        assertTrue(gateway.adb)
        assertFalse(gateway.wirelessAdb)
    }

    @Test
    fun `transient system overlays do not trigger restore while protected app is open`() {
        val gateway = FakeSettingsGateway(devOptions = true, adb = true, wirelessAdb = false)
        val controller = DevSettingsController(gateway)
        val detector = FakeForegroundDetector()
        val engine = AppRuleEngine(prefs, controller, detector)

        prefs.addSafeLaunchPackage("com.vietinbank.ipay")
        prefs.autoAppRulesEnabled = true
        prefs.autoRestoreOnExit = true

        detector.emit(300_000L, "com.vietinbank.ipay")
        engine.poll(nowMs = 300_100L)
        assertFalse(gateway.devOptions)

        // Biometric prompt / notification shade / permission controller appears for > 15s.
        detector.emit(301_000L, "com.android.systemui")
        detector.emit(305_000L, "com.oplus.securitypermission")
        engine.poll(nowMs = 306_000L)
        engine.poll(nowMs = 320_000L)
        assertFalse(gateway.devOptions)
        assertEquals("com.vietinbank.ipay", prefs.heldRulePackage)
    }

    @Test
    fun `quick OTP hop under 8s cancels restore countdown when returning to listed app`() {
        val gateway = FakeSettingsGateway(devOptions = true, adb = true, wirelessAdb = false)
        val controller = DevSettingsController(gateway)
        val detector = FakeForegroundDetector()
        val engine = AppRuleEngine(prefs, controller, detector)

        prefs.addSafeLaunchPackage("com.vietinbank.ipay")
        prefs.autoAppRulesEnabled = true
        prefs.autoRestoreOnExit = true

        detector.emit(400_000L, "com.vietinbank.ipay")
        engine.poll(nowMs = 400_100L)
        assertFalse(gateway.devOptions)

        // Hop to SMS / Authenticator for 4 seconds to grab OTP, then return.
        detector.emit(401_000L, "com.google.android.apps.messaging")
        engine.poll(nowMs = 401_200L)
        engine.poll(nowMs = 405_000L)
        assertFalse(gateway.devOptions)

        detector.emit(405_500L, "com.vietinbank.ipay")
        engine.poll(nowMs = 405_700L)
        // Stay inside banking app for another 15s — must remain off continuously.
        engine.poll(nowMs = 420_000L)
        assertFalse(gateway.devOptions)
        assertFalse(gateway.adb)
        assertEquals("com.vietinbank.ipay", prefs.heldRulePackage)
    }

    @Test
    fun `autoRestoreOnExit false disables debugging without restoring on exit`() {
        val gateway = FakeSettingsGateway(devOptions = true, adb = true, wirelessAdb = true)
        val controller = DevSettingsController(gateway)
        val detector = FakeForegroundDetector()
        val engine = AppRuleEngine(prefs, controller, detector)

        prefs.addSafeLaunchPackage("com.vietinbank.ipay")
        prefs.autoAppRulesEnabled = true
        prefs.autoRestoreOnExit = false

        detector.emit(500_000L, "com.vietinbank.ipay")
        engine.poll(nowMs = 500_100L)
        assertFalse(gateway.devOptions)
        assertEquals("", prefs.heldRulePackage)

        detector.emit(502_000L, "com.android.launcher")
        engine.poll(nowMs = 502_100L)
        engine.poll(nowMs = 515_000L)
        assertFalse(gateway.devOptions)
        assertFalse(gateway.adb)
    }

    @Test
    fun `AppPreferences persists auto rules state per-app modes and hold cleanup`() {
        assertTrue(prefs.autoAppRulesEnabled)
        assertTrue(prefs.autoRestoreOnExit)
        assertEquals(AppRuleMode.DEV_OFF, prefs.getAppRuleMode("com.vietinbank.ipay"))

        prefs.autoAppRulesEnabled = false
        assertFalse(AppPreferences(context).autoAppRulesEnabled)

        prefs.autoAppRulesEnabled = true
        prefs.autoRestoreOnExit = false
        prefs.addSafeLaunchPackage("com.vietinbank.ipay")
        prefs.setAppRuleMode("com.vietinbank.ipay", AppRuleMode.USB_OFF)
        prefs.saveRuleHold("com.vietinbank.ipay", dev = false, usb = true, wifi = true)

        val reloaded = AppPreferences(context)
        assertTrue(reloaded.autoAppRulesEnabled)
        assertFalse(reloaded.autoRestoreOnExit)
        assertEquals(AppRuleMode.USB_OFF, reloaded.getAppRuleMode("com.vietinbank.ipay"))
        assertEquals("com.vietinbank.ipay", reloaded.heldRulePackage)
        assertFalse(reloaded.heldDevOptions)
        assertTrue(reloaded.heldUsbDebug)
        assertTrue(reloaded.heldWirelessDebug)

        reloaded.removeSafeLaunchPackage("com.vietinbank.ipay")
        assertEquals(AppRuleMode.DEV_OFF, AppPreferences(context).getAppRuleMode("com.vietinbank.ipay"))
        reloaded.clearRuleHold()
        assertEquals("", AppPreferences(context).heldRulePackage)
    }

    @Test
    fun `SafeLaunchHelper launchSafely respects per-app mode and records hold when auto-restore active`() {
        val app = context as Application
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.WRITE_SECURE_SETTINGS,
            Manifest.permission.PACKAGE_USAGE_STATS,
        )
        val appOps = context.getSystemService(AppOpsManager::class.java)
        Shadows.shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED,
        )

        val targetPkg = "com.vietinbank.ipay"
        val component = ComponentName(targetPkg, "$targetPkg.MainActivity")
        val shadowPm = Shadows.shadowOf(context.packageManager)
        shadowPm.addActivityIfNotPresent(component)
        shadowPm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        )

        Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)

        prefs.autoAppRulesEnabled = true
        prefs.autoRestoreOnExit = true
        prefs.addSafeLaunchPackage(targetPkg)
        prefs.setAppRuleMode(targetPkg, AppRuleMode.USB_OFF)

        val result = SafeLaunchHelper.launchSafely(context, targetPkg)
        assertEquals(SafeLaunchResult.LaunchedClean, result)
        assertEquals(1, Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
        assertEquals(0, Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1))
        assertEquals(targetPkg, prefs.heldRulePackage)
        assertFalse(prefs.heldDevOptions)
        assertTrue(prefs.heldUsbDebug)

        // Switching the rule to DEV_OFF cascades Developer Options off as well.
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)
        prefs.clearRuleHold()
        prefs.setAppRuleMode(targetPkg, AppRuleMode.DEV_OFF)

        assertEquals(SafeLaunchResult.LaunchedClean, SafeLaunchHelper.launchSafely(context, targetPkg))
        assertEquals(0, Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1))
        assertEquals(0, Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1))
        assertTrue(prefs.heldDevOptions)
        assertTrue(prefs.heldUsbDebug)
    }

    @Test
    fun `AppWatcherService formats watching and holding notification text cleanly across locales`() {
        val enContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        )
        val viContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("vi")) }
        )

        val (enOneTitle, enOneBody) = AppWatcherService.resolveNotificationText(
            context = enContext,
            heldPkg = "",
            protectedCount = 1,
            autoRestoreOnExit = true,
        )
        assertEquals("Watching 1 protected app", enOneTitle)
        assertFalse(enOneTitle.contains("%"))
        assertFalse(enOneBody.contains("%"))

        val (enMultiTitle, enMultiBody) = AppWatcherService.resolveNotificationText(
            context = enContext,
            heldPkg = "",
            protectedCount = 3,
            autoRestoreOnExit = true,
        )
        assertEquals("Watching 3 protected apps", enMultiTitle)
        assertFalse(enMultiTitle.contains("%"))
        assertFalse(enMultiBody.contains("%"))

        val (viOneTitle, viOneBody) = AppWatcherService.resolveNotificationText(
            context = viContext,
            heldPkg = "",
            protectedCount = 1,
            autoRestoreOnExit = true,
        )
        assertEquals("Đang canh 1 app tự tắt gỡ lỗi", viOneTitle)
        assertFalse(viOneTitle.contains("%"))
        assertFalse(viOneBody.contains("%"))

        val (viMultiTitle, viMultiBody) = AppWatcherService.resolveNotificationText(
            context = viContext,
            heldPkg = "",
            protectedCount = 3,
            autoRestoreOnExit = true,
        )
        assertEquals("Đang canh 3 app tự tắt gỡ lỗi", viMultiTitle)
        assertFalse(viMultiTitle.contains("%"))
        assertFalse(viMultiBody.contains("%"))

        val (enHoldTitle, enHoldRestoreBody) = AppWatcherService.resolveNotificationText(
            context = enContext,
            heldPkg = "com.vietinbank.ipay",
            protectedCount = 2,
            autoRestoreOnExit = true,
        )
        assertTrue(enHoldTitle.contains("com.vietinbank.ipay"))
        assertFalse(enHoldTitle.contains("%"))
        assertEquals(enContext.getString(R.string.watcher_notif_holding_restore_body), enHoldRestoreBody)

        val (viHoldTitle, viHoldStayOffBody) = AppWatcherService.resolveNotificationText(
            context = viContext,
            heldPkg = "com.vietinbank.ipay",
            protectedCount = 2,
            autoRestoreOnExit = false,
        )
        assertTrue(viHoldTitle.contains("com.vietinbank.ipay"))
        assertFalse(viHoldTitle.contains("%"))
        assertEquals(viContext.getString(R.string.watcher_notif_holding_stay_off_body), viHoldStayOffBody)
    }

    @Test
    fun `AppWatcherService posts foreground notification with formatted protected app count`() {
        val app = context as Application
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.WRITE_SECURE_SETTINGS,
            Manifest.permission.PACKAGE_USAGE_STATS,
        )
        val appOps = context.getSystemService(AppOpsManager::class.java)
        Shadows.shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED,
        )

        prefs.autoAppRulesEnabled = true
        prefs.addSafeLaunchPackage("com.vietinbank.ipay")
        prefs.addSafeLaunchPackage("com.VCB")

        val serviceController = Robolectric.buildService(AppWatcherService::class.java)
            .create()
            .startCommand(0, 1)
        try {
            val service = serviceController.get()
            val notifManager = context.getSystemService(NotificationManager::class.java)
            val fgNotification = Shadows.shadowOf(service).lastForegroundNotification
                ?: Shadows.shadowOf(notifManager).getNotification(AppWatcherService.NOTIFICATION_ID)
            assertNotNull(fgNotification)

            val title = NotificationCompat.getContentTitle(fgNotification!!)?.toString().orEmpty()
            val body = NotificationCompat.getContentText(fgNotification)?.toString().orEmpty()
            assertTrue("Expected formatted app count in title: $title", title.contains("2"))
            assertFalse("Unexpected unformatted % in title: $title", title.contains("%"))
            assertFalse("Unexpected unformatted % in body: $body", body.contains("%"))
        } finally {
            serviceController.destroy()
        }
    }
}
