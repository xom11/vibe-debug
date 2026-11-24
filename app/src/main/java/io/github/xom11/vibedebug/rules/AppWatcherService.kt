package io.github.xom11.vibedebug.rules

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.xom11.vibedebug.MainActivity
import io.github.xom11.vibedebug.R
import io.github.xom11.vibedebug.data.AndroidSettingsGateway
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.data.AutoOffManager
import io.github.xom11.vibedebug.data.DevSettingsController
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper
import io.github.xom11.vibedebug.shortcut.SafeLaunchHelper

/**
 * Foreground service that polls foreground app transitions on a background thread
 * while the screen is interactive, disabling and restoring debugging for listed apps.
 */
class AppWatcherService : Service() {

    private lateinit var prefs: AppPreferences
    private lateinit var controller: DevSettingsController
    private lateinit var detector: AndroidForegroundAppDetector
    private lateinit var engine: AppRuleEngine

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var lastNotificationSignature: String? = null
    @Volatile
    private var destroyed = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (destroyed) return
            val keepRunning = runCatching { engine.poll() }.getOrDefault(false)
            updateNotificationIfNeeded()
            if (!keepRunning && !shouldRun(applicationContext)) {
                stopSelf()
                return
            }
            if (!destroyed && detector.isScreenInteractive()) {
                workerHandler?.postDelayed(this, AppRuleEngine.POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        destroyed = false
        val appContext = applicationContext
        prefs = AppPreferences(appContext)
        controller = DevSettingsController(AndroidSettingsGateway(appContext))
        detector = AndroidForegroundAppDetector(appContext)
        engine = AppRuleEngine(
            prefs = prefs,
            controller = controller,
            detector = detector,
            autoOffManager = AutoOffManager(appContext, prefs),
            onSurfaceSync = { QuickSurfaceHelper.syncAllSurfaces(appContext) },
        )

        ensureNotificationChannel()
        startForegroundCompat(buildNotification())

        workerThread = HandlerThread("VibeDebugAppWatcher", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()
            workerHandler = Handler(looper)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        workerHandler?.removeCallbacks(pollRunnable)
                    }
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        scheduleImmediatePoll()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiver = receiver
        activeInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification())

        if (intent?.action == ACTION_STOP_WATCHER) {
            prefs.autoAppRulesEnabled = false
            workerHandler?.removeCallbacks(pollRunnable)
            engine.releaseHoldOnStop()
            QuickSurfaceHelper.syncAllSurfaces(applicationContext)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!shouldRun(applicationContext)) {
            stopSelf()
            return START_NOT_STICKY
        }

        scheduleImmediatePoll()
        return START_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        if (activeInstance === this) {
            activeInstance = null
        }
        workerHandler?.removeCallbacksAndMessages(null)
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
        screenReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        screenReceiver = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ensureNotificationChannel()
        updateNotificationIfNeeded()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleImmediatePoll() {
        val handler = workerHandler ?: return
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
    }

    private fun startForegroundCompat(notification: Notification) {
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        lastNotificationSignature = currentNotificationSignature()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val name = getString(R.string.watcher_notif_channel_name)
        val desc = getString(R.string.watcher_notif_channel_desc)
        val existing = manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
        if (existing != null) {
            if (existing.name?.toString() != name || existing.description != desc) {
                existing.name = name
                existing.description = desc
                manager.createNotificationChannel(existing)
            }
            return
        }
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            name,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = desc
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun currentNotificationSignature(): String {
        val localeTags = resources.configuration.locales.toLanguageTags()
        return "${prefs.heldRulePackage}|${prefs.safeLaunchPackages.size}|${prefs.autoRestoreOnExit}|$localeTags"
    }

    private fun updateNotificationIfNeeded() {
        val sig = currentNotificationSignature()
        if (sig == lastNotificationSignature) return
        lastNotificationSignature = sig
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val (title, body) = resolveNotificationText(
            context = this,
            heldPkg = prefs.heldRulePackage,
            protectedCount = prefs.safeLaunchPackages.size,
            autoRestoreOnExit = prefs.autoRestoreOnExit,
        )

        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_TAB, "shortcuts")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(applicationContext, AppWatcherService::class.java).apply {
            action = ACTION_STOP_WATCHER
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_usb_debug)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.watcher_notif_action_stop), stopPendingIntent)
            .build()
    }

    companion object {
        const val ACTION_STOP_WATCHER = "io.github.xom11.vibedebug.ACTION_STOP_WATCHER"
        const val NOTIFICATION_CHANNEL_ID = "vibe_debug_app_rules"
        const val NOTIFICATION_ID = 2001

        @Volatile
        private var activeInstance: AppWatcherService? = null

        fun shouldRun(context: Context): Boolean {
            val appContext = context.applicationContext ?: context
            val prefs = AppPreferences(appContext)
            if (!prefs.autoAppRulesEnabled || prefs.safeLaunchPackages.isEmpty()) return false
            val controller = DevSettingsController(AndroidSettingsGateway(appContext))
            if (!controller.hasWritePermission() || controller.readState().blockedByPolicy) return false
            return AndroidForegroundAppDetector(appContext).hasUsageAccess()
        }

        fun sync(context: Context) {
            val appContext = context.applicationContext ?: context
            val serviceIntent = Intent(appContext, AppWatcherService::class.java)
            if (shouldRun(appContext)) {
                runCatching {
                    ContextCompat.startForegroundService(appContext, serviceIntent)
                }
            } else {
                runCatching {
                    appContext.stopService(serviceIntent)
                }
            }
        }

        fun onSafeLaunch(packageName: String) {
            activeInstance?.engine?.markLaunchedFromUi(packageName)
        }

        internal fun resolveNotificationText(
            context: Context,
            heldPkg: String,
            protectedCount: Int,
            autoRestoreOnExit: Boolean,
        ): Pair<String, String> {
            return if (heldPkg.isNotEmpty()) {
                val appCtx = context.applicationContext ?: context
                val label = SafeLaunchHelper.getAppLabel(appCtx, heldPkg)
                val title = context.getString(R.string.watcher_notif_holding_title, label)
                val body = context.getString(
                    if (autoRestoreOnExit) R.string.watcher_notif_holding_restore_body
                    else R.string.watcher_notif_holding_stay_off_body
                )
                title to body
            } else {
                val title = if (protectedCount == 1) {
                    context.getString(R.string.watcher_notif_watching_title_one)
                } else {
                    context.getString(R.string.watcher_notif_watching_title, protectedCount)
                }
                val body = context.getString(R.string.watcher_notif_watching_body)
                title to body
            }
        }
    }
}

/** Re-syncs [AppWatcherService] on device boot or app update if the user enabled auto rules. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (AppWatcherService.shouldRun(context)) {
                AppWatcherService.sync(context)
            }
        }
    }
}
