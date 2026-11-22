package io.github.l3n4k4.usbdebugswitch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import io.github.l3n4k4.usbdebugswitch.data.ThemePreference

/** Semantic color tokens for the Obsidian & Mint dark theme and Crisp Daylight light theme. */
@Immutable
data class AppPalette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val code: Color,
    val codeText: Color,
    val line: Color,
    val divider: Color,
    val text: Color,
    val soft: Color,
    val muted: Color,
    val offBorder: Color,
    val mint: Color,
    val mintLight: Color,
    val onMint: Color,
    val mintDim: Color,
    val mintBg: Color,
    val mintBorder: Color,
    val amber: Color,
    val amberText: Color,
    val amberBody: Color,
    val amberBg: Color,
    val amberBorder: Color,
    val onAmber: Color,
    val coral: Color,
    val coralBody: Color,
    val coralBg: Color,
    val coralBorder: Color,
    val blue: Color,
    val blueText: Color,
    val blueBg: Color,
    val blueBorder: Color,
)

val ObsidianDarkPalette = AppPalette(
    isDark = true,
    bg = Color(0xFF0E1113),
    surface = Color(0xFF161A1D),
    surface2 = Color(0xFF1E2327),
    code = Color(0xFF07090A),
    codeText = Color(0xFFBFEFD2),
    line = Color(0xFF2A3137),
    divider = Color(0xFF22292E),
    text = Color(0xFFE9EDF0),
    soft = Color(0xFFC9D1D7),
    muted = Color(0xFF9BA6AF),
    offBorder = Color(0xFF7C8790),
    mint = Color(0xFF7EE2A8),
    mintLight = Color(0xFFBFEFD2),
    onMint = Color(0xFF0B1F14),
    mintDim = Color(0xFF1F3328),
    mintBg = Color(0xFF132219),
    mintBorder = Color(0xFF3E7A57),
    amber = Color(0xFFF2B55A),
    amberText = Color(0xFFF7CD8A),
    amberBody = Color(0xFFE2CFAE),
    amberBg = Color(0xFF2A2113),
    amberBorder = Color(0xFF5A4520),
    onAmber = Color(0xFF1E1608),
    coral = Color(0xFFFFB4A8),
    coralBody = Color(0xFFE8C6C0),
    coralBg = Color(0xFF2A1714),
    coralBorder = Color(0xFF5C2E27),
    blue = Color(0xFF8DB6FF),
    blueText = Color(0xFFB7C9EA),
    blueBg = Color(0xFF1A2333),
    blueBorder = Color(0xFF2C3A52),
)

val CrispLightPalette = AppPalette(
    isDark = false,
    bg = Color(0xFFF5F7FA),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEDF2F7),
    code = Color(0xFF0F172A),
    codeText = Color(0xFFBFEFD2),
    line = Color(0xFFD7E0E8),
    divider = Color(0xFFE6ECF2),
    text = Color(0xFF0F172A),
    soft = Color(0xFF334155),
    muted = Color(0xFF64748B),
    offBorder = Color(0xFF94A3B8),
    mint = Color(0xFF059669),
    mintLight = Color(0xFF065F46),
    onMint = Color(0xFFFFFFFF),
    mintDim = Color(0xFFD1FAE5),
    mintBg = Color(0xFFECFDF5),
    mintBorder = Color(0xFF6EE7B7),
    amber = Color(0xFFD97706),
    amberText = Color(0xFF92400E),
    amberBody = Color(0xFF78350F),
    amberBg = Color(0xFFFFFBEB),
    amberBorder = Color(0xFFFCD34D),
    onAmber = Color(0xFFFFFFFF),
    coral = Color(0xFFDC2626),
    coralBody = Color(0xFF991B1B),
    coralBg = Color(0xFFFEF2F2),
    coralBorder = Color(0xFFFCA5A5),
    blue = Color(0xFF2563EB),
    blueText = Color(0xFF1E40AF),
    blueBg = Color(0xFFEFF6FF),
    blueBorder = Color(0xFFBFDBFE),
)

val LocalAppPalette = staticCompositionLocalOf { ObsidianDarkPalette }

object AppTheme {
    val palette: AppPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppPalette.current
}

val MonoFont: FontFamily = FontFamily.Monospace
val SansFont: FontFamily = FontFamily.SansSerif

private fun TextStyle.sans() = copy(fontFamily = SansFont)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.sans(),
        displayMedium = t.displayMedium.sans(),
        displaySmall = t.displaySmall.sans(),
        headlineLarge = t.headlineLarge.sans(),
        headlineMedium = t.headlineMedium.sans(),
        headlineSmall = t.headlineSmall.sans(),
        titleLarge = t.titleLarge.sans(),
        titleMedium = t.titleMedium.sans(),
        titleSmall = t.titleSmall.sans(),
        bodyLarge = t.bodyLarge.sans(),
        bodyMedium = t.bodyMedium.sans(),
        bodySmall = t.bodySmall.sans(),
        labelLarge = t.labelLarge.sans(),
        labelMedium = t.labelMedium.sans(),
        labelSmall = t.labelSmall.sans(),
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = ObsidianDarkPalette.mint,
    onPrimary = ObsidianDarkPalette.onMint,
    primaryContainer = ObsidianDarkPalette.mintDim,
    onPrimaryContainer = ObsidianDarkPalette.mintLight,
    secondary = ObsidianDarkPalette.blue,
    onSecondary = ObsidianDarkPalette.onMint,
    secondaryContainer = ObsidianDarkPalette.blueBg,
    onSecondaryContainer = ObsidianDarkPalette.blueText,
    tertiary = ObsidianDarkPalette.amber,
    onTertiary = ObsidianDarkPalette.onAmber,
    tertiaryContainer = ObsidianDarkPalette.amberBg,
    onTertiaryContainer = ObsidianDarkPalette.amberText,
    background = ObsidianDarkPalette.bg,
    onBackground = ObsidianDarkPalette.text,
    surface = ObsidianDarkPalette.surface,
    onSurface = ObsidianDarkPalette.text,
    surfaceVariant = ObsidianDarkPalette.surface2,
    onSurfaceVariant = ObsidianDarkPalette.muted,
    outline = ObsidianDarkPalette.line,
    outlineVariant = ObsidianDarkPalette.divider,
    error = ObsidianDarkPalette.coral,
    errorContainer = ObsidianDarkPalette.coralBg,
    onErrorContainer = ObsidianDarkPalette.coralBody,
)

private val LightColorScheme = lightColorScheme(
    primary = CrispLightPalette.mint,
    onPrimary = CrispLightPalette.onMint,
    primaryContainer = CrispLightPalette.mintDim,
    onPrimaryContainer = CrispLightPalette.mintLight,
    secondary = CrispLightPalette.blue,
    onSecondary = Color.White,
    secondaryContainer = CrispLightPalette.blueBg,
    onSecondaryContainer = CrispLightPalette.blueText,
    tertiary = CrispLightPalette.amber,
    onTertiary = CrispLightPalette.onAmber,
    tertiaryContainer = CrispLightPalette.amberBg,
    onTertiaryContainer = CrispLightPalette.amberText,
    background = CrispLightPalette.bg,
    onBackground = CrispLightPalette.text,
    surface = CrispLightPalette.surface,
    onSurface = CrispLightPalette.text,
    surfaceVariant = CrispLightPalette.surface2,
    onSurfaceVariant = CrispLightPalette.muted,
    outline = CrispLightPalette.line,
    outlineVariant = CrispLightPalette.divider,
    error = CrispLightPalette.coral,
    errorContainer = CrispLightPalette.coralBg,
    onErrorContainer = CrispLightPalette.coralBody,
)

@Composable
fun resolveIsDarkTheme(preference: ThemePreference): Boolean = when (preference) {
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
    ThemePreference.DARK -> true
    ThemePreference.LIGHT -> false
}

@Composable
fun UsbDebugSwitchTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = resolveIsDarkTheme(themePreference)
    val palette = if (isDark) ObsidianDarkPalette else CrispLightPalette
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
