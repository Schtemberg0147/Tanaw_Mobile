package com.tanaw.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class WindowHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

@Immutable
data class TanawWindowSize(
    val widthClass: WindowWidthClass,
    val heightClass: WindowHeightClass,
    val widthDp: Float,
    val heightDp: Float,
    val fontScale: Float,
    val isCompactLandscape: Boolean,
    val isNarrowPhone: Boolean,
    val isLargeFontScale: Boolean,
    val useNavRail: Boolean,
    val useTwoPane: Boolean,
    val gridColumns: Int,
    val horizontalGutter: Dp,
    val cardSpacing: Dp,
    val authMaxWidth: Dp = 480.dp,
    val formMaxWidth: Dp = 600.dp,
    val contentMaxWidth: Dp = 960.dp,
    val wideContentMaxWidth: Dp = 1200.dp
)

object AdaptiveLayoutRules {
    fun compute(
        widthDp: Float,
        heightDp: Float,
        fontScale: Float = 1f
    ): TanawWindowSize {
        val safeWidth = widthDp.coerceAtLeast(1f)
        val safeHeight = heightDp.coerceAtLeast(1f)
        val safeFontScale = fontScale.coerceAtLeast(0.5f)

        val widthClass = when {
            safeWidth < 600f -> WindowWidthClass.COMPACT
            safeWidth < 840f -> WindowWidthClass.MEDIUM
            else -> WindowWidthClass.EXPANDED
        }
        val heightClass = when {
            safeHeight < 480f -> WindowHeightClass.COMPACT
            safeHeight < 900f -> WindowHeightClass.MEDIUM
            else -> WindowHeightClass.EXPANDED
        }

        val isCompactLandscape = heightClass == WindowHeightClass.COMPACT && safeWidth > safeHeight
        val isNarrowPhone = safeWidth < 340f
        val isLargeFontScale = safeFontScale >= 1.35f

        val useNavRail = widthClass != WindowWidthClass.COMPACT ||
            (heightClass == WindowHeightClass.COMPACT && safeWidth >= 560f)

        val useTwoPane = widthClass == WindowWidthClass.EXPANDED ||
            (widthClass == WindowWidthClass.MEDIUM && safeWidth >= 700f)

        val gridColumns = when (widthClass) {
            WindowWidthClass.COMPACT -> 1
            WindowWidthClass.MEDIUM -> 2
            WindowWidthClass.EXPANDED -> if (safeWidth >= 1100f) 3 else 2
        }

        val horizontalGutter = when {
            isNarrowPhone -> 12.dp
            widthClass == WindowWidthClass.COMPACT -> 16.dp
            widthClass == WindowWidthClass.MEDIUM -> 24.dp
            else -> 32.dp
        }

        val cardSpacing = if (widthClass == WindowWidthClass.COMPACT) 12.dp else 16.dp

        return TanawWindowSize(
            widthClass = widthClass,
            heightClass = heightClass,
            widthDp = safeWidth,
            heightDp = safeHeight,
            fontScale = safeFontScale,
            isCompactLandscape = isCompactLandscape,
            isNarrowPhone = isNarrowPhone,
            isLargeFontScale = isLargeFontScale,
            useNavRail = useNavRail,
            useTwoPane = useTwoPane,
            gridColumns = gridColumns,
            horizontalGutter = horizontalGutter,
            cardSpacing = cardSpacing
        )
    }
}

val LocalTanawWindowSize = compositionLocalOf {
    AdaptiveLayoutRules.compute(widthDp = 360f, heightDp = 800f, fontScale = 1f)
}

@Composable
fun rememberTanawWindowSize(): TanawWindowSize {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val widthDp = if (containerSize.width > 0) {
        with(density) { containerSize.width.toDp().value }
    } else {
        360f
    }
    val heightDp = if (containerSize.height > 0) {
        with(density) { containerSize.height.toDp().value }
    } else {
        800f
    }
    val fontScale = density.fontScale
    return remember(widthDp, heightDp, fontScale) {
        AdaptiveLayoutRules.compute(
            widthDp = widthDp,
            heightDp = heightDp,
            fontScale = fontScale
        )
    }
}

@Preview(name = "1. Narrow Phone (320dp)", widthDp = 320, heightDp = 640, showBackground = true)
@Preview(name = "2. Phone (360dp)", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "3. Landscape Phone", widthDp = 800, heightDp = 360, showBackground = true)
@Preview(name = "4. Foldable Unfolded", widthDp = 673, heightDp = 841, showBackground = true)
@Preview(name = "5. Tablet (1024dp)", widthDp = 1024, heightDp = 768, showBackground = true)
annotation class FormFactorPreviews
