package com.tanaw.app.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutRulesTest {

    @Test
    fun narrowPortraitPhoneUsesCompactSinglePaneWithTighterGutter() {
        val size = AdaptiveLayoutRules.compute(widthDp = 320f, heightDp = 568f, fontScale = 1.0f)

        assertEquals(WindowWidthClass.COMPACT, size.widthClass)
        assertEquals(WindowHeightClass.MEDIUM, size.heightClass)
        assertTrue(size.isNarrowPhone)
        assertFalse(size.isCompactLandscape)
        assertFalse(size.useNavRail)
        assertFalse(size.useTwoPane)
        assertEquals(1, size.gridColumns)
        assertEquals(12.dp, size.horizontalGutter)
    }

    @Test
    fun standardPortraitPhoneUsesBottomBarAndStandardGutter() {
        val size = AdaptiveLayoutRules.compute(widthDp = 360f, heightDp = 800f, fontScale = 1.0f)

        assertEquals(WindowWidthClass.COMPACT, size.widthClass)
        assertEquals(WindowHeightClass.MEDIUM, size.heightClass)
        assertFalse(size.isNarrowPhone)
        assertFalse(size.useNavRail)
        assertFalse(size.useTwoPane)
        assertEquals(1, size.gridColumns)
        assertEquals(16.dp, size.horizontalGutter)
    }

    @Test
    fun landscapePhoneUsesNavRailAndMarksCompactLandscape() {
        val size = AdaptiveLayoutRules.compute(widthDp = 800f, heightDp = 360f, fontScale = 1.0f)

        assertEquals(WindowWidthClass.MEDIUM, size.widthClass)
        assertEquals(WindowHeightClass.COMPACT, size.heightClass)
        assertTrue(size.isCompactLandscape)
        assertTrue(size.useNavRail)
        assertTrue(size.useTwoPane)
        assertEquals(2, size.gridColumns)
        assertEquals(24.dp, size.horizontalGutter)
    }

    @Test
    fun unfoldedFoldableUsesMediumClassWithNavRailAndTwoColumns() {
        val size = AdaptiveLayoutRules.compute(widthDp = 673f, heightDp = 841f, fontScale = 1.0f)

        assertEquals(WindowWidthClass.MEDIUM, size.widthClass)
        assertEquals(WindowHeightClass.MEDIUM, size.heightClass)
        assertFalse(size.isCompactLandscape)
        assertTrue(size.useNavRail)
        assertFalse(size.useTwoPane)
        assertEquals(2, size.gridColumns)
        assertEquals(24.dp, size.horizontalGutter)
    }

    @Test
    fun tabletExpandedEnablesTwoPaneAndThreeColumnsAt1100DpPlus() {
        val mediumTablet = AdaptiveLayoutRules.compute(widthDp = 900f, heightDp = 1200f, fontScale = 1.0f)
        assertEquals(WindowWidthClass.EXPANDED, mediumTablet.widthClass)
        assertEquals(WindowHeightClass.EXPANDED, mediumTablet.heightClass)
        assertTrue(mediumTablet.useNavRail)
        assertTrue(mediumTablet.useTwoPane)
        assertEquals(2, mediumTablet.gridColumns)
        assertEquals(32.dp, mediumTablet.horizontalGutter)

        val wideTablet = AdaptiveLayoutRules.compute(widthDp = 1280f, heightDp = 800f, fontScale = 1.0f)
        assertEquals(WindowWidthClass.EXPANDED, wideTablet.widthClass)
        assertEquals(3, wideTablet.gridColumns)
    }

    @Test
    fun largeFontScaleIsFlaggedAt135PercentAndAbove() {
        val normal = AdaptiveLayoutRules.compute(widthDp = 360f, heightDp = 800f, fontScale = 1.2f)
        val large = AdaptiveLayoutRules.compute(widthDp = 360f, heightDp = 800f, fontScale = 1.35f)

        assertFalse(normal.isLargeFontScale)
        assertTrue(large.isLargeFontScale)
    }
}
