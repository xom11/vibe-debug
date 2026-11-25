package io.github.xom11.vibedebug

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.xom11.vibedebug.data.AndroidSettingsGateway
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.data.AutoOffManager
import io.github.xom11.vibedebug.data.DevSettingsController
import io.github.xom11.vibedebug.data.WirelessSwitchResult
import io.github.xom11.vibedebug.debug.DebugPreviewConfig
import io.github.xom11.vibedebug.model.OemBrandDetector
import io.github.xom11.vibedebug.navigation.SettingsIntents
import io.github.xom11.vibedebug.rules.AndroidForegroundAppDetector
import io.github.xom11.vibedebug.rules.AppRuleEngine
import io.github.xom11.vibedebug.rules.AppWatcherService
import io.github.xom11.vibedebug.shortcut.HomePinFallbackKind
import io.github.xom11.vibedebug.shortcut.HomePinOutcome
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper
import io.github.xom11.vibedebug.ui.UsbDebugScreen
import io.github.xom11.vibedebug.ui.theme.UsbDebugSwitchTheme
import io.github.xom11.vibedebug.ui.theme.resolveIsDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var gateway: AndroidSettingsGateway
    private lateinit var controller: DevSettingsController
    private lateinit var preferences: AppPreferences
    private lateinit var autoOffManager: AutoOffManager
    private lateinit var foregroundDetector: AndroidForegroundAppDetector

    private var currentPreviewConfig by mutableStateOf<DebugPreviewConfig?>(null)
    private var requestedTab by mutableStateOf<String?>(null)
    private var requestedTabTick by mutableIntStateOf(0)
    private var windowFocusTick by mutableIntStateOf(0)
    private var isWindowFocused by mutableStateOf(true)
    private var notificationPromptShown = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        gateway = AndroidSettingsGateway(applicationContext)
        controller = DevSettingsController(gateway)
        preferences = AppPreferences(applicationContext)
        autoOffManager = AutoOffManager(applicationContext, preferences)
        foregroundDetector = AndroidForegroundAppDetector(applicationContext)
        currentPreviewConfig = DebugPreviewConfig.fromIntent(intent)
        requestedTab = intent?.getStringExtra(EXTRA_OPEN_TAB)

        setContent {
            val preview = currentPreviewConfig
            var liveState by remember { mutableStateOf(controller.readState()) }
            var livePermission by remember { mutableStateOf(controller.hasWritePermission()) }
            var previewState by remember(preview) { mutableStateOf(preview?.simulatedState) }

            var autoJumpEnabled by remember { mutableStateOf(preferences.autoJumpToUsbDebugging) }
            var autoJumpConsumedForSession by remember { mutableStateOf(savedInstanceState != null) }
            var autoJumpPending by remember { mutableStateOf(false) }

            var themePreference by remember { mutableStateOf(preferences.themePreference) }
            var autoOffEnabled by remember { mutableStateOf(preferences.autoOffEnabled) }
            var autoOffMinutes by remember { mutableIntStateOf(preferences.autoOffMinutes) }
            var autoOffTargetAtMs by remember { mutableLongStateOf(preferences.autoOffTargetAtMs) }
            var keepAwakeOnUsb by remember { mutableStateOf(preferences.keepAwakeOnUsb) }
            var safeLaunchPackages by remember { mutableStateOf(preferences.safeLaunchPackages) }
            var autoAppRulesEnabled by remember { mutableStateOf(preferences.autoAppRulesEnabled) }
            var autoRestoreOnExit by remember { mutableStateOf(preferences.autoRestoreOnExit) }
            var hasUsageAccess by remember { mutableStateOf(foregroundDetector.hasUsageAccess()) }
            var quickSurfaceState by remember {
                mutableStateOf(QuickSurfaceHelper.readSurfaceState(applicationContext, preferences))
            }
            var wirelessWifiWarning by remember { mutableStateOf(false) }
            var wirelessBlockedTick by remember { mutableIntStateOf(0) }
            var homePinFallbackTick by remember { mutableIntStateOf(0) }
            var homePinFallbackKind by remember { mutableStateOf(HomePinFallbackKind.XIAOMI_PERMISSION) }
            var pendingWirelessEnableAtMs by remember { mutableLongStateOf(0L) }
            val surfaceRefreshScope = rememberCoroutineScope()

            val refreshSurfaceState = {
                quickSurfaceState = QuickSurfaceHelper.readSurfaceState(applicationContext, preferences)
            }

            val refreshRuleUiState = {
                autoAppRulesEnabled = preferences.autoAppRulesEnabled
                autoRestoreOnExit = preferences.autoRestoreOnExit
                safeLaunchPackages = preferences.safeLaunchPackages
                hasUsageAccess = foregroundDetector.hasUsageAccess()
            }

            val schedulePostPinRefresh: () -> Unit = {
                surfaceRefreshScope.launch {
                    listOf(500L, 1000L, 1500L).forEach { delayMs ->
                        delay(delayMs)
                        refreshSurfaceState()
                    }
                }
            }

            val watchSilentHomePinRejection: (() -> Boolean) -> Unit = { checkPinned ->
                surfaceRefreshScope.launch {
                    val startMs = SystemClock.elapsedRealtime()
                    val startFocusTick = windowFocusTick
                    var sawFocusLoss = !isWindowFocused
                    var fastBounceBack = false
                    while (SystemClock.elapsedRealtime() - startMs < 750L) {
                        delay(50L)
                        if (!isWindowFocused) {
                            sawFocusLoss = true
                        } else if (sawFocusLoss || windowFocusTick > startFocusTick) {
                            fastBounceBack = (SystemClock.elapsedRealtime() - startMs) < 450L
                            break
                        }
                    }
                    if (!sawFocusLoss || fastBounceBack) {
                        delay(250L)
                        refreshSurfaceState()
                        if (!checkPinned()) {
                            homePinFallbackKind = if (QuickSurfaceHelper.isXiaomiPinRestrictedDevice(applicationContext)) {
                                HomePinFallbackKind.XIAOMI_PERMISSION
                            } else {
                                HomePinFallbackKind.GENERIC_MANUAL
                            }
                            homePinFallbackTick++
                        }
                    }
                }
            }

            val refreshLiveState = {
                controller.syncKeepAwakeWithUsb(preferences.keepAwakeOnUsb)
                val updated = controller.readState()
                liveState = updated
                livePermission = controller.hasWritePermission()
                if (updated.wirelessAdbEnabled) {
                    wirelessWifiWarning = false
                    if (pendingWirelessEnableAtMs == 0L || SystemClock.elapsedRealtime() - pendingWirelessEnableAtMs >= 800L) {
                        pendingWirelessEnableAtMs = 0L
                    }
                } else if (
                    pendingWirelessEnableAtMs > 0L &&
                    SystemClock.elapsedRealtime() - pendingWirelessEnableAtMs <= 2000L
                ) {
                    pendingWirelessEnableAtMs = 0L
                    wirelessWifiWarning = true
                    wirelessBlockedTick++
                }
                autoOffManager.resync(updated.adbEnabled)
                autoOffTargetAtMs = preferences.autoOffTargetAtMs
                QuickSurfaceHelper.syncAllSurfaces(applicationContext)
                refreshSurfaceState()
            }

            DisposableEffect(Unit) {
                val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        refreshLiveState()
                    }
                }
                val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    refreshSurfaceState()
                    refreshRuleUiState()
                }
                gateway.registerObserver(observer)
                preferences.registerChangeListener(prefListener)
                onDispose {
                    gateway.unregisterObserver(observer)
                    preferences.unregisterChangeListener(prefListener)
                }
            }

            LaunchedEffect(windowFocusTick) {
                if (windowFocusTick > 0) {
                    refreshSurfaceState()
                    refreshRuleUiState()
                    AppWatcherService.sync(applicationContext)
                    delay(400L)
                    refreshSurfaceState()
                    delay(900L)
                    refreshSurfaceState()
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val lifecycleObserver = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        refreshLiveState()
                        refreshRuleUiState()
                        AppWatcherService.sync(applicationContext)
                        maybeRequestPostNotifications(hasUsageAccess)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                }
            }

            val isDark = resolveIsDarkTheme(themePreference)
            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.argb(0x66, 0x00, 0x00, 0x00))
                    },
                )
            }

            val effectiveState = previewState ?: liveState
            val effectivePermission = preview?.hasWritePermission ?: livePermission
            val detectedBrand = remember(preview) {
                preview?.brandOverride ?: OemBrandDetector.detect(Build.MANUFACTURER, Build.BRAND)
            }
            val effectiveAutoOffTargetAtMs = if (previewState != null) {
                if (effectivePermission && effectiveState.adbEnabled && autoOffEnabled) {
                    autoOffTargetAtMs.takeIf { it > 0L }
                        ?: (System.currentTimeMillis() + autoOffMinutes * 60_000L)
                } else {
                    0L
                }
            } else {
                autoOffTargetAtMs
            }

            val keepScreenAwakeNow = !effectiveState.blockedByPolicy &&
                ((keepAwakeOnUsb && effectiveState.adbEnabled) || effectiveState.keepAwakeEnabled)
            LaunchedEffect(keepScreenAwakeNow) {
                if (keepScreenAwakeNow) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            LaunchedEffect(
                preview,
                autoJumpEnabled,
                effectivePermission,
                effectiveState.devOptionsEnabled,
                effectiveState.blockedByPolicy,
            ) {
                if (
                    preview == null &&
                    !autoJumpConsumedForSession &&
                    autoJumpEnabled &&
                    !effectivePermission &&
                    effectiveState.devOptionsEnabled &&
                    !effectiveState.blockedByPolicy
                ) {
                    autoJumpPending = true
                    delay(1200)
                    if (autoJumpPending) {
                        autoJumpPending = false
                        autoJumpConsumedForSession = true
                        SettingsIntents.launchEnableAdbScreen(this@MainActivity)
                    }
                } else {
                    autoJumpPending = false
                }
            }

            val addProtectedPackages: (List<String>) -> Unit = { pkgs ->
                val wasEmpty = preferences.safeLaunchPackages.isEmpty()
                preferences.addSafeLaunchPackages(pkgs)
                safeLaunchPackages = preferences.safeLaunchPackages
                if (
                    wasEmpty &&
                    preferences.safeLaunchPackages.isNotEmpty() &&
                    !preferences.autoAppRulesEnabled &&
                    effectivePermission &&
                    !effectiveState.blockedByPolicy
                ) {
                    autoAppRulesEnabled = true
                    preferences.autoAppRulesEnabled = true
                }
                hasUsageAccess = foregroundDetector.hasUsageAccess()
                AppWatcherService.sync(applicationContext)
                if (autoAppRulesEnabled) {
                    maybeRequestPostNotifications(hasUsageAccess)
                }
            }

            UsbDebugSwitchTheme(themePreference = themePreference) {
                UsbDebugScreen(
                    devState = effectiveState,
                    hasWritePermission = effectivePermission,
                    detectedBrand = detectedBrand,
                    autoJumpEnabled = autoJumpEnabled,
                    autoJumpPending = autoJumpPending,
                    autoOffEnabled = autoOffEnabled,
                    autoOffMinutes = autoOffMinutes,
                    autoOffTargetAtMs = effectiveAutoOffTargetAtMs,
                    keepAwakeOnUsb = keepAwakeOnUsb,
                    themePreference = themePreference,
                    safeLaunchPackages = safeLaunchPackages,
                    autoAppRulesEnabled = autoAppRulesEnabled,
                    autoRestoreOnExit = autoRestoreOnExit,
                    hasUsageAccess = hasUsageAccess,
                    quickSurfaceState = quickSurfaceState,
                    wirelessWifiWarning = wirelessWifiWarning,
                    wirelessBlockedTick = wirelessBlockedTick,
                    homePinFallbackTick = homePinFallbackTick,
                    homePinFallbackKind = homePinFallbackKind,
                    initialTab = requestedTab ?: preview?.initialTab,
                    initialTabTick = requestedTabTick,
                    debugPreviewBanner = preview?.bannerText,
                    onRefreshState = { refreshLiveState() },
                    onRecheckPermission = {
                        refreshLiveState()
                        livePermission
                    },
                    onCancelAutoJump = {
                        autoJumpPending = false
                        autoJumpConsumedForSession = true
                    },
                    onAutoJumpChanged = { enabled ->
                        autoJumpEnabled = enabled
                        preferences.autoJumpToUsbDebugging = enabled
                        autoJumpConsumedForSession = true
                        autoJumpPending = false
                    },
                    onAutoOffEnabledChanged = { enabled ->
                        autoOffEnabled = enabled
                        preferences.autoOffEnabled = enabled
                        if (previewState == null) {
                            if (enabled) {
                                autoOffManager.scheduleIfEnabled(liveState.adbEnabled)
                            } else {
                                autoOffManager.cancel()
                            }
                            autoOffTargetAtMs = preferences.autoOffTargetAtMs
                            QuickSurfaceHelper.syncAllSurfaces(applicationContext)
                        } else {
                            autoOffTargetAtMs = if (enabled && effectiveState.adbEnabled) {
                                System.currentTimeMillis() + autoOffMinutes * 60_000L
                            } else {
                                0L
                            }
                        }
                    },
                    onAutoOffMinutesChanged = { minutes ->
                        autoOffMinutes = minutes
                        preferences.autoOffMinutes = minutes
                        if (previewState == null && autoOffEnabled && liveState.adbEnabled && livePermission) {
                            autoOffManager.start(minutes)
                            autoOffTargetAtMs = preferences.autoOffTargetAtMs
                            QuickSurfaceHelper.syncAllSurfaces(applicationContext)
                        } else if (previewState != null && autoOffEnabled && effectiveState.adbEnabled) {
                            autoOffTargetAtMs = System.currentTimeMillis() + minutes * 60_000L
                        }
                    },
                    onKeepAwakeOnUsbChanged = { enabled ->
                        keepAwakeOnUsb = enabled
                        preferences.keepAwakeOnUsb = enabled
                        val sim = previewState
                        if (sim != null) {
                            previewState = sim.copy(keepAwakeEnabled = enabled && sim.adbEnabled)
                        } else {
                            if (enabled) {
                                controller.syncKeepAwakeWithUsb(true)
                            } else {
                                controller.setKeepAwake(false)
                            }
                            refreshLiveState()
                        }
                    },
                    onThemePreferenceChanged = { pref ->
                        themePreference = pref
                        preferences.themePreference = pref
                    },
                    onAddSafeLaunchPackage = { pkg -> addProtectedPackages(listOf(pkg)) },
                    onAddSafeLaunchPackages = addProtectedPackages,
                    onRemoveSafeLaunchPackage = { pkg ->
                        preferences.removeSafeLaunchPackage(pkg)
                        safeLaunchPackages = preferences.safeLaunchPackages
                        if (preferences.safeLaunchPackages.isEmpty() && preferences.heldRulePackage.isNotEmpty()) {
                            AppRuleEngine(
                                prefs = preferences,
                                controller = controller,
                                detector = foregroundDetector,
                                autoOffManager = autoOffManager,
                                onSurfaceSync = { QuickSurfaceHelper.syncAllSurfaces(applicationContext) },
                            ).releaseHoldOnStop()
                            refreshLiveState()
                        }
                        AppWatcherService.sync(applicationContext)
                    },
                    onClearSafeLaunchPackages = {
                        preferences.clearSafeLaunchPackages()
                        safeLaunchPackages = preferences.safeLaunchPackages
                        if (preferences.heldRulePackage.isNotEmpty()) {
                            AppRuleEngine(
                                prefs = preferences,
                                controller = controller,
                                detector = foregroundDetector,
                                autoOffManager = autoOffManager,
                                onSurfaceSync = { QuickSurfaceHelper.syncAllSurfaces(applicationContext) },
                            ).releaseHoldOnStop()
                            refreshLiveState()
                        }
                        AppWatcherService.sync(applicationContext)
                    },
                    onAutoAppRulesEnabledChanged = { enabled ->
                        autoAppRulesEnabled = enabled
                        preferences.autoAppRulesEnabled = enabled
                        if (!enabled && preferences.heldRulePackage.isNotEmpty()) {
                            AppRuleEngine(
                                prefs = preferences,
                                controller = controller,
                                detector = foregroundDetector,
                                autoOffManager = autoOffManager,
                                onSurfaceSync = { QuickSurfaceHelper.syncAllSurfaces(applicationContext) },
                            ).releaseHoldOnStop()
                            refreshLiveState()
                        }
                        AppWatcherService.sync(applicationContext)
                        if (enabled) {
                            maybeRequestPostNotifications(hasUsageAccess)
                        }
                    },
                    onAutoRestoreOnExitChanged = { enabled ->
                        autoRestoreOnExit = enabled
                        preferences.autoRestoreOnExit = enabled
                        if (!enabled) {
                            preferences.clearRuleHold()
                        }
                        AppWatcherService.sync(applicationContext)
                    },
                    onOpenUsageAccessSettings = {
                        SettingsIntents.launchUsageAccessScreen(this@MainActivity)
                    },
                    onOpenBuildNumberScreen = {
                        SettingsIntents.launchBuildNumberScreen(this@MainActivity)
                    },
                    onOpenEnableAdbScreen = {
                        SettingsIntents.launchEnableAdbScreen(this@MainActivity)
                    },
                    onOpenEnableWirelessAdbScreen = {
                        SettingsIntents.launchEnableWirelessAdbScreen(this@MainActivity)
                    },
                    onOpenDeveloperOptionsTop = {
                        SettingsIntents.launchDeveloperOptionsTopScreen(this@MainActivity)
                    },
                    onAddUsbQuickTile = { onResult ->
                        QuickSurfaceHelper.requestAddUsbTile(this@MainActivity, onResult)
                    },
                    onAddWirelessQuickTile = { onResult ->
                        QuickSurfaceHelper.requestAddWirelessTile(this@MainActivity, onResult)
                    },
                    onRemoveQuickTile = { tile ->
                        val label = QuickSurfaceHelper.removeQsTile(this@MainActivity, tile)
                        refreshSurfaceState()
                        label
                    },
                    onPinHomeWidget = {
                        val outcome = QuickSurfaceHelper.requestPinUsbWidgetWithOutcome(this@MainActivity)
                        if (outcome == HomePinOutcome.REQUESTED) {
                            schedulePostPinRefresh()
                            watchSilentHomePinRejection {
                                QuickSurfaceHelper.readSurfaceState(applicationContext, preferences).isHomeWidgetAdded
                            }
                        }
                        outcome
                    },
                    onOpenHomeShortcutPermissions = {
                        SettingsIntents.launchHomeShortcutPermissionScreen(this@MainActivity)
                    },
                    onScheduleSurfaceRefresh = schedulePostPinRefresh,
                    onOpenHomeScreen = {
                        SettingsIntents.launchHomeScreen(this@MainActivity)
                    },
                    onSetUsbDebugging = { enabled ->
                        val sim = previewState
                        if (sim != null) {
                            if (!sim.blockedByPolicy) {
                                previewState = sim.copy(
                                    devOptionsEnabled = if (enabled) true else sim.devOptionsEnabled,
                                    adbEnabled = enabled,
                                )
                                autoOffTargetAtMs = if (enabled && autoOffEnabled) {
                                    System.currentTimeMillis() + autoOffMinutes * 60_000L
                                } else {
                                    0L
                                }
                            }
                        } else {
                            preferences.clearRuleHold()
                            if (controller.setUsbDebugging(enabled)) {
                                autoOffManager.scheduleIfEnabled(controller.readState().adbEnabled)
                                controller.syncKeepAwakeWithUsb(preferences.keepAwakeOnUsb)
                            }
                            refreshLiveState()
                        }
                    },
                    onSetWirelessDebugging = { enabled ->
                        val sim = previewState
                        if (sim != null) {
                            if (!sim.blockedByPolicy) {
                                previewState = sim.copy(
                                    devOptionsEnabled = if (enabled) true else sim.devOptionsEnabled,
                                    wirelessAdbEnabled = enabled,
                                )
                            }
                        } else if (!enabled) {
                            preferences.clearRuleHold()
                            pendingWirelessEnableAtMs = 0L
                            wirelessWifiWarning = false
                            controller.setWirelessDebugging(false)
                            refreshLiveState()
                        } else {
                            preferences.clearRuleHold()
                            when (controller.setWirelessDebuggingWithResult(true)) {
                                WirelessSwitchResult.WIFI_NOT_CONNECTED -> {
                                    pendingWirelessEnableAtMs = 0L
                                    wirelessWifiWarning = true
                                    wirelessBlockedTick++
                                    refreshLiveState()
                                }
                                WirelessSwitchResult.UPDATED -> {
                                    val attemptAt = SystemClock.elapsedRealtime()
                                    pendingWirelessEnableAtMs = attemptAt
                                    refreshLiveState()
                                    surfaceRefreshScope.launch {
                                        listOf(250L, 650L).forEach { delayMs ->
                                            delay(delayMs)
                                            if (pendingWirelessEnableAtMs == attemptAt) {
                                                val check = controller.readState()
                                                if (!check.wirelessAdbEnabled) {
                                                    pendingWirelessEnableAtMs = 0L
                                                    wirelessWifiWarning = true
                                                    wirelessBlockedTick++
                                                    refreshLiveState()
                                                }
                                            }
                                        }
                                        if (
                                            pendingWirelessEnableAtMs == attemptAt &&
                                            controller.readState().wirelessAdbEnabled
                                        ) {
                                            pendingWirelessEnableAtMs = 0L
                                            wirelessWifiWarning = false
                                        }
                                    }
                                }
                                WirelessSwitchResult.UNAVAILABLE -> refreshLiveState()
                            }
                        }
                    },
                    onSetDeveloperOptions = { enabled ->
                        val sim = previewState
                        if (sim != null) {
                            if (!sim.blockedByPolicy) {
                                previewState = if (enabled) {
                                    sim.copy(devOptionsEnabled = true)
                                } else {
                                    autoOffTargetAtMs = 0L
                                    sim.copy(
                                        devOptionsEnabled = false,
                                        adbEnabled = false,
                                        wirelessAdbEnabled = false,
                                    )
                                }
                            }
                        } else {
                            preferences.clearRuleHold()
                            if (controller.setDeveloperOptions(enabled) && !enabled) {
                                autoOffManager.cancel()
                            }
                            controller.syncKeepAwakeWithUsb(preferences.keepAwakeOnUsb)
                            refreshLiveState()
                        }
                    },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        isWindowFocused = hasFocus
        if (hasFocus) {
            windowFocusTick++
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentPreviewConfig = DebugPreviewConfig.fromIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_TAB)?.let { tab ->
            requestedTab = tab
            requestedTabTick++
        }
    }

    private fun maybeRequestPostNotifications(usageGranted: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!preferences.autoAppRulesEnabled || preferences.safeLaunchPackages.isEmpty() || !usageGranted || notificationPromptShown) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPromptShown = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}
