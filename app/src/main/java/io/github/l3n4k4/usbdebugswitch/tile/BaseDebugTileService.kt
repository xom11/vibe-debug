package io.github.l3n4k4.usbdebugswitch.tile

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
import io.github.l3n4k4.usbdebugswitch.MainActivity
import io.github.l3n4k4.usbdebugswitch.data.AndroidSettingsGateway
import io.github.l3n4k4.usbdebugswitch.data.AppPreferences
import io.github.l3n4k4.usbdebugswitch.data.AutoOffManager
import io.github.l3n4k4.usbdebugswitch.data.DevSettingsController
import io.github.l3n4k4.usbdebugswitch.shortcut.QuickSurfaceHelper

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

    override fun onStartListening() {
        super.onStartListening()
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
