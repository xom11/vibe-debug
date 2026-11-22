package io.github.l3n4k4.usbdebugswitch.ui

import android.content.Context
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.l3n4k4.usbdebugswitch.R
import io.github.l3n4k4.usbdebugswitch.model.DevState
import io.github.l3n4k4.usbdebugswitch.shortcut.LaunchableApp
import io.github.l3n4k4.usbdebugswitch.shortcut.SafeLaunchHelper
import io.github.l3n4k4.usbdebugswitch.shortcut.SafeLaunchResult
import io.github.l3n4k4.usbdebugswitch.shortcut.TileAddOutcome
import io.github.l3n4k4.usbdebugswitch.ui.theme.AppTheme
import io.github.l3n4k4.usbdebugswitch.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ShortcutsTab(
    devState: DevState,
    hasWritePermission: Boolean,
    safeLaunchPackages: List<String>,
    onShowSnackbar: (String) -> Unit,
    onShowManualTileDialog: (String) -> Unit,
    onAddUsbTile: ((TileAddOutcome) -> Unit) -> Unit,
    onAddWirelessTile: ((TileAddOutcome) -> Unit) -> Unit,
    onAddDevOptionsTile: ((TileAddOutcome) -> Unit) -> Unit,
    onAddKeepAwakeTile: ((TileAddOutcome) -> Unit) -> Unit,
    onPinHomeWidget: () -> Boolean,
    onPinHomeShortcut: () -> Boolean,
    onAddSafeLaunchPackage: (String) -> Unit,
    onRemoveSafeLaunchPackage: (String) -> Unit,
    onOpenDeveloperOptionsTop: () -> Unit,
    onRefreshState: () -> Unit,
) {
    val palette = AppTheme.palette
    val context = LocalContext.current
    var showAppPicker by rememberSaveable { mutableStateOf(false) }

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

    ScreenColumn(spacing = 14.dp) {
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

        // Section 1: Quick Settings Tiles
        SectionLabel(stringResource(R.string.section_qs_tiles))
        SurfaceCard(modifier = Modifier.testTag("card_qs_tiles")) {
            TileActionRow(
                icon = R.drawable.ic_qs_usb_debug,
                title = stringResource(R.string.tile_label),
                description = stringResource(R.string.tile_usb_desc),
                buttonTag = "btn_add_qs_usb",
                onAdd = { onAddUsbTile(::handleTileOutcome) },
            )
            if (devState.supportsWirelessAdb) {
                RowDivider()
                TileActionRow(
                    icon = R.drawable.ic_qs_wireless_debug,
                    title = stringResource(R.string.tile_wireless_label),
                    description = stringResource(R.string.tile_wireless_desc),
                    buttonTag = "btn_add_qs_wireless",
                    onAdd = { onAddWirelessTile(::handleTileOutcome) },
                )
            }
            RowDivider()
            TileActionRow(
                icon = R.drawable.ic_qs_dev_options,
                title = stringResource(R.string.tile_dev_options_label),
                description = stringResource(R.string.tile_dev_options_desc),
                buttonTag = "btn_add_qs_dev_options",
                onAdd = { onAddDevOptionsTile(::handleTileOutcome) },
            )
            RowDivider()
            TileActionRow(
                icon = R.drawable.ic_qs_keep_awake,
                title = stringResource(R.string.tile_keep_awake_label),
                description = stringResource(R.string.tile_keep_awake_desc),
                buttonTag = "btn_add_qs_keep_awake",
                onAdd = { onAddKeepAwakeTile(::handleTileOutcome) },
            )
        }

        // Section 2: Home Screen Widget & Shortcut
        SectionLabel(stringResource(R.string.section_home_surfaces))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlineActionButton(
                text = stringResource(R.string.btn_pin_home_widget),
                onClick = {
                    val requested = onPinHomeWidget()
                    onShowSnackbar(
                        context.getString(
                            if (requested) R.string.feedback_widget_pin_requested
                            else R.string.feedback_widget_unsupported
                        )
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("btn_pin_home_widget"),
                color = palette.text,
                border = palette.line,
                fill = palette.surface,
                height = 50.dp,
                icon = R.drawable.ic_grid,
            )

            OutlineActionButton(
                text = stringResource(R.string.btn_pin_home_shortcut),
                onClick = {
                    val requested = onPinHomeShortcut()
                    onShowSnackbar(
                        context.getString(
                            if (requested) R.string.feedback_shortcut_pin_requested
                            else R.string.feedback_shortcut_unsupported
                        )
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("btn_pin_home_shortcut"),
                color = palette.text,
                border = palette.line,
                fill = palette.surface,
                height = 50.dp,
                icon = R.drawable.ic_open_external,
            )
        }

        // Section 3: Banking / VNeID Safe Launch
        SectionLabel(stringResource(R.string.section_safe_launch))

        StatusBanner(
            title = stringResource(R.string.section_safe_launch),
            body = stringResource(R.string.safe_launch_desc),
            fg = palette.blueText,
            bodyColor = palette.soft,
            bg = palette.blueBg,
            border = palette.blueBorder,
            icon = R.drawable.ic_lock,
        )

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

        if (safeLaunchPackages.isEmpty()) {
            Text(
                text = stringResource(R.string.safe_launch_empty),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = palette.muted,
            )
        } else {
            safeLaunchPackages.forEach { pkg ->
                SafeLaunchAppCard(
                    packageName = pkg,
                    onOpenNow = { label ->
                        when (SafeLaunchHelper.launchSafely(context, pkg)) {
                            SafeLaunchResult.LaunchedClean -> {
                                context.showToast(context.getString(R.string.safe_launch_toast_clean, label))
                                onRefreshState()
                            }
                            SafeLaunchResult.NeedsPermission -> {
                                onShowSnackbar(context.getString(R.string.safe_launch_toast_need_perm))
                                onOpenDeveloperOptionsTop()
                            }
                            SafeLaunchResult.BlockedByPolicy -> {
                                onShowSnackbar(context.getString(R.string.safe_launch_toast_blocked))
                            }
                            SafeLaunchResult.AppNotFound -> {
                                onShowSnackbar(context.getString(R.string.safe_launch_toast_not_found))
                            }
                        }
                    },
                    onPinShortcut = { label ->
                        val ok = SafeLaunchHelper.requestPinSafeLaunchShortcut(context, pkg)
                        onShowSnackbar(
                            if (ok) context.getString(R.string.safe_launch_pin_ok, label)
                            else context.getString(R.string.safe_launch_pin_unsupported)
                        )
                    },
                    onRemove = { onRemoveSafeLaunchPackage(pkg) },
                )
            }
        }

        OutlineActionButton(
            text = stringResource(R.string.safe_launch_add_btn),
            onClick = { showAppPicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_add_safe_launch_app"),
            color = palette.mint,
            border = palette.mintBorder,
            fill = palette.mintBg,
            height = 48.dp,
        )
    }

    if (showAppPicker) {
        SafeLaunchAppPickerDialog(
            savedPackages = safeLaunchPackages,
            onTogglePackage = { pkg, currentlySaved ->
                if (currentlySaved) onRemoveSafeLaunchPackage(pkg) else onAddSafeLaunchPackage(pkg)
            },
            onDismiss = { showAppPicker = false },
        )
    }
}

@Composable
private fun TileActionRow(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    buttonTag: String,
    onAdd: () -> Unit,
) {
    val palette = AppTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(
            icon = icon,
            bg = palette.surface2,
            fg = palette.mint,
            size = 38.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
            )
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = palette.muted,
            )
        }
        OutlineActionButton(
            text = stringResource(R.string.action_add_tile),
            onClick = onAdd,
            modifier = Modifier.testTag(buttonTag),
            color = palette.mint,
            border = palette.mintBorder,
            height = 38.dp,
        )
    }
}

@Composable
private fun SafeLaunchAppCard(
    packageName: String,
    onOpenNow: (label: String) -> Unit,
    onPinShortcut: (label: String) -> Unit,
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
                text = stringResource(R.string.safe_launch_pin),
                onClick = { onPinShortcut(label) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = palette.text,
                border = palette.line,
                height = 42.dp,
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
                                .heightIn(max = 340.dp),
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
