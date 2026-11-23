package io.github.xom11.vibedebug

import io.github.xom11.vibedebug.ui.AdaptiveLayoutSpec
import io.github.xom11.vibedebug.ui.NavigationChrome
import io.github.xom11.vibedebug.ui.WindowWidthTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun `phone portrait uses compact bottom bar single column with side-by-side subcards`() {
        listOf(360 to 800, 412 to 915).forEach { (w, h) ->
            val spec = AdaptiveLayoutSpec.fromWindowSizeDp(w, h)
            assertEquals(WindowWidthTier.COMPACT, spec.widthTier)
            assertEquals(NavigationChrome.BOTTOM_BAR, spec.navigationChrome)
            assertFalse(spec.isCompactHeight)
            assertFalse(spec.useTwoColumnContent)
            assertEquals(0, spec.railWidthDp)
            assertTrue(spec.canFitSideBySideSubCards)
            assertFalse(spec.compactHeroTypography)
            assertEquals(420, spec.dialogMaxWidthDp)
        }
    }

    @Test
    fun `ultra narrow split screen stacks subcards and compacts hero typography`() {
        val spec = AdaptiveLayoutSpec.fromWindowSizeDp(290, 700)
        assertEquals(WindowWidthTier.COMPACT, spec.widthTier)
        assertEquals(NavigationChrome.BOTTOM_BAR, spec.navigationChrome)
        assertFalse(spec.useTwoColumnContent)
        assertFalse(spec.canFitSideBySideSubCards)
        assertTrue(spec.compactHeroTypography)
    }

    @Test
    fun `phone landscape uses side rail and clamps dialog list height`() {
        val narrowLandscape = AdaptiveLayoutSpec.fromWindowSizeDp(640, 360)
        assertEquals(WindowWidthTier.MEDIUM, narrowLandscape.widthTier)
        assertTrue(narrowLandscape.isCompactHeight)
        assertEquals(NavigationChrome.SIDE_RAIL, narrowLandscape.navigationChrome)
        assertFalse(narrowLandscape.useTwoColumnContent)
        assertTrue(narrowLandscape.dialogListMaxHeightDp <= 170)
        assertEquals(140, narrowLandscape.dialogListMaxHeightDp)

        val wideLandscape = AdaptiveLayoutSpec.fromWindowSizeDp(844, 390)
        assertEquals(WindowWidthTier.EXPANDED, wideLandscape.widthTier)
        assertTrue(wideLandscape.isCompactHeight)
        assertEquals(NavigationChrome.SIDE_RAIL, wideLandscape.navigationChrome)
        assertTrue(wideLandscape.useTwoColumnContent)
        assertTrue(wideLandscape.dialogListMaxHeightDp <= 170)
        assertEquals(170, wideLandscape.dialogListMaxHeightDp)
    }

    @Test
    fun `small tablet portrait uses side rail centered single column`() {
        val spec = AdaptiveLayoutSpec.fromWindowSizeDp(600, 960)
        assertEquals(WindowWidthTier.MEDIUM, spec.widthTier)
        assertEquals(NavigationChrome.SIDE_RAIL, spec.navigationChrome)
        assertFalse(spec.useTwoColumnContent)
        assertEquals(620, spec.contentMaxWidthDp)
        assertEquals(80, spec.railWidthDp)
        assertTrue(spec.canFitSideBySideSubCards)
    }

    @Test
    fun `book style foldables unfolded use side rail two columns and adapt subcards to pane width`() {
        val zFold = AdaptiveLayoutSpec.fromWindowSizeDp(691, 829)
        assertEquals(WindowWidthTier.MEDIUM, zFold.widthTier)
        assertEquals(NavigationChrome.SIDE_RAIL, zFold.navigationChrome)
        assertTrue(zFold.useTwoColumnContent)
        assertFalse(zFold.canFitSideBySideSubCards)
        assertTrue(zFold.compactHeroTypography)

        val pixelFold = AdaptiveLayoutSpec.fromWindowSizeDp(773, 802)
        assertEquals(WindowWidthTier.MEDIUM, pixelFold.widthTier)
        assertEquals(NavigationChrome.SIDE_RAIL, pixelFold.navigationChrome)
        assertTrue(pixelFold.useTwoColumnContent)
        assertTrue(pixelFold.canFitSideBySideSubCards)
        assertFalse(pixelFold.compactHeroTypography)
    }

    @Test
    fun `large tablet landscape uses expanded two column layout`() {
        listOf(1024 to 768, 1280 to 800).forEach { (w, h) ->
            val spec = AdaptiveLayoutSpec.fromWindowSizeDp(w, h)
            assertEquals(WindowWidthTier.EXPANDED, spec.widthTier)
            assertEquals(NavigationChrome.SIDE_RAIL, spec.navigationChrome)
            assertTrue(spec.useTwoColumnContent)
            assertEquals(980, spec.contentMaxWidthDp)
            assertEquals(88, spec.railWidthDp)
            assertTrue(spec.canFitSideBySideSubCards)
            assertFalse(spec.compactHeroTypography)
            assertEquals(540, spec.dialogMaxWidthDp)
            assertEquals(440, spec.dialogListMaxHeightDp)
        }
    }
}
