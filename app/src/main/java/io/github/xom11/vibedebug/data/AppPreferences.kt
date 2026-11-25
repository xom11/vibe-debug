package io.github.xom11.vibedebug.data

import android.content.Context
import android.content.SharedPreferences
import io.github.xom11.vibedebug.rules.DexAntiDebugSignals
import io.github.xom11.vibedebug.rules.StrictAppDetector

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

/** Per-app disable level when launching or entering a protected app. */
enum class AppRuleMode(val key: String) {
    DEV_OFF("dev"),
    USB_OFF("usb");

    companion object {
        fun fromKey(raw: String?): AppRuleMode =
            entries.firstOrNull { it.key == raw } ?: DEV_OFF
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

    var keepAwakeOnUsb: Boolean
        get() = prefs.getBoolean(KEY_KEEP_AWAKE_ON_USB, false)
        set(value) {
            prefs.edit().putBoolean(KEY_KEEP_AWAKE_ON_USB, value).apply()
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

    /** Appends all valid, non-duplicate [packageNames] to the Safe Launch allowlist in a single write. */
    fun addSafeLaunchPackages(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        val current = safeLaunchPackages
        val currentSet = current.toHashSet()
        val additions = packageNames
            .map { it.replace("\r", "").replace("\n", "").trim() }
            .filter { it.isNotEmpty() && currentSet.add(it) }
        if (additions.isNotEmpty()) {
            safeLaunchPackages = current + additions
        }
    }

    /** Cached on-device DEX anti-debug scan output keyed by package name and update timestamp. */
    data class CachedDexEntry(
        val lastUpdateTime: Long,
        val scannerVersion: Int,
        val signals: DexAntiDebugSignals,
    )

    var lastDeepScanAtMs: Long
        get() = prefs.getLong(KEY_LAST_DEEP_SCAN_AT_MS, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_DEEP_SCAN_AT_MS, value.coerceAtLeast(0L)).apply()
        }

    fun readDexScanCache(): Map<String, CachedDexEntry> {
        val raw = prefs.getString(KEY_DEEP_SCAN_CACHE, null).orEmpty()
        if (raw.isEmpty()) return emptyMap()
        return buildMap {
            raw.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split('|')
                if (parts.size < 4) return@forEach
                val pkg = parts[0].trim()
                val updateTime = parts[1].toLongOrNull() ?: return@forEach
                val version = parts[2].toIntOrNull() ?: return@forEach
                val mask = parts[3].toIntOrNull() ?: return@forEach
                val sdks = if (parts.size >= 5 && parts[4].isNotBlank()) {
                    parts[4].split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                } else {
                    emptySet()
                }
                if (pkg.isNotEmpty()) {
                    put(
                        pkg,
                        CachedDexEntry(
                            lastUpdateTime = updateTime,
                            scannerVersion = version,
                            signals = DexAntiDebugSignals(
                                checksAdbEnabled = (mask and FLAG_DEX_ADB) != 0,
                                checksDevSettings = (mask and FLAG_DEX_DEV) != 0,
                                checksAdbWifi = (mask and FLAG_DEX_WIFI) != 0,
                                raspClassSdks = sdks,
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun getCachedDexSignals(
        packageName: String,
        lastUpdateTime: Long,
        scannerVersion: Int = StrictAppDetector.SCANNER_VERSION,
    ): DexAntiDebugSignals? {
        val entry = readDexScanCache()[packageName.trim()] ?: return null
        if (entry.lastUpdateTime != lastUpdateTime || entry.scannerVersion != scannerVersion) {
            return null
        }
        return entry.signals
    }

    fun putAllCachedDexSignals(entries: Map<String, CachedDexEntry>) {
        if (entries.isEmpty()) return
        val merged = readDexScanCache().toMutableMap()
        for ((rawPkg, entry) in entries) {
            val pkg = rawPkg.replace("\r", "").replace("\n", "").replace("|", "").trim()
            if (pkg.isNotEmpty()) {
                merged[pkg] = entry
            }
        }
        val cappedEntries = if (merged.size > MAX_DEX_CACHE_ENTRIES) {
            merged.entries
                .sortedByDescending { it.value.lastUpdateTime }
                .take(MAX_DEX_CACHE_ENTRIES)
        } else {
            merged.entries.toList()
        }
        val serialized = cappedEntries.joinToString("\n") { (pkg, entry) ->
            var mask = 0
            if (entry.signals.checksAdbEnabled) mask = mask or FLAG_DEX_ADB
            if (entry.signals.checksDevSettings) mask = mask or FLAG_DEX_DEV
            if (entry.signals.checksAdbWifi) mask = mask or FLAG_DEX_WIFI
            val sdkCsv = entry.signals.raspClassSdks
                .map { it.replace("|", "").replace(",", "").replace("\n", "").trim() }
                .filter { it.isNotEmpty() }
                .sorted()
                .joinToString(",")
            "$pkg|${entry.lastUpdateTime}|${entry.scannerVersion}|$mask|$sdkCsv"
        }
        prefs.edit().putString(KEY_DEEP_SCAN_CACHE, serialized).apply()
    }

    /** Removes [packageName] from the Safe Launch allowlist and cleans up its per-app rule mode. */
    fun removeSafeLaunchPackage(packageName: String) {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return
        safeLaunchPackages = safeLaunchPackages.filterNot { it == normalized }
        val modes = appRuleModes
        if (normalized in modes) {
            appRuleModes = modes - normalized
        }
    }

    /** Clears all protected packages and any saved per-app rule modes. */
    fun clearSafeLaunchPackages() {
        safeLaunchPackages = emptyList()
        appRuleModes = emptyMap()
    }

    var autoAppRulesEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_APP_RULES_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_APP_RULES_ENABLED, value).apply()
        }

    var autoRestoreOnExit: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESTORE_ON_EXIT, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_RESTORE_ON_EXIT, value).apply()
        }

    var appRuleModes: Map<String, AppRuleMode>
        get() {
            val raw = prefs.getString(KEY_APP_RULE_MODES, null).orEmpty()
            if (raw.isEmpty()) return emptyMap()
            return buildMap {
                raw.split(PACKAGE_DELIMITER).forEach { line ->
                    val idx = line.indexOf('=')
                    if (idx > 0) {
                        val pkg = line.substring(0, idx).trim()
                        val mode = AppRuleMode.fromKey(line.substring(idx + 1).trim())
                        if (pkg.isNotEmpty()) {
                            put(pkg, mode)
                        }
                    }
                }
            }
        }
        set(value) {
            val serialized = value.entries
                .mapNotNull { (pkg, mode) ->
                    val normalized = pkg.replace("\r", "").replace("\n", "").replace("=", "").trim()
                    if (normalized.isEmpty()) null else "$normalized=${mode.key}"
                }
                .distinct()
                .joinToString(PACKAGE_DELIMITER)
            prefs.edit().putString(KEY_APP_RULE_MODES, serialized).apply()
        }

    fun getAppRuleMode(packageName: String): AppRuleMode {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return AppRuleMode.DEV_OFF
        return appRuleModes[normalized] ?: AppRuleMode.DEV_OFF
    }

    fun setAppRuleMode(packageName: String, mode: AppRuleMode) {
        val normalized = packageName.replace("\r", "").replace("\n", "").replace("=", "").trim()
        if (normalized.isEmpty()) return
        val current = appRuleModes
        if (current[normalized] != mode) {
            appRuleModes = current + (normalized to mode)
        }
    }

    val heldRulePackage: String
        get() = prefs.getString(KEY_HELD_RULE_PKG, null).orEmpty()

    val heldDevOptions: Boolean
        get() = prefs.getBoolean(KEY_HELD_RULE_DEV, false)

    val heldUsbDebug: Boolean
        get() = prefs.getBoolean(KEY_HELD_RULE_USB, false)

    val heldWirelessDebug: Boolean
        get() = prefs.getBoolean(KEY_HELD_RULE_WIFI, false)

    fun saveRuleHold(packageName: String, dev: Boolean, usb: Boolean, wifi: Boolean) {
        val normalized = packageName.replace("\r", "").replace("\n", "").trim()
        if (normalized.isEmpty()) return
        prefs.edit()
            .putString(KEY_HELD_RULE_PKG, normalized)
            .putBoolean(KEY_HELD_RULE_DEV, dev)
            .putBoolean(KEY_HELD_RULE_USB, usb)
            .putBoolean(KEY_HELD_RULE_WIFI, wifi)
            .apply()
    }

    fun clearRuleHold() {
        if (heldRulePackage.isEmpty() && !heldDevOptions && !heldUsbDebug && !heldWirelessDebug) return
        prefs.edit()
            .remove(KEY_HELD_RULE_PKG)
            .remove(KEY_HELD_RULE_DEV)
            .remove(KEY_HELD_RULE_USB)
            .remove(KEY_HELD_RULE_WIFI)
            .apply()
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
        private const val KEY_KEEP_AWAKE_ON_USB = "keep_awake_on_usb"
        private const val KEY_SAFE_LAUNCH_PACKAGES = "safe_launch_packages"
        private const val KEY_AUTO_APP_RULES_ENABLED = "auto_app_rules_enabled"
        private const val KEY_AUTO_RESTORE_ON_EXIT = "auto_restore_on_exit"
        private const val KEY_APP_RULE_MODES = "app_rule_modes"
        private const val KEY_HELD_RULE_PKG = "held_rule_pkg"
        private const val KEY_HELD_RULE_DEV = "held_rule_dev"
        private const val KEY_HELD_RULE_USB = "held_rule_usb"
        private const val KEY_HELD_RULE_WIFI = "held_rule_wifi"
        private const val KEY_ADDED_QS_TILES = "added_qs_tiles"
        private const val KEY_DEEP_SCAN_CACHE = "strict_app_dex_cache_v1"
        private const val KEY_LAST_DEEP_SCAN_AT_MS = "strict_app_last_deep_scan_ms"
        private const val FLAG_DEX_ADB = 1 shl 0
        private const val FLAG_DEX_DEV = 1 shl 1
        private const val FLAG_DEX_WIFI = 1 shl 2
        private const val MAX_DEX_CACHE_ENTRIES = 400
        private const val PACKAGE_DELIMITER = "\n"
        private const val DEFAULT_AUTO_OFF_MINUTES = 30
        private val VALID_AUTO_OFF_MINUTES = setOf(15, 30, 60)
    }
}
