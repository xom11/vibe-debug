package io.github.xom11.vibedebug.shortcut

import android.app.AppOpsManager
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import io.github.xom11.vibedebug.R
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.model.OemBrand
import io.github.xom11.vibedebug.model.OemBrandDetector
import io.github.xom11.vibedebug.navigation.SettingsIntents
import io.github.xom11.vibedebug.tile.UsbDebugTileService
import io.github.xom11.vibedebug.tile.WirelessDebugTileService
import io.github.xom11.vibedebug.widget.UsbDebugWidgetProvider

enum class HomePinOutcome {
    REQUESTED,
    NEEDS_OEM_PERMISSION,
    UNSUPPORTED,
}

enum class HomePinFallbackKind {
    XIAOMI_PERMISSION,
    GENERIC_MANUAL,
}

sealed interface TileAddOutcome {
    data class Added(val tileLabel: String) : TileAddOutcome
    data class AlreadyAdded(val tileLabel: String) : TileAddOutcome
    data object Dismissed : TileAddOutcome
    data class NeedsManualInstructions(val tileLabel: String) : TileAddOutcome
}

enum class QuickTileType(
    val prefKey: String,
    val serviceClass: Class<*>,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    USB_DEBUG(
        "usb_debug",
        UsbDebugTileService::class.java,
        R.string.tile_label,
        R.drawable.ic_qs_usb_debug,
    ),
    WIRELESS_DEBUG(
        "wireless_debug",
        WirelessDebugTileService::class.java,
        R.string.tile_wireless_label,
        R.drawable.ic_qs_wireless_debug,
    );

    companion object {
        fun fromServiceClass(cls: Class<*>): QuickTileType? =
            entries.firstOrNull { it.serviceClass == cls }
    }
}

data class QuickSurfaceState(
    val addedTiles: Set<QuickTileType> = emptySet(),
    val isHomeWidgetAdded: Boolean = false,
    val isHomeUsbShortcutPinned: Boolean = false,
    val pinnedSafeLaunchPackages: Set<String> = emptySet(),
)

/** Coordinates Quick Settings tile prompts, removal, pinned widgets/shortcuts, and surface refreshes. */
object QuickSurfaceHelper {

    const val PINNED_USB_SHORTCUT_ID = "pin_usb_debug"
    const val STATIC_USB_SHORTCUT_ID = "usb_debug"
    const val SAFE_LAUNCH_SHORTCUT_PREFIX = "safe_launch_"

    private const val SECURE_KEY_QS_TILES = "sysui_qs_tiles"

    internal const val RESULT_TILE_NOT_ADDED = 0
    internal const val RESULT_TILE_ALREADY_ADDED = 1
    internal const val RESULT_TILE_ADDED = 2

    internal const val MIUI_OP_INSTALL_SHORTCUT = 10017
    internal const val MIUI_MODE_ASK = 5

    fun requestAddUsbTile(
        context: Context,
        onResult: (TileAddOutcome) -> Unit
    ) {
        requestAddTile(context, QuickTileType.USB_DEBUG, onResult)
    }

    fun requestAddWirelessTile(
        context: Context,
        onResult: (TileAddOutcome) -> Unit
    ) {
        requestAddTile(context, QuickTileType.WIRELESS_DEBUG, onResult)
    }

    fun requestAddTile(
        context: Context,
        tile: QuickTileType,
        onResult: (TileAddOutcome) -> Unit,
    ) {
        val appContext = context.applicationContext ?: context
        val label = context.getString(tile.labelRes)
        val component = ComponentName(appContext, tile.serviceClass)
        val wasDisabled = !isTileComponentEnabled(appContext, tile.serviceClass)
        if (wasDisabled) {
            runCatching {
                appContext.packageManager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }

        val invokeRequest = {
            requestAddTileInternal(
                context = context,
                tile = tile,
                label = label,
                wasReEnabled = wasDisabled,
                onResult = onResult,
            )
        }

        if (wasDisabled) {
            Handler(Looper.getMainLooper()).postDelayed(invokeRequest, 400L)
        } else {
            invokeRequest()
        }
    }

    fun removeQsTile(context: Context, tile: QuickTileType): String {
        val appContext = context.applicationContext ?: context
        val component = ComponentName(appContext, tile.serviceClass)
        AppPreferences(appContext).setQsTileAdded(tile.prefKey, false)
        runCatching {
            val raw = Settings.Secure.getString(appContext.contentResolver, SECURE_KEY_QS_TILES)
            if (!raw.isNullOrBlank()) {
                val cleaned = removeTileSpecFromCsv(raw, component)
                if (cleaned != raw) {
                    Settings.Secure.putString(appContext.contentResolver, SECURE_KEY_QS_TILES, cleaned)
                }
            }
        }
        runCatching {
            appContext.packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        return context.getString(tile.labelRes)
    }

    fun isTileComponentEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val appContext = context.applicationContext ?: context
        return runCatching {
            val state = appContext.packageManager.getComponentEnabledSetting(
                ComponentName(appContext, serviceClass)
            )
            state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.getOrDefault(true)
    }

    fun readSurfaceState(
        context: Context,
        preferences: AppPreferences = AppPreferences(context),
    ): QuickSurfaceState {
        val appContext = context.applicationContext ?: context
        val rawCsv = runCatching {
            Settings.Secure.getString(appContext.contentResolver, SECURE_KEY_QS_TILES)
        }.getOrNull()
        if (!rawCsv.isNullOrBlank()) {
            QuickTileType.entries.forEach { tile ->
                val component = ComponentName(appContext, tile.serviceClass)
                val present = isTileComponentEnabled(appContext, tile.serviceClass) &&
                    isTileSpecInCsv(rawCsv, component)
                preferences.setQsTileAdded(tile.prefKey, present)
            }
        }

        val addedTiles = QuickTileType.entries.filter { tile ->
            isTileComponentEnabled(appContext, tile.serviceClass) &&
                preferences.isQsTileAdded(tile.prefKey)
        }.toSet()

        val isHomeWidgetAdded = runCatching {
            val manager = AppWidgetManager.getInstance(appContext)
            manager.getAppWidgetIds(ComponentName(appContext, UsbDebugWidgetProvider::class.java)).isNotEmpty()
        }.getOrDefault(false)

        val pinnedIds = runCatching {
            appContext.getSystemService(ShortcutManager::class.java)
                ?.pinnedShortcuts
                ?.filter { it.isEnabled }
                ?.map { it.id }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())

        val isHomeUsbShortcutPinned =
            PINNED_USB_SHORTCUT_ID in pinnedIds || STATIC_USB_SHORTCUT_ID in pinnedIds

        val pinnedSafeLaunchPackages = pinnedIds.mapNotNull { id ->
            if (id.startsWith(SAFE_LAUNCH_SHORTCUT_PREFIX)) {
                id.removePrefix(SAFE_LAUNCH_SHORTCUT_PREFIX).takeIf { it.isNotEmpty() }
            } else {
                null
            }
        }.toSet()

        return QuickSurfaceState(
            addedTiles = addedTiles,
            isHomeWidgetAdded = isHomeWidgetAdded,
            isHomeUsbShortcutPinned = isHomeUsbShortcutPinned,
            pinnedSafeLaunchPackages = pinnedSafeLaunchPackages,
        )
    }

    internal fun isTileSpecInCsv(csv: String, component: ComponentName): Boolean {
        val full = component.flattenToString()
        val short = component.flattenToShortString()
        val targets = setOf(full, short, "custom($full)", "custom($short)")
        return csv.split(',')
            .map { it.trim() }
            .any { token -> targets.any { target -> token.equals(target, ignoreCase = true) } }
    }

    internal fun removeTileSpecFromCsv(csv: String, component: ComponentName): String {
        return csv.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isTileSpecInCsv(it, component) }
            .joinToString(",")
    }

    /** Refreshes the home widget and enabled Quick Settings tile services. */
    fun syncAllSurfaces(context: Context) {
        val appContext = context.applicationContext ?: context
        UsbDebugWidgetProvider.updateAllWidgets(appContext)
        QuickTileType.entries.forEach { tile ->
            if (isTileComponentEnabled(appContext, tile.serviceClass)) {
                runCatching {
                    TileService.requestListeningState(
                        appContext,
                        ComponentName(appContext, tile.serviceClass)
                    )
                }
            }
        }
    }

    internal fun mapTileAddResultCode(
        resultCode: Int,
        tileLabel: String,
        wasReEnabled: Boolean = false,
    ): TileAddOutcome {
        return when (resultCode) {
            RESULT_TILE_ADDED -> TileAddOutcome.Added(tileLabel)
            RESULT_TILE_ALREADY_ADDED -> {
                if (wasReEnabled) TileAddOutcome.Added(tileLabel)
                else TileAddOutcome.AlreadyAdded(tileLabel)
            }
            RESULT_TILE_NOT_ADDED -> TileAddOutcome.Dismissed
            else -> TileAddOutcome.NeedsManualInstructions(tileLabel)
        }
    }

    private fun requestAddTileInternal(
        context: Context,
        tile: QuickTileType,
        label: String,
        wasReEnabled: Boolean,
        onResult: (TileAddOutcome) -> Unit,
    ) {
        val appContext = context.applicationContext ?: context
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(TileAddOutcome.NeedsManualInstructions(label))
            return
        }

        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onResult(TileAddOutcome.NeedsManualInstructions(label))
            return
        }

        try {
            statusBarManager.requestAddTileService(
                ComponentName(appContext, tile.serviceClass),
                label,
                Icon.createWithResource(context, tile.iconRes),
                ContextCompat.getMainExecutor(context)
            ) { code ->
                val outcome = mapTileAddResultCode(code, label, wasReEnabled = wasReEnabled)
                if (outcome is TileAddOutcome.Added || outcome is TileAddOutcome.AlreadyAdded) {
                    AppPreferences(appContext).setQsTileAdded(tile.prefKey, true)
                }
                onResult(outcome)
            }
        } catch (_: Exception) {
            onResult(TileAddOutcome.NeedsManualInstructions(label))
        }
    }

    /** Returns true when running on Xiaomi / MIUI / HyperOS where home shortcut pinning is gated. */
    fun isXiaomiPinRestrictedDevice(context: Context): Boolean {
        return OemBrandDetector.detect(Build.MANUFACTURER, Build.BRAND) == OemBrand.XIAOMI ||
            SettingsIntents.hasMiuiPermissionEditor(context)
    }

    internal fun isMiuiShortcutOpDenied(mode: Int): Boolean {
        return mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED
    }

    internal fun queryMiuiInstallShortcutOpMode(context: Context): Int? {
        if (!isXiaomiPinRestrictedDevice(context)) return null
        val appContext = context.applicationContext ?: context
        return runCatching {
            val appOps = appContext.getSystemService(AppOpsManager::class.java) ?: return null
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            )
            method.invoke(appOps, MIUI_OP_INSTALL_SHORTCUT, Process.myUid(), appContext.packageName) as? Int
        }.getOrNull()
    }

    /** Returns true when Xiaomi's custom OP_INSTALL_SHORTCUT (10017) explicitly denies pinning. */
    fun isHomeShortcutPinPermissionDenied(context: Context): Boolean {
        return queryMiuiInstallShortcutOpMode(context)?.let(::isMiuiShortcutOpDenied) ?: false
    }

    /** Returns true when MIUI's custom AppOp 10017 can be queried via reflection on this device. */
    fun isMiuiInstallShortcutOpReadable(context: Context): Boolean {
        return queryMiuiInstallShortcutOpMode(context) != null
    }

    internal fun resolvePinOutcome(
        permissionDenied: Boolean,
        supported: Boolean,
        requested: Boolean,
    ): HomePinOutcome = when {
        permissionDenied -> HomePinOutcome.NEEDS_OEM_PERMISSION
        !supported -> HomePinOutcome.UNSUPPORTED
        requested -> HomePinOutcome.REQUESTED
        else -> HomePinOutcome.UNSUPPORTED
    }

    fun requestPinUsbWidgetWithOutcome(context: Context): HomePinOutcome {
        if (isHomeShortcutPinPermissionDenied(context)) {
            return HomePinOutcome.NEEDS_OEM_PERMISSION
        }
        val manager = AppWidgetManager.getInstance(context)
        val supported = manager.isRequestPinAppWidgetSupported
        val requested = if (supported) {
            runCatching {
                manager.requestPinAppWidget(
                    ComponentName(context, UsbDebugWidgetProvider::class.java),
                    null,
                    null
                )
            }.getOrDefault(false)
        } else {
            false
        }
        return resolvePinOutcome(
            permissionDenied = false,
            supported = supported,
            requested = requested,
        )
    }

    fun requestPinUsbWidget(context: Context): Boolean =
        requestPinUsbWidgetWithOutcome(context) == HomePinOutcome.REQUESTED

    fun requestPinUsbShortcut(context: Context): Boolean {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!shortcutManager.isRequestPinShortcutSupported) return false

        runCatching { shortcutManager.enableShortcuts(listOf(PINNED_USB_SHORTCUT_ID)) }

        val shortcutInfo = ShortcutInfo.Builder(context, PINNED_USB_SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.shortcut_usb_short))
            .setLongLabel(context.getString(R.string.shortcut_usb_long))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_usb))
            .setIntent(
                Intent(context, ShortcutDispatchActivity::class.java).apply {
                    action = ShortcutDispatchActivity.ACTION_SHORTCUT_USB
                }
            )
            .build()

        return runCatching {
            shortcutManager.requestPinShortcut(shortcutInfo, null)
        }.getOrDefault(false)
    }

    internal fun showMainToast(context: Context, @StringRes resId: Int) {
        val appContext = context.applicationContext ?: context
        val show = {
            runCatching {
                Toast.makeText(appContext, resId, Toast.LENGTH_LONG).show()
            }
            Unit
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            show()
        } else {
            Handler(Looper.getMainLooper()).post(show)
        }
    }
}
