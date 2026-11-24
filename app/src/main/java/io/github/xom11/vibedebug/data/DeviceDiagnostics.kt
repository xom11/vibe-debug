package io.github.xom11.vibedebug.data

import android.app.AppOpsManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.os.Build
import io.github.xom11.vibedebug.BuildConfig
import io.github.xom11.vibedebug.model.OemBrandDetector
import io.github.xom11.vibedebug.navigation.SettingsIntents
import io.github.xom11.vibedebug.rules.AndroidForegroundAppDetector
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper
import java.util.Locale

data class DiagnosticsSnapshot(
    val versionName: String,
    val versionCode: Int,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val detectedOem: String,
    val androidRelease: String,
    val sdkInt: Int,
    val localeTag: String,
    val writeSecureSettings: Boolean,
    val usageAccess: Boolean,
    val blockedByPolicy: Boolean,
    val devOptionsEnabled: Boolean,
    val adbEnabled: Boolean,
    val wirelessAdbEnabled: Boolean,
    val supportsWirelessAdb: Boolean,
    val homeLauncher: String,
    val pinWidgetSupported: Boolean,
    val pinShortcutSupported: Boolean,
    val xiaomiPinRestricted: Boolean,
    val miuiShortcutOpMode: Int?,
    val widgetAdded: Boolean,
    val addedTiles: List<String>,
    val intentDeviceInfo: String,
    val intentDevSettings: String,
    val intentUsageAccess: String,
    val intentAppDetails: String,
    val intentMiuiPerm: String,
)

/** Collects non-personal technical device diagnostics on-device for troubleshooting ROM/OEM behavior. */
object DeviceDiagnostics {

    fun collect(context: Context): DiagnosticsSnapshot {
        val appContext = context.applicationContext ?: context
        val prefs = AppPreferences(appContext)
        val gateway = AndroidSettingsGateway(appContext)
        val controller = DevSettingsController(gateway)
        val state = controller.readState()
        val usageAccess = AndroidForegroundAppDetector(appContext).hasUsageAccess()
        val surfaceState = QuickSurfaceHelper.readSurfaceState(appContext, prefs)
        val pm = appContext.packageManager
        val pkg = appContext.packageName

        val pinWidgetSupported = runCatching {
            AppWidgetManager.getInstance(appContext).isRequestPinAppWidgetSupported
        }.getOrDefault(false)

        val pinShortcutSupported = runCatching {
            appContext.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true
        }.getOrDefault(false)

        val usageAccessIntentResolved = resolveComponent(pm, SettingsIntents.buildUsageAccessAppIntent(pkg))
            .takeUnless { it == "none" }
            ?: resolveComponent(pm, SettingsIntents.buildUsageAccessFallbackIntent())

        val miuiPermResolved = resolveComponent(pm, SettingsIntents.buildMiuiAppPermEditorIntent(pkg))
            .takeUnless { it == "none" }
            ?: resolveComponent(pm, SettingsIntents.buildMiuiAppPermEditorExplicitIntent(pkg))

        return DiagnosticsSnapshot(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            detectedOem = OemBrandDetector.detect(Build.MANUFACTURER, Build.BRAND).id,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            localeTag = Locale.getDefault().toLanguageTag(),
            writeSecureSettings = controller.hasWritePermission(),
            usageAccess = usageAccess,
            blockedByPolicy = state.blockedByPolicy,
            devOptionsEnabled = state.devOptionsEnabled,
            adbEnabled = state.adbEnabled,
            wirelessAdbEnabled = state.wirelessAdbEnabled,
            supportsWirelessAdb = state.supportsWirelessAdb,
            homeLauncher = resolveComponent(pm, SettingsIntents.buildHomeLauncherIntent()),
            pinWidgetSupported = pinWidgetSupported,
            pinShortcutSupported = pinShortcutSupported,
            xiaomiPinRestricted = QuickSurfaceHelper.isXiaomiPinRestrictedDevice(appContext),
            miuiShortcutOpMode = QuickSurfaceHelper.queryMiuiInstallShortcutOpMode(appContext),
            widgetAdded = surfaceState.isHomeWidgetAdded,
            addedTiles = surfaceState.addedTiles.map { it.prefKey }.sorted(),
            intentDeviceInfo = resolveComponent(pm, SettingsIntents.buildBuildNumberIntent()),
            intentDevSettings = resolveComponent(pm, SettingsIntents.buildDeveloperOptionsTopIntent()),
            intentUsageAccess = usageAccessIntentResolved,
            intentAppDetails = resolveComponent(pm, SettingsIntents.buildAppDetailsIntent(pkg)),
            intentMiuiPerm = miuiPermResolved,
        )
    }

    internal fun formatMiuiOpMode(mode: Int?): String = when (mode) {
        null -> "n/a"
        AppOpsManager.MODE_ALLOWED -> "allow(0)"
        AppOpsManager.MODE_IGNORED -> "ignore(1)"
        AppOpsManager.MODE_ERRORED -> "error(2)"
        AppOpsManager.MODE_DEFAULT -> "default(3)"
        QuickSurfaceHelper.MIUI_MODE_ASK -> "ask(5)"
        else -> "mode($mode)"
    }

    fun format(snapshot: DiagnosticsSnapshot): String = buildString {
        appendLine("Vibe Debug Diagnostics")
        appendLine("app=${snapshot.versionName} (${snapshot.versionCode})")
        appendLine("device=${snapshot.manufacturer} / ${snapshot.brand} / ${snapshot.model} (oem=${snapshot.detectedOem})")
        appendLine("android=${snapshot.androidRelease} (API ${snapshot.sdkInt}) locale=${snapshot.localeTag}")
        appendLine(
            "permissions: WRITE_SECURE_SETTINGS=${snapshot.writeSecureSettings}, " +
                "usageAccess=${snapshot.usageAccess}, policyBlocked=${snapshot.blockedByPolicy}"
        )
        appendLine(
            "flags: dev=${snapshot.devOptionsEnabled}, adb=${snapshot.adbEnabled}, " +
                "wireless=${snapshot.wirelessAdbEnabled} (supported=${snapshot.supportsWirelessAdb})"
        )
        appendLine(
            "pin: launcher=${snapshot.homeLauncher}, widgetSupported=${snapshot.pinWidgetSupported}, " +
                "shortcutSupported=${snapshot.pinShortcutSupported}, xiaomiRestricted=${snapshot.xiaomiPinRestricted}, " +
                "miuiOp10017=${formatMiuiOpMode(snapshot.miuiShortcutOpMode)}"
        )
        val tiles = if (snapshot.addedTiles.isEmpty()) "none" else snapshot.addedTiles.joinToString(",")
        appendLine("surfaces: widgetAdded=${snapshot.widgetAdded}, qsTiles=[$tiles]")
        appendLine(
            "intents: deviceInfo=${snapshot.intentDeviceInfo}, devSettings=${snapshot.intentDevSettings}, " +
                "usageAccess=${snapshot.intentUsageAccess}, appDetails=${snapshot.intentAppDetails}, " +
                "miuiPerm=${snapshot.intentMiuiPerm}"
        )
    }.trimEnd()

    fun buildReport(context: Context): String = format(collect(context))

    private fun resolveComponent(pm: PackageManager, intent: Intent): String {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            val ai = info?.activityInfo ?: return@runCatching "none"
            ComponentName(ai.packageName, ai.name).flattenToShortString()
        }.getOrDefault("none")
    }
}
