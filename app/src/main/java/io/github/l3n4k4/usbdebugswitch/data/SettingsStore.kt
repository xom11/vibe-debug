package io.github.l3n4k4.usbdebugswitch.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.github.l3n4k4.usbdebugswitch.model.DevState

object SettingsKeys {
    const val DEV_OPTIONS: String = Settings.Global.DEVELOPMENT_SETTINGS_ENABLED
    const val ADB_ENABLED: String = Settings.Global.ADB_ENABLED
    const val ADB_WIFI_ENABLED: String = "adb_wifi_enabled"
}

interface SettingsGateway {
    val supportsWirelessAdb: Boolean
    fun hasWriteSecureSettings(): Boolean
    fun isDevOptionsEnabled(): Boolean
    fun isAdbEnabled(): Boolean
    fun isWirelessAdbEnabled(): Boolean
    fun putDevOptionsEnabled(enabled: Boolean): Boolean
    fun putAdbEnabled(enabled: Boolean): Boolean
    fun putWirelessAdbEnabled(enabled: Boolean): Boolean
}

class AndroidSettingsGateway(private val context: Context) : SettingsGateway {

    override val supportsWirelessAdb: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override fun hasWriteSecureSettings(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isDevOptionsEnabled(): Boolean = readGlobalFlag(SettingsKeys.DEV_OPTIONS)

    override fun isAdbEnabled(): Boolean = readGlobalFlag(SettingsKeys.ADB_ENABLED)

    override fun isWirelessAdbEnabled(): Boolean {
        if (!supportsWirelessAdb) return false
        return readGlobalFlag(SettingsKeys.ADB_WIFI_ENABLED)
    }

    override fun putDevOptionsEnabled(enabled: Boolean): Boolean =
        writeGlobalFlag(SettingsKeys.DEV_OPTIONS, enabled)

    override fun putAdbEnabled(enabled: Boolean): Boolean =
        writeGlobalFlag(SettingsKeys.ADB_ENABLED, enabled)

    override fun putWirelessAdbEnabled(enabled: Boolean): Boolean {
        if (!supportsWirelessAdb) return false
        return writeGlobalFlag(SettingsKeys.ADB_WIFI_ENABLED, enabled)
    }

    fun registerObserver(observer: ContentObserver) {
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.Global.getUriFor(SettingsKeys.DEV_OPTIONS),
            false,
            observer
        )
        resolver.registerContentObserver(
            Settings.Global.getUriFor(SettingsKeys.ADB_ENABLED),
            false,
            observer
        )
        if (supportsWirelessAdb) {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.ADB_WIFI_ENABLED),
                false,
                observer
            )
        }
    }

    fun unregisterObserver(observer: ContentObserver) {
        runCatching {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    private fun readGlobalFlag(key: String): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, key, 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    private fun writeGlobalFlag(key: String, enabled: Boolean): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, key, if (enabled) 1 else 0)
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}

class DevSettingsController(private val gateway: SettingsGateway) {

    fun hasWritePermission(): Boolean = gateway.hasWriteSecureSettings()

    fun readState(): DevState {
        val wirelessSupported = gateway.supportsWirelessAdb
        return DevState(
            devOptionsEnabled = gateway.isDevOptionsEnabled(),
            adbEnabled = gateway.isAdbEnabled(),
            wirelessAdbEnabled = if (wirelessSupported) gateway.isWirelessAdbEnabled() else false,
            supportsWirelessAdb = wirelessSupported
        )
    }

    fun setUsbDebugging(enabled: Boolean): Boolean {
        if (!gateway.hasWriteSecureSettings()) return false
        if (enabled && !gateway.isDevOptionsEnabled()) {
            if (!gateway.putDevOptionsEnabled(true)) return false
        }
        return gateway.putAdbEnabled(enabled)
    }

    fun setWirelessDebugging(enabled: Boolean): Boolean {
        if (!gateway.hasWriteSecureSettings() || !gateway.supportsWirelessAdb) return false
        if (enabled && !gateway.isDevOptionsEnabled()) {
            if (!gateway.putDevOptionsEnabled(true)) return false
        }
        return gateway.putWirelessAdbEnabled(enabled)
    }

    fun setDeveloperOptions(enabled: Boolean): Boolean {
        if (!gateway.hasWriteSecureSettings()) return false
        if (enabled) {
            return gateway.putDevOptionsEnabled(true)
        }
        gateway.putAdbEnabled(false)
        if (gateway.supportsWirelessAdb) {
            gateway.putWirelessAdbEnabled(false)
        }
        return gateway.putDevOptionsEnabled(false)
    }

    fun toggleUsbDebugging(): Boolean {
        val target = !gateway.isAdbEnabled()
        return setUsbDebugging(target)
    }

    fun toggleWirelessDebugging(): Boolean {
        if (!gateway.supportsWirelessAdb) return false
        val target = !gateway.isWirelessAdbEnabled()
        return setWirelessDebugging(target)
    }
}
