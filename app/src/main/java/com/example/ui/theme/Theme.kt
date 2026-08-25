package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

@Composable
fun ArcboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentOption: AccentColorOption = AccentColorOption.AZUL_CLARO,
    content: @Composable () -> Unit
) {
    val primaryColor = if (darkTheme) accentOption.darkColor else accentOption.color

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.22f).compositeOver(SlateSurfaceDark),
            onPrimaryContainer = Color.White,
            secondary = primaryColor,
            onSecondary = Color.White,
            secondaryContainer = primaryColor.copy(alpha = 0.18f).compositeOver(SlateSurfaceDark),
            onSecondaryContainer = Color.White,
            tertiary = primaryColor,
            onTertiary = Color.White,
            tertiaryContainer = primaryColor.copy(alpha = 0.14f).compositeOver(SlateSurfaceDark),
            onTertiaryContainer = Color.White,
            background = SlateBackgroundDark,
            onBackground = SlateOnBackgroundDark,
            surface = SlateSurfaceDark,
            onSurface = SlateOnSurfaceDark,
            surfaceVariant = SlateSurfaceVariantDark,
            onSurfaceVariant = SlateOnSurfaceDark,
            outline = SlateBorderDark,
            surfaceContainer = ArcboxDarkCard,
            surfaceContainerHigh = Color(0xFF1A2030),
            surfaceContainerLow = Color(0xFF0E121D),
            surfaceBright = Color(0xFF20283A),
            surfaceDim = Color(0xFF080A10)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.12f).compositeOver(Color.White),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor,
            onSecondary = Color.White,
            secondaryContainer = primaryColor.copy(alpha = 0.10f).compositeOver(Color.White),
            onSecondaryContainer = primaryColor,
            tertiary = primaryColor,
            onTertiary = Color.White,
            tertiaryContainer = primaryColor.copy(alpha = 0.10f).compositeOver(Color.White),
            onTertiaryContainer = primaryColor,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurface,
            outline = LightBorder,
            surfaceContainer = Color(0xFFF9FAFD),
            surfaceContainerHigh = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF0F3F8),
            surfaceBright = Color.White,
            surfaceDim = Color(0xFFE7EBF2)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
