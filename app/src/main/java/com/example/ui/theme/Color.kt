package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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
    val actualFraction = if (luminance() > 0.45f) fraction * 0.35f else fraction
    return Color(
        red = (red + (1f - red) * actualFraction).coerceIn(0f, 1f),
        green = (green + (1f - green) * actualFraction).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * actualFraction).coerceIn(0f, 1f),
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
        darkColor = Color(0xFFE2E8F0),
        lightBg = Color(0xFFFFFFFF),
        lightBorder = Color(0xFFE2E8F0),
        lightSurfaceVariant = Color(0xFFF8FAFC)
    ),
    PERSONALIZADO(
        label = "Personalizado",
        color = Color(0xFFFF5500),
        darkColor = Color(0xFFFB923C),
        lightBg = Color(0xFFFFFFFF),
        lightBorder = Color(0xFFE2E8F0),
        lightSurfaceVariant = Color(0xFFF8FAFC)
    )
}

// 16 Cores Modernas, Vivas e Equilibradas
data class CustomColorPreset(
    val name: String,
    val hexValue: Long,
    val color: Color,
    val darkColor: Color
)

val PredefinedCustomColors = listOf(
    // Linha 1: Tons de Azul & Violeta (Ciano, Azul Claro, Azul Escuro, Roxo)
    CustomColorPreset("Ciano", 0xFF0284C7L, Color(0xFF0284C7), Color(0xFF38BDF8)),
    CustomColorPreset("Azul Claro", 0xFF0080FFL, Color(0xFF0080FF), Color(0xFF38BDF8)),
    CustomColorPreset("Azul Escuro", 0xFF0055FFL, Color(0xFF0055FF), Color(0xFF60A5FA)),
    CustomColorPreset("Roxo", 0xFF7C3AEDL, Color(0xFF7C3AED), Color(0xFFA78BFA)),

    // Linha 2: Tons de Lilás, Rosa & Vermelho (Lilás, Rosa Claro, Rosa Choque, Vermelho)
    CustomColorPreset("Lilás", 0xFF9333EAL, Color(0xFF9333EA), Color(0xFFC084FC)),
    CustomColorPreset("Rosa Claro", 0xFFFF5E8AL, Color(0xFFFF5E8A), Color(0xFFF472B6)),
    CustomColorPreset("Rosa Choque", 0xFFFF007FL, Color(0xFFFF007F), Color(0xFFFF4081)),
    CustomColorPreset("Vermelho", 0xFFE60000L, Color(0xFFE60000), Color(0xFFFF3344)), // Vermelho Ferrari Puro (Rosso Corsa)

    // Linha 3: Tons de Vinho, Terra & Laranja (Vinho, Marrom, Laranja, Dourado)
    CustomColorPreset("Vinho", 0xFF9F1239L, Color(0xFF9F1239), Color(0xFFF43F5E)),   // Vinho Ruby Nobre & Vibrante
    CustomColorPreset("Marrom", 0xFF92400EL, Color(0xFF92400E), Color(0xFFF59E0B)),    // Bronze terracota quente e moderno
    CustomColorPreset("Laranja", 0xFFFF5500L, Color(0xFFFF5500), Color(0xFFFB923C)),   // Laranja elétrico vívido
    CustomColorPreset("Dourado", 0xFFD97706L, Color(0xFFD97706), Color(0xFFFCD34D)),   // Ouro imperial rico e radiante

    // Linha 4: Tons de Amarelo & Verde (Amarelo, Verde Lima, Verde, Verde Água)
    CustomColorPreset("Amarelo", 0xFFEAB308L, Color(0xFFEAB308), Color(0xFFFACC15)),   // Amarelo dourado vivo, encorpado e legível
    CustomColorPreset("Verde Lima", 0xFF70B800L, Color(0xFF70B800), Color(0xFFA3E635)), // Lima vibrante com contraste nítido
    CustomColorPreset("Verde", 0xFF00C853L, Color(0xFF00C853), Color(0xFF4ADE80)),
    CustomColorPreset("Verde Água", 0xFF0D9488L, Color(0xFF0D9488), Color(0xFF2DD4BF))  // Verde água tropical moderno
)

fun findCustomColorPreset(hexValue: Long): CustomColorPreset? {
    return PredefinedCustomColors.find { it.hexValue == hexValue }
        ?: when (hexValue) {
            0xFFFF3B30L -> PredefinedCustomColors.find { it.name == "Vermelho" }
            0xFF990033L -> PredefinedCustomColors.find { it.name == "Vinho" }
            0xFFFF69B4L -> PredefinedCustomColors.find { it.name == "Rosa Claro" }
            0xFFFF1493L -> PredefinedCustomColors.find { it.name == "Rosa Choque" }
            0xFF76FF03L -> PredefinedCustomColors.find { it.name == "Verde Lima" }
            0xFF00E5FFL -> PredefinedCustomColors.find { it.name == "Verde Água" }
            0xFFFF6D00L -> PredefinedCustomColors.find { it.name == "Laranja" }
            0xFFFFD600L -> PredefinedCustomColors.find { it.name == "Amarelo" }
            0xFFFFAB00L -> PredefinedCustomColors.find { it.name == "Dourado" }
            0xFF8D4004L -> PredefinedCustomColors.find { it.name == "Marrom" }
            0xFF00F0FFL -> PredefinedCustomColors.find { it.name == "Ciano" }
            else -> null
        }
}

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


