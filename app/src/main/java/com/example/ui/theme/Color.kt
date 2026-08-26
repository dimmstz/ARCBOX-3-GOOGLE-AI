package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Neutros de Fundo (Slate / Dark Mode)
val ArcboxDarkBg = Color(0xFF0F172A)
val ArcboxDarkCard = Color(0xFF1E293B)
val ArcboxDarkBorder = Color(0xFF334155)

val ArcboxLightBg = Color(0xFFFFFFFF) // Pure white background
val ArcboxLightCard = Color(0xFFFFFFFF)
val ArcboxLightBorder = Color(0xFFD8E6F5)

// Accent Colors (Chave de Destaque Arcbox - Saturadas e Vivas)
val ArcboxBlue = Color(0xFF007AFF)
val ArcboxEmerald = Color(0xFF00C853)
val ArcboxFuchsia = Color(0xFFFF1493)
val ArcboxRose = Color(0xFFFF2D55)
val ArcboxAmber = Color(0xFFFF9500)
val ArcboxIndigo = Color(0xFF5856D6)

// Cores por Categoria de Arquivo (Saturadas, Vivas e Marcantes)
val ColorFolder = Color(0xFF007AFF)      // Azul elétrico saturado
val ColorImage = Color(0xFF00C853)       // Verde esmeralda vivo
val ColorVideo = Color(0xFFFF9500)       // Âmbar / Laranja vibrante
val ColorAudio = Color(0xFFFF2D55)       // Rosa / Magenta saturado
val ColorDocument = Color(0xFF5856D6)    // Índigo vivo
val ColorApk = Color(0xFFAF52DE)         // Roxo / Violeta vibrante
val ColorArchive = Color(0xFF00BCD4)     // Ciano / Turquesa vivo
val ColorCode = Color(0xFF00D2D3)        // Ciano código saturado
val ColorTrash = Color(0xFF718096)

// Funções utilitárias para gerar gradiente suave puxando para um tom mais claro
fun Color.lighterTone(fraction: Float = 0.28f): Color {
    return Color(
        red = (red + (1f - red) * fraction).coerceIn(0f, 1f),
        green = (green + (1f - green) * fraction).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * fraction).coerceIn(0f, 1f),
        alpha = alpha
    )
}

fun Color.darkerTone(fraction: Float = 0.2f): Color {
    return Color(
        red = (red * (1f - fraction)).coerceIn(0f, 1f),
        green = (green * (1f - fraction)).coerceIn(0f, 1f),
        blue = (blue * (1f - fraction)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

fun getVibrantLinearGradient(baseColor: Color, lightFraction: Float = 0.32f): Brush {
    return Brush.linearGradient(
        colors = listOf(baseColor, baseColor.lighterTone(lightFraction))
    )
}

fun getVibrantHorizontalGradient(baseColor: Color, lightFraction: Float = 0.30f): Brush {
    return Brush.horizontalGradient(
        colors = listOf(baseColor, baseColor.lighterTone(lightFraction))
    )
}

fun getVibrantBadgeGradient(baseColor: Color): Brush {
    return Brush.linearGradient(
        colors = listOf(
            baseColor.copy(alpha = 0.22f),
            baseColor.lighterTone(0.35f).copy(alpha = 0.08f)
        )
    )
}

// Accent Preset Colors
enum class AccentColorOption(
    val label: String,
    val color: Color,
    val darkColor: Color,
    val lightBg: Color,
    val lightBorder: Color,
    val lightSurfaceVariant: Color
) {
    AZUL_CLARO(
        label = "Azul Claro",
        color = Color(0xFF007AFF),
        darkColor = Color(0xFF38BDF8),
        lightBg = Color(0xFFFFFFFF),
        lightBorder = Color(0xFFE2E8F0),
        lightSurfaceVariant = Color(0xFFF8FAFC)
    ),
    ROXO(
        label = "Roxo",
        color = Color(0xFF7C3AED),
        darkColor = Color(0xFFA78BFA),
        lightBg = Color(0xFFFFFFFF),
        lightBorder = Color(0xFFE2E8F0),
        lightSurfaceVariant = Color(0xFFF8FAFC)
    ),
    PRETO(
        label = "Preto/Branco",
        color = Color(0xFF18181B),
        darkColor = Color(0xFF94A3B8),
        lightBg = Color(0xFFFFFFFF),
        lightBorder = Color(0xFFE2E8F0),
        lightSurfaceVariant = Color(0xFFF8FAFC)
    ),
    PERSONALIZADO(
        label = "Personalizado",
        color = Color(0xFFFF6D00),
        darkColor = Color(0xFFFB923C),
        lightBg = Color(0xFFFFFFFF),
        lightBorder = Color(0xFFE2E8F0),
        lightSurfaceVariant = Color(0xFFF8FAFC)
    )
}

// 16 Cores Básicas e Populares Conhecidas
data class CustomColorPreset(
    val name: String,
    val hexValue: Long,
    val color: Color,
    val darkColor: Color
)

val PredefinedCustomColors = listOf(
    // Linha 1: Tons de Rosa & Vermelho
    CustomColorPreset("Rosa Claro", 0xFFFF69B4L, Color(0xFFFF69B4), Color(0xFFFBCFE8)),
    CustomColorPreset("Rosa Choque", 0xFFFF1493L, Color(0xFFFF1493), Color(0xFFF472B6)),
    CustomColorPreset("Vermelho", 0xFFFF3B30L, Color(0xFFFF3B30), Color(0xFFF87171)),
    CustomColorPreset("Vinho", 0xFF990033L, Color(0xFF990033), Color(0xFFFDA4AF)),

    // Linha 2: Tons de Azul & Verde
    CustomColorPreset("Azul Escuro", 0xFF0055FFL, Color(0xFF0055FF), Color(0xFF60A5FA)),
    CustomColorPreset("Azul Claro", 0xFF0080FFL, Color(0xFF0080FF), Color(0xFF38BDF8)),
    CustomColorPreset("Verde", 0xFF00C853L, Color(0xFF00C853), Color(0xFF4ADE80)),
    CustomColorPreset("Verde Lima", 0xFF76FF03L, Color(0xFF76FF03), Color(0xFFA3E635)),

    // Linha 3: Tons de Verde Água, Laranja & Amarelo
    CustomColorPreset("Verde Água", 0xFF00E5FFL, Color(0xFF00E5FF), Color(0xFF2DD4BF)),
    CustomColorPreset("Laranja", 0xFFFF6D00L, Color(0xFFFF6D00), Color(0xFFFB923C)),
    CustomColorPreset("Amarelo", 0xFFFFD600L, Color(0xFFFFD600), Color(0xFFFACC15)),
    CustomColorPreset("Dourado", 0xFFFFAB00L, Color(0xFFFFAB00), Color(0xFFFCD34D)),

    // Linha 4: Tons de Roxo, Lilás, Marrom & Ciano
    CustomColorPreset("Roxo", 0xFF7C3AEDL, Color(0xFF7C3AED), Color(0xFFA78BFA)),
    CustomColorPreset("Lilás", 0xFFA855F7L, Color(0xFFA855F7), Color(0xFFC084FC)),
    CustomColorPreset("Marrom", 0xFF8D4004L, Color(0xFF8D4004), Color(0xFFD97706)),
    CustomColorPreset("Ciano", 0xFF00F0FFL, Color(0xFF00F0FF), Color(0xFF67E8F9))
)

// Clean Minimalism Dark Theme Colors (Zinc/Slate #09090B canvas)
val SlateBackgroundDark = Color(0xFF09090B)
val SlateSurfaceDark = Color(0xFF18181B)
val SlateSurfaceVariantDark = Color(0xFF27272A)
val SlateOnBackgroundDark = Color(0xFFFAFAFA)
val SlateOnSurfaceDark = Color(0xFFF4F4F5)
val SlateBorderDark = Color(0xFF27272A)

// Light Theme Colors (Branco Gelo com leve tom de azul claro)
val LightBackground = ArcboxLightBg
val LightSurface = ArcboxLightCard
val LightSurfaceVariant = Color(0xFFE2EFFD)
val LightOnBackground = Color(0xFF0F172A)
val LightOnSurface = Color(0xFF1E293B)
val LightBorder = ArcboxLightBorder

// File Type Specific Category Colors
val FileColorFolder = ColorFolder
val FileColorImage = ColorImage
val FileColorVideo = ColorVideo
val FileColorAudio = ColorAudio
val FileColorDocument = ColorDocument
val FileColorApk = ColorApk
val FileColorArchive = ColorArchive
val FileColorCode = ColorCode
val FileColorTrash = ColorTrash


