package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SageGreen,
    onPrimary = ForestPineDark,
    primaryContainer = ForestPine,
    onPrimaryContainer = SageMuted,
    secondary = TerracottaLight,
    onSecondary = Color(0xFF491807),
    secondaryContainer = TerracottaDark,
    onSecondaryContainer = TerracottaMuted,
    tertiary = OchreLight,
    onTertiary = Color(0xFF402D0E),
    tertiaryContainer = OchreDark,
    onTertiaryContainer = SandSurfaceLight,
    background = SlateCanvasDark,
    onBackground = TextPrimaryDark,
    surface = SlateSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    outlineVariant = Color(0xFF2A3631)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestPine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8DF),
    onPrimaryContainer = ForestPineDark,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = TerracottaMuted,
    onSecondaryContainer = TerracottaDark,
    tertiary = OchreDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7EBD9),
    onTertiaryContainer = Color(0xFF4E3516),
    background = SandCanvasLight,
    onBackground = TextPrimaryLight,
    surface = SandSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SandSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
    outlineVariant = Color(0xFFDFD6C2)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep travel theme aesthetic cohesive
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TravelTypography,
        content = content
    )
}
