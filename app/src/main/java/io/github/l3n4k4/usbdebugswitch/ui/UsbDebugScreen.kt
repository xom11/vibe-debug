package io.github.l3n4k4.usbdebugswitch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.l3n4k4.usbdebugswitch.R
import io.github.l3n4k4.usbdebugswitch.data.ThemePreference
import io.github.l3n4k4.usbdebugswitch.model.DevState
import io.github.l3n4k4.usbdebugswitch.model.OemBrand
import io.github.l3n4k4.usbdebugswitch.shortcut.TileAddOutcome
import io.github.l3n4k4.usbdebugswitch.ui.theme.AppTheme
import io.github.l3n4k4.usbdebugswitch.ui.theme.MonoFont
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UsbDebugScreen(
    devState: DevState,
    hasWritePermission: Boolean,
    detectedBrand: OemBrand,
    autoJumpEnabled: Boolean,
    autoJumpPending: Boolean,
    autoOffEnabled: Boolean,
    autoOffMinutes: Int,
    autoOffTargetAtMs: Long,
    themePreference: ThemePreference,
    safeLaunchPackages: List<String>,
    initialTab: String? = null,
    debugPreviewBanner: String?,
    onRefreshState: () -> Unit,
    onRecheckPermission: () -> Boolean,
    onCancelAutoJump: () -> Unit,
    onAutoJumpChanged: (Boolean) -> Unit,
    onAutoOffEnabledChanged: (Boolean) -> Unit,
    onAutoOffMinutesChanged: (Int) -> Unit,
    onThemePreferenceChanged: (ThemePreference) -> Unit,
    onAddSafeLaunchPackage: (String) -> Unit,
    onRemoveSafeLaunchPackage: (String) -> Unit,
    onOpenBuildNumberScreen: () -> Unit,
    onOpenEnableAdbScreen: () -> Unit,
    onOpenEnableWirelessAdbScreen: () -> Unit,
    onOpenDeveloperOptionsTop: () -> Unit,
    onAddUsbQuickTile: ((TileAddOutcome) -> Unit) -> Unit,
    onAddWirelessQuickTile: ((TileAddOutcome) -> Unit) -> Unit,
    onAddDevOptionsQuickTile: ((TileAddOutcome) -> Unit) -> Unit,
    onAddKeepAwakeQuickTile: ((TileAddOutcome) -> Unit) -> Unit,
    onPinHomeWidget: () -> Boolean,
    onPinHomeShortcut: () -> Boolean,
    onSetUsbDebugging: (Boolean) -> Unit,
    onSetWirelessDebugging: (Boolean) -> Unit,
    onSetDeveloperOptions: (Boolean) -> Unit,
    onSetKeepAwake: (Boolean) -> Unit,
) {
    val palette = AppTheme.palette
    var currentTab by rememberSaveable(initialTab) {
        mutableStateOf(AppTab.fromRoute(initialTab))
    }
    var manualQsTileLabel by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val showSnackbar: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = currentTab != AppTab.HOME) {
        currentTab = AppTab.HOME
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
        containerColor = palette.bg,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = palette.surface2,
                    contentColor = palette.text,
                    actionColor = palette.mint,
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab,
                onSelect = { currentTab = it },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (debugPreviewBanner != null) {
                val shape = RoundedCornerShape(10.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                        .testTag("debug_preview_banner")
                        .clip(shape)
                        .background(palette.amberBg)
                        .border(1.dp, palette.amberBorder, shape)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = debugPreviewBanner,
                        fontFamily = MonoFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.amberText,
                    )
                }
            }

            if (autoJumpPending) {
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                        .testTag("auto_jump_banner")
                        .clip(shape)
                        .background(palette.blueBg)
                        .border(1.dp, palette.blueBorder, shape)
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.auto_jump_countdown_notice),
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.blueText,
                    )
                    TextButton(onClick = onCancelAutoJump) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            fontWeight = FontWeight.SemiBold,
                            color = palette.blue,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (currentTab) {
                    AppTab.HOME -> {
                        HomeTab(
                            devState = devState,
                            hasWritePermission = hasWritePermission,
                            detectedBrand = detectedBrand,
                            autoJumpEnabled = autoJumpEnabled,
                            autoOffEnabled = autoOffEnabled,
                            autoOffMinutes = autoOffMinutes,
                            autoOffTargetAtMs = autoOffTargetAtMs,
                            onRefreshState = onRefreshState,
                            onNavigateTab = { currentTab = it },
                            onAutoJumpChanged = onAutoJumpChanged,
                            onToggleAutoOff = onAutoOffEnabledChanged,
                            onOpenBuildNumberScreen = onOpenBuildNumberScreen,
                            onOpenEnableAdbScreen = onOpenEnableAdbScreen,
                            onOpenEnableWirelessAdbScreen = onOpenEnableWirelessAdbScreen,
                            onOpenDeveloperOptionsTop = onOpenDeveloperOptionsTop,
                            onSetUsbDebugging = onSetUsbDebugging,
                            onSetWirelessDebugging = onSetWirelessDebugging,
                            onSetDeveloperOptions = onSetDeveloperOptions,
                            onSetKeepAwake = onSetKeepAwake,
                        )
                    }

                    AppTab.SHORTCUTS -> {
                        ShortcutsTab(
                            devState = devState,
                            hasWritePermission = hasWritePermission,
                            safeLaunchPackages = safeLaunchPackages,
                            onShowSnackbar = showSnackbar,
                            onShowManualTileDialog = { manualQsTileLabel = it },
                            onAddUsbTile = onAddUsbQuickTile,
                            onAddWirelessTile = onAddWirelessQuickTile,
                            onAddDevOptionsTile = onAddDevOptionsQuickTile,
                            onAddKeepAwakeTile = onAddKeepAwakeQuickTile,
                            onPinHomeWidget = onPinHomeWidget,
                            onPinHomeShortcut = onPinHomeShortcut,
                            onAddSafeLaunchPackage = onAddSafeLaunchPackage,
                            onRemoveSafeLaunchPackage = onRemoveSafeLaunchPackage,
                            onOpenDeveloperOptionsTop = onOpenDeveloperOptionsTop,
                            onRefreshState = onRefreshState,
                        )
                    }

                    AppTab.SETUP -> {
                        SetupTab(
                            devState = devState,
                            hasWritePermission = hasWritePermission,
                            detectedBrand = detectedBrand,
                            autoOffEnabled = autoOffEnabled,
                            autoOffMinutes = autoOffMinutes,
                            themePreference = themePreference,
                            autoJumpEnabled = autoJumpEnabled,
                            onOpenBuildNumberScreen = onOpenBuildNumberScreen,
                            onRecheckPermission = onRecheckPermission,
                            onAutoOffEnabledChanged = onAutoOffEnabledChanged,
                            onAutoOffMinutesChanged = onAutoOffMinutesChanged,
                            onThemePreferenceChanged = onThemePreferenceChanged,
                            onAutoJumpChanged = onAutoJumpChanged,
                        )
                    }
                }
            }
        }
    }

    manualQsTileLabel?.let { tileLabel ->
        AlertDialog(
            onDismissRequest = { manualQsTileLabel = null },
            containerColor = palette.surface,
            titleContentColor = palette.text,
            textContentColor = palette.soft,
            title = {
                Text(
                    text = stringResource(R.string.dialog_manual_qs_title),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_manual_qs_body, tileLabel),
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { manualQsTileLabel = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.mint,
                        contentColor = palette.onMint,
                    ),
                ) {
                    Text(text = stringResource(R.string.action_got_it), fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}
