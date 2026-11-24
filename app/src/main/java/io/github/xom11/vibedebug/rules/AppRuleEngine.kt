package io.github.xom11.vibedebug.rules

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.content.ContextCompat
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.data.AppRuleMode
import io.github.xom11.vibedebug.data.AutoOffManager
import io.github.xom11.vibedebug.data.DevSettingsController

/** Queries device Usage Access state and foreground app transitions. */
interface ForegroundAppDetector {
    fun hasUsageAccess(): Boolean
    fun isScreenInteractive(): Boolean
    fun queryLatestForegroundPackage(
        fromMs: Long,
        toMs: Long,
        transientPackages: Set<String>
    ): Pair<String?, Long>
}

/** [ForegroundAppDetector] backed by Android [UsageStatsManager], [AppOpsManager], and [PowerManager]. */
class AndroidForegroundAppDetector(context: Context) : ForegroundAppDetector {

    private val appContext = context.applicationContext ?: context

    override fun hasUsageAccess(): Boolean {
        val ops = appContext.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }
        if (mode == AppOpsManager.MODE_ALLOWED) return true
        if (mode != AppOpsManager.MODE_DEFAULT) return false
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.PACKAGE_USAGE_STATS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val usm = appContext.getSystemService(UsageStatsManager::class.java) ?: return false
        val now = System.currentTimeMillis()
        val probe = runCatching { usm.queryEvents(now - 24 * 60 * 60_000L, now) }.getOrNull()
        return probe?.hasNextEvent() == true
    }

    override fun isScreenInteractive(): Boolean =
        appContext.getSystemService(PowerManager::class.java)?.isInteractive != false

    override fun queryLatestForegroundPackage(
        fromMs: Long,
        toMs: Long,
        transientPackages: Set<String>
    ): Pair<String?, Long> {
        val usm = appContext.getSystemService(UsageStatsManager::class.java) ?: return null to 0L
        val events = runCatching { usm.queryEvents(fromMs, toMs) }.getOrNull() ?: return null to 0L
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var maxTs = 0L
        val targetEventType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp
            if (ts > maxTs) {
                maxTs = ts
            }
            if (event.eventType == targetEventType) {
                val pkg = event.packageName
                if (!pkg.isNullOrBlank() && pkg !in transientPackages) {
                    latestPackage = pkg
                }
            }
        }
        return latestPackage to maxTs
    }
}

/**
 * Watches foreground app transitions via [ForegroundAppDetector] so developer/debugging flags
 * are automatically turned off while a user-selected app is active and restored after leaving.
 */
class AppRuleEngine(
    private val prefs: AppPreferences,
    private val controller: DevSettingsController,
    private val detector: ForegroundAppDetector,
    private val autoOffManager: AutoOffManager? = null,
    private val onSurfaceSync: () -> Unit = {},
) {

    private var currentForeground: String? = null
    private var lastEventTs: Long = 0L
    private var restoreDeadlineMs: Long = 0L
    private var suppressRestoreUntilMs: Long = 0L
    private var cachedUsageAccess: Boolean = false
    private var usageCheckedAtMs: Long? = null

    fun isWatcherConfigured(): Boolean =
        prefs.autoAppRulesEnabled &&
            prefs.safeLaunchPackages.isNotEmpty() &&
            controller.hasWritePermission() &&
            !controller.readState().blockedByPolicy

    @Synchronized
    fun poll(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!isWatcherConfigured()) return false
        if (!detector.isScreenInteractive()) return true

        if (lastEventTs > nowMs) {
            lastEventTs = 0L
        }
        val lastCheck = usageCheckedAtMs
        if (lastCheck == null || nowMs < lastCheck || nowMs - lastCheck >= USAGE_RECHECK_MS) {
            cachedUsageAccess = detector.hasUsageAccess()
            usageCheckedAtMs = nowMs
        }
        if (!cachedUsageAccess) return false

        val fromMs = if (lastEventTs == 0L) {
            (nowMs - INITIAL_LOOKBACK_MS).coerceAtLeast(0L)
        } else {
            (lastEventTs - QUERY_OVERLAP_MS).coerceAtLeast(0L)
        }
        val (detectedFg, maxTs) = detector.queryLatestForegroundPackage(
            fromMs = fromMs,
            toMs = nowMs,
            transientPackages = TRANSIENT_PACKAGES
        )
        if (maxTs > lastEventTs) {
            lastEventTs = maxTs
        }

        val previousFg = currentForeground
        val fg = detectedFg ?: previousFg
        if (fg != previousFg) {
            currentForeground = fg
        }

        val listedPackages = prefs.safeLaunchPackages.toSet()
        val inListedApp = fg != null && fg in listedPackages

        if (fg != null && fg in listedPackages && fg != previousFg) {
            restoreDeadlineMs = 0L
            applyRuleForPackage(
                pkg = fg,
                mode = prefs.getAppRuleMode(fg),
                recordHold = prefs.autoRestoreOnExit
            )
        }

        val hasHold = prefs.heldRulePackage.isNotEmpty()
        when {
            inListedApp -> restoreDeadlineMs = 0L
            !hasHold -> restoreDeadlineMs = 0L
            !prefs.autoRestoreOnExit -> {
                prefs.clearRuleHold()
                restoreDeadlineMs = 0L
            }
            nowMs < suppressRestoreUntilMs -> Unit
            restoreDeadlineMs == 0L -> restoreDeadlineMs = nowMs + RESTORE_DELAY_MS
            nowMs >= restoreDeadlineMs -> {
                restoreDeadlineMs = 0L
                restoreHeldState()
            }
        }
        return true
    }

    /** Turns off debugging according to [mode] and optionally persists pre-rule flags for restore. */
    @Synchronized
    fun applyRuleForPackage(
        pkg: String,
        mode: AppRuleMode,
        recordHold: Boolean
    ): Boolean {
        val state = controller.readState()
        if (state.blockedByPolicy || !controller.hasWritePermission()) return false

        var dev = prefs.heldDevOptions
        var usb = prefs.heldUsbDebug
        var wifi = prefs.heldWirelessDebug
        var changed = false

        if (mode == AppRuleMode.DEV_OFF) {
            if (state.devOptionsEnabled || state.adbEnabled || (state.supportsWirelessAdb && state.wirelessAdbEnabled)) {
                dev = dev || state.devOptionsEnabled
                usb = usb || state.adbEnabled
                wifi = wifi || (state.supportsWirelessAdb && state.wirelessAdbEnabled)
                changed = controller.setDeveloperOptions(false) || changed
            }
        } else {
            if (state.adbEnabled) {
                usb = true
                changed = controller.setUsbDebugging(false) || changed
            }
            if (state.supportsWirelessAdb && state.wirelessAdbEnabled) {
                wifi = true
                changed = controller.setWirelessDebugging(false) || changed
            }
        }

        if (recordHold) {
            if (dev || usb || wifi) {
                prefs.saveRuleHold(pkg, dev = dev, usb = usb, wifi = wifi)
            }
        } else {
            prefs.clearRuleHold()
        }

        if (changed) {
            val updated = controller.readState()
            controller.syncKeepAwakeWithUsb(prefs.keepAwakeOnUsb)
            if (!updated.adbEnabled) {
                autoOffManager?.cancel()
            }
            onSurfaceSync()
        }
        return changed
    }

    /** Restores only the debugging flags that were previously disabled by an app rule. */
    @Synchronized
    fun restoreHeldState(): Boolean {
        val heldPkg = prefs.heldRulePackage
        val dev = prefs.heldDevOptions
        val usb = prefs.heldUsbDebug
        val wifi = prefs.heldWirelessDebug
        prefs.clearRuleHold()
        if (heldPkg.isEmpty() || (!dev && !usb && !wifi)) return false

        val state = controller.readState()
        if (state.blockedByPolicy || !controller.hasWritePermission()) return false

        var changed = false
        if (dev && !controller.readState().devOptionsEnabled) {
            changed = controller.setDeveloperOptions(true) || changed
        }
        if (usb && !controller.readState().adbEnabled) {
            changed = controller.setUsbDebugging(true) || changed
        }
        if (wifi && controller.readState().supportsWirelessAdb && !controller.readState().wirelessAdbEnabled) {
            changed = controller.setWirelessDebugging(true) || changed
        }

        if (changed) {
            val updated = controller.readState()
            controller.syncKeepAwakeWithUsb(prefs.keepAwakeOnUsb)
            autoOffManager?.scheduleIfEnabled(updated.adbEnabled)
            onSurfaceSync()
        }
        return changed
    }

    @Synchronized
    internal fun currentForegroundPackage(): String? = currentForeground

    /** Marks [packageName] as launched via Safe Launch so restore waits during app startup. */
    @Synchronized
    fun markLaunchedFromUi(packageName: String, nowMs: Long = System.currentTimeMillis()) {
        currentForeground = packageName
        restoreDeadlineMs = 0L
        suppressRestoreUntilMs = nowMs + SAFE_LAUNCH_GRACE_MS
    }

    /** Restores or clears held state when the watcher is turned off by the user. */
    @Synchronized
    fun releaseHoldOnStop() {
        restoreDeadlineMs = 0L
        if (prefs.heldRulePackage.isEmpty()) return
        if (prefs.autoRestoreOnExit) {
            restoreHeldState()
        } else {
            prefs.clearRuleHold()
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 200L
        const val RESTORE_DELAY_MS = 8_000L
        const val SAFE_LAUNCH_GRACE_MS = 5_000L
        private const val INITIAL_LOOKBACK_MS = 10 * 60_000L
        private const val QUERY_OVERLAP_MS = 50L
        private const val USAGE_RECHECK_MS = 10_000L

        /** Transient system overlays/prompts that should not count as leaving a banking app. */
        val TRANSIENT_PACKAGES: Set<String> = setOf(
            "android",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.oplus.securitypermission",
            "com.coloros.securitypermission",
            "com.oplus.biometrics",
            "com.coloros.biometrics",
            "com.samsung.android.biometrics.app.setting",
            "com.samsung.android.samsungpass",
            "com.samsung.android.authfw",
            "com.lbe.security.miui",
            "com.miui.securitycenter",
            "com.miui.biometric",
            "com.vivo.permissionmanager",
            "com.iqoo.secure",
            "com.vivo.fingerprintui",
        )
    }
}
