package io.github.xom11.vibedebug.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import io.github.xom11.vibedebug.data.AppRuleMode
import io.github.xom11.vibedebug.data.AutoOffManager
import io.github.xom11.vibedebug.data.DevSettingsController
import io.github.xom11.vibedebug.rules.AndroidForegroundAppDetector
import io.github.xom11.vibedebug.rules.AppRuleEngine
import io.github.xom11.vibedebug.rules.AppWatcherService
import io.github.xom11.vibedebug.rules.StrictAppAnalysis
import io.github.xom11.vibedebug.rules.StrictAppCatalog
import io.github.xom11.vibedebug.rules.StrictAppDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Metadata for an installed app that can be launched via Safe Launch. */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val isBankingSuggested: Boolean,
    val analysis: StrictAppAnalysis = StrictAppAnalysis(),
    val isSystemOnly: Boolean = false,
    val lastUpdateTime: Long = 0L,
    val apkPaths: List<String> = emptyList(),
    val appCategory: Int = ApplicationInfo.CATEGORY_UNDEFINED,
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

    val SUGGESTED_VN_BANKING_PACKAGES: Set<String> = StrictAppCatalog.KNOWN_STRICT_PACKAGES

    /** Lists deduplicated launcher apps (excluding this app), ranked by strict-app detection confidence. */
    fun queryLaunchableApps(context: Context): List<LaunchableApp> {
        val appContext = context.applicationContext ?: context
        val pm = appContext.packageManager
        val dexCache = AppPreferences(appContext).readDexScanCache()
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
            val activityInfo = info.activityInfo ?: continue
            val pkg = activityInfo.packageName?.takeIf { it.isNotBlank() } ?: continue
            if (pkg == appContext.packageName || !seenPackages.add(pkg)) continue

            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg).ifBlank { pkg }
            val appInfo = activityInfo.applicationInfo
            val isSystemApp = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val rawApkPaths = if (isSystemApp) {
                emptyList()
            } else {
                buildList {
                    appInfo?.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
                    appInfo?.splitSourceDirs?.filter { it.isNotBlank() }?.let(::addAll)
                }
            }
            val appCategory = appInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
            val lastUpdateTime = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L)).lastUpdateTime
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0).lastUpdateTime
                }
            }.getOrDefault(0L)

            val cachedDex = dexCache[pkg]?.takeIf {
                it.lastUpdateTime == lastUpdateTime &&
                    it.scannerVersion == StrictAppDetector.SCANNER_VERSION
            }?.signals

            val analysis = StrictAppDetector.analyzeFast(
                packageName = pkg,
                label = label,
                apkPaths = rawApkPaths,
                appCategory = appCategory,
                cachedDex = cachedDex,
            )

            apps += LaunchableApp(
                packageName = pkg,
                label = label,
                isBankingSuggested = analysis.isSuggested,
                analysis = analysis,
                isSystemOnly = isSystemApp,
                lastUpdateTime = lastUpdateTime,
                apkPaths = rawApkPaths,
                appCategory = appCategory,
            )
        }
        return sortLaunchableApps(apps)
    }

    /** Sorts launchable apps by detection confidence, score, label, and package name. */
    fun sortLaunchableApps(apps: List<LaunchableApp>): List<LaunchableApp> =
        apps.sortedWith(
            compareByDescending<LaunchableApp> { it.analysis.confidence.ordinal }
                .thenByDescending { it.analysis.score }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName }
        )

    /**
     * Runs an on-demand streaming DEX scan on unflagged non-system apps to uncover hidden
     * anti-debug / RASP checks and persists cached results by `(packageName, lastUpdateTime)`.
     */
    suspend fun runDeepStrictScan(
        context: Context,
        apps: List<LaunchableApp>,
        onProgress: (scanned: Int, total: Int, currentLabel: String) -> Unit = { _, _, _ -> },
    ): List<LaunchableApp> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext ?: context
        val prefs = AppPreferences(appContext)
        val candidates = apps.filter {
            !it.isSystemOnly &&
                !it.analysis.isHighConfidence &&
                !it.analysis.deepScanned &&
                it.apkPaths.isNotEmpty()
        }
        if (candidates.isEmpty()) {
            return@withContext sortLaunchableApps(apps)
        }

        val updatedByPkg = HashMap<String, LaunchableApp>(apps.size)
        apps.forEach { updatedByPkg[it.packageName] = it }
        val cacheUpdates = LinkedHashMap<String, AppPreferences.CachedDexEntry>()
        val scanJob = currentCoroutineContext()[Job]

        try {
            candidates.forEachIndexed { idx, app ->
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.Main) {
                    onProgress(idx + 1, candidates.size, app.label)
                }
                val (updatedAnalysis, dexSignals) = StrictAppDetector.analyzeDeep(
                    packageName = app.packageName,
                    label = app.label,
                    apkPaths = app.apkPaths,
                    appCategory = app.appCategory,
                    fastAnalysis = app.analysis,
                    isSystemOnly = app.isSystemOnly,
                    isCancelled = { scanJob?.isActive == false },
                )
                if (dexSignals != null) {
                    updatedByPkg[app.packageName] = app.copy(
                        isBankingSuggested = updatedAnalysis.isSuggested,
                        analysis = updatedAnalysis,
                    )
                    cacheUpdates[app.packageName] = AppPreferences.CachedDexEntry(
                        lastUpdateTime = app.lastUpdateTime,
                        scannerVersion = StrictAppDetector.SCANNER_VERSION,
                        signals = dexSignals,
                    )
                }
            }
            prefs.lastDeepScanAtMs = System.currentTimeMillis()
        } finally {
            if (cacheUpdates.isNotEmpty()) {
                prefs.putAllCachedDexSignals(cacheUpdates)
            }
        }

        sortLaunchableApps(apps.map { updatedByPkg[it.packageName] ?: it })
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
        val detector = AndroidForegroundAppDetector(appContext)
        val engine = AppRuleEngine(
            prefs = prefs,
            controller = controller,
            detector = detector,
            autoOffManager = AutoOffManager(appContext, prefs),
            onSurfaceSync = { QuickSurfaceHelper.syncAllSurfaces(appContext) },
        )
        val recordHold = prefs.autoAppRulesEnabled && prefs.autoRestoreOnExit && detector.hasUsageAccess()
        engine.applyRuleForPackage(packageName, AppRuleMode.DEV_OFF, recordHold = recordHold)
        AppWatcherService.onSafeLaunch(packageName)
        AppWatcherService.sync(appContext)

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            SafeLaunchResult.LaunchedClean
        } catch (_: Exception) {
            SafeLaunchResult.AppNotFound
        }
    }

    /** Requests pinning a badged 1x1 Safe Launch home shortcut for [packageName] and reports OEM permission blocks. */
    fun requestPinSafeLaunchShortcutWithOutcome(context: Context, packageName: String): HomePinOutcome {
        if (QuickSurfaceHelper.isHomeShortcutPinPermissionDenied(context)) {
            return HomePinOutcome.NEEDS_OEM_PERMISSION
        }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return HomePinOutcome.UNSUPPORTED
        }
        val pm = context.packageManager
        val appInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        }.getOrNull() ?: return HomePinOutcome.UNSUPPORTED

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

        val requested = runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrDefault(false)

        return QuickSurfaceHelper.resolvePinOutcome(
            permissionDenied = false,
            supported = true,
            requested = requested,
        )
    }

    /** Requests pinning a badged 1x1 Safe Launch home shortcut for [packageName]. */
    fun requestPinSafeLaunchShortcut(context: Context, packageName: String): Boolean =
        requestPinSafeLaunchShortcutWithOutcome(context, packageName) == HomePinOutcome.REQUESTED

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
