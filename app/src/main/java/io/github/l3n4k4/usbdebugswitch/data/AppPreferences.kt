package io.github.l3n4k4.usbdebugswitch.data

import android.content.Context
import android.content.SharedPreferences

/** User-selectable app theme preference. */
enum class ThemePreference(val key: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromKey(raw: String?): ThemePreference =
            entries.firstOrNull { it.key == raw } ?: SYSTEM
    }
}

/** Persistent preferences stored in private SharedPreferences. */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoJumpToUsbDebugging: Boolean
        get() = prefs.getBoolean(KEY_AUTO_JUMP_USB_DEBUG, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_JUMP_USB_DEBUG, value).apply()
        }

    var themePreference: ThemePreference
        get() = ThemePreference.fromKey(prefs.getString(KEY_THEME_PREFERENCE, ThemePreference.SYSTEM.key))
        set(value) {
            prefs.edit().putString(KEY_THEME_PREFERENCE, value.key).apply()
        }

    var autoOffEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_OFF_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_OFF_ENABLED, value).apply()
        }

    var autoOffMinutes: Int
        get() = normalizeAutoOffMinutes(prefs.getInt(KEY_AUTO_OFF_MINUTES, DEFAULT_AUTO_OFF_MINUTES))
        set(value) {
            prefs.edit().putInt(KEY_AUTO_OFF_MINUTES, normalizeAutoOffMinutes(value)).apply()
        }

    var autoOffTargetAtMs: Long
        get() = prefs.getLong(KEY_AUTO_OFF_TARGET_AT_MS, 0L)
        set(value) {
            prefs.edit().putLong(KEY_AUTO_OFF_TARGET_AT_MS, value.coerceAtLeast(0L)).apply()
        }

    var safeLaunchPackages: List<String>
        get() {
            val raw = prefs.getString(KEY_SAFE_LAUNCH_PACKAGES, null).orEmpty()
            if (raw.isEmpty()) return emptyList()
            return raw.split(PACKAGE_DELIMITER)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }
        set(value) {
            val sanitized = value
                .map { it.replace("\r", "").replace("\n", "").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            prefs.edit().putString(KEY_SAFE_LAUNCH_PACKAGES, sanitized.joinToString(PACKAGE_DELIMITER)).apply()
        }

    /** Appends [packageName] to the ordered Safe Launch allowlist if not already present. */
    fun addSafeLaunchPackage(packageName: String) {
        val normalized = packageName.replace("\r", "").replace("\n", "").trim()
        if (normalized.isEmpty()) return
        val current = safeLaunchPackages
        if (normalized !in current) {
            safeLaunchPackages = current + normalized
        }
    }

    /** Removes [packageName] from the Safe Launch allowlist. */
    fun removeSafeLaunchPackage(packageName: String) {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return
        safeLaunchPackages = safeLaunchPackages.filterNot { it == normalized }
    }

    var addedQsTileKeys: Set<String>
        get() {
            val raw = prefs.getString(KEY_ADDED_QS_TILES, null).orEmpty()
            if (raw.isEmpty()) return emptySet()
            return raw.split(PACKAGE_DELIMITER)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
        set(value) {
            val sanitized = value
                .map { it.replace("\r", "").replace("\n", "").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            prefs.edit().putString(KEY_ADDED_QS_TILES, sanitized.joinToString(PACKAGE_DELIMITER)).apply()
        }

    fun isQsTileAdded(prefKey: String): Boolean = prefKey.trim() in addedQsTileKeys

    fun setQsTileAdded(prefKey: String, added: Boolean) {
        val normalized = prefKey.replace("\r", "").replace("\n", "").trim()
        if (normalized.isEmpty()) return
        val current = addedQsTileKeys
        val updated = if (added) current + normalized else current - normalized
        if (updated != current) {
            addedQsTileKeys = updated
        }
    }

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun normalizeAutoOffMinutes(minutes: Int): Int =
        if (minutes in VALID_AUTO_OFF_MINUTES) minutes else DEFAULT_AUTO_OFF_MINUTES

    companion object {
        private const val PREFS_NAME = "usb_debug_switch_prefs"
        private const val KEY_AUTO_JUMP_USB_DEBUG = "auto_jump_usb_debug"
        private const val KEY_THEME_PREFERENCE = "theme_preference"
        private const val KEY_AUTO_OFF_ENABLED = "auto_off_enabled"
        private const val KEY_AUTO_OFF_MINUTES = "auto_off_minutes"
        private const val KEY_AUTO_OFF_TARGET_AT_MS = "auto_off_target_at_ms"
        private const val KEY_SAFE_LAUNCH_PACKAGES = "safe_launch_packages"
        private const val KEY_ADDED_QS_TILES = "added_qs_tiles"
        private const val PACKAGE_DELIMITER = "\n"
        private const val DEFAULT_AUTO_OFF_MINUTES = 30
        private val VALID_AUTO_OFF_MINUTES = setOf(15, 30, 60)
    }
}
