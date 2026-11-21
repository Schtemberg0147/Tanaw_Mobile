package com.tanaw.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
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
    val decorativeOrange: Color = DestinationOrange
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        successBannerBackground = SuccessBannerBg,
        onSuccessBannerText = SuccessGreen,
        warningContainer = ExpiringOrangeBg,
        onWarningContainer = ExpiringOrange,
        infoContainer = OngoingBlueBg,
        onInfoContainer = OngoingBlue,
        decorativeOrange = DestinationOrange
    )
}

val LightColorScheme = lightColorScheme(
    primary                 = NavyPrimary,
    onPrimary               = Color.White,
    primaryContainer        = PrimaryContainerLight,
    onPrimaryContainer      = NavyPrimary,
    secondary               = ElectricBlueSecondary,
    onSecondary             = Color.White,
    secondaryContainer      = SecondaryContainerLight,
    onSecondaryContainer    = OnSecondaryContainerBlue,
    tertiary                = DestinationOrange,
    onTertiary              = Color.White,
    background              = BackgroundGray,
    onBackground            = TextDark,
    surface                 = Color.White,
    onSurface               = TextDark,
    surfaceContainerHigh    = InputGray,
    surfaceContainerHighest = BorderGray,
    surfaceVariant          = InputGray,
    onSurfaceVariant        = HintGray,
    outline                 = OutlineGray,
    outlineVariant          = BorderGray,
    error                   = ExpiredRed,
    onError                 = Color.White,
    errorContainer          = ExpiredRedBg,
    onErrorContainer        = ExpiredRed
)

val ExtendedLightColors = ExtendedColors(
    successBannerBackground = SuccessBannerBg,
    onSuccessBannerText     = SuccessGreen,
    warningContainer        = ExpiringOrangeBg,
    onWarningContainer      = ExpiringOrange,
    infoContainer           = OngoingBlueBg,
    onInfoContainer         = OngoingBlue,
    decorativeOrange        = DestinationOrange
)

val CustomerLightColorScheme = LightColorScheme

val CustomerExtendedLightColors = ExtendedColors(
    successBannerBackground = SuccessBannerBg,
    onSuccessBannerText     = SuccessGreen,
    warningContainer        = InputGray,
    onWarningContainer      = TextDark,
    infoContainer           = SecondaryContainerLight,
    onInfoContainer         = OnSecondaryContainerBlue,
    decorativeOrange        = DestinationOrange
)

object TanawTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}

@Composable
fun TanawTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }
    }

    val windowSize = rememberTanawWindowSize()
    CompositionLocalProvider(
        LocalExtendedColors provides ExtendedLightColors,
        LocalTanawWindowSize provides windowSize
    ) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography  = Typography,
            content     = content
        )
    }
}

@Composable
fun TanawCustomerTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }
    }

    val windowSize = rememberTanawWindowSize()
    CompositionLocalProvider(
        LocalExtendedColors provides CustomerExtendedLightColors,
        LocalTanawWindowSize provides windowSize
    ) {
        MaterialTheme(
            colorScheme = CustomerLightColorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
