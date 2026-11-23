package io.github.xom11.vibedebug.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import io.github.xom11.vibedebug.data.AppRuleMode
import io.github.xom11.vibedebug.model.DevState
import io.github.xom11.vibedebug.shortcut.LaunchableApp
import io.github.xom11.vibedebug.shortcut.QuickSurfaceState
import io.github.xom11.vibedebug.shortcut.QuickTileType
import io.github.xom11.vibedebug.shortcut.SafeLaunchHelper
import io.github.xom11.vibedebug.shortcut.TileAddOutcome
import io.github.xom11.vibedebug.ui.theme.AppTheme
import io.github.xom11.vibedebug.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ShortcutsTab(
    devState: DevState,
    hasWritePermission: Boolean,
    safeLaunchPackages: List<String>,
    autoAppRulesEnabled: Boolean = false,
    autoRestoreOnExit: Boolean = true,
    hasUsageAccess: Boolean = false,
    appRuleModes: Map<String, AppRuleMode> = emptyMap(),
    quickSurfaceState: QuickSurfaceState = QuickSurfaceState(),
    onShowSnackbar: (String) -> Unit,
    onShowManualTileDialog: (String) -> Unit,
    onAddUsbTile: ((TileAddOutcome) -> Unit) -> Unit,
    onAddWirelessTile: ((TileAddOutcome) -> Unit) -> Unit,
    onRemoveTile: (QuickTileType) -> String = { "" },
    onPinHomeWidget: () -> Boolean = { false },
    onScheduleSurfaceRefresh: () -> Unit = {},
    onOpenHomeScreen: () -> Unit = {},
    onAddSafeLaunchPackage: (String) -> Unit,
    onRemoveSafeLaunchPackage: (String) -> Unit,
    onAutoAppRulesEnabledChanged: (Boolean) -> Unit = {},
    onAutoRestoreOnExitChanged: (Boolean) -> Unit = {},
    onSetAppRuleMode: (String, AppRuleMode) -> Unit = { _, _ -> },
    onOpenUsageAccessSettings: () -> Unit = {},
    onLaunchSafeApp: (packageName: String, label: String) -> Unit = { _, _ -> },
    onOpenDeveloperOptionsTop: () -> Unit,
    onRefreshState: () -> Unit,
) {
    val palette = AppTheme.palette
    val context = LocalContext.current
    val adaptive = LocalAdaptiveLayout.current
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    var showUsageDisclosure by rememberSaveable { mutableStateOf(false) }
    var removeWidgetDialogLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var removeShortcutDialogLabel by rememberSaveable { mutableStateOf<String?>(null) }

    fun handleTileOutcome(outcome: TileAddOutcome) {
        when (outcome) {
            is TileAddOutcome.Added -> {
                onShowSnackbar(context.getString(R.string.feedback_tile_added, outcome.tileLabel))
            }
            is TileAddOutcome.AlreadyAdded -> {
                onShowSnackbar(context.getString(R.string.feedback_tile_already_added, outcome.tileLabel))
            }
            TileAddOutcome.Dismissed -> Unit
            is TileAddOutcome.NeedsManualInstructions -> {
                onShowManualTileDialog(outcome.tileLabel)
            }
        }
    }

    fun removeTile(tile: QuickTileType) {
        val label = onRemoveTile(tile)
        onShowSnackbar(context.getString(R.string.feedback_tile_removed, label))
    }

    fun handlePinHomeWidget() {
        val label = context.getString(R.string.widget_hub_label)
        if (quickSurfaceState.isHomeWidgetAdded) {
            removeWidgetDialogLabel = label
        } else {
            val requested = onPinHomeWidget()
            onShowSnackbar(
                if (requested) context.getString(R.string.feedback_widget_pin_requested, label)
                else context.getString(R.string.feedback_widget_unsupported)
            )
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
                                onClick = { showAppPicker = true },
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
                    Text(
                        text = stringResource(R.string.safe_launch_list_hint),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = palette.muted,
                    )
                    safeLaunchPackages.forEach { pkg ->
                        SafeLaunchAppCard(
                            packageName = pkg,
                            ruleMode = appRuleModes[pkg] ?: AppRuleMode.DEV_OFF,
                            isShortcutPinned = pkg in quickSurfaceState.pinnedSafeLaunchPackages,
                            onRuleModeChanged = { mode -> onSetAppRuleMode(pkg, mode) },
                            onOpenNow = { label -> onLaunchSafeApp(pkg, label) },
                            onPinShortcut = { label ->
                                val ok = SafeLaunchHelper.requestPinSafeLaunchShortcut(context, pkg)
                                if (ok) onScheduleSurfaceRefresh()
                                onShowSnackbar(
                                    if (ok) context.getString(R.string.safe_launch_pin_ok, label)
                                    else context.getString(R.string.safe_launch_pin_unsupported)
                                )
                            },
                            onUnpinShortcut = { label ->
                                removeShortcutDialogLabel = label
                            },
                            onRemove = { onRemoveSafeLaunchPackage(pkg) },
                        )
                    }
                    OutlineActionButton(
                        text = stringResource(R.string.safe_launch_edit_btn),
                        onClick = { showAppPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_add_safe_launch_app"),
                        color = palette.mint,
                        border = palette.mintBorder,
                        fill = palette.mintBg,
                        height = 46.dp,
                        icon = R.drawable.ic_add,
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

    if (showAppPicker) {
        SafeLaunchAppPickerDialog(
            savedPackages = safeLaunchPackages,
            onTogglePackage = { pkg, currentlySaved ->
                if (currentlySaved) onRemoveSafeLaunchPackage(pkg) else onAddSafeLaunchPackage(pkg)
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

    removeShortcutDialogLabel?.let { label ->
        RemoveHomeSurfaceDialog(
            title = stringResource(R.string.dialog_remove_shortcut_title),
            body = stringResource(R.string.dialog_remove_shortcut_body, label),
            onGoHome = {
                removeShortcutDialogLabel = null
                onOpenHomeScreen()
            },
            onDismiss = { removeShortcutDialogLabel = null },
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
    val palette = AppTheme.palette
    val adaptive = LocalAdaptiveLayout.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = adaptive.dialogMaxWidthDp.dp)
            .fillMaxWidth(0.92f)
            .testTag("dialog_usage_access_disclosure"),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = palette.surface,
        titleContentColor = palette.text,
        textContentColor = palette.soft,
        title = {
            Text(
                text = stringResource(R.string.usage_dialog_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.usage_dialog_body),
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.mint,
                    contentColor = palette.onMint,
                ),
            ) {
                Text(
                    text = stringResource(R.string.usage_dialog_open_settings),
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
private fun SafeLaunchAppCard(
    packageName: String,
    ruleMode: AppRuleMode,
    isShortcutPinned: Boolean,
    onRuleModeChanged: (AppRuleMode) -> Unit,
    onOpenNow: (label: String) -> Unit,
    onPinShortcut: (label: String) -> Unit,
    onUnpinShortcut: (label: String) -> Unit,
    onRemove: () -> Unit,
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

    SurfaceCard(modifier = Modifier.testTag("card_safe_launch_$packageName")) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconBox(iconBitmap)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = packageName,
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconAction(
                icon = R.drawable.ic_close,
                label = stringResource(R.string.cd_remove_app, label),
                onClick = onRemove,
                tint = palette.muted,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 6.dp),
        ) {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.rule_mode_dev_off),
                    stringResource(R.string.rule_mode_usb_off),
                ),
                selectedIndex = if (ruleMode == AppRuleMode.DEV_OFF) 0 else 1,
                onSelect = { index ->
                    onRuleModeChanged(if (index == 0) AppRuleMode.DEV_OFF else AppRuleMode.USB_OFF)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("seg_rule_mode_$packageName"),
                height = 34.dp,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlineActionButton(
                text = stringResource(R.string.safe_launch_open_now),
                onClick = { onOpenNow(label) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = palette.mint,
                border = palette.mintBorder,
                fill = palette.mintBg,
                height = 42.dp,
                icon = R.drawable.ic_open_external,
            )
            OutlineActionButton(
                text = stringResource(if (isShortcutPinned) R.string.safe_launch_unpin else R.string.safe_launch_pin),
                onClick = { if (isShortcutPinned) onUnpinShortcut(label) else onPinShortcut(label) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = if (isShortcutPinned) palette.coral else palette.text,
                border = if (isShortcutPinned) palette.coralBorder else palette.line,
                fill = if (isShortcutPinned) palette.coralBg else Color.Transparent,
                height = 42.dp,
                icon = if (isShortcutPinned) R.drawable.ic_trash else R.drawable.ic_add,
            )
        }
    }
}

@Composable
private fun SafeLaunchAppPickerDialog(
    savedPackages: List<String>,
    onTogglePackage: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = AppTheme.palette
    val context = LocalContext.current
    val adaptive = LocalAdaptiveLayout.current
    var query by rememberSaveable { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            SafeLaunchHelper.queryLaunchableApps(context)
        }
    }

    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        apps.orEmpty().filter {
            q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
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
                                val selected = app.packageName in savedPackages
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
private fun PickerAppRow(
    app: LaunchableApp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = AppTheme.palette
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { loadAppIconBitmap(context, app.packageName) }
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
                if (app.isBankingSuggested) {
                    Text(
                        text = stringResource(R.string.badge_banking_suggested),
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(palette.mintDim)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.mint,
                        maxLines = 1,
                        softWrap = false,
                    )
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
