package io.github.xom11.vibedebug.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

enum class WindowWidthTier {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

enum class NavigationChrome {
    BOTTOM_BAR,
    SIDE_RAIL,
}

@Immutable
data class AdaptiveLayoutSpec(
    val widthDp: Int,
    val heightDp: Int,
    val widthTier: WindowWidthTier,
    val isCompactHeight: Boolean,
    val navigationChrome: NavigationChrome,
    val useTwoColumnContent: Boolean,
    val railWidthDp: Int,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val columnSpacingDp: Int,
    val contentMaxWidthDp: Int,
    val canFitSideBySideSubCards: Boolean,
    val compactHeroTypography: Boolean,
    val dialogMaxWidthDp: Int,
    val dialogListMaxHeightDp: Int,
) {
    companion object {
        fun fromWindowSize(width: Dp, height: Dp): AdaptiveLayoutSpec =
            fromWindowSizeDp(width.value.toInt(), height.value.toInt())

        fun fromWindowSizeDp(widthDp: Int, heightDp: Int): AdaptiveLayoutSpec {
            val widthTier = when {
                widthDp >= 840 -> WindowWidthTier.EXPANDED
                widthDp >= 600 -> WindowWidthTier.MEDIUM
                else -> WindowWidthTier.COMPACT
            }
            val isCompactHeight = heightDp < 480
            val navigationChrome = if (widthDp >= 600 || (isCompactHeight && widthDp >= 480)) {
                NavigationChrome.SIDE_RAIL
            } else {
                NavigationChrome.BOTTOM_BAR
            }
            val useTwoColumnContent = widthDp >= 680
            val railWidthDp = if (navigationChrome == NavigationChrome.SIDE_RAIL) {
                if (widthTier == WindowWidthTier.EXPANDED) 88 else 80
            } else {
                0
            }
            val horizontalPaddingDp = when {
                widthTier == WindowWidthTier.EXPANDED -> 28
                widthTier == WindowWidthTier.MEDIUM && !useTwoColumnContent -> 24
                else -> 20
            }
            val verticalPaddingDp = if (isCompactHeight) 12 else 18
            val columnSpacingDp = if (widthTier == WindowWidthTier.EXPANDED) 20 else 16
            val contentMaxWidthDp = when {
                useTwoColumnContent -> 980
                widthTier == WindowWidthTier.MEDIUM -> 620
                else -> 600
            }

            val availableWidth = (widthDp - railWidthDp).coerceAtLeast(0)
            val containerWidth = (minOf(availableWidth, contentMaxWidthDp) - horizontalPaddingDp * 2).coerceAtLeast(0)
            val columnWidth = if (useTwoColumnContent) {
                (containerWidth - columnSpacingDp) / 2
            } else {
                containerWidth
            }
            val canFitSideBySideSubCards = columnWidth >= 300
            val compactHeroTypography = columnWidth < 300

            val dialogMaxWidthDp = if (widthTier == WindowWidthTier.COMPACT && !isCompactHeight) 420 else 540
            val dialogListMaxHeightDp = (heightDp - 220).coerceIn(140, 440)

            return AdaptiveLayoutSpec(
                widthDp = widthDp,
                heightDp = heightDp,
                widthTier = widthTier,
                isCompactHeight = isCompactHeight,
                navigationChrome = navigationChrome,
                useTwoColumnContent = useTwoColumnContent,
                railWidthDp = railWidthDp,
                horizontalPaddingDp = horizontalPaddingDp,
                verticalPaddingDp = verticalPaddingDp,
                columnSpacingDp = columnSpacingDp,
                contentMaxWidthDp = contentMaxWidthDp,
                canFitSideBySideSubCards = canFitSideBySideSubCards,
                compactHeroTypography = compactHeroTypography,
                dialogMaxWidthDp = dialogMaxWidthDp,
                dialogListMaxHeightDp = dialogListMaxHeightDp,
            )
        }
    }
}

val LocalAdaptiveLayout = staticCompositionLocalOf {
    AdaptiveLayoutSpec.fromWindowSizeDp(390, 844)
}
