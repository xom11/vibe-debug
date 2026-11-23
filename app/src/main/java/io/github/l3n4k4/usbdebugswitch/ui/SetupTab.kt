package io.github.l3n4k4.usbdebugswitch.ui

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.l3n4k4.usbdebugswitch.BuildConfig
import io.github.l3n4k4.usbdebugswitch.R
import io.github.l3n4k4.usbdebugswitch.data.ThemePreference
import io.github.l3n4k4.usbdebugswitch.model.DevState
import io.github.l3n4k4.usbdebugswitch.model.OemBrand
import io.github.l3n4k4.usbdebugswitch.navigation.SettingsIntents
import io.github.l3n4k4.usbdebugswitch.ui.theme.AppTheme
import io.github.l3n4k4.usbdebugswitch.ui.theme.MonoFont

private val AUTO_OFF_OPTIONS = listOf(15, 30, 60)
private val THEME_OPTIONS = listOf(ThemePreference.SYSTEM, ThemePreference.DARK, ThemePreference.LIGHT)
private val LANGUAGE_TAGS = listOf("", "vi", "en")

@Composable
fun SetupTab(
    devState: DevState,
    hasWritePermission: Boolean,
    detectedBrand: OemBrand,
    autoOffEnabled: Boolean,
    autoOffMinutes: Int,
    themePreference: ThemePreference,
    autoJumpEnabled: Boolean,
    onOpenBuildNumberScreen: () -> Unit,
    onRecheckPermission: () -> Boolean,
    onAutoOffEnabledChanged: (Boolean) -> Unit,
    onAutoOffMinutesChanged: (Int) -> Unit,
    onThemePreferenceChanged: (ThemePreference) -> Unit,
    onAutoJumpChanged: (Boolean) -> Unit,
) {
    val palette = AppTheme.palette
    val context = LocalContext.current
    val adaptive = LocalAdaptiveLayout.current
    var showLegacyOplusNote by rememberSaveable { mutableStateOf(false) }

    val oneTapSetupCard: @Composable (Modifier) -> Unit = { cardModifier ->
        SurfaceCard(
            modifier = cardModifier.testTag("card_setup_checklist"),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.fillMaxWidth()) {
                ChecklistRow(
                    index = 1,
                    title = stringResource(R.string.setup_checklist_dev),
                    status = stringResource(
                        if (devState.devOptionsEnabled) R.string.setup_dev_on else R.string.setup_dev_off
                    ),
                    done = devState.devOptionsEnabled,
                    warn = false,
                    onClick = if (devState.devOptionsEnabled || devState.blockedByPolicy) null else onOpenBuildNumberScreen,
                )
                RowDivider()
                ChecklistRow(
                    index = 2,
                    title = stringResource(R.string.setup_checklist_perm),
                    subtitle = "WRITE_SECURE_SETTINGS",
                    status = stringResource(
                        if (hasWritePermission) R.string.setup_perm_granted else R.string.setup_perm_missing
                    ),
                    done = hasWritePermission,
                    warn = !hasWritePermission,
                )
                RowDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when {
                        devState.blockedByPolicy -> {
                            StatusBanner(
                                title = stringResource(R.string.banner_policy_title),
                                body = stringResource(R.string.banner_policy_body),
                                fg = palette.coral,
                                bodyColor = palette.coralBody,
                                bg = palette.coralBg,
                                border = palette.coralBorder,
                                icon = R.drawable.ic_lock,
                            )
                        }

                        hasWritePermission -> {
                            StatusBanner(
                                title = stringResource(R.string.mode_one_tap_badge),
                                body = stringResource(R.string.setup_already_ready),
                                fg = palette.mint,
                                bodyColor = palette.mintLight,
                                bg = palette.mintBg,
                                border = palette.mintBorder,
                                icon = R.drawable.ic_check,
                            )
                        }

                        detectedBrand == OemBrand.ONEPLUS_OPPO_REALME -> {
                            StatusBanner(
                                title = stringResource(R.string.setup_coloros_title),
                                body = stringResource(R.string.setup_coloros_body),
                                fg = palette.amberText,
                                bodyColor = palette.amberBody,
                                bg = palette.amberBg,
                                border = palette.amberBorder,
                            )
                            CommandCopyBox(
                                command = SettingsIntents.ADB_REINSTALL_GRANT_COMMAND,
                                copyTag = "btn_copy_reinstall_cmd",
                            )
                            Text(
                                text = stringResource(R.string.other_brands_toggle).let {
                                    if (showLegacyOplusNote) "▾ " else "▸ "
                                } + stringResource(R.string.setup_coloros_legacy_note),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(role = Role.Button) { showLegacyOplusNote = !showLegacyOplusNote }
                                    .padding(vertical = 4.dp),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = palette.muted,
                            )
                            AnimatedVisibility(visible = showLegacyOplusNote) {
                                CommandCopyBox(
                                    command = SettingsIntents.ADB_GRANT_COMMAND,
                                    copyTag = "btn_copy_adb_cmd",
                                )
                            }
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.setup_standard_intro),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = palette.soft,
                            )
                            CommandCopyBox(
                                command = SettingsIntents.ADB_GRANT_COMMAND,
                                copyTag = "btn_copy_adb_cmd",
                            )
                            if (detectedBrand == OemBrand.XIAOMI) {
                                StatusBanner(
                                    title = stringResource(R.string.brand_xiaomi),
                                    body = stringResource(R.string.setup_xiaomi_warning),
                                    fg = palette.amberText,
                                    bodyColor = palette.amberBody,
                                    bg = palette.amberBg,
                                    border = palette.amberBorder,
                                )
                            }
                            Text(
                                text = stringResource(R.string.setup_reinstall_fallback),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = palette.muted,
                            )
                            CommandCopyBox(
                                command = SettingsIntents.ADB_REINSTALL_GRANT_COMMAND,
                                copyTag = "btn_copy_reinstall_cmd",
                            )
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth()) {
                RowDivider()
                Box(Modifier.padding(14.dp)) {
                    if (!hasWritePermission) {
                        PrimaryActionButton(
                            text = stringResource(R.string.btn_recheck_permission),
                            onClick = {
                                val granted = onRecheckPermission()
                                context.showToast(
                                    context.getString(
                                        if (granted) R.string.toast_perm_granted_now
                                        else R.string.toast_perm_still_missing
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_recheck_permission"),
                            height = 48.dp,
                        )
                    } else {
                        OutlineActionButton(
                            text = stringResource(R.string.btn_recheck_permission),
                            onClick = {
                                val granted = onRecheckPermission()
                                context.showToast(
                                    context.getString(
                                        if (granted) R.string.toast_perm_granted_now
                                        else R.string.toast_perm_still_missing
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_recheck_permission"),
                            color = palette.soft,
                            border = palette.line,
                            icon = R.drawable.ic_refresh,
                        )
                    }
                }
            }
        }
    }

    val safetyTimerCard: @Composable (Modifier) -> Unit = { cardModifier ->
        val canUseAutoOff = hasWritePermission && !devState.blockedByPolicy
        SurfaceCard(
            modifier = cardModifier.testTag("card_auto_off_timer"),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = stringResource(R.string.auto_off_title),
                    checked = autoOffEnabled && canUseAutoOff,
                    onChange = onAutoOffEnabledChanged,
                    modifier = Modifier
                        .alpha(if (canUseAutoOff) 1f else 0.65f)
                        .testTag("row_auto_off_switch"),
                    sub = stringResource(
                        if (canUseAutoOff) R.string.auto_off_subtitle
                        else R.string.auto_off_requires_one_tap
                    ),
                    enabled = canUseAutoOff,
                )
                AnimatedVisibility(visible = autoOffEnabled && canUseAutoOff) {
                    if (adaptive.canFitSideBySideSubCards) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.auto_off_duration_label),
                                fontSize = 13.sp,
                                color = palette.muted,
                            )
                            SegmentedControl(
                                options = AUTO_OFF_OPTIONS.map { stringResource(R.string.minutes_option, it) },
                                selectedIndex = AUTO_OFF_OPTIONS.indexOf(autoOffMinutes).coerceAtLeast(0),
                                onSelect = { index -> onAutoOffMinutesChanged(AUTO_OFF_OPTIONS[index]) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("seg_auto_off_minutes"),
                                height = 36.dp,
                            )
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.auto_off_duration_label),
                                fontSize = 13.sp,
                                color = palette.muted,
                            )
                            SegmentedControl(
                                options = AUTO_OFF_OPTIONS.map { stringResource(R.string.minutes_option, it) },
                                selectedIndex = AUTO_OFF_OPTIONS.indexOf(autoOffMinutes).coerceAtLeast(0),
                                onSelect = { index -> onAutoOffMinutesChanged(AUTO_OFF_OPTIONS[index]) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("seg_auto_off_minutes"),
                                height = 36.dp,
                            )
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth()) {
                RowDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(palette.surface2.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_timer),
                        contentDescription = null,
                        modifier = Modifier.padding(top = 1.dp).size(16.dp),
                        tint = palette.muted,
                    )
                    Text(
                        text = stringResource(R.string.auto_off_inexact_note),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = palette.muted,
                    )
                }
            }
        }
    }

    val appearanceCard: @Composable (Modifier) -> Unit = { cardModifier ->
        SurfaceCard(modifier = cardModifier.testTag("card_appearance")) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.pref_theme_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.text,
                )
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.theme_system),
                        stringResource(R.string.theme_dark),
                        stringResource(R.string.theme_light),
                    ),
                    selectedIndex = THEME_OPTIONS.indexOf(themePreference).coerceAtLeast(0),
                    onSelect = { index -> onThemePreferenceChanged(THEME_OPTIONS[index]) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("seg_theme"),
                    height = 38.dp,
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                RowDivider()
                LanguagePickerRow()
            }

            if (!hasWritePermission) {
                RowDivider()
                SwitchRow(
                    title = stringResource(R.string.pref_auto_jump_title),
                    checked = autoJumpEnabled,
                    onChange = onAutoJumpChanged,
                    modifier = Modifier.testTag("row_auto_jump_switch"),
                    sub = stringResource(R.string.pref_auto_jump_subtitle),
                )
            }
        }
    }

    val privacyAboutCard: @Composable (Modifier) -> Unit = { cardModifier ->
        SurfaceCard(
            modifier = cardModifier.testTag("card_privacy_about"),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
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
                        text = stringResource(R.string.privacy_card_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                    )
                    Text(
                        text = stringResource(R.string.privacy_card_body),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = palette.muted,
                    )
                }
            }
            Column(Modifier.fillMaxWidth()) {
                RowDivider()
                ValueRow(
                    title = stringResource(R.string.label_app_version),
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
            }
        }
    }

    ScreenColumn(spacing = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.setup_tab_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            Text(
                text = stringResource(R.string.setup_tab_subtitle),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = palette.muted,
            )
        }

        if (adaptive.useTwoColumnContent) {
            if (hasWritePermission || devState.blockedByPolicy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(adaptive.columnSpacingDp.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionLabel(stringResource(R.string.section_one_tap_setup))
                        oneTapSetupCard(Modifier.weight(1f))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionLabel(stringResource(R.string.section_appearance))
                        appearanceCard(Modifier.weight(1f))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(adaptive.columnSpacingDp.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionLabel(stringResource(R.string.section_safety_timer))
                        safetyTimerCard(Modifier.weight(1f))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionLabel(stringResource(R.string.section_privacy_about))
                        privacyAboutCard(Modifier.weight(1f))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(adaptive.columnSpacingDp.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionLabel(stringResource(R.string.section_one_tap_setup))
                        oneTapSetupCard(Modifier)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel(stringResource(R.string.section_safety_timer))
                            safetyTimerCard(Modifier)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel(stringResource(R.string.section_appearance))
                            appearanceCard(Modifier)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel(stringResource(R.string.section_privacy_about))
                            privacyAboutCard(Modifier)
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.section_one_tap_setup))
                oneTapSetupCard(Modifier)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.section_safety_timer))
                safetyTimerCard(Modifier)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.section_appearance))
                appearanceCard(Modifier)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.section_privacy_about))
                privacyAboutCard(Modifier)
            }
        }
    }
}

@Composable
private fun LanguagePickerRow() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val palette = AppTheme.palette
    val context = LocalContext.current
    val localeManager = remember { context.getSystemService(LocaleManager::class.java) }
    var selectedIndex by remember {
        val tags = localeManager?.applicationLocales?.toLanguageTags().orEmpty().lowercase()
        val idx = when {
            tags.startsWith("vi") -> 1
            tags.startsWith("en") -> 2
            else -> 0
        }
        mutableIntStateOf(idx)
    }

    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.pref_language_label),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = palette.text,
        )
        SegmentedControl(
            options = listOf(
                stringResource(R.string.language_system),
                stringResource(R.string.language_vi),
                stringResource(R.string.language_en),
            ),
            selectedIndex = selectedIndex,
            onSelect = { index ->
                selectedIndex = index
                val tag = LANGUAGE_TAGS[index]
                localeManager?.applicationLocales = if (tag.isEmpty()) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(tag)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("seg_language"),
            height = 38.dp,
        )
    }
}

@Composable
private fun ChecklistRow(
    index: Int,
    title: String,
    status: String,
    done: Boolean,
    warn: Boolean,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = AppTheme.palette
    val clickMod = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(clickMod)
            .heightIn(min = 54.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val ringColor = if (warn && !done) palette.amber else palette.offBorder
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(
                    if (done) Modifier.background(palette.mint)
                    else Modifier.border(2.dp, ringColor, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = palette.onMint,
                )
            } else {
                Text(
                    text = "$index",
                    fontFamily = MonoFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (warn) palette.amber else palette.muted,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    color = palette.muted,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = status,
            modifier = Modifier.widthIn(max = 150.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            color = when {
                done -> palette.mint
                warn -> palette.amber
                else -> palette.muted
            },
        )
    }
}
