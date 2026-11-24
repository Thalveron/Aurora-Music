package com.craftworks.music.ui.theme

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.tv.material3.MaterialTheme

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

private val AuroraTvDarkColors = androidx.tv.material3.darkColorScheme(
    primary = AuroraMint,
    onPrimary = Color(0xFF00382B),
    primaryContainer = AuroraMintDeep,
    onPrimaryContainer = Color(0xFFB4F8E2),
    inversePrimary = AuroraDayMint,
    secondary = AuroraBlue,
    onSecondary = Color(0xFF10205A),
    secondaryContainer = AuroraBlueDeep,
    onSecondaryContainer = Color(0xFFDDE4FF),
    tertiary = AuroraViolet,
    onTertiary = Color(0xFF2E1260),
    tertiaryContainer = AuroraVioletDeep,
    onTertiaryContainer = Color(0xFFEBDCFF),
    background = AuroraNight,
    onBackground = AuroraText,
    surface = AuroraSurface,
    onSurface = AuroraText,
    surfaceVariant = AuroraSurfaceHigh,
    onSurfaceVariant = AuroraTextMuted,
    surfaceTint = AuroraMint,
    inverseSurface = AuroraFocus,
    inverseOnSurface = Color(0xFF0B141C),
    border = AuroraOutline,
    borderVariant = AuroraOutlineSoft,
)

private val AuroraTvLightColors = androidx.tv.material3.lightColorScheme(
    primary = AuroraDayMint,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1E4),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF3F57B8),
    secondaryContainer = Color(0xFFDDE4FF),
    onSecondaryContainer = Color(0xFF0E1C52),
    background = AuroraDay,
    onBackground = AuroraDayText,
    surface = AuroraDaySurface,
    onSurface = AuroraDayText,
    surfaceVariant = AuroraDaySurfaceHigh,
    onSurfaceVariant = AuroraDayTextMuted,
    surfaceTint = AuroraDayMint,
    inverseSurface = Color(0xFF16202E),
    inverseOnSurface = Color(0xFFEFF4FB),
)

private val AuroraDarkColors = darkColorScheme(
    primary = AuroraMint,
    onPrimary = Color(0xFF00382B),
    primaryContainer = AuroraMintDeep,
    onPrimaryContainer = Color(0xFFB4F8E2),
    secondary = AuroraBlue,
    secondaryContainer = AuroraBlueDeep,
    tertiary = AuroraViolet,
    background = AuroraNight,
    onBackground = AuroraText,
    surface = AuroraSurface,
    onSurface = AuroraText,
    surfaceVariant = AuroraSurfaceHigh,
    onSurfaceVariant = AuroraTextMuted,
    surfaceContainer = AuroraSurface,
    surfaceContainerHigh = AuroraSurfaceHigh,
    surfaceContainerHighest = AuroraSurfaceHigh,
    outline = AuroraOutline,
    outlineVariant = AuroraOutlineSoft,
)

private val AuroraLightColors = lightColorScheme(
    primary = AuroraDayMint,
    background = AuroraDay,
    onBackground = AuroraDayText,
    surface = AuroraDaySurface,
    onSurface = AuroraDayText,
    surfaceVariant = AuroraDaySurfaceHigh,
    onSurfaceVariant = AuroraDayTextMuted,
)

private val AuroraTvShapes = androidx.tv.material3.Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            /*window.navigationBarColor = colorScheme.surfaceColorAtElevation(3.dp).toArgb()*/
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    if (isTv) {
        val colorScheme = if (darkTheme) AuroraTvDarkColors else AuroraTvLightColors

        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AuroraTvShapes,
            typography = androidx.tv.material3.Typography(),
        ) {
            // Text fields, dialogs and dividers come from the phone Material library, so it gets
            // the same palette instead of its purple defaults.
            androidx.compose.material3.MaterialTheme(
                colorScheme = if (darkTheme) AuroraDarkColors else AuroraLightColors,
                typography = Typography,
                content = content
            )
        }
    }
    else {
        val colorScheme = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

        MaterialExpressiveTheme (
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
            motionScheme = MotionScheme.expressive()
        )
    }
}