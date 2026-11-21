package io.github.l3n4k4.usbdebugswitch.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.l3n4k4.usbdebugswitch.R
import io.github.l3n4k4.usbdebugswitch.model.DevState
import io.github.l3n4k4.usbdebugswitch.model.OemBrand
import io.github.l3n4k4.usbdebugswitch.navigation.SettingsIntents
import io.github.l3n4k4.usbdebugswitch.shortcut.TileAddOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PendingTurnOffConfirm {
    USB_DEBUGGING,
    WIRELESS_DEBUGGING,
    DEVELOPER_OPTIONS
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun UsbDebugScreen(
    devState: DevState,
    hasWritePermission: Boolean,
    detectedBrand: OemBrand,
    autoJumpEnabled: Boolean,
    autoJumpPending: Boolean,
    debugPreviewBanner: String?,
    onCancelAutoJump: () -> Unit,
    onAutoJumpChanged: (Boolean) -> Unit,
    onOpenBuildNumberScreen: () -> Unit,
    onOpenEnableAdbScreen: () -> Unit,
    onOpenDeveloperOptionsTop: () -> Unit,
    onAddUsbQuickTile: ((TileAddOutcome) -> Unit) -> Unit,
    onAddWirelessQuickTile: ((TileAddOutcome) -> Unit) -> Unit,
    onPinHomeWidget: () -> Boolean,
    onPinHomeShortcut: () -> Boolean,
    onSetUsbDebugging: (Boolean) -> Unit,
    onSetWirelessDebugging: (Boolean) -> Unit,
    onSetDeveloperOptions: (Boolean) -> Unit
) {
    var pendingConfirm by remember { mutableStateOf<PendingTurnOffConfirm?>(null) }
    var selectedBrand by remember(detectedBrand) { mutableStateOf(detectedBrand) }
    var showBrandPicker by rememberSaveable { mutableStateOf(false) }
    var techExpanded by rememberSaveable { mutableStateOf(false) }
    var manualQsTileLabel by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun handleTileOutcome(outcome: TileAddOutcome) {
        when (outcome) {
            is TileAddOutcome.Added -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.feedback_tile_added, outcome.tileLabel)
                    )
                }
            }

            is TileAddOutcome.AlreadyAdded -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.feedback_tile_already_added, outcome.tileLabel)
                    )
                }
            }

            TileAddOutcome.Dismissed -> Unit

            is TileAddOutcome.NeedsManualInstructions -> {
                manualQsTileLabel = outcome.tileLabel
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_qs_usb_debug),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (debugPreviewBanner != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debug_preview_banner"),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = debugPreviewBanner,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            if (autoJumpPending) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_jump_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.auto_jump_countdown_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onCancelAutoJump) {
                            Text(text = stringResource(R.string.action_cancel))
                        }
                    }
                }
            }

            when {
                hasWritePermission -> {
                    PrivilegedOneTapContent(
                        devState = devState,
                        onRequestUsbChange = { enabled ->
                            if (enabled) {
                                onSetUsbDebugging(true)
                            } else {
                                pendingConfirm = PendingTurnOffConfirm.USB_DEBUGGING
                            }
                        },
                        onRequestWirelessChange = { enabled ->
                            if (enabled) {
                                onSetWirelessDebugging(true)
                            } else {
                                pendingConfirm = PendingTurnOffConfirm.WIRELESS_DEBUGGING
                            }
                        },
                        onRequestDevOptionsChange = { enabled ->
                            if (enabled) {
                                onSetDeveloperOptions(true)
                            } else {
                                pendingConfirm = PendingTurnOffConfirm.DEVELOPER_OPTIONS
                            }
                        },
                        onOpenDeveloperOptionsTop = onOpenDeveloperOptionsTop
                    )
                }

                !devState.devOptionsEnabled -> {
                    UnprivilegedDevOffContent(
                        selectedBrand = selectedBrand,
                        showBrandPicker = showBrandPicker,
                        onToggleBrandPicker = { showBrandPicker = !showBrandPicker },
                        onSelectBrand = { selectedBrand = it },
                        onOpenBuildNumberScreen = onOpenBuildNumberScreen
                    )
                }

                else -> {
                    UnprivilegedDevOnContent(
                        devState = devState,
                        autoJumpEnabled = autoJumpEnabled,
                        onAutoJumpChanged = onAutoJumpChanged,
                        onOpenEnableAdbScreen = onOpenEnableAdbScreen,
                        onOpenDeveloperOptionsTop = onOpenDeveloperOptionsTop
                    )
                }
            }

            QuickSurfacesCard(
                supportsWirelessAdb = devState.supportsWirelessAdb,
                onAddUsbTile = { onAddUsbQuickTile(::handleTileOutcome) },
                onAddWirelessTile = { onAddWirelessQuickTile(::handleTileOutcome) },
                onPinWidget = {
                    val requested = onPinHomeWidget()
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(
                                if (requested) {
                                    R.string.feedback_widget_pin_requested
                                } else {
                                    R.string.feedback_widget_unsupported
                                }
                            )
                        )
                    }
                },
                onPinShortcut = {
                    val requested = onPinHomeShortcut()
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(
                                if (requested) {
                                    R.string.feedback_shortcut_pin_requested
                                } else {
                                    R.string.feedback_shortcut_unsupported
                                }
                            )
                        )
                    }
                }
            )

            TechnicianFooterCard(
                hasWritePermission = hasWritePermission,
                expanded = techExpanded,
                onToggleExpanded = { techExpanded = !techExpanded }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    when (pendingConfirm) {
        PendingTurnOffConfirm.USB_DEBUGGING -> {
            ConfirmTurnOffDialog(
                title = stringResource(R.string.dialog_turn_off_usb_title),
                body = stringResource(R.string.dialog_turn_off_usb_body),
                onConfirm = {
                    pendingConfirm = null
                    onSetUsbDebugging(false)
                },
                onDismiss = { pendingConfirm = null }
            )
        }

        PendingTurnOffConfirm.WIRELESS_DEBUGGING -> {
            ConfirmTurnOffDialog(
                title = stringResource(R.string.dialog_turn_off_wireless_title),
                body = stringResource(R.string.dialog_turn_off_wireless_body),
                onConfirm = {
                    pendingConfirm = null
                    onSetWirelessDebugging(false)
                },
                onDismiss = { pendingConfirm = null }
            )
        }

        PendingTurnOffConfirm.DEVELOPER_OPTIONS -> {
            ConfirmTurnOffDialog(
                title = stringResource(R.string.dialog_turn_off_dev_title),
                body = stringResource(R.string.dialog_turn_off_dev_body),
                onConfirm = {
                    pendingConfirm = null
                    onSetDeveloperOptions(false)
                },
                onDismiss = { pendingConfirm = null }
            )
        }

        null -> Unit
    }

    manualQsTileLabel?.let { tileLabel ->
        AlertDialog(
            onDismissRequest = { manualQsTileLabel = null },
            title = {
                Text(
                    text = stringResource(R.string.dialog_manual_qs_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(R.string.dialog_manual_qs_body, tileLabel))
            },
            confirmButton = {
                Button(onClick = { manualQsTileLabel = null }) {
                    Text(text = stringResource(R.string.action_got_it))
                }
            }
        )
    }
}

@Composable
private fun QuickSurfacesCard(
    supportsWirelessAdb: Boolean,
    onAddUsbTile: () -> Unit,
    onAddWirelessTile: () -> Unit,
    onPinWidget: () -> Unit,
    onPinShortcut: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_quick_surfaces"),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_surfaces_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.quick_surfaces_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = onAddUsbTile,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("btn_add_qs_usb"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_qs_usb_debug),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_add_qs_usb),
                    textAlign = TextAlign.Center
                )
            }

            if (supportsWirelessAdb) {
                FilledTonalButton(
                    onClick = onAddWirelessTile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_add_qs_wireless"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_qs_wireless_debug),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_add_qs_wireless),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPinWidget,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 44.dp)
                        .testTag("btn_pin_home_widget"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_pin_home_widget),
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = onPinShortcut,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 44.dp)
                        .testTag("btn_pin_home_shortcut"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_pin_home_shortcut),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun UnprivilegedDevOffContent(
    selectedBrand: OemBrand,
    showBrandPicker: Boolean,
    onToggleBrandPicker: () -> Unit,
    onSelectBrand: (OemBrand) -> Unit,
    onOpenBuildNumberScreen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_dev_off_status"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.dev_off_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusPillBadge(isOn = false)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.dev_off_banner_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_oem_guide"),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.guide_card_title, brandLabel(selectedBrand)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onToggleBrandPicker,
                    modifier = Modifier.testTag("btn_other_brands")
                ) {
                    Text(text = stringResource(R.string.other_brands_toggle))
                }
            }

            AnimatedVisibility(visible = showBrandPicker) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OemBrand.entries.forEach { brand ->
                        FilterChip(
                            selected = brand == selectedBrand,
                            onClick = { onSelectBrand(brand) },
                            label = { Text(text = brandLabel(brand)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val (step1, step2) = brandSteps(selectedBrand)
            GuideStepRow(number = "1", text = step1, emphasize = false)
            Spacer(modifier = Modifier.height(10.dp))
            GuideStepRow(number = "2", text = step2, emphasize = true)
        }
    }

    Button(
        onClick = onOpenBuildNumberScreen,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("btn_open_build_number"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = stringResource(R.string.btn_open_build_number),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }

    Text(
        text = stringResource(R.string.note_pin_and_back),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .testTag("note_pin_and_back")
    )
}

@Composable
private fun UnprivilegedDevOnContent(
    devState: DevState,
    autoJumpEnabled: Boolean,
    onAutoJumpChanged: (Boolean) -> Unit,
    onOpenEnableAdbScreen: () -> Unit,
    onOpenDeveloperOptionsTop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_status_hero"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (devState.adbEnabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.status_hero_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            StatusRow(
                label = stringResource(R.string.label_usb_debugging),
                isOn = devState.adbEnabled,
                prominent = true
            )

            if (devState.supportsWirelessAdb) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                StatusRow(
                    label = stringResource(R.string.label_wireless_debugging),
                    isOn = devState.wirelessAdbEnabled,
                    prominent = false
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            StatusRow(
                label = stringResource(R.string.label_developer_mode),
                isOn = devState.devOptionsEnabled,
                prominent = false
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = onOpenEnableAdbScreen,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("btn_primary_adb_jump"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (devState.adbEnabled) {
                    stringResource(R.string.btn_go_turn_off_adb)
                } else {
                    stringResource(R.string.btn_go_turn_on_adb)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = stringResource(R.string.adb_jump_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_banking_helper"),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.banking_card_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.banking_card_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onOpenDeveloperOptionsTop,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_open_dev_options_top"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_open_dev_options_top),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_auto_jump_pref"),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAutoJumpChanged(!autoJumpEnabled) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.pref_auto_jump_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.pref_auto_jump_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoJumpEnabled,
                onCheckedChange = onAutoJumpChanged,
                modifier = Modifier.testTag("switch_auto_jump")
            )
        }
    }
}

@Composable
private fun PrivilegedOneTapContent(
    devState: DevState,
    onRequestUsbChange: (Boolean) -> Unit,
    onRequestWirelessChange: (Boolean) -> Unit,
    onRequestDevOptionsChange: (Boolean) -> Unit,
    onOpenDeveloperOptionsTop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_privileged_switches"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.mode_one_tap_badge),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = stringResource(R.string.mode_one_tap_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            PrivilegedSwitchRow(
                title = stringResource(R.string.label_usb_debugging),
                subtitle = stringResource(R.string.switch_usb_desc),
                checked = devState.adbEnabled,
                onCheckedChange = onRequestUsbChange,
                tag = "switch_privileged_usb"
            )

            if (devState.supportsWirelessAdb) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                PrivilegedSwitchRow(
                    title = stringResource(R.string.label_wireless_debugging),
                    subtitle = stringResource(R.string.switch_wireless_desc),
                    checked = devState.wirelessAdbEnabled,
                    onCheckedChange = onRequestWirelessChange,
                    tag = "switch_privileged_wireless"
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            PrivilegedSwitchRow(
                title = stringResource(R.string.label_developer_mode),
                subtitle = stringResource(R.string.switch_dev_options_desc),
                checked = devState.devOptionsEnabled,
                onCheckedChange = onRequestDevOptionsChange,
                tag = "switch_privileged_dev"
            )
        }
    }

    OutlinedButton(
        onClick = onOpenDeveloperOptionsTop,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("btn_open_system_dev_settings"),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = stringResource(R.string.btn_open_system_dev_settings),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PrivilegedSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                StatusPillBadge(isOn = checked)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun TechnicianFooterCard(
    hasWritePermission: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val context = LocalContext.current
    var copiedRecently by remember { mutableStateOf(false) }

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(2000)
            copiedRecently = false
        }
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_tech_footer"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("btn_tech_footer_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.tech_footer_header),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tech_perm_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (hasWritePermission) {
                                Color(0xFF059669).copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = if (hasWritePermission) {
                                    stringResource(R.string.tech_perm_granted)
                                } else {
                                    stringResource(R.string.tech_perm_not_granted)
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasWritePermission) {
                                    Color(0xFF059669)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.tech_cmd_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        SelectionContainer {
                            Text(
                                text = SettingsIntents.ADB_GRANT_COMMAND,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("ADB Grant Command", SettingsIntents.ADB_GRANT_COMMAND)
                            )
                            copiedRecently = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_copy_adb_cmd"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (copiedRecently) {
                                stringResource(R.string.action_copied)
                            } else {
                                stringResource(R.string.action_copy_command)
                            }
                        )
                    }

                    Text(
                        text = stringResource(R.string.tech_note_oneplus),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.tech_note_xiaomi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    isOn: Boolean,
    prominent: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (prominent) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            fontWeight = if (prominent) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        StatusPillBadge(isOn = isOn)
    }
}

@Composable
private fun StatusPillBadge(isOn: Boolean) {
    val bgColor = if (isOn) Color(0xFF059669) else Color(0xFF475569)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bgColor
    ) {
        Text(
            text = if (isOn) stringResource(R.string.status_on) else stringResource(R.string.status_off),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun GuideStepRow(
    number: String,
    text: String,
    emphasize: Boolean
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ConfirmTurnOffDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = body)
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.action_confirm_turn_off))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun brandLabel(brand: OemBrand): String {
    return when (brand) {
        OemBrand.SAMSUNG -> stringResource(R.string.brand_samsung)
        OemBrand.ONEPLUS_OPPO_REALME -> stringResource(R.string.brand_oneplus_oppo_realme)
        OemBrand.XIAOMI -> stringResource(R.string.brand_xiaomi)
        OemBrand.VIVO -> stringResource(R.string.brand_vivo)
        OemBrand.GOOGLE_OTHER -> stringResource(R.string.brand_google_other)
    }
}

@Composable
private fun brandSteps(brand: OemBrand): Pair<String, String> {
    return when (brand) {
        OemBrand.SAMSUNG ->
            stringResource(R.string.guide_samsung_step1) to stringResource(R.string.guide_samsung_step2)
        OemBrand.ONEPLUS_OPPO_REALME ->
            stringResource(R.string.guide_oneplus_step1) to stringResource(R.string.guide_oneplus_step2)
        OemBrand.XIAOMI ->
            stringResource(R.string.guide_xiaomi_step1) to stringResource(R.string.guide_xiaomi_step2)
        OemBrand.VIVO ->
            stringResource(R.string.guide_vivo_step1) to stringResource(R.string.guide_vivo_step2)
        OemBrand.GOOGLE_OTHER ->
            stringResource(R.string.guide_google_step1) to stringResource(R.string.guide_google_step2)
    }
}
