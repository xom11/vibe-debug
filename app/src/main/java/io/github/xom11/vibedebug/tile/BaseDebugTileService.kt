package io.github.xom11.vibedebug.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.xom11.vibedebug.MainActivity
import io.github.xom11.vibedebug.data.AndroidSettingsGateway
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.data.AutoOffManager
import io.github.xom11.vibedebug.data.DevSettingsController
import io.github.xom11.vibedebug.rules.AppWatcherService
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper
import io.github.xom11.vibedebug.shortcut.QuickTileType

/**
 * Shared Quick Settings tile base handling live [ContentObserver] updates,
 * unlock-before-action behavior, surface sync, and API-compat activity launch.
 */
abstract class BaseDebugTileService : TileService() {

    private val settingsGateway: AndroidSettingsGateway by lazy {
        AndroidSettingsGateway(applicationContext)
    }

    protected val controller: DevSettingsController by lazy {
        DevSettingsController(settingsGateway)
    }

    private var settingsObserver: ContentObserver? = null

    override fun onTileAdded() {
        super.onTileAdded()
        if (QuickSurfaceHelper.isTileComponentEnabled(applicationContext, this::class.java)) {
            syncAddedPreference(true)
        }
        refreshTile()
    }

    override fun onTileRemoved() {
        syncAddedPreference(false)
        super.onTileRemoved()
    }

    override fun onStartListening() {
        super.onStartListening()
        AppWatcherService.sync(applicationContext)
        if (QuickSurfaceHelper.isTileComponentEnabled(applicationContext, this::class.java)) {
            syncAddedPreference(true)
        }
        refreshTile()
        if (settingsObserver == null) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    refreshTile()
                }
            }
            settingsGateway.registerObserver(observer)
            settingsObserver = observer
        }
    }

    private fun syncAddedPreference(added: Boolean) {
        QuickTileType.fromServiceClass(this::class.java)?.let { tile ->
            AppPreferences(applicationContext).setQsTileAdded(tile.prefKey, added)
        }
    }

    override fun onStopListening() {
        settingsObserver?.let { settingsGateway.unregisterObserver(it) }
        settingsObserver = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            handleTileClick()
        }
    }

    protected abstract fun refreshTile()

    protected abstract fun handleTileClick()

    protected fun renderTile(
        @DrawableRes iconRes: Int,
        @StringRes labelRes: Int,
        tileState: Int,
        subtitle: String?
    ) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.label = getString(labelRes)
        tile.state = tileState
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }

    protected fun createAutoOffManager(): AutoOffManager =
        AutoOffManager(applicationContext, AppPreferences(applicationContext))

    protected fun syncAllSurfaces() {
        refreshTile()
        QuickSurfaceHelper.syncAllSurfaces(applicationContext)
    }

    protected fun openSettingsOrMain(
        devOptionsEnabled: Boolean,
        requestCode: Int,
        settingsIntent: () -> Intent
    ) {
        val targetIntent = if (devOptionsEnabled) settingsIntent() else createMainAppIntent()
        launchFromTileAndCollapse(targetIntent, requestCode)
    }

    protected fun createMainAppIntent(): Intent {
        return Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    protected fun launchFromTileAndCollapse(intent: Intent, requestCode: Int = 0) {
        val prepared = intent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivityAndCollapseCompat(prepared, requestCode)
        } catch (_: Exception) {
            runCatching {
                startActivityAndCollapseCompat(createMainAppIntent(), requestCode + 1)
            }
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseCompat(intent: Intent, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
