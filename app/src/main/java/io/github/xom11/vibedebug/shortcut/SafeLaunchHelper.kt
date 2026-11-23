package io.github.xom11.vibedebug.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.xom11.vibedebug.R
import io.github.xom11.vibedebug.data.AndroidSettingsGateway
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.data.AutoOffManager
import io.github.xom11.vibedebug.data.DevSettingsController

/** Metadata for an installed app that can be launched via Safe Launch. */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val isBankingSuggested: Boolean
)

/** Outcome of attempting to turn off Developer Options and launch an app cleanly. */
sealed interface SafeLaunchResult {
    data object LaunchedClean : SafeLaunchResult
    data object NeedsPermission : SafeLaunchResult
    data object BlockedByPolicy : SafeLaunchResult
    data object AppNotFound : SafeLaunchResult
}

/**
 * Queries launcher apps, launches allowlisted apps with Developer Options disabled,
 * and creates badged pinned shortcuts targeting [SafeLaunchActivity].
 */
object SafeLaunchHelper {

    const val EXTRA_PACKAGE = "safe_launch_package"
    const val ACTION_SAFE_LAUNCH = "io.github.xom11.vibedebug.ACTION_SAFE_LAUNCH"

    private const val BADGE_ICON_SIZE_PX = 192
    private const val BADGE_BG_COLOR = 0xFF0E1113.toInt()
    private const val BADGE_RING_COLOR = 0xFF7EE2A8.toInt()

    val SUGGESTED_VN_BANKING_PACKAGES: Set<String> = setOf(
        "com.vnid",
        "com.VCB",
        "com.vnpay.vpbankonline",
        "com.vietinbank.ipay",
        "com.vnpay.bidv",
        "vn.com.techcombank.bb.app",
        "com.mbmobile",
        "com.tpb.mb.gprsandroid",
        "mobile.acb.com.vn",
        "com.mservice.momotransfer",
        "vn.com.vng.zalopay",
        "xyz.be.cake",
        "com.bplus.vtpay"
    )

    /** Lists deduplicated launcher apps (excluding this app), prioritizing suggested banking apps. */
    fun queryLaunchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
        }.getOrDefault(emptyList())

        val seenPackages = HashSet<String>(activities.size)
        val apps = ArrayList<LaunchableApp>(activities.size)
        for (info in activities) {
            val pkg = info.activityInfo?.packageName?.takeIf { it.isNotBlank() } ?: continue
            if (pkg == context.packageName || !seenPackages.add(pkg)) continue
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg).ifBlank { pkg }
            apps += LaunchableApp(
                packageName = pkg,
                label = label,
                isBankingSuggested = pkg in SUGGESTED_VN_BANKING_PACKAGES
            )
        }
        return apps.sortedWith(
            compareByDescending<LaunchableApp> { it.isBankingSuggested }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName }
        )
    }

    /** Returns the user-visible label for [packageName], falling back to [packageName]. */
    fun getAppLabel(context: Context, packageName: String): String {
        val pm = context.packageManager
        return runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
        }.getOrDefault(packageName)
    }

    /** Disables Developer Options and debugging before launching [packageName]. */
    fun launchSafely(context: Context, packageName: String): SafeLaunchResult {
        val appContext = context.applicationContext ?: context
        val controller = DevSettingsController(AndroidSettingsGateway(appContext))
        if (controller.readState().blockedByPolicy) {
            return SafeLaunchResult.BlockedByPolicy
        }
        if (!controller.hasWritePermission()) {
            return SafeLaunchResult.NeedsPermission
        }
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
            ?: return SafeLaunchResult.AppNotFound

        val prefs = AppPreferences(appContext)
        controller.setDeveloperOptions(false)
        controller.syncKeepAwakeWithUsb(prefs.keepAwakeOnUsb)
        AutoOffManager(appContext, prefs).cancel()
        QuickSurfaceHelper.syncAllSurfaces(appContext)

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            SafeLaunchResult.LaunchedClean
        } catch (_: Exception) {
            SafeLaunchResult.AppNotFound
        }
    }

    /** Requests pinning a badged 1x1 Safe Launch home shortcut for [packageName]. */
    fun requestPinSafeLaunchShortcut(context: Context, packageName: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val pm = context.packageManager
        val appInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        }.getOrNull() ?: return false

        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
            .getOrDefault(packageName)
            .ifBlank { packageName }

        val badgedBitmap = runCatching {
            createBadgedIcon(pm.getApplicationIcon(appInfo).toBitmap(BADGE_ICON_SIZE_PX, BADGE_ICON_SIZE_PX))
        }.getOrNull()

        val iconCompat = if (badgedBitmap != null) {
            IconCompat.createWithBitmap(badgedBitmap)
        } else {
            IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        }

        val launchIntent = Intent(context, SafeLaunchActivity::class.java).apply {
            action = ACTION_SAFE_LAUNCH
            putExtra(EXTRA_PACKAGE, packageName)
        }

        val shortcutId = "${QuickSurfaceHelper.SAFE_LAUNCH_SHORTCUT_PREFIX}$packageName"
        runCatching {
            context.getSystemService(ShortcutManager::class.java)?.enableShortcuts(listOf(shortcutId))
        }

        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(label)
            .setLongLabel(context.getString(R.string.safe_launch_shortcut_long, label))
            .setIcon(iconCompat)
            .setIntent(launchIntent)
            .build()

        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrDefault(false)
    }

    // Dark badge + mint ring at bottom-right distinguishes Safe Launch shortcuts from stock app icons.
    private fun createBadgedIcon(source: Bitmap): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
        val canvas = Canvas(out)
        val size = out.width.toFloat()
        val radius = size * 0.2f
        val center = size - radius
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BADGE_BG_COLOR
        }
        canvas.drawCircle(center, center, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.28f
        paint.color = BADGE_RING_COLOR
        canvas.drawCircle(center, center, radius * 0.55f, paint)
        return out
    }
}
