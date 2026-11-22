package io.github.l3n4k4.usbdebugswitch.ui

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.l3n4k4.usbdebugswitch.R
import io.github.l3n4k4.usbdebugswitch.model.DevState
import io.github.l3n4k4.usbdebugswitch.model.OemBrand
import io.github.l3n4k4.usbdebugswitch.ui.theme.AppTheme
import io.github.l3n4k4.usbdebugswitch.ui.theme.MonoFont
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeTab(
    devState: DevState,
    hasWritePermission: Boolean,
    detectedBrand: OemBrand,
    autoJumpEnabled: Boolean,
    autoOffEnabled: Boolean,
    autoOffMinutes: Int,
    autoOffTargetAtMs: Long,
    onRefreshState: () -> Unit,
    onNavigateTab: (AppTab) -> Unit,
    onAutoJumpChanged: (Boolean) -> Unit,
    onToggleAutoOff: (Boolean) -> Unit,
    onOpenBuildNumberScreen: () -> Unit,
    onOpenEnableAdbScreen: () -> Unit,
    onOpenEnableWirelessAdbScreen: () -> Unit,
    onOpenDeveloperOptionsTop: () -> Unit,
    onSetUsbDebugging: (Boolean) -> Unit,
    onRequestTurnOffUsb: () -> Unit,
    onSetWirelessDebugging: (Boolean) -> Unit,
    onRequestTurnOffWireless: () -> Unit,
    onSetDeveloperOptions: (Boolean) -> Unit,
    onRequestTurnOffDevOptions: () -> Unit,
    onSetKeepAwake: (Boolean) -> Unit,
) {
    val palette = AppTheme.palette
    val context = LocalContext.current
    val now = rememberNow(active = hasWritePermission && devState.adbEnabled && autoOffTargetAtMs > 0L)
    var selectedBrand by remember(detectedBrand) { mutableStateOf(detectedBrand) }
    var showBrandPicker by rememberSaveable { mutableStateOf(false) }

    val deviceBrandLabel = remember {
        val raw = Build.BRAND.takeIf { it.isNotBlank() } ?: Build.MANUFACTURER
        raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    ScreenColumn(spacing = 12.dp) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(
                icon = R.drawable.ic_qs_usb_debug,
                bg = palette.mint,
                fg = palette.onMint,
                size = 40.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                )
                Text(
                    text = stringResource(
                        R.string.home_device_line,
                        Build.VERSION.RELEASE,
                        Build.VERSION.SDK_INT,
                        deviceBrandLabel,
                    ),
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    color = palette.muted,
                )
            }
            IconAction(
                icon = R.drawable.ic_refresh,
                label = stringResource(R.string.cd_refresh),
                onClick = {
                    onRefreshState()
                    context.showToast(context.getString(R.string.toast_refreshed))
                },
                modifier = Modifier.testTag("btn_refresh"),
            )
        }

        // Top mode / policy status strip
        when {
            devState.blockedByPolicy -> {
                StatusBanner(
                    title = stringResource(R.string.banner_policy_title),
                    body = stringResource(R.string.banner_policy_body),
                    fg = palette.coral,
                    bodyColor = palette.coralBody,
                    bg = palette.coralBg,
                    border = palette.coralBorder,
                    modifier = Modifier.testTag("banner_policy_blocked"),
                    icon = R.drawable.ic_lock,
                )
            }

            hasWritePermission -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .testTag("strip_one_tap_active")
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.mintBg)
                        .border(1.dp, palette.mintBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = palette.mint,
                    )
                    Text(
                        text = stringResource(R.string.strip_one_tap_active),
                        fontFamily = MonoFont,
                        fontSize = 11.sp,
                        color = palette.mintLight,
                    )
                }
            }

            else -> {
                val stripShape = RoundedCornerShape(12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .testTag("strip_mode_a")
                        .clip(stripShape)
                        .background(palette.amberBg)
                        .border(1.dp, palette.amberBorder, stripShape)
                        .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.strip_mode_a_active),
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.amberText,
                    )
                    Box(
                        Modifier
                            .testTag("btn_strip_enable_one_tap")
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(palette.amber)
                            .clickable(role = Role.Button) { onNavigateTab(AppTab.SETUP) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.action_enable_one_tap),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onAmber,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }

        // Signature USB Hero Card
        UsbHeroCard(
            devState = devState,
            hasWritePermission = hasWritePermission,
            autoJumpEnabled = autoJumpEnabled,
            autoOffEnabled = autoOffEnabled,
            autoOffMinutes = autoOffMinutes,
            autoOffTargetAtMs = autoOffTargetAtMs,
            nowMs = now,
            onHeroPowerClick = {
                if (hasWritePermission) {
                    if (devState.adbEnabled) onRequestTurnOffUsb() else onSetUsbDebugging(true)
                } else {
                    if (devState.devOptionsEnabled) onOpenEnableAdbScreen() else onOpenBuildNumberScreen()
                }
            },
            onToggleAutoOffChip = {
                val next = !autoOffEnabled
                onToggleAutoOff(next)
                context.showToast(
                    if (next) context.getString(R.string.toast_auto_off_enabled, autoOffMinutes)
                    else context.getString(R.string.toast_auto_off_disabled)
                )
            },
            onToggleAutoJumpChip = {
                val next = !autoJumpEnabled
                onAutoJumpChanged(next)
                context.showToast(
                    context.getString(if (next) R.string.toast_auto_jump_on else R.string.toast_auto_jump_off)
                )
            },
        )

        // OEM Unlock Guide (only in Mode A when Developer Options is OFF and not policy-blocked)
        if (!hasWritePermission && !devState.devOptionsEnabled && !devState.blockedByPolicy) {
            OemUnlockGuideCard(
                selectedBrand = selectedBrand,
                showBrandPicker = showBrandPicker,
                onToggleBrandPicker = { showBrandPicker = !showBrandPicker },
                onSelectBrand = { selectedBrand = it },
                onOpenBuildNumberScreen = onOpenBuildNumberScreen,
            )
        }

        SectionLabel(stringResource(R.string.section_controls))

        // Wireless Debugging Wide Card
        if (devState.supportsWirelessAdb) {
            WirelessDebugCard(
                devState = devState,
                hasWritePermission = hasWritePermission,
                onClick = {
                    if (hasWritePermission) {
                        if (devState.wirelessAdbEnabled) onRequestTurnOffWireless() else onSetWirelessDebugging(true)
                    } else {
                        if (devState.devOptionsEnabled) onOpenEnableWirelessAdbScreen() else onOpenBuildNumberScreen()
                    }
                },
            )
        }

        // Equal-height 2-Column Controls: Developer Options & Keep Awake
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmallControlCard(
                icon = R.drawable.ic_qs_dev_options,
                title = stringResource(R.string.label_developer_mode),
                subtitle = stringResource(
                    if (devState.devOptionsEnabled) R.string.dev_menu_visible else R.string.dev_menu_hidden
                ),
                isOn = devState.devOptionsEnabled,
                hasWritePermission = hasWritePermission,
                enabled = !devState.blockedByPolicy,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("card_dev_options"),
                onClick = {
                    if (hasWritePermission) {
                        if (devState.devOptionsEnabled) onRequestTurnOffDevOptions() else onSetDeveloperOptions(true)
                    } else {
                        if (devState.devOptionsEnabled) onOpenDeveloperOptionsTop() else onOpenBuildNumberScreen()
                    }
                },
            )

            SmallControlCard(
                icon = R.drawable.ic_qs_keep_awake,
                title = stringResource(R.string.label_keep_awake),
                subtitle = when {
                    devState.keepAwakeEnabled -> stringResource(R.string.awake_while_charging)
                    hasWritePermission -> stringResource(R.string.awake_off)
                    else -> stringResource(R.string.awake_mode_a_hint)
                },
                isOn = devState.keepAwakeEnabled,
                hasWritePermission = hasWritePermission,
                enabled = !devState.blockedByPolicy || hasWritePermission,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("card_keep_awake"),
                onClick = {
                    if (hasWritePermission) {
                        onSetKeepAwake(!devState.keepAwakeEnabled)
                    } else {
                        if (devState.devOptionsEnabled) onOpenDeveloperOptionsTop() else onOpenBuildNumberScreen()
                    }
                },
            )
        }

        // Banking / VNeID Helper Row
        SectionLabel(stringResource(R.string.section_banking_helper))
        val bankingShape = RoundedCornerShape(16.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .testTag("card_banking_helper")
                .clip(bankingShape)
                .background(palette.surface)
                .border(1.dp, palette.line, bankingShape)
                .clickable(
                    enabled = !devState.blockedByPolicy,
                    role = Role.Button,
                ) {
                    if (hasWritePermission) {
                        onNavigateTab(AppTab.SHORTCUTS)
                    } else {
                        onOpenDeveloperOptionsTop()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(
                icon = R.drawable.ic_lock,
                bg = palette.blueBg,
                fg = palette.blue,
                size = 38.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(
                        if (hasWritePermission) R.string.banking_helper_mode_b_title
                        else R.string.banking_helper_mode_a_title
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                )
                Text(
                    text = stringResource(
                        if (hasWritePermission) R.string.banking_helper_mode_b_sub
                        else R.string.banking_helper_mode_a_sub
                    ),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = palette.muted,
                )
            }
            Icon(
                painter = painterResource(
                    if (hasWritePermission) R.drawable.ic_chevron else R.drawable.ic_open_external
                ),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = palette.muted,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsbHeroCard(
    devState: DevState,
    hasWritePermission: Boolean,
    autoJumpEnabled: Boolean,
    autoOffEnabled: Boolean,
    autoOffMinutes: Int,
    autoOffTargetAtMs: Long,
    nowMs: Long,
    onHeroPowerClick: () -> Unit,
    onToggleAutoOffChip: () -> Unit,
    onToggleAutoJumpChip: () -> Unit,
) {
    val palette = AppTheme.palette
    val on = devState.adbEnabled && !devState.blockedByPolicy
    val enabled = !devState.blockedByPolicy
    val toggleLabel = stringResource(R.string.cd_toggle_usb)
    val stateLabel = stringResource(if (on) R.string.status_on else R.string.status_off)
    val subtitle = when {
        devState.blockedByPolicy -> stringResource(R.string.usb_sub_blocked)
        hasWritePermission && on && autoOffTargetAtMs > nowMs -> {
            val formattedTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(autoOffTargetAtMs))
            stringResource(R.string.usb_sub_on_auto_off, formattedTime)
        }
        hasWritePermission && on -> stringResource(R.string.usb_sub_on_manual)
        hasWritePermission -> stringResource(R.string.usb_sub_off_one_tap)
        !devState.devOptionsEnabled -> stringResource(R.string.usb_sub_mode_a_dev_off)
        on -> stringResource(R.string.usb_sub_mode_a_on)
        else -> stringResource(R.string.usb_sub_mode_a_off)
    }

    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("card_usb_hero")
            .alpha(if (enabled) 1f else 0.6f)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, if (on) palette.mintBorder else palette.line, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_qs_usb_debug),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (on) palette.mint else palette.muted,
                    )
                    Text(
                        text = stringResource(R.string.usb_hero_label),
                        fontFamily = MonoFont,
                        fontSize = 11.sp,
                        letterSpacing = 0.08.em,
                        color = palette.muted,
                    )
                }
                Text(
                    text = stringResource(if (on) R.string.state_on_caps else R.string.state_off_caps),
                    fontSize = 52.sp,
                    lineHeight = 52.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.03).em,
                    color = if (on) palette.mint else palette.text,
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = palette.muted,
                )
            }

            Box(
                Modifier
                    .size(88.dp)
                    .testTag("btn_hero_usb_power")
                    .clip(CircleShape)
                    .background(if (on) palette.mint else palette.surface2)
                    .border(2.dp, if (on) palette.mint else palette.offBorder, CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onHeroPowerClick)
                    .semantics {
                        contentDescription = toggleLabel
                        stateDescription = stateLabel
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_power),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = if (on) palette.onMint else palette.text,
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MonoChip(text = "adb_enabled = ${if (devState.adbEnabled) 1 else 0}")
            if (hasWritePermission) {
                MonoChip(
                    text = if (autoOffEnabled) {
                        stringResource(R.string.chip_auto_off_min, autoOffMinutes)
                    } else {
                        stringResource(R.string.chip_auto_off_off)
                    },
                    icon = R.drawable.ic_timer,
                    bg = if (autoOffEnabled) palette.mintBg else palette.surface2,
                    border = if (autoOffEnabled) palette.mintBorder else null,
                    fg = if (autoOffEnabled) palette.mintLight else palette.soft,
                    modifier = Modifier.testTag("chip_auto_off"),
                    onClick = onToggleAutoOffChip,
                )
            } else {
                MonoChip(
                    text = stringResource(
                        if (autoJumpEnabled) R.string.chip_auto_jump_on else R.string.chip_auto_jump_off
                    ),
                    icon = R.drawable.ic_open_external,
                    bg = if (autoJumpEnabled) palette.mintBg else palette.surface2,
                    border = if (autoJumpEnabled) palette.mintBorder else null,
                    fg = if (autoJumpEnabled) palette.mintLight else palette.soft,
                    modifier = Modifier.testTag("chip_auto_jump"),
                    onClick = onToggleAutoJumpChip,
                )
            }
        }
    }
}

@Composable
private fun WirelessDebugCard(
    devState: DevState,
    hasWritePermission: Boolean,
    onClick: () -> Unit,
) {
    val palette = AppTheme.palette
    val on = devState.wirelessAdbEnabled && !devState.blockedByPolicy
    val enabled = !devState.blockedByPolicy
    val shape = RoundedCornerShape(18.dp)

    val rowModifier = if (hasWritePermission) {
        Modifier.toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
    } else {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .testTag("card_wireless_debugging")
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.line, shape)
            .then(rowModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(
            icon = R.drawable.ic_qs_wireless_debug,
            bg = if (on) palette.mintDim else palette.surface2,
            fg = if (on) palette.mint else palette.muted,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(R.string.label_wireless_debugging),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            Text(
                text = when {
                    hasWritePermission && on -> stringResource(R.string.wireless_sub_on)
                    hasWritePermission -> stringResource(R.string.wireless_sub_off_one_tap)
                    else -> stringResource(R.string.wireless_sub_mode_a)
                },
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = palette.muted,
            )
        }
        if (hasWritePermission) {
            AccentSwitch(checked = on, onCheckedChange = null, enabled = enabled)
        } else {
            StatePillBadge(isOn = on)
            Icon(
                painter = painterResource(R.drawable.ic_open_external),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = palette.muted,
            )
        }
    }
}

@Composable
private fun SmallControlCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    isOn: Boolean,
    hasWritePermission: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = AppTheme.palette
    val shape = RoundedCornerShape(18.dp)
    val clickMod = if (hasWritePermission) {
        Modifier.toggleable(value = isOn, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
    } else {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }

    Column(
        modifier
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.line, shape)
            .then(clickMod)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconTile(
                icon = icon,
                bg = if (isOn) palette.mintDim else palette.surface2,
                fg = if (isOn) palette.mint else palette.muted,
                size = 36.dp,
            )
            if (hasWritePermission) {
                AccentSwitch(checked = isOn, onCheckedChange = null, enabled = enabled)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatePillBadge(isOn = isOn)
                    Icon(
                        painter = painterResource(R.drawable.ic_open_external),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = palette.muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun StatePillBadge(isOn: Boolean) {
    val palette = AppTheme.palette
    Text(
        text = stringResource(if (isOn) R.string.state_on_caps else R.string.state_off_caps),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isOn) palette.mintDim else palette.surface2)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        fontFamily = MonoFont,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (isOn) palette.mint else palette.muted,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun OemUnlockGuideCard(
    selectedBrand: OemBrand,
    showBrandPicker: Boolean,
    onToggleBrandPicker: () -> Unit,
    onSelectBrand: (OemBrand) -> Unit,
    onOpenBuildNumberScreen: () -> Unit,
) {
    val palette = AppTheme.palette
    val (step1, step2) = oemBrandSteps(selectedBrand)

    SurfaceCard(modifier = Modifier.testTag("card_oem_guide")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.guide_card_title, oemBrandLabel(selectedBrand)),
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                )
                TextButton(
                    onClick = onToggleBrandPicker,
                    modifier = Modifier.testTag("btn_other_brands"),
                ) {
                    Text(
                        text = stringResource(R.string.other_brands_toggle),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.mint,
                    )
                }
            }

            AnimatedVisibility(visible = showBrandPicker) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OemBrand.entries.forEach { brand ->
                        val selected = brand == selectedBrand
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectBrand(brand) },
                            label = {
                                Text(
                                    text = oemBrandLabel(brand),
                                    fontSize = 12.sp,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.mintDim,
                                selectedLabelColor = palette.mintLight,
                                containerColor = palette.surface2,
                                labelColor = palette.soft,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = palette.line,
                                selectedBorderColor = palette.mintBorder,
                            ),
                        )
                    }
                }
            }

            GuideStepRow(number = "1", text = step1, emphasize = false)
            GuideStepRow(number = "2", text = step2, emphasize = true)

            PrimaryActionButton(
                text = stringResource(R.string.btn_open_build_number),
                onClick = onOpenBuildNumberScreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_open_build_number"),
                height = 50.dp,
            )

            Text(
                text = stringResource(R.string.note_pin_and_back),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_pin_and_back"),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun GuideStepRow(
    number: String,
    text: String,
    emphasize: Boolean,
) {
    val palette = AppTheme.palette
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (emphasize) palette.mintDim else palette.surface2)
                .border(1.dp, if (emphasize) palette.mintBorder else palette.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                fontFamily = MonoFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (emphasize) palette.mint else palette.soft,
            )
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasize) palette.text else palette.soft,
        )
    }
}

@Composable
private fun oemBrandLabel(brand: OemBrand): String = when (brand) {
    OemBrand.SAMSUNG -> stringResource(R.string.brand_samsung)
    OemBrand.ONEPLUS_OPPO_REALME -> stringResource(R.string.brand_oneplus_oppo_realme)
    OemBrand.XIAOMI -> stringResource(R.string.brand_xiaomi)
    OemBrand.VIVO -> stringResource(R.string.brand_vivo)
    OemBrand.GOOGLE_OTHER -> stringResource(R.string.brand_google_other)
}

@Composable
private fun oemBrandSteps(brand: OemBrand): Pair<String, String> = when (brand) {
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
