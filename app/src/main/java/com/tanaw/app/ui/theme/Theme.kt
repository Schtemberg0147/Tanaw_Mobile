package com.tanaw.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class ExtendedColors(
    val successBannerBackground: Color,
    val onSuccessBannerText: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        successBannerBackground = SuccessBannerBg,
        onSuccessBannerText = SuccessGreen
    )
}

val LightColorScheme = lightColorScheme(
    primary              = NavyPrimary,
    onPrimary            = Color.White,
    primaryContainer     = NavyPrimary,
    onPrimaryContainer   = Color.White,
    secondary            = OrangeAccent,
    onSecondary          = Color.White,
    secondaryContainer   = OrangeAccent.copy(alpha = 0.1f),
    onSecondaryContainer = OrangeAccent,
    background           = Color.White,
    onBackground         = TextDark,
    surface              = Color.White,
    onSurface            = TextDark,
    surfaceContainerHigh = InputGray,
    surfaceVariant       = BackgroundGray,
    onSurfaceVariant     = HintGray,
    outline              = BorderGray,
    outlineVariant       = BorderGray
)

val DarkColorScheme = darkColorScheme(
    primary              = NavyPrimaryDark,
    onPrimary            = TextDark,
    primaryContainer     = NavyPrimary,
    onPrimaryContainer   = Color.White,
    secondary            = OrangeAccentDark,
    onSecondary          = TextDark,
    secondaryContainer   = OrangeAccentDark.copy(alpha = 0.15f),
    onSecondaryContainer = OrangeAccentDark,
    background           = BackgroundDark,
    onBackground         = TextLight,
    surface              = SurfaceDark,
    onSurface            = TextLight,
    surfaceContainerHigh = InputDark,
    surfaceVariant       = SurfaceDark,
    onSurfaceVariant     = HintDark,
    outline              = BorderDark,
    outlineVariant       = BorderDark
)

val ExtendedLightColors = ExtendedColors(
    successBannerBackground = SuccessBannerBg,
    onSuccessBannerText     = SuccessGreen
)

val ExtendedDarkColors = ExtendedColors(
    successBannerBackground = SuccessBannerBgDark,
    onSuccessBannerText     = SuccessGreenDark
)

object TanawTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}

@Composable
fun TanawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) ExtendedDarkColors else ExtendedLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
