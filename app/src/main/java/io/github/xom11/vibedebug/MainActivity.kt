package io.github.xom11.vibedebug

import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.xom11.vibedebug.data.AndroidSettingsGateway
import io.github.xom11.vibedebug.data.AppPreferences
import io.github.xom11.vibedebug.data.AutoOffManager
import io.github.xom11.vibedebug.data.DevSettingsController
import io.github.xom11.vibedebug.debug.DebugPreviewConfig
import io.github.xom11.vibedebug.model.OemBrandDetector
import io.github.xom11.vibedebug.navigation.SettingsIntents
import io.github.xom11.vibedebug.shortcut.QuickSurfaceHelper
import io.github.xom11.vibedebug.shortcut.SafeLaunchHelper
import io.github.xom11.vibedebug.shortcut.SafeLaunchResult
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

    private var currentPreviewConfig by mutableStateOf<DebugPreviewConfig?>(null)
    private var windowFocusTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        gateway = AndroidSettingsGateway(applicationContext)
        controller = DevSettingsController(gateway)
        preferences = AppPreferences(applicationContext)
        autoOffManager = AutoOffManager(applicationContext, preferences)
        currentPreviewConfig = DebugPreviewConfig.fromIntent(intent)

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
            var safeLaunchPackages by remember { mutableStateOf(preferences.safeLaunchPackages) }
            var quickSurfaceState by remember {
                mutableStateOf(QuickSurfaceHelper.readSurfaceState(applicationContext, preferences))
            }
            val surfaceRefreshScope = rememberCoroutineScope()

            val refreshSurfaceState = {
                quickSurfaceState = QuickSurfaceHelper.readSurfaceState(applicationContext, preferences)
            }

            val schedulePostPinRefresh: () -> Unit = {
                surfaceRefreshScope.launch {
                    listOf(500L, 1000L, 1500L).forEach { delayMs ->
                        delay(delayMs)
                        refreshSurfaceState()
                    }
                }
            }

            val refreshLiveState = {
                val updated = controller.readState()
                liveState = updated
                livePermission = controller.hasWritePermission()
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
                        safeLaunchPackages = preferences.safeLaunchPackages
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
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
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
                    themePreference = themePreference,
                    safeLaunchPackages = safeLaunchPackages,
                    quickSurfaceState = quickSurfaceState,
                    initialTab = preview?.initialTab,
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
                    onThemePreferenceChanged = { pref ->
                        themePreference = pref
                        preferences.themePreference = pref
                    },
                    onAddSafeLaunchPackage = { pkg ->
                        preferences.addSafeLaunchPackage(pkg)
                        safeLaunchPackages = preferences.safeLaunchPackages
                    },
                    onRemoveSafeLaunchPackage = { pkg ->
                        preferences.removeSafeLaunchPackage(pkg)
                        safeLaunchPackages = preferences.safeLaunchPackages
                    },
                    onLaunchSafeApp = { pkg ->
                        val sim = previewState
                        if (sim != null) {
                            when {
                                sim.blockedByPolicy -> SafeLaunchResult.BlockedByPolicy
                                !effectivePermission -> SafeLaunchResult.NeedsPermission
                                else -> {
                                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                                    if (launchIntent == null) {
                                        SafeLaunchResult.AppNotFound
                                    } else {
                                        autoOffTargetAtMs = 0L
                                        previewState = sim.copy(
                                            devOptionsEnabled = false,
                                            adbEnabled = false,
                                            wirelessAdbEnabled = false,
                                        )
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        runCatching { startActivity(launchIntent) }
                                            .fold(
                                                onSuccess = { SafeLaunchResult.LaunchedClean },
                                                onFailure = { SafeLaunchResult.AppNotFound },
                                            )
                                    }
                                }
                            }
                        } else {
                            val result = SafeLaunchHelper.launchSafely(this@MainActivity, pkg)
                            if (result == SafeLaunchResult.LaunchedClean) {
                                refreshLiveState()
                            }
                            result
                        }
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
                    onAddDevOptionsQuickTile = { onResult ->
                        QuickSurfaceHelper.requestAddDevOptionsTile(this@MainActivity, onResult)
                    },
                    onAddKeepAwakeQuickTile = { onResult ->
                        QuickSurfaceHelper.requestAddKeepAwakeTile(this@MainActivity, onResult)
                    },
                    onRemoveQuickTile = { tile ->
                        val label = QuickSurfaceHelper.removeQsTile(this@MainActivity, tile)
                        refreshSurfaceState()
                        label
                    },
                    onPinWidget = { tile ->
                        val requested = QuickSurfaceHelper.requestPinWidget(this@MainActivity, tile)
                        if (requested) schedulePostPinRefresh()
                        requested
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
                            if (controller.setUsbDebugging(enabled)) {
                                autoOffManager.scheduleIfEnabled(controller.readState().adbEnabled)
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
                        } else {
                            controller.setWirelessDebugging(enabled)
                            refreshLiveState()
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
                            if (controller.setDeveloperOptions(enabled) && !enabled) {
                                autoOffManager.cancel()
                            }
                            refreshLiveState()
                        }
                    },
                    onSetKeepAwake = { enabled ->
                        val sim = previewState
                        if (sim != null) {
                            previewState = sim.copy(keepAwakeEnabled = enabled)
                        } else {
                            controller.setKeepAwake(enabled)
                            refreshLiveState()
                        }
                    },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            windowFocusTick++
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentPreviewConfig = DebugPreviewConfig.fromIntent(intent)
    }
}
