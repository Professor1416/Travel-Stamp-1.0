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
import com.example.data.local.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = SageGreen,
    onPrimary = Color(0xFF0F1B16),
    primaryContainer = ForestPine,
    onPrimaryContainer = Color(0xFFD3E6DC),
    secondary = TerracottaLight,
    onSecondary = Color(0xFF381408),
    secondaryContainer = TerracottaDark,
    onSecondaryContainer = TerracottaMuted,
    tertiary = OchreLight,
    onTertiary = Color(0xFF332008),
    tertiaryContainer = OchreDark,
    onTertiaryContainer = OchreParchment,
    background = SlateCanvasDark,
    onBackground = TextPrimaryDark,
    surface = SlateSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    outlineVariant = Color(0xFF26322C)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestPine,
    onPrimary = Color.White,
    primaryContainer = SageParchment,
    onPrimaryContainer = ForestPineDark,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = TerracottaSoft,
    onSecondaryContainer = TerracottaDark,
    tertiary = OchreGold,
    onTertiary = Color.White,
    tertiaryContainer = OchreParchment,
    onTertiaryContainer = OchreDark,
    background = SandCanvasLight,
    onBackground = TextPrimaryLight,
    surface = SandSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SandSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
    outlineVariant = Color(0xFFECE4D4)
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false, // Preserve coherent authentic vintage travel palette
    content: @Composable () -> Unit,
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TravelTypography,
        content = content
    )
}
