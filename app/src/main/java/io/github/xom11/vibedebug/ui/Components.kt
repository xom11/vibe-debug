package io.github.xom11.vibedebug.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.xom11.vibedebug.R
import io.github.xom11.vibedebug.ui.theme.AppTheme
import io.github.xom11.vibedebug.ui.theme.MonoFont
import kotlinx.coroutines.delay

enum class AppTab(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val tag: String,
) {
    HOME("home", R.string.nav_home, R.drawable.ic_home, "nav_tab_home"),
    SHORTCUTS("shortcuts", R.string.nav_shortcuts, R.drawable.ic_grid, "nav_tab_shortcuts"),
    SETUP("setup", R.string.nav_setup, R.drawable.ic_sliders, "nav_tab_setup");

    companion object {
        fun fromRoute(route: String?): AppTab =
            entries.firstOrNull { it.route == route } ?: HOME
    }
}

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val adaptive = LocalAdaptiveLayout.current
    Box(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = adaptive.contentMaxWidthDp.dp)
                .fillMaxWidth()
                .padding(
                    horizontal = adaptive.horizontalPaddingDp.dp,
                    vertical = adaptive.verticalPaddingDp.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val palette = AppTheme.palette
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontFamily = MonoFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.08.em,
        color = palette.muted,
    )
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = AppTheme.palette
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.line, shape),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = AppTheme.palette.divider,
    )
}

@Composable
fun StatusBanner(
    title: String,
    body: String?,
    fg: Color,
    bodyColor: Color,
    bg: Color,
    border: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = fg,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg,
            )
            if (body != null) {
                Text(
                    text = body,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = bodyColor,
                )
            }
        }
        action?.invoke(this)
    }
}

@Composable
fun MonoChip(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    bg: Color = AppTheme.palette.surface2,
    border: Color? = null,
    fg: Color = AppTheme.palette.soft,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val clickModifier = if (onClick != null) {
        Modifier
            .heightIn(min = 36.dp)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    val borderModifier = if (border != null) {
        Modifier.border(1.dp, border, shape)
    } else {
        Modifier
    }
    Row(
        modifier
            .clip(shape)
            .background(bg)
            .then(borderModifier)
            .then(clickModifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
            text = text,
            fontFamily = MonoFont,
            fontSize = 11.sp,
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun IconTile(
    @DrawableRes icon: Int,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(size * 0.5f),
            tint = fg,
        )
    }
}

@Composable
fun IconAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.palette.text,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

@Composable
fun AccentSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val palette = AppTheme.palette
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = palette.onMint,
            checkedTrackColor = palette.mint,
            checkedBorderColor = palette.mint,
            uncheckedThumbColor = palette.muted,
            uncheckedTrackColor = palette.surface2,
            uncheckedBorderColor = palette.offBorder,
            disabledCheckedThumbColor = palette.onMint.copy(alpha = 0.6f),
            disabledCheckedTrackColor = palette.mint.copy(alpha = 0.4f),
            disabledUncheckedThumbColor = palette.muted.copy(alpha = 0.5f),
            disabledUncheckedTrackColor = palette.surface2.copy(alpha = 0.5f),
        ),
    )
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    enabled: Boolean = true,
) {
    val palette = AppTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = palette.muted,
                )
            }
        }
        AccentSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun ValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    valueColor: Color = AppTheme.palette.muted,
) {
    val palette = AppTheme.palette
    val clickModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = palette.text,
        )
        Text(
            text = value,
            modifier = Modifier.widthIn(max = 180.dp),
            fontSize = 13.sp,
            color = valueColor,
            textAlign = TextAlign.End,
        )
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = palette.muted,
            )
        }
    }
}

/** Equal-height segmented control whose options expand together for wrapped translations. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
) {
    val palette = AppTheme.palette
    val trackBg = if (palette.isDark) palette.bg else palette.surface2
    Row(
        modifier
            .height(IntrinsicSize.Max)
            .clip(RoundedCornerShape(12.dp))
            .background(trackBg)
            .border(1.dp, palette.divider, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = height)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) palette.text else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = if (selected) {
                        if (palette.isDark) palette.bg else Color.White
                    } else {
                        palette.soft
                    },
                )
            }
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AppTheme.palette.mint,
    textColor: Color = AppTheme.palette.onMint,
    height: Dp = 48.dp,
) {
    val palette = AppTheme.palette
    Box(
        modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else palette.surface2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (enabled) textColor else palette.muted,
        )
    }
}

@Composable
fun OutlineActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AppTheme.palette.text,
    border: Color = AppTheme.palette.line,
    fill: Color = Color.Transparent,
    height: Dp = 44.dp,
    @DrawableRes icon: Int? = null,
) {
    val palette = AppTheme.palette
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .heightIn(min = height)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (enabled) border else palette.divider, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (enabled) color else palette.muted,
            )
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (enabled) color else palette.muted,
        )
    }
}

@Composable
fun CommandCopyBox(
    command: String,
    modifier: Modifier = Modifier,
    copyTag: String? = null,
) {
    val palette = AppTheme.palette
    val context = LocalContext.current
    var copied by remember(command) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000L)
            copied = false
        }
    }

    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.code)
            .border(1.dp, palette.line, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SelectionContainer(Modifier.fillMaxWidth()) {
            Text(
                text = command,
                fontFamily = MonoFont,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = palette.codeText,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            val btnShape = RoundedCornerShape(10.dp)
            Row(
                Modifier
                    .then(if (copyTag != null) Modifier.testTag(copyTag) else Modifier)
                    .heightIn(min = 36.dp)
                    .clip(btnShape)
                    .background(Color(0xFF161A1D))
                    .border(1.dp, Color(0xFF2A3137), btnShape)
                    .clickable(role = Role.Button) {
                        context.copyText("ADB command", command)
                        copied = true
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(if (copied) R.drawable.ic_check else R.drawable.ic_copy),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (copied) Color(0xFF7EE2A8) else Color(0xFFE9EDF0),
                )
                Text(
                    text = stringResource(if (copied) R.string.action_copied else R.string.action_copy),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (copied) Color(0xFF7EE2A8) else Color(0xFFE9EDF0),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun BottomNavBar(
    currentTab: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    val dividerColor = palette.divider
    Row(
        modifier
            .fillMaxWidth()
            .background(palette.bg)
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .windowInsetsPadding(
                WindowInsets.systemBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        AppTab.entries.forEach { tab ->
            NavBarItem(
                tab = tab,
                selected = tab == currentTab,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
fun SideNavRail(
    currentTab: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AppTheme.palette
    val adaptive = LocalAdaptiveLayout.current
    val dividerColor = palette.divider
    val showLabels = adaptive.heightDp >= 300
    Column(
        modifier
            .fillMaxHeight()
            .background(palette.bg)
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .windowInsetsPadding(
                WindowInsets.systemBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
            )
            .width(adaptive.railWidthDp.dp)
            .padding(
                horizontal = 8.dp,
                vertical = if (adaptive.isCompactHeight) 6.dp else adaptive.verticalPaddingDp.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            if (adaptive.isCompactHeight) 6.dp else 10.dp,
            if (adaptive.isCompactHeight) Alignment.CenterVertically else Alignment.Top,
        ),
    ) {
        AppTab.entries.forEach { tab ->
            RailNavItem(
                tab = tab,
                selected = tab == currentTab,
                compact = adaptive.isCompactHeight,
                showLabel = showLabels,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun RailNavItem(
    tab: AppTab,
    selected: Boolean,
    compact: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val palette = AppTheme.palette
    val label = stringResource(tab.labelRes)
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(tab.tag)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .then(
                if (showLabel) Modifier
                else Modifier.semantics { contentDescription = label }
            )
            .padding(vertical = if (compact) 4.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) palette.mintDim else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) palette.mint else palette.muted,
            )
        }
        if (showLabel) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) palette.text else palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowScope.NavBarItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = AppTheme.palette
    Column(
        Modifier
            .weight(1f)
            .testTag(tab.tag)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) palette.mintDim else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) palette.mint else palette.muted,
            )
        }
        Text(
            text = stringResource(tab.labelRes),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) palette.text else palette.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun rememberNow(active: Boolean = true, periodMs: Long = 15_000L): Long {
    val now by produceState(System.currentTimeMillis(), active, periodMs) {
        value = System.currentTimeMillis()
        while (active) {
            delay(periodMs)
            value = System.currentTimeMillis()
        }
    }
    return now
}

fun Context.showToast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

fun Context.copyText(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}
