package com.example.ui.theme

import android.app.Activity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun ArcboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentOption: AccentColorOption = AccentColorOption.AZUL_CLARO,
    customColorHex: Long = 0xFF4F46E5L,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    val isPretoTheme = accentOption == AccentColorOption.PRETO

    val primaryColor = if (accentOption == AccentColorOption.PERSONALIZADO) {
        val preset = PredefinedCustomColors.find { it.hexValue == customColorHex }
        if (preset != null) {
            if (darkTheme) preset.darkColor else preset.color
        } else {
            Color(customColorHex)
        }
    } else if (isPretoTheme) {
        if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF18181B)
    } else {
        if (darkTheme) accentOption.darkColor else accentOption.color
    }

    val contentOnPrimary = if (isPretoTheme && darkTheme) {
        Color(0xFF09090B)
    } else Color.White

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = contentOnPrimary,
            primaryContainer = primaryColor.copy(alpha = 0.25f).compositeOver(SlateSurfaceDark),
            onPrimaryContainer = contentOnPrimary,
            secondary = primaryColor,
            onSecondary = contentOnPrimary,
            secondaryContainer = primaryColor.copy(alpha = 0.25f).compositeOver(SlateSurfaceDark),
            onSecondaryContainer = contentOnPrimary,
            tertiary = primaryColor,
            onTertiary = contentOnPrimary,
            tertiaryContainer = primaryColor.copy(alpha = 0.15f).compositeOver(SlateSurfaceDark),
            onTertiaryContainer = contentOnPrimary,
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
            onPrimary = contentOnPrimary,
            primaryContainer = primaryColor.copy(alpha = 0.06f).compositeOver(Color.White),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor,
            onSecondary = contentOnPrimary,
            secondaryContainer = primaryColor.copy(alpha = 0.06f).compositeOver(Color.White),
            onSecondaryContainer = primaryColor,
            tertiary = primaryColor,
            onTertiary = contentOnPrimary,
            tertiaryContainer = primaryColor.copy(alpha = 0.05f).compositeOver(Color.White),
            onTertiaryContainer = primaryColor,
            background = Color.White,
            onBackground = LightOnBackground,
            surface = Color.White,
            onSurface = LightOnSurface,
            surfaceVariant = Color(0xFFF8FAFC),
            onSurfaceVariant = LightOnSurface,
            outline = Color(0xFFE2E8F0),
            surfaceContainer = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainerHigh = Color.White
        )
    }

    // Adaptive & Safe Font Scaling:
    // Prevents extreme system font scales (e.g. 1.3x, 1.5x, 2.0x) from breaking cards, rows, and buttons,
    // while keeping typography readable, accessible, and perfectly aligned across all Android devices.
    val currentDensity = LocalDensity.current
    val safeFontScale = currentDensity.fontScale.coerceIn(0.85f, 1.15f)
    val customDensity = Density(
        density = currentDensity.density,
        fontScale = safeFontScale
    )

    CompositionLocalProvider(LocalDensity provides customDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
