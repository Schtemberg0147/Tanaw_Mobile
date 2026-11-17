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
    val onSuccessBannerText: Color,
    val warningContainer: Color = ExpiringOrangeBg,
    val onWarningContainer: Color = ExpiringOrange,
    val infoContainer: Color = OngoingBlueBg,
    val onInfoContainer: Color = OngoingBlue,
    val decorativeOrange: Color = OrangeAccent
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        successBannerBackground = SuccessBannerBg,
        onSuccessBannerText = SuccessGreen,
        warningContainer = ExpiringOrangeBg,
        onWarningContainer = ExpiringOrange,
        infoContainer = OngoingBlueBg,
        onInfoContainer = OngoingBlue,
        decorativeOrange = OrangeAccent
    )
}

val LightColorScheme = lightColorScheme(
    primary              = NavyPrimary,
    onPrimary            = Color.White,
    primaryContainer     = PrimaryContainerLight,
    onPrimaryContainer   = NavyPrimary,
    secondary            = OrangeAccessible,
    onSecondary          = Color.White,
    secondaryContainer   = OrangeSpecialTagBg,
    onSecondaryContainer = OnSecondaryContainerLight,
    background           = BackgroundGray,
    onBackground         = TextDark,
    surface              = Color.White,
    onSurface            = TextDark,
    surfaceContainerHigh = InputGray,
    surfaceVariant       = BorderGray,
    onSurfaceVariant     = HintGray,
    outline              = OutlineGray,
    outlineVariant       = BorderGray,
    error                = ExpiredRed,
    onError              = Color.White,
    errorContainer       = ExpiredRedBg,
    onErrorContainer     = ExpiredRed
)

val DarkColorScheme = darkColorScheme(
    primary              = NavyPrimaryDark,
    onPrimary            = OnPrimaryDark,
    primaryContainer     = PrimaryContainerDark,
    onPrimaryContainer   = OnPrimaryContainerDark,
    secondary            = OrangeAccentDark,
    onSecondary          = OnSecondaryDark,
    secondaryContainer   = OrangeSpecialTagBgDark,
    onSecondaryContainer = OrangeSpecialTagBg,
    background           = BackgroundDark,
    onBackground         = TextLight,
    surface              = SurfaceDark,
    onSurface            = TextLight,
    surfaceContainerHigh = InputDark,
    surfaceVariant       = SurfaceVariantDark,
    onSurfaceVariant     = HintDark,
    outline              = OutlineDark,
    outlineVariant       = DriverLineDark,
    error                = ErrorRedDark,
    onError              = Color(0xFF690005),
    errorContainer       = ErrorBgDark,
    onErrorContainer     = ErrorRedDark
)

val ExtendedLightColors = ExtendedColors(
    successBannerBackground = SuccessBannerBg,
    onSuccessBannerText     = SuccessGreen,
    warningContainer        = ExpiringOrangeBg,
    onWarningContainer      = ExpiringOrange,
    infoContainer           = OngoingBlueBg,
    onInfoContainer         = OngoingBlue,
    decorativeOrange        = OrangeAccent
)

val ExtendedDarkColors = ExtendedColors(
    successBannerBackground = SuccessBannerBgDark,
    onSuccessBannerText     = SuccessGreenDark,
    warningContainer        = WarningBgDark,
    onWarningContainer      = WarningOrangeDark,
    infoContainer           = InfoBgDark,
    onInfoContainer         = InfoBlueDark,
    decorativeOrange        = OrangeAccentDark
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
            val window = (view.context as? Activity)?.window ?: return@SideEffect
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
