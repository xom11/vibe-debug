package io.github.l3n4k4.usbdebugswitch

import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.l3n4k4.usbdebugswitch.data.AndroidSettingsGateway
import io.github.l3n4k4.usbdebugswitch.data.AppPreferences
import io.github.l3n4k4.usbdebugswitch.data.DevSettingsController
import io.github.l3n4k4.usbdebugswitch.debug.DebugPreviewConfig
import io.github.l3n4k4.usbdebugswitch.model.OemBrandDetector
import io.github.l3n4k4.usbdebugswitch.navigation.SettingsIntents
import io.github.l3n4k4.usbdebugswitch.tile.UsbDebugTileService
import io.github.l3n4k4.usbdebugswitch.ui.UsbDebugScreen
import io.github.l3n4k4.usbdebugswitch.ui.theme.UsbDebugSwitchTheme
import io.github.l3n4k4.usbdebugswitch.widget.UsbDebugWidgetProvider
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var gateway: AndroidSettingsGateway
    private lateinit var controller: DevSettingsController
    private lateinit var preferences: AppPreferences

    private var currentPreviewConfig by mutableStateOf<DebugPreviewConfig?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        gateway = AndroidSettingsGateway(applicationContext)
        controller = DevSettingsController(gateway)
        preferences = AppPreferences(applicationContext)
        currentPreviewConfig = DebugPreviewConfig.fromIntent(intent)

        setContent {
            val preview = currentPreviewConfig
            var liveState by remember { mutableStateOf(controller.readState()) }
            var livePermission by remember { mutableStateOf(controller.hasWritePermission()) }
            var previewState by remember(preview) { mutableStateOf(preview?.simulatedState) }
            var autoJumpEnabled by remember { mutableStateOf(preferences.autoJumpToUsbDebugging) }
            var autoJumpConsumedForSession by remember { mutableStateOf(savedInstanceState != null) }
            var autoJumpPending by remember { mutableStateOf(false) }

            val refreshLiveState = {
                liveState = controller.readState()
                livePermission = controller.hasWritePermission()
                syncExternalSurfaces()
            }

            DisposableEffect(Unit) {
                val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        refreshLiveState()
                    }
                }
                gateway.registerObserver(observer)
                onDispose {
                    gateway.unregisterObserver(observer)
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val lifecycleObserver = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        refreshLiveState()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                }
            }

            val effectiveState = previewState ?: liveState
            val effectivePermission = preview?.hasWritePermission ?: livePermission
            val detectedBrand = remember(preview) {
                preview?.brandOverride ?: OemBrandDetector.detect(Build.MANUFACTURER, Build.BRAND)
            }

            LaunchedEffect(preview, autoJumpEnabled, effectivePermission, effectiveState.devOptionsEnabled) {
                if (
                    preview == null &&
                    !autoJumpConsumedForSession &&
                    autoJumpEnabled &&
                    !effectivePermission &&
                    effectiveState.devOptionsEnabled
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

            UsbDebugSwitchTheme {
                UsbDebugScreen(
                    devState = effectiveState,
                    hasWritePermission = effectivePermission,
                    detectedBrand = detectedBrand,
                    autoJumpEnabled = autoJumpEnabled,
                    autoJumpPending = autoJumpPending,
                    debugPreviewBanner = preview?.bannerText,
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
                    onOpenBuildNumberScreen = {
                        SettingsIntents.launchBuildNumberScreen(this@MainActivity)
                    },
                    onOpenEnableAdbScreen = {
                        SettingsIntents.launchEnableAdbScreen(this@MainActivity)
                    },
                    onOpenDeveloperOptionsTop = {
                        SettingsIntents.launchDeveloperOptionsTopScreen(this@MainActivity)
                    },
                    onSetUsbDebugging = { enabled ->
                        val sim = previewState
                        if (sim != null) {
                            previewState = sim.copy(
                                devOptionsEnabled = if (enabled) true else sim.devOptionsEnabled,
                                adbEnabled = enabled
                            )
                        } else {
                            controller.setUsbDebugging(enabled)
                            refreshLiveState()
                        }
                    },
                    onSetWirelessDebugging = { enabled ->
                        val sim = previewState
                        if (sim != null) {
                            previewState = sim.copy(
                                devOptionsEnabled = if (enabled) true else sim.devOptionsEnabled,
                                wirelessAdbEnabled = enabled
                            )
                        } else {
                            controller.setWirelessDebugging(enabled)
                            refreshLiveState()
                        }
                    },
                    onSetDeveloperOptions = { enabled ->
                        val sim = previewState
                        if (sim != null) {
                            previewState = if (enabled) {
                                sim.copy(devOptionsEnabled = true)
                            } else {
                                sim.copy(
                                    devOptionsEnabled = false,
                                    adbEnabled = false,
                                    wirelessAdbEnabled = false
                                )
                            }
                        } else {
                            controller.setDeveloperOptions(enabled)
                            refreshLiveState()
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentPreviewConfig = DebugPreviewConfig.fromIntent(intent)
    }

    private fun syncExternalSurfaces() {
        UsbDebugWidgetProvider.updateAllWidgets(applicationContext)
        runCatching {
            TileService.requestListeningState(
                applicationContext,
                ComponentName(applicationContext, UsbDebugTileService::class.java)
            )
        }
    }
}
