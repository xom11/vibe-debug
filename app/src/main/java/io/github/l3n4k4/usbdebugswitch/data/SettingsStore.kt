package io.github.l3n4k4.usbdebugswitch.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.github.l3n4k4.usbdebugswitch.model.DevState

object SettingsKeys {
    const val DEV_OPTIONS: String = Settings.Global.DEVELOPMENT_SETTINGS_ENABLED
    const val ADB_ENABLED: String = Settings.Global.ADB_ENABLED
    const val ADB_WIFI_ENABLED: String = "adb_wifi_enabled"
    const val STAY_ON_WHILE_PLUGGED_IN: String = Settings.Global.STAY_ON_WHILE_PLUGGED_IN
    const val STAY_ON_MASK: Int = 7
}

interface SettingsGateway {
    val supportsWirelessAdb: Boolean
    fun hasWriteSecureSettings(): Boolean
    fun isDevOptionsEnabled(): Boolean
    fun isAdbEnabled(): Boolean
    fun isWirelessAdbEnabled(): Boolean
    fun isKeepAwakeEnabled(): Boolean = false
    fun isDebuggingBlockedByPolicy(): Boolean = false
    fun putDevOptionsEnabled(enabled: Boolean): Boolean
    fun putAdbEnabled(enabled: Boolean): Boolean
    fun putWirelessAdbEnabled(enabled: Boolean): Boolean
    fun putKeepAwakeEnabled(enabled: Boolean): Boolean = false
}

/** Gateway backed by Android [Settings.Global] and [UserManager]. */
class AndroidSettingsGateway(private val context: Context) : SettingsGateway {

    override val supportsWirelessAdb: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override fun hasWriteSecureSettings(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isDevOptionsEnabled(): Boolean = readGlobalInt(SettingsKeys.DEV_OPTIONS) == 1

    override fun isAdbEnabled(): Boolean = readGlobalInt(SettingsKeys.ADB_ENABLED) == 1

    override fun isWirelessAdbEnabled(): Boolean {
        if (!supportsWirelessAdb) return false
        return readGlobalInt(SettingsKeys.ADB_WIFI_ENABLED) == 1
    }

    override fun isKeepAwakeEnabled(): Boolean =
        readGlobalInt(SettingsKeys.STAY_ON_WHILE_PLUGGED_IN) != 0

    override fun isDebuggingBlockedByPolicy(): Boolean {
        return try {
            val userManager = context.getSystemService(UserManager::class.java) ?: return false
            userManager.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES)
        } catch (_: Exception) {
            false
        }
    }

    override fun putDevOptionsEnabled(enabled: Boolean): Boolean =
        writeGlobalInt(SettingsKeys.DEV_OPTIONS, if (enabled) 1 else 0)

    override fun putAdbEnabled(enabled: Boolean): Boolean =
        writeGlobalInt(SettingsKeys.ADB_ENABLED, if (enabled) 1 else 0)

    override fun putWirelessAdbEnabled(enabled: Boolean): Boolean {
        if (!supportsWirelessAdb) return false
        return writeGlobalInt(SettingsKeys.ADB_WIFI_ENABLED, if (enabled) 1 else 0)
    }

    override fun putKeepAwakeEnabled(enabled: Boolean): Boolean =
        writeGlobalInt(
            SettingsKeys.STAY_ON_WHILE_PLUGGED_IN,
            if (enabled) SettingsKeys.STAY_ON_MASK else 0
        )

    /** Registers [observer] on all developer setting keys supported by this device. */
    fun registerObserver(observer: ContentObserver) {
        val resolver = context.contentResolver
        val watchedKeys = buildList {
            add(SettingsKeys.DEV_OPTIONS)
            add(SettingsKeys.ADB_ENABLED)
            add(SettingsKeys.STAY_ON_WHILE_PLUGGED_IN)
            if (supportsWirelessAdb) {
                add(SettingsKeys.ADB_WIFI_ENABLED)
            }
        }
        watchedKeys.forEach { key ->
            resolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
        }
    }

    fun unregisterObserver(observer: ContentObserver) {
        runCatching {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    private fun readGlobalInt(key: String): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, key, 0)
        } catch (_: Exception) {
            0
        }
    }

    private fun writeGlobalInt(key: String, value: Int): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, key, value)
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}

/** Coordinates safe reads and writes for developer, debugging, and keep-awake settings. */
class DevSettingsController(private val gateway: SettingsGateway) {

    fun hasWritePermission(): Boolean = gateway.hasWriteSecureSettings()

    fun readState(): DevState {
        val wirelessSupported = gateway.supportsWirelessAdb
        return DevState(
            devOptionsEnabled = gateway.isDevOptionsEnabled(),
            adbEnabled = gateway.isAdbEnabled(),
            wirelessAdbEnabled = if (wirelessSupported) gateway.isWirelessAdbEnabled() else false,
            keepAwakeEnabled = gateway.isKeepAwakeEnabled(),
            supportsWirelessAdb = wirelessSupported,
            blockedByPolicy = gateway.isDebuggingBlockedByPolicy()
        )
    }

    fun setUsbDebugging(enabled: Boolean): Boolean {
        if (!canMutateDebugging()) return false
        if (enabled && !ensureDevOptionsEnabled()) return false
        return gateway.putAdbEnabled(enabled)
    }

    fun setWirelessDebugging(enabled: Boolean): Boolean {
        if (!canMutateDebugging() || !gateway.supportsWirelessAdb) return false
        if (enabled && !ensureDevOptionsEnabled()) return false
        return gateway.putWirelessAdbEnabled(enabled)
    }

    fun setDeveloperOptions(enabled: Boolean): Boolean {
        if (!canMutateDebugging()) return false
        if (enabled) {
            return gateway.putDevOptionsEnabled(true)
        }
        gateway.putAdbEnabled(false)
        if (gateway.supportsWirelessAdb) {
            gateway.putWirelessAdbEnabled(false)
        }
        return gateway.putDevOptionsEnabled(false)
    }

    fun setKeepAwake(enabled: Boolean): Boolean {
        if (!gateway.hasWriteSecureSettings()) return false
        return gateway.putKeepAwakeEnabled(enabled)
    }

    fun toggleUsbDebugging(): Boolean = setUsbDebugging(!gateway.isAdbEnabled())

    fun toggleWirelessDebugging(): Boolean {
        if (!gateway.supportsWirelessAdb) return false
        return setWirelessDebugging(!gateway.isWirelessAdbEnabled())
    }

    fun toggleDeveloperOptions(): Boolean = setDeveloperOptions(!gateway.isDevOptionsEnabled())

    fun toggleKeepAwake(): Boolean = setKeepAwake(!gateway.isKeepAwakeEnabled())

    private fun canMutateDebugging(): Boolean =
        !gateway.isDebuggingBlockedByPolicy() && gateway.hasWriteSecureSettings()

    private fun ensureDevOptionsEnabled(): Boolean =
        gateway.isDevOptionsEnabled() || gateway.putDevOptionsEnabled(true)
}
