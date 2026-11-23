package io.github.xom11.vibedebug.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper

/**
 * Schedules an inexact auto-off timer via [AlarmManager.setWindow] to turn off USB debugging
 * without requiring exact-alarm or foreground-service permissions.
 */
class AutoOffManager(private val context: Context, private val prefs: AppPreferences) {

    private val appContext: Context = context.applicationContext ?: context

    private val alarmManager: AlarmManager?
        get() = appContext.getSystemService(AlarmManager::class.java)

    /** Starts or replaces the auto-off countdown for [minutes]; cancels if [minutes] <= 0. */
    fun start(minutes: Int) {
        if (minutes <= 0) {
            cancel()
            return
        }
        val targetAtMs = System.currentTimeMillis() + minutes * MILLIS_PER_MINUTE
        prefs.autoOffTargetAtMs = targetAtMs
        scheduleWindow(targetAtMs)
    }

    /** Schedules auto-off when USB debugging is on and enabled in prefs; cancels when off. */
    fun scheduleIfEnabled(usbEnabled: Boolean) {
        if (!usbEnabled) {
            cancel()
            return
        }
        if (prefs.autoOffEnabled) {
            start(prefs.autoOffMinutes)
        }
    }

    /** Re-arms a persisted timer if USB debugging is active, or clears it otherwise. */
    fun resync(usbEnabled: Boolean) {
        if (!usbEnabled) {
            cancel()
            return
        }
        val target = prefs.autoOffTargetAtMs
        if (target > 0L) {
            scheduleWindow(maxOf(target, System.currentTimeMillis() + MIN_RESYNC_DELAY_MS))
        }
    }

    /** Cancels any pending auto-off alarm and clears the stored deadline. */
    fun cancel() {
        alarmManager?.cancel(createAlarmPendingIntent())
        prefs.autoOffTargetAtMs = 0L
    }

    /** Returns remaining whole minutes until the target deadline, or null if expired/inactive. */
    fun remainingMinutes(nowMs: Long = System.currentTimeMillis()): Int? {
        val target = prefs.autoOffTargetAtMs
        if (target <= nowMs) return null
        return ((target - nowMs) / MILLIS_PER_MINUTE).coerceAtLeast(0).toInt()
    }

    private fun scheduleWindow(targetAtMs: Long) {
        val manager = alarmManager ?: return
        runCatching {
            manager.setWindow(
                AlarmManager.RTC_WAKEUP,
                targetAtMs,
                WINDOW_LENGTH_MS,
                createAlarmPendingIntent()
            )
        }
    }

    private fun createAlarmPendingIntent(): PendingIntent {
        val intent = Intent(appContext, AutoOffReceiver::class.java).apply {
            action = AutoOffReceiver.ACTION_AUTO_OFF
        }
        return PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val ALARM_REQUEST_CODE = 1001
        const val MILLIS_PER_MINUTE = 60_000L
        const val WINDOW_LENGTH_MS = 60_000L
        const val MIN_RESYNC_DELAY_MS = 1_000L
    }
}

/** Broadcast receiver invoked when the inexact auto-off alarm fires. */
class AutoOffReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_AUTO_OFF) return
        val appContext = context.applicationContext ?: context
        val prefs = AppPreferences(appContext)
        val controller = DevSettingsController(AndroidSettingsGateway(appContext))
        if (controller.hasWritePermission() && controller.readState().adbEnabled) {
            controller.setUsbDebugging(false)
        }
        controller.syncKeepAwakeWithUsb(prefs.keepAwakeOnUsb)
        AutoOffManager(appContext, prefs).cancel()
        QuickSurfaceHelper.syncAllSurfaces(appContext)
    }

    companion object {
        const val ACTION_AUTO_OFF = "io.github.xom11.vibedebug.ACTION_AUTO_OFF"
    }
}
