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
            primaryContainer = primaryColor.copy(alpha = 0.25f).compositeOver(SlateSurfaceDark),
            onPrimaryContainer = Color.White,
            secondary = primaryColor,
            onSecondary = Color.White,
            secondaryContainer = primaryColor.copy(alpha = 0.25f).compositeOver(SlateSurfaceDark),
            onSecondaryContainer = Color.White,
            tertiary = primaryColor,
            onTertiary = Color.White,
            tertiaryContainer = primaryColor.copy(alpha = 0.15f).compositeOver(SlateSurfaceDark),
            onTertiaryContainer = Color.White,
            background = SlateBackgroundDark,
            onBackground = SlateOnBackgroundDark,
            surface = SlateSurfaceDark,
            onSurface = SlateOnSurfaceDark,
            surfaceVariant = SlateSurfaceVariantDark,
            onSurfaceVariant = SlateOnSurfaceDark,
            outline = SlateBorderDark,
            surfaceContainer = SlateSurfaceDark
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.18f).compositeOver(Color.White),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor,
            onSecondary = Color.White,
            secondaryContainer = primaryColor.copy(alpha = 0.18f).compositeOver(Color.White),
            onSecondaryContainer = primaryColor,
            tertiary = primaryColor,
            onTertiary = Color.White,
            tertiaryContainer = primaryColor.copy(alpha = 0.12f).compositeOver(Color.White),
            onTertiaryContainer = primaryColor,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurface,
            outline = LightBorder,
            surfaceContainer = LightSurface
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
