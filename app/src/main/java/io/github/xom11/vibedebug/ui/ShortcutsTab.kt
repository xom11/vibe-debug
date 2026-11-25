package io.github.xom11.vibedebug.ui

import android.content.Context
import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import io.github.xom11.vibedebug.R
import io.github.xom11.vibedebug.model.DevState
import io.github.xom11.vibedebug.rules.DetectionSource
import io.github.xom11.vibedebug.rules.StrictAppAnalysis
import io.github.xom11.vibedebug.rules.StrictAppCategory
import io.github.xom11.vibedebug.shortcut.HomePinFallbackKind
import io.github.xom11.vibedebug.shortcut.HomePinOutcome
import io.github.xom11.vibedebug.shortcut.LaunchableApp
import io.github.xom11.vibedebug.shortcut.QuickSurfaceState
import io.github.xom11.vibedebug.shortcut.QuickTileType
import io.github.xom11.vibedebug.shortcut.SafeLaunchHelper
import io.github.xom11.vibedebug.shortcut.TileAddOutcome
import io.github.xom11.vibedebug.ui.theme.AppTheme
import io.github.xom11.vibedebug.ui.theme.MonoFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_COLLAPSED_SAVED_APPS = 6

private enum class AppPickerFilter {
    ALL,
    DETECTED,
    SELECTED,
}

@Composable
fun ShortcutsTab(
    devState: DevState,
    hasWritePermission: Boolean,
    safeLaunchPackages: List<String>,
    autoAppRulesEnabled: Boolean = false,
    autoRestoreOnExit: Boolean = true,
    hasUsageAccess: Boolean = false,
    quickSurfaceState: QuickSurfaceState = QuickSurfaceState(),
    homePinFallbackTick: Int = 0,
    homePinFallbackKind: HomePinFallbackKind = HomePinFallbackKind.XIAOMI_PERMISSION,
    onShowSnackbar: (String) -> Unit,
    onShowActionSnackbar: (String, String, () -> Unit) -> Unit = { msg, _, _ -> onShowSnackbar(msg) },
    onShowManualTileDialog: (String) -> Unit,
    onAddUsbTile: ((TileAddOutcome) -> Unit) -> Unit,
    onAddWirelessTile: ((TileAddOutcome) -> Unit) -> Unit,
    onRemoveTile: (QuickTileType) -> String = { "" },
    onPinHomeWidget: () -> HomePinOutcome = { HomePinOutcome.UNSUPPORTED },
    onOpenHomeShortcutPermissions: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onScheduleSurfaceRefresh: () -> Unit = {},
    onOpenHomeScreen: () -> Unit = {},
    onAddSafeLaunchPackage: (String) -> Unit,
    onAddSafeLaunchPackages: (List<String>) -> Unit = { pkgs -> pkgs.forEach(onAddSafeLaunchPackage) },
    onRemoveSafeLaunchPackage: (String) -> Unit,
    onClearSafeLaunchPackages: () -> Unit = {},
    onAutoAppRulesEnabledChanged: (Boolean) -> Unit = {},
    onAutoRestoreOnExitChanged: (Boolean) -> Unit = {},
    onOpenUsageAccessSettings: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenDeveloperOptionsTop: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onRefreshState: () -> Unit,
) {
    val palette = AppTheme.palette
    val context = LocalContext.current
    val resources = LocalResources.current
    val adaptive = LocalAdaptiveLayout.current
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    var initialPickerFilter by rememberSaveable { mutableStateOf(AppPickerFilter.ALL) }
    var expandAllSavedApps by rememberSaveable { mutableStateOf(false) }
    var discoveredApps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var showUsageDisclosure by rememberSaveable { mutableStateOf(false) }
    var activePinFallbackDialog by rememberSaveable { mutableStateOf<HomePinFallbackKind?>(null) }
    var removeWidgetDialogLabel by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        discoveredApps = withContext(Dispatchers.IO) {
            SafeLaunchHelper.queryLaunchableApps(context)
        }
    }

    LaunchedEffect(safeLaunchPackages.size <= MAX_COLLAPSED_SAVED_APPS) {
        if (safeLaunchPackages.size <= MAX_COLLAPSED_SAVED_APPS) {
            expandAllSavedApps = false
        }
    }

    val discoveredByPkg = remember(discoveredApps) {
        discoveredApps.orEmpty().associateBy { it.packageName }
    }

    val unaddedDetected = remember(discoveredApps, safeLaunchPackages) {
        val savedSet = safeLaunchPackages.toHashSet()
        discoveredApps.orEmpty().filter { it.analysis.isHighConfidence && it.packageName !in savedSet }
    }

    LaunchedEffect(homePinFallbackTick) {
        if (homePinFallbackTick > 0) {
            activePinFallbackDialog = homePinFallbackKind
        }
    }

    fun handleTileOutcome(outcome: TileAddOutcome) {
        when (outcome) {
            is TileAddOutcome.Added -> {
                onShowSnackbar(resources.getString(R.string.feedback_tile_added, outcome.tileLabel))
            }
            is TileAddOutcome.AlreadyAdded -> {
                onShowSnackbar(resources.getString(R.string.feedback_tile_already_added, outcome.tileLabel))
            }
            TileAddOutcome.Dismissed -> Unit
            is TileAddOutcome.NeedsManualInstructions -> {
                onShowManualTileDialog(outcome.tileLabel)
            }
        }
    }

    fun removeTile(tile: QuickTileType) {
        val label = onRemoveTile(tile)
        onShowSnackbar(resources.getString(R.string.feedback_tile_removed, label))
    }

    fun handlePinHomeWidget() {
        val label = resources.getString(R.string.widget_hub_label)
        if (quickSurfaceState.isHomeWidgetAdded) {
            removeWidgetDialogLabel = label
        } else {
            when (onPinHomeWidget()) {
                HomePinOutcome.REQUESTED -> {
                    onShowSnackbar(resources.getString(R.string.feedback_widget_pin_requested, label))
                }
                HomePinOutcome.NEEDS_OEM_PERMISSION -> {
                    activePinFallbackDialog = HomePinFallbackKind.XIAOMI_PERMISSION
                }
                HomePinOutcome.UNSUPPORTED -> {
                    onShowSnackbar(resources.getString(R.string.feedback_widget_unsupported))
                }
            }
        }
    }

    val qsTilesAndHomeSurfacesSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.section_qs_tiles))
                QuickSettingsGridCard(
                    supportsWirelessAdb = devState.supportsWirelessAdb,
                    addedTiles = quickSurfaceState.addedTiles,
                    onToggleTile = { tile ->
                        if (tile in quickSurfaceState.addedTiles) {
                            removeTile(tile)
                        } else {
                            when (tile) {
                                QuickTileType.USB_DEBUG -> onAddUsbTile(::handleTileOutcome)
                                QuickTileType.WIRELESS_DEBUG -> onAddWirelessTile(::handleTileOutcome)
                            }
                        }
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.section_home_widgets))
                HomeWidgetsCard(
                    isWidgetAdded = quickSurfaceState.isHomeWidgetAdded,
                    onPinWidget = ::handlePinHomeWidget,
                )
            }
        }
    }

    val safeLaunchSection: @Composable () -> Unit = {
        val canUseAutoRules = hasWritePermission && !devState.blockedByPolicy
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Section 3: Turn Off Debug & Launch
            SectionLabel(stringResource(R.string.section_safe_launch))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!hasWritePermission) {
                    StatusBanner(
                        title = stringResource(R.string.strip_mode_a_active),
                        body = stringResource(R.string.safe_launch_mode_a_hint),
                        fg = palette.amberText,
                        bodyColor = palette.amberBody,
                        bg = palette.amberBg,
                        border = palette.amberBorder,
                    )
                }

                AutoAppRulesCard(
                    canUseAutoRules = canUseAutoRules,
                    hasSavedApps = safeLaunchPackages.isNotEmpty(),
                    autoAppRulesEnabled = autoAppRulesEnabled,
                    autoRestoreOnExit = autoRestoreOnExit,
                    hasUsageAccess = hasUsageAccess,
                    onToggleAutoRules = { enabled ->
                        onAutoAppRulesEnabledChanged(enabled)
                        if (enabled && !hasUsageAccess) {
                            showUsageDisclosure = true
                        }
                    },
                    onToggleAutoRestore = onAutoRestoreOnExitChanged,
                    onRequestUsageAccess = { showUsageDisclosure = true },
                )

                if (unaddedDetected.isNotEmpty()) {
                    StrictAppDiscoveryCard(
                        detectedApps = unaddedDetected,
                        onAddAll = {
                            val pkgs = unaddedDetected.map { it.packageName }
                            onAddSafeLaunchPackages(pkgs)
                            onShowSnackbar(resources.getString(R.string.auto_detect_added_feedback, pkgs.size))
                            if (canUseAutoRules && (autoAppRulesEnabled || safeLaunchPackages.isEmpty()) && !hasUsageAccess) {
                                showUsageDisclosure = true
                            }
                        },
                        onReviewAndScan = {
                            initialPickerFilter = AppPickerFilter.DETECTED
                            showAppPicker = true
                        },
                    )
                }

                if (safeLaunchPackages.isEmpty()) {
                    SurfaceCard(modifier = Modifier.testTag("card_safe_launch_empty")) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconTile(
                                icon = R.drawable.ic_lock,
                                bg = palette.mintDim,
                                fg = palette.mint,
                                size = 38.dp,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = stringResource(R.string.safe_launch_banner_title),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.text,
                                )
                                Text(
                                    text = stringResource(R.string.safe_launch_desc),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = palette.muted,
                                )
                            }
                        }
                        Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                            OutlineActionButton(
                                text = stringResource(R.string.safe_launch_add_btn),
                                onClick = {
                                    initialPickerFilter = AppPickerFilter.ALL
                                    showAppPicker = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_add_safe_launch_app"),
                                color = palette.mint,
                                border = palette.mintBorder,
                                fill = palette.mintBg,
                                height = 44.dp,
                                icon = R.drawable.ic_add,
                            )
                        }
                    }
                } else {
                    ProtectedAppsCard(
                        safeLaunchPackages = safeLaunchPackages,
                        discoveredByPkg = discoveredByPkg,
                        expanded = expandAllSavedApps,
                        onToggleExpand = { expandAllSavedApps = !expandAllSavedApps },
                        onOpenAddPicker = {
                            initialPickerFilter = AppPickerFilter.ALL
                            showAppPicker = true
                        },
                        onRemovePackage = { pkg, label ->
                            onRemoveSafeLaunchPackage(pkg)
                            onShowActionSnackbar(
                                resources.getString(R.string.feedback_app_removed, label),
                                resources.getString(R.string.action_undo),
                            ) {
                                onAddSafeLaunchPackage(pkg)
                            }
                        },
                        onClearAll = {
                            val snapshot = safeLaunchPackages.toList()
                            onClearSafeLaunchPackages()
                            onShowActionSnackbar(
                                resources.getString(R.string.feedback_apps_cleared, snapshot.size),
                                resources.getString(R.string.action_undo),
                            ) {
                                onAddSafeLaunchPackages(snapshot)
                            }
                        },
                    )
                }
            }
        }
    }

    ScreenColumn(spacing = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.shortcuts_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            Text(
                text = stringResource(R.string.shortcuts_subtitle),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = palette.muted,
            )
        }

        if (adaptive.useTwoColumnContent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(adaptive.columnSpacingDp.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    qsTilesAndHomeSurfacesSection()
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    safeLaunchSection()
                }
            }
        } else {
            qsTilesAndHomeSurfacesSection()
            safeLaunchSection()
        }
    }

    if (showUsageDisclosure) {
        UsageAccessDisclosureDialog(
            onOpenSettings = {
                showUsageDisclosure = false
                onOpenUsageAccessSettings()
            },
            onDismiss = { showUsageDisclosure = false },
        )
    }

    activePinFallbackDialog?.let { kind ->
        when (kind) {
            HomePinFallbackKind.XIAOMI_PERMISSION -> XiaomiPinPermissionDialog(
                onOpenPermissions = {
                    activePinFallbackDialog = null
                    onOpenHomeShortcutPermissions()
                },
                onDismiss = { activePinFallbackDialog = null },
            )
            HomePinFallbackKind.GENERIC_MANUAL -> GenericHomePinFallbackDialog(
                onGoHome = {
                    activePinFallbackDialog = null
                    onOpenHomeScreen()
                },
                onDismiss = { activePinFallbackDialog = null },
            )
        }
    }

    if (showAppPicker) {
        SafeLaunchAppPickerDialog(
            savedPackages = safeLaunchPackages,
            initialApps = discoveredApps,
            initialFilter = initialPickerFilter,
            onAppsUpdated = { discoveredApps = it },
            onTogglePackage = { pkg, currentlySaved ->
                if (currentlySaved) onRemoveSafeLaunchPackage(pkg) else onAddSafeLaunchPackage(pkg)
            },
            onAddAllPackages = { pkgs ->
                onAddSafeLaunchPackages(pkgs)
                if (pkgs.isNotEmpty()) {
                    onShowSnackbar(resources.getString(R.string.auto_detect_added_feedback, pkgs.size))
                }
            },
            onDismiss = {
                showAppPicker = false
                if (hasWritePermission && !devState.blockedByPolicy && autoAppRulesEnabled && safeLaunchPackages.isNotEmpty() && !hasUsageAccess) {
                    showUsageDisclosure = true
                }
            },
        )
    }

    removeWidgetDialogLabel?.let { label ->
        RemoveHomeSurfaceDialog(
            title = stringResource(R.string.dialog_remove_widget_title),
            body = stringResource(R.string.dialog_remove_widget_body, label),
            onGoHome = {
                removeWidgetDialogLabel = null
                onOpenHomeScreen()
            },
            onDismiss = { removeWidgetDialogLabel = null },
        )
    }
}

@Composable
private fun RemoveHomeSurfaceDialog(
    title: String,
    body: String,
    onGoHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AppTheme.palette
    val adaptive = LocalAdaptiveLayout.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = adaptive.dialogMaxWidthDp.dp)
            .fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = palette.surface,
        titleContentColor = palette.text,
        textContentColor = palette.soft,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = body,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onGoHome,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.mint,
                    contentColor = palette.onMint,
                ),
            ) {
                Text(text = stringResource(R.string.action_go_home), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_close),
                    fontWeight = FontWeight.SemiBold,
                    color = palette.muted,
                )
            }
        },
    )
}

private data class SurfaceControlSpec(
    val tile: QuickTileType,
    @StringRes val shortLabelRes: Int,
)

private fun buildSurfaceSpecs(supportsWirelessAdb: Boolean): List<SurfaceControlSpec> = buildList {
    add(SurfaceControlSpec(QuickTileType.USB_DEBUG, R.string.widget_short_usb))
    if (supportsWirelessAdb) {
        add(SurfaceControlSpec(QuickTileType.WIRELESS_DEBUG, R.string.widget_short_wireless))
    }
}

@Composable
private fun QuickSettingsGridCard(
    supportsWirelessAdb: Boolean,
    addedTiles: Set<QuickTileType>,
    onToggleTile: (QuickTileType) -> Unit,
) {
    val rows = remember(supportsWirelessAdb) { buildSurfaceSpecs(supportsWirelessAdb).chunked(2) }
    SurfaceCard(modifier = Modifier.testTag("card_qs_tiles")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { spec ->
                        QsSurfaceGridTile(
                            tile = spec.tile,
                            shortLabel = stringResource(spec.shortLabelRes),
                            isAdded = spec.tile in addedTiles,
                            onClick = { onToggleTile(spec.tile) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QsSurfaceGridTile(
    tile: QuickTileType,
    shortLabel: String,
    isAdded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    val shape = RoundedCornerShape(12.dp)
    val fullTitle = stringResource(tile.labelRes)
    val contentDesc = stringResource(R.string.cd_toggle_qs_tile, fullTitle)
    val fill = if (isAdded) palette.mintBg else palette.surface2
    val border = if (isAdded) palette.mintBorder else palette.line
    val labelColor = if (isAdded) palette.mint else palette.text
    val iconColor = if (isAdded) palette.mint else palette.soft

    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .testTag("btn_qs_${tile.prefKey}")
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .clickable(onClickLabel = contentDesc, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDesc }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(tile.iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = iconColor,
        )
        Text(
            text = shortLabel,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(if (isAdded) R.drawable.ic_check else R.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isAdded) palette.mint else palette.muted,
        )
    }
}

@Composable
private fun HomeWidgetsCard(
    isWidgetAdded: Boolean,
    onPinWidget: () -> Unit,
) {
    val palette = AppTheme.palette

    SurfaceCard(modifier = Modifier.testTag("card_widget_hub")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onPinWidget)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(
                icon = R.drawable.ic_surface_widget,
                bg = if (isWidgetAdded) palette.mintDim else palette.surface2,
                fg = if (isWidgetAdded) palette.mint else palette.soft,
                size = 38.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.widget_hub_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isWidgetAdded) {
                    Text(
                        text = stringResource(R.string.widget_hub_compact_sub),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = palette.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HubPinPillButton(
                isAdded = isWidgetAdded,
                onClick = onPinWidget,
                modifier = Modifier.testTag("btn_pin_hub_widget"),
            )
        }
    }
}

@Composable
private fun HubPinPillButton(
    isAdded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    val shape = RoundedCornerShape(10.dp)
    val label = stringResource(if (isAdded) R.string.action_remove_tile else R.string.action_add_tile)
    val fill = if (isAdded) palette.surface2 else palette.mintBg
    val border = if (isAdded) palette.line else palette.mintBorder
    val contentColor = if (isAdded) palette.soft else palette.mint
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        if (!isAdded) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor,
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun AutoAppRulesCard(
    canUseAutoRules: Boolean,
    hasSavedApps: Boolean = false,
    autoAppRulesEnabled: Boolean,
    autoRestoreOnExit: Boolean,
    hasUsageAccess: Boolean,
    onToggleAutoRules: (Boolean) -> Unit,
    onToggleAutoRestore: (Boolean) -> Unit,
    onRequestUsageAccess: () -> Unit,
) {
    val palette = AppTheme.palette
    val subText = when {
        !canUseAutoRules -> stringResource(R.string.auto_off_requires_one_tap)
        !autoAppRulesEnabled -> stringResource(R.string.auto_rules_sub_off)
        !hasSavedApps -> stringResource(R.string.auto_rules_sub_empty)
        !hasUsageAccess -> stringResource(R.string.auto_rules_sub_need_usage)
        else -> stringResource(R.string.auto_rules_sub_active)
    }
    SurfaceCard(modifier = Modifier.testTag("card_auto_app_rules")) {
        SwitchRow(
            title = stringResource(R.string.auto_rules_title),
            checked = autoAppRulesEnabled && canUseAutoRules,
            onChange = onToggleAutoRules,
            modifier = Modifier
                .alpha(if (canUseAutoRules) 1f else 0.65f)
                .testTag("row_auto_app_rules_switch"),
            sub = subText,
            enabled = canUseAutoRules,
        )
        AnimatedVisibility(visible = autoAppRulesEnabled && canUseAutoRules) {
            Column(Modifier.fillMaxWidth()) {
                if (hasSavedApps && !hasUsageAccess) {
                    RowDivider()
                    ValueRow(
                        title = stringResource(R.string.usage_access_title),
                        value = stringResource(R.string.usage_access_grant),
                        modifier = Modifier.testTag("row_grant_usage_access"),
                        onClick = onRequestUsageAccess,
                        valueColor = palette.amber,
                    )
                }
                RowDivider()
                SwitchRow(
                    title = stringResource(R.string.auto_restore_title),
                    checked = autoRestoreOnExit,
                    onChange = onToggleAutoRestore,
                    modifier = Modifier.testTag("row_auto_restore_switch"),
                    sub = stringResource(R.string.auto_restore_subtitle),
                )
            }
        }
    }
}

@Composable
private fun UsageAccessDisclosureDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    PermissionDisclosureDialog(
        dialogTag = "dialog_usage_access_disclosure",
        confirmButtonTag = null,
        title = stringResource(R.string.usage_dialog_title),
        body = stringResource(R.string.usage_dialog_body),
        confirmText = stringResource(R.string.usage_dialog_open_settings),
        onConfirm = onOpenSettings,
        onDismiss = onDismiss,
    )
}

@Composable
private fun XiaomiPinPermissionDialog(
    onOpenPermissions: () -> Unit,
    onDismiss: () -> Unit,
) {
    PermissionDisclosureDialog(
        dialogTag = "dialog_xiaomi_pin_permission",
        confirmButtonTag = "btn_open_xiaomi_pin_permissions",
        title = stringResource(R.string.dialog_xiaomi_pin_title),
        body = stringResource(R.string.dialog_xiaomi_pin_body),
        confirmText = stringResource(R.string.action_open_app_permissions),
        onConfirm = onOpenPermissions,
        onDismiss = onDismiss,
    )
}

@Composable
private fun GenericHomePinFallbackDialog(
    onGoHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    PermissionDisclosureDialog(
        dialogTag = "dialog_home_pin_fallback",
        confirmButtonTag = "btn_go_home_from_pin_fallback",
        title = stringResource(R.string.dialog_generic_pin_title),
        body = stringResource(R.string.dialog_generic_pin_body),
        confirmText = stringResource(R.string.action_go_home),
        onConfirm = onGoHome,
        onDismiss = onDismiss,
    )
}

@Composable
private fun PermissionDisclosureDialog(
    dialogTag: String,
    confirmButtonTag: String?,
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AppTheme.palette
    val adaptive = LocalAdaptiveLayout.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = adaptive.dialogMaxWidthDp.dp)
            .fillMaxWidth(0.92f)
            .testTag(dialogTag),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = palette.surface,
        titleContentColor = palette.text,
        textContentColor = palette.soft,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = body,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = if (confirmButtonTag != null) Modifier.testTag(confirmButtonTag) else Modifier,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.mint,
                    contentColor = palette.onMint,
                ),
            ) {
                Text(
                    text = confirmText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_close),
                    fontWeight = FontWeight.SemiBold,
                    color = palette.muted,
                )
            }
        },
    )
}

@Composable
private fun ProtectedAppsCard(
    safeLaunchPackages: List<String>,
    discoveredByPkg: Map<String, LaunchableApp>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenAddPicker: () -> Unit,
    onRemovePackage: (packageName: String, label: String) -> Unit,
    onClearAll: () -> Unit,
) {
    val palette = AppTheme.palette
    val hasMore = safeLaunchPackages.size > MAX_COLLAPSED_SAVED_APPS
    val visiblePackages = if (expanded || !hasMore) {
        safeLaunchPackages
    } else {
        safeLaunchPackages.take(MAX_COLLAPSED_SAVED_APPS)
    }

    SurfaceCard(modifier = Modifier.testTag("card_protected_apps")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.protected_apps_count_title, safeLaunchPackages.size),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                )
                Text(
                    text = stringResource(R.string.safe_launch_list_hint),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = palette.muted,
                )
            }
            CompactPillButton(
                label = stringResource(R.string.action_add_tile),
                fg = palette.mint,
                bg = palette.mintBg,
                border = palette.mintBorder,
                icon = R.drawable.ic_add,
                modifier = Modifier.testTag("btn_add_safe_launch_app"),
                onClick = onOpenAddPicker,
            )
        }

        RowDivider()

        visiblePackages.forEachIndexed { index, pkg ->
            if (index > 0) {
                RowDivider(Modifier.padding(start = 56.dp))
            }
            ProtectedAppRow(
                packageName = pkg,
                detectedSdk = discoveredByPkg[pkg]?.analysis?.detectedSdks?.firstOrNull(),
                onRemove = { label -> onRemovePackage(pkg, label) },
            )
        }

        if (hasMore || safeLaunchPackages.size > 1) {
            RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasMore) {
                    TextButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.testTag("btn_toggle_expand_apps"),
                    ) {
                        Text(
                            text = if (expanded) {
                                stringResource(R.string.protected_apps_collapse)
                            } else {
                                stringResource(
                                    R.string.protected_apps_show_more,
                                    safeLaunchPackages.size - MAX_COLLAPSED_SAVED_APPS,
                                )
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.mint,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (safeLaunchPackages.size > 1) {
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.testTag("btn_clear_all_apps"),
                    ) {
                        Text(
                            text = stringResource(R.string.action_clear_all),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.muted,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectedAppRow(
    packageName: String,
    detectedSdk: String?,
    onRemove: (label: String) -> Unit,
) {
    val palette = AppTheme.palette
    val context = LocalContext.current
    val appMeta by produceState<Pair<String, ImageBitmap?>>(packageName to null, packageName) {
        value = withContext(Dispatchers.IO) {
            val label = SafeLaunchHelper.getAppLabel(context, packageName)
            val icon = loadAppIconBitmap(context, packageName)
            label to icon
        }
    }
    val label = appMeta.first
    val iconBitmap = appMeta.second
    val metaLine = if (detectedSdk != null) "$packageName · $detectedSdk" else packageName

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_safe_launch_$packageName")
            .heightIn(min = 52.dp)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppIconBox(iconBitmap, size = 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metaLine,
                fontFamily = MonoFont,
                fontSize = 10.sp,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconAction(
            icon = R.drawable.ic_close,
            label = stringResource(R.string.cd_remove_app, label),
            onClick = { onRemove(label) },
            modifier = Modifier.testTag("btn_remove_app_$packageName"),
            tint = palette.muted,
        )
    }
}

@Composable
private fun StrictAppDiscoveryCard(
    detectedApps: List<LaunchableApp>,
    onAddAll: () -> Unit,
    onReviewAndScan: () -> Unit,
) {
    val palette = AppTheme.palette
    val previewNames = detectedApps.take(4).joinToString(", ") { it.label }
    val previewLine = if (detectedApps.size > 4) {
        stringResource(R.string.auto_detect_preview_more, previewNames, detectedApps.size - 4)
    } else {
        previewNames
    }
    val detectedSdks = remember(detectedApps) {
        detectedApps.flatMap { it.analysis.detectedSdks }.distinct().take(3)
    }
    val subText = if (detectedSdks.isNotEmpty()) {
        "$previewLine · RASP: ${detectedSdks.joinToString(", ")}"
    } else {
        previewLine
    }

    SurfaceCard(modifier = Modifier.testTag("card_auto_detect_strict")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconTile(
                icon = R.drawable.ic_shortcut_shield,
                bg = palette.mintDim,
                fg = palette.mint,
                size = 36.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.auto_detect_found_title, detectedApps.size),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                )
                Text(
                    text = subText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = palette.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val stackButtons = LocalDensity.current.fontScale >= 1.25f
        if (stackButtons) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlineActionButton(
                    text = stringResource(R.string.auto_detect_add_all, detectedApps.size),
                    onClick = onAddAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_add_all_detected"),
                    color = palette.mint,
                    border = palette.mintBorder,
                    fill = palette.mintBg,
                    height = 40.dp,
                    icon = R.drawable.ic_add,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlineActionButton(
                    text = stringResource(R.string.auto_detect_review_scan),
                    onClick = onReviewAndScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_review_detected"),
                    color = palette.text,
                    border = palette.line,
                    fill = Color.Transparent,
                    height = 40.dp,
                    icon = R.drawable.ic_search,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlineActionButton(
                    text = stringResource(R.string.auto_detect_add_all, detectedApps.size),
                    onClick = onAddAll,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_add_all_detected"),
                    color = palette.mint,
                    border = palette.mintBorder,
                    fill = palette.mintBg,
                    height = 38.dp,
                    icon = R.drawable.ic_add,
                    fontSize = 12.sp,
                    horizontalPadding = 8.dp,
                    iconSize = 14.dp,
                    iconSpacing = 5.dp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlineActionButton(
                    text = stringResource(R.string.auto_detect_review_scan),
                    onClick = onReviewAndScan,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_review_detected"),
                    color = palette.text,
                    border = palette.line,
                    fill = Color.Transparent,
                    height = 38.dp,
                    icon = R.drawable.ic_search,
                    fontSize = 12.sp,
                    horizontalPadding = 8.dp,
                    iconSize = 14.dp,
                    iconSpacing = 5.dp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SafeLaunchAppPickerDialog(
    savedPackages: List<String>,
    initialApps: List<LaunchableApp>?,
    initialFilter: AppPickerFilter,
    onAppsUpdated: (List<LaunchableApp>) -> Unit,
    onTogglePackage: (String, Boolean) -> Unit,
    onAddAllPackages: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AppTheme.palette
    val context = LocalContext.current
    val adaptive = LocalAdaptiveLayout.current
    var query by rememberSaveable { mutableStateOf("") }
    var activeFilter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }
    var apps by remember { mutableStateOf(initialApps) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var scanDone by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableIntStateOf(0) }
    var scanTotal by remember { mutableIntStateOf(0) }
    var scanCurrentLabel by remember { mutableStateOf("") }
    val scanScope = rememberCoroutineScope()

    LaunchedEffect(initialApps) {
        if (apps == null && initialApps != null) {
            apps = initialApps
        }
    }

    LaunchedEffect(Unit) {
        if (apps == null) {
            val loaded = withContext(Dispatchers.IO) {
                SafeLaunchHelper.queryLaunchableApps(context)
            }
            apps = loaded
            onAppsUpdated(loaded)
        }
    }

    DisposableEffect(Unit) {
        onDispose { scanJob?.cancel() }
    }

    val allApps = apps.orEmpty()
    val savedSet = remember(savedPackages) { savedPackages.toHashSet() }
    val detectedCount = remember(allApps) { allApps.count { it.analysis.isSuggested } }
    val selectedCount = remember(allApps, savedSet) { allApps.count { it.packageName in savedSet } }
    val canDeepScan = remember(allApps) {
        allApps.any {
            !it.isSystemOnly &&
                !it.analysis.isHighConfidence &&
                !it.analysis.deepScanned &&
                it.apkPaths.isNotEmpty()
        }
    }
    val unselectedHighDetected = remember(allApps, savedSet) {
        allApps.filter { it.analysis.isHighConfidence && it.packageName !in savedSet }
    }

    val filtered = remember(apps, query, activeFilter, savedSet) {
        val q = query.trim().lowercase()
        apps.orEmpty().filter { app ->
            val matchesFilter = when (activeFilter) {
                AppPickerFilter.ALL -> true
                AppPickerFilter.DETECTED -> app.analysis.isSuggested
                AppPickerFilter.SELECTED -> app.packageName in savedSet
            }
            matchesFilter && (q.isEmpty() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q))
        }
    }

    fun startDeepScan() {
        if (isScanning || apps == null) return
        scanJob = scanScope.launch {
            isScanning = true
            scanDone = false
            scanProgress = 0
            scanTotal = 0
            scanCurrentLabel = ""
            try {
                val updated = SafeLaunchHelper.runDeepStrictScan(
                    context = context,
                    apps = apps.orEmpty(),
                ) { scanned, total, label ->
                    scanProgress = scanned
                    scanTotal = total
                    scanCurrentLabel = label
                }
                apps = updated
                onAppsUpdated(updated)
                scanDone = true
            } catch (_: CancellationException) {
                val reloaded = withContext(Dispatchers.IO) {
                    SafeLaunchHelper.queryLaunchableApps(context)
                }
                apps = reloaded
                onAppsUpdated(reloaded)
            } finally {
                isScanning = false
                scanJob = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = adaptive.dialogMaxWidthDp.dp)
            .fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = palette.surface,
        titleContentColor = palette.text,
        textContentColor = palette.soft,
        title = {
            Text(
                text = stringResource(R.string.dialog_pick_app_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_apps"),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.hint_search_apps), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = palette.muted,
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { query = "" },
                                tint = palette.muted,
                            )
                        }
                    } else null,
                    textStyle = TextStyle(fontSize = 14.sp, color = palette.text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.mint,
                        unfocusedBorderColor = palette.line,
                        cursorColor = palette.mint,
                        focusedTextColor = palette.text,
                        unfocusedTextColor = palette.text,
                        focusedPlaceholderColor = palette.muted,
                        unfocusedPlaceholderColor = palette.muted,
                    ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PickerFilterChip(
                        label = stringResource(R.string.picker_filter_all),
                        selected = activeFilter == AppPickerFilter.ALL,
                        tag = "filter_apps_all",
                        onClick = { activeFilter = AppPickerFilter.ALL },
                    )
                    PickerFilterChip(
                        label = stringResource(R.string.picker_filter_detected, detectedCount),
                        selected = activeFilter == AppPickerFilter.DETECTED,
                        tag = "filter_apps_detected",
                        onClick = { activeFilter = AppPickerFilter.DETECTED },
                    )
                    PickerFilterChip(
                        label = stringResource(R.string.picker_filter_selected, selectedCount),
                        selected = activeFilter == AppPickerFilter.SELECTED,
                        tag = "filter_apps_selected",
                        onClick = { activeFilter = AppPickerFilter.SELECTED },
                    )
                }

                if (apps != null && (isScanning || canDeepScan || scanDone)) {
                    DeepScanBar(
                        isScanning = isScanning,
                        scanDone = scanDone,
                        canDeepScan = canDeepScan,
                        scanProgress = scanProgress,
                        scanTotal = scanTotal,
                        scanCurrentLabel = scanCurrentLabel,
                        detectedCount = detectedCount,
                        unselectedHighCount = unselectedHighDetected.size,
                        onStartScan = ::startDeepScan,
                        onCancelScan = { scanJob?.cancel() },
                        onAddAllDetected = {
                            onAddAllPackages(unselectedHighDetected.map { it.packageName })
                        },
                    )
                }

                when {
                    apps == null -> {
                        Text(
                            text = stringResource(R.string.loading_apps),
                            modifier = Modifier.padding(vertical = 12.dp),
                            fontSize = 13.sp,
                            color = palette.muted,
                        )
                    }
                    filtered.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.no_matching_apps),
                            modifier = Modifier.padding(vertical = 12.dp),
                            fontSize = 13.sp,
                            color = palette.muted,
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = adaptive.dialogListMaxHeightDp.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(filtered, key = { it.packageName }) { app ->
                                val selected = app.packageName in savedSet
                                PickerAppRow(
                                    app = app,
                                    selected = selected,
                                    onClick = { onTogglePackage(app.packageName, selected) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_close),
                    fontWeight = FontWeight.SemiBold,
                    color = palette.mint,
                )
            }
        },
    )
}

@Composable
private fun PickerFilterChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val palette = AppTheme.palette
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .testTag(tag)
            .clip(shape)
            .background(if (selected) palette.mintBg else palette.surface2)
            .border(1.dp, if (selected) palette.mintBorder else palette.line, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) palette.mint else palette.soft,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun DeepScanBar(
    isScanning: Boolean,
    scanDone: Boolean,
    canDeepScan: Boolean,
    scanProgress: Int,
    scanTotal: Int,
    scanCurrentLabel: String,
    detectedCount: Int,
    unselectedHighCount: Int,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onAddAllDetected: () -> Unit,
) {
    val palette = AppTheme.palette
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_deep_scan_bar")
            .clip(shape)
            .background(palette.surface2)
            .border(1.dp, palette.line, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            isScanning -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.deep_scan_progress,
                            scanProgress,
                            scanTotal.coerceAtLeast(1),
                            scanCurrentLabel,
                        ),
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                        color = palette.soft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CompactPillButton(
                        label = stringResource(R.string.action_cancel),
                        fg = palette.muted,
                        bg = palette.surface,
                        border = palette.line,
                        onClick = onCancelScan,
                    )
                }
                LinearProgressIndicator(
                    progress = {
                        if (scanTotal > 0) (scanProgress.toFloat() / scanTotal.toFloat()).coerceIn(0f, 1f) else 0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.mint,
                    trackColor = palette.line,
                )
            }
            scanDone || !canDeepScan -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.deep_scan_done, detectedCount),
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                        color = palette.soft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (unselectedHighCount > 0) {
                        CompactPillButton(
                            label = stringResource(R.string.auto_detect_add_all, unselectedHighCount),
                            fg = palette.mint,
                            bg = palette.mintBg,
                            border = palette.mintBorder,
                            onClick = onAddAllDetected,
                        )
                    } else if (canDeepScan) {
                        CompactPillButton(
                            label = stringResource(R.string.deep_scan_rescan),
                            fg = palette.mint,
                            bg = palette.mintBg,
                            border = palette.mintBorder,
                            modifier = Modifier.testTag("btn_deep_scan_apks"),
                            onClick = onStartScan,
                        )
                    }
                }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.deep_scan_prompt),
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = palette.muted,
                    )
                    CompactPillButton(
                        label = stringResource(R.string.deep_scan_action),
                        fg = palette.mint,
                        bg = palette.mintBg,
                        border = palette.mintBorder,
                        modifier = Modifier.testTag("btn_deep_scan_apks"),
                        onClick = onStartScan,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPillButton(
    label: String,
    fg: Color,
    bg: Color,
    border: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .heightIn(min = 30.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = fg,
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun DetectionBadgePill(
    text: String,
    fg: Color,
    bg: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun PickerAppRow(
    app: LaunchableApp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = AppTheme.palette
    val context = LocalContext.current
    val resources = LocalResources.current
    val iconBitmap by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { loadAppIconBitmap(context, app.packageName) }
    }
    val reasonLine = remember(app.analysis, resources) {
        if (app.analysis.isSuggested) formatDetectionReason(resources, app.analysis) else ""
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) palette.mintBg else palette.bg)
            .border(1.dp, if (selected) palette.mintBorder else palette.line, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppIconBox(iconBitmap, size = 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = app.label,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    app.analysis.detectedSdks.isNotEmpty() -> {
                        DetectionBadgePill(
                            text = stringResource(R.string.badge_rasp_named, app.analysis.detectedSdks.first()),
                            fg = palette.mint,
                            bg = palette.mintDim,
                        )
                    }
                    DetectionSource.CATALOG in app.analysis.sources -> {
                        DetectionBadgePill(
                            text = stringResource(R.string.badge_banking_suggested),
                            fg = palette.mint,
                            bg = palette.mintDim,
                        )
                    }
                    app.analysis.isSuggested -> {
                        DetectionBadgePill(
                            text = stringResource(R.string.badge_checks_debug),
                            fg = palette.amberText,
                            bg = palette.amberBg,
                        )
                    }
                }
            }
            Text(
                text = app.packageName,
                fontFamily = MonoFont,
                fontSize = 10.sp,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (reasonLine.isNotEmpty()) {
                Text(
                    text = reasonLine,
                    fontSize = 10.sp,
                    color = palette.soft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) palette.mint else Color.Transparent)
                .border(2.dp, if (selected) palette.mint else palette.offBorder, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = palette.onMint,
                )
            }
        }
    }
}

private fun formatDetectionReason(resources: Resources, analysis: StrictAppAnalysis): String {
    val parts = ArrayList<String>(3)
    when (analysis.category) {
        StrictAppCategory.GOV_ID -> parts += resources.getString(R.string.detect_reason_gov_id)
        StrictAppCategory.BANKING -> parts += resources.getString(R.string.detect_reason_banking)
        StrictAppCategory.EWALLET -> parts += resources.getString(R.string.detect_reason_ewallet)
        StrictAppCategory.SECURITIES_FINTECH -> parts += resources.getString(R.string.detect_reason_fintech)
        StrictAppCategory.ENTERPRISE_AUTH -> parts += resources.getString(R.string.detect_reason_enterprise)
        null -> Unit
    }
    val extraSdks = if (analysis.detectedSdks.size > 1) analysis.detectedSdks.drop(1) else emptyList()
    if (extraSdks.isNotEmpty()) {
        parts += extraSdks.joinToString(", ")
    }
    if (DetectionSource.DEX_ANTI_DEBUG in analysis.sources) {
        parts += resources.getString(R.string.detect_reason_dex_checks)
    } else if (DetectionSource.DOMAIN_HEURISTIC in analysis.sources && analysis.category == null) {
        parts += resources.getString(R.string.detect_reason_domain)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun AppIconBox(icon: ImageBitmap?, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val palette = AppTheme.palette
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surface2),
        )
    }
}

private fun loadAppIconBitmap(context: Context, packageName: String): ImageBitmap? = runCatching {
    context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
}.getOrNull()
