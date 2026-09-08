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

// Accent Colors (Chave de Destaque Arcbox - Saturadas, Vivas e Elétricas)
val ArcboxBlue = Color(0xFF0066FF)     // Azul Elétrico Puro & Saturado
val ArcboxEmerald = Color(0xFF00E676)  // Verde Esmeralda Néon
val ArcboxFuchsia = Color(0xFFFF007F)  // Pink / Fuchsia Elétrico
val ArcboxRose = Color(0xFFFF0055)     // Rosa / Vermelho Choque Radiante
val ArcboxAmber = Color(0xFFFF9100)    // Âmbar / Laranja Elétrico Bright
val ArcboxIndigo = Color(0xFF651FFF)   // Índigo / Violeta Puro Ultra Sat

// Cores por Categoria de Arquivo (Saturadas, Vivas e Marcantes)
val ColorFolder = Color(0xFF0066FF)      // Azul elétrico ultra saturado
val ColorImage = Color(0xFF00E676)       // Verde esmeralda vivo & néon
val ColorVideo = Color(0xFFFF9100)       // Âmbar / Laranja elétrico vibrante
val ColorAudio = Color(0xFFFF0055)       // Rosa / Magenta elétrico saturado
val ColorDocument = Color(0xFF651FFF)    // Índigo / Violeta elétrico
val ColorApk = Color(0xFFD500F9)         // Roxo / Violeta néon super vibrante
val ColorArchive = Color(0xFF00E5FF)     // Ciano / Turquesa elétrico radiante
val ColorCode = Color(0xFF00F0FF)        // Ciano código ultra brilhante
val ColorTrash = Color(0xFF8E9AAF)

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

private val linearGradientCache = java.util.concurrent.ConcurrentHashMap<Pair<Color, Float>, Brush>()
private val horizontalGradientCache = java.util.concurrent.ConcurrentHashMap<Pair<Color, Float>, Brush>()
private val badgeGradientCache = java.util.concurrent.ConcurrentHashMap<Color, Brush>()

fun getVibrantLinearGradient(baseColor: Color, lightFraction: Float = 0.32f): Brush {
    return linearGradientCache.getOrPut(Pair(baseColor, lightFraction)) {
        Brush.linearGradient(
            colors = listOf(baseColor, baseColor.lighterTone(lightFraction))
        )
    }
}

fun getVibrantHorizontalGradient(baseColor: Color, lightFraction: Float = 0.30f): Brush {
    return horizontalGradientCache.getOrPut(Pair(baseColor, lightFraction)) {
        Brush.horizontalGradient(
            colors = listOf(baseColor, baseColor.lighterTone(lightFraction))
        )
    }
}

fun getVibrantBadgeGradient(baseColor: Color): Brush {
    return badgeGradientCache.getOrPut(baseColor) {
        Brush.linearGradient(
            colors = listOf(
                baseColor.copy(alpha = 0.22f),
                baseColor.lighterTone(0.35f).copy(alpha = 0.08f)
            )
        )
    }
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
        color = Color(0xFF0066FF),       // Azul Elétrico Ultra Saturado
        darkColor = Color(0xFF00A2FF),   // Azul Elétrico Neon
        lightBg = Color(0xFFFFFFFF),
        lightBorder = Color(0xFFE2E8F0),
        lightSurfaceVariant = Color(0xFFF8FAFC)
    ),
    ROXO(
        label = "Roxo",
        color = Color(0xFF8A2BE2),       // Roxo Violeta Elétrico
        darkColor = Color(0xFFB042FF),   // Roxo Neon Vibrante
        lightBg = Color(0xFFFFFFFF),
        lightBorder = Color(0xFFE2E8F0),
        lightSurfaceVariant = Color(0xFFF8FAFC)
    ),
    PRETO(
        label = "Preto/Branco",
        color = Color(0xFF18181B),
        darkColor = Color(0xFFFAFAFA),
        lightBg = Color(0xFFFFFFFF),
        lightBorder = Color(0xFFE2E8F0),
        lightSurfaceVariant = Color(0xFFF8FAFC)
    ),
    PERSONALIZADO(
        label = "Personalizado",
        color = Color(0xFFFF5500),       // Laranja Elétrico
        darkColor = Color(0xFFFF6D00),   // Laranja Neon Radiante
        lightBg = Color(0xFFFFFFFF),
        lightBorder = Color(0xFFE2E8F0),
        lightSurfaceVariant = Color(0xFFF8FAFC)
    )
}

// 16 Cores Ultra Vivas, Saturadas e Elétricas
data class CustomColorPreset(
    val name: String,
    val hexValue: Long,
    val color: Color,
    val darkColor: Color
)

val PredefinedCustomColors = listOf(
    // Linha 1: Tons de Azul & Violeta (Ciano, Azul Claro, Azul Escuro, Roxo)
    CustomColorPreset("Ciano", 0xFF00B2FFL, Color(0xFF00B2FF), Color(0xFF00E5FF)),
    CustomColorPreset("Azul Claro", 0xFF0066FFL, Color(0xFF0066FF), Color(0xFF00A2FF)),
    CustomColorPreset("Azul Escuro", 0xFF0038FFL, Color(0xFF0038FF), Color(0xFF3D66FF)),
    CustomColorPreset("Roxo", 0xFF7000FFL, Color(0xFF7000FF), Color(0xFF9933FF)),

    // Linha 2: Tons de Lilás, Rosa & Vermelho (Lilás, Rosa Claro, Rosa Choque, Vermelho)
    CustomColorPreset("Lilás", 0xFFA800FFL, Color(0xFFA800FF), Color(0xFFC440FF)),
    CustomColorPreset("Rosa Claro", 0xFFFF007FL, Color(0xFFFF007F), Color(0xFFFF3399)),
    CustomColorPreset("Rosa Choque", 0xFFFF0055L, Color(0xFFFF0055), Color(0xFFFF1A66)),
    CustomColorPreset("Vermelho", 0xFFFF0000L, Color(0xFFFF0000), Color(0xFFFF2A2A)),

    // Linha 3: Tons de Vinho, Terra & Laranja (Vinho, Marrom, Laranja, Dourado)
    CustomColorPreset("Vinho", 0xFFC20038L, Color(0xFFC20038), Color(0xFFFF1A53)),
    CustomColorPreset("Marrom", 0xFFD44800L, Color(0xFFD44800), Color(0xFFFF6014)),
    CustomColorPreset("Laranja", 0xFFFF5500L, Color(0xFFFF5500), Color(0xFFFF6D00)),
    CustomColorPreset("Dourado", 0xFFFF9900L, Color(0xFFFF9900), Color(0xFFFFAB00)),

    // Linha 4: Tons de Amarelo & Verde (Amarelo, Verde Lima, Verde, Verde Água)
    CustomColorPreset("Amarelo", 0xFFFFC400L, Color(0xFFFFC400), Color(0xFFFFD600)),
    CustomColorPreset("Verde Lima", 0xFF66E000L, Color(0xFF66E000), Color(0xFF76FF03)),
    CustomColorPreset("Verde", 0xFF00C853L, Color(0xFF00C853), Color(0xFF00E676)),
    CustomColorPreset("Verde Água", 0xFF00BFA5L, Color(0xFF00BFA5), Color(0xFF1DE9B6))
)

fun findCustomColorPreset(hexValue: Long): CustomColorPreset? {
    return PredefinedCustomColors.find { it.hexValue == hexValue }
        ?: when (hexValue) {
            0xFFFF0000L, 0xFFFF3B30L, 0xFFE60000L -> PredefinedCustomColors.find { it.name == "Vermelho" }
            0xFFC20038L, 0xFF990033L, 0xFF9F1239L -> PredefinedCustomColors.find { it.name == "Vinho" }
            0xFFFF007FL, 0xFFFF69B4L, 0xFFFF5E8AL -> PredefinedCustomColors.find { it.name == "Rosa Claro" }
            0xFFFF0055L, 0xFFFF1493L -> PredefinedCustomColors.find { it.name == "Rosa Choque" }
            0xFF66E000L, 0xFF76FF03L, 0xFF70B800L -> PredefinedCustomColors.find { it.name == "Verde Lima" }
            0xFF00BFA5L, 0xFF00E5FFL, 0xFF0D9488L -> PredefinedCustomColors.find { it.name == "Verde Água" }
            0xFFFF5500L, 0xFFFF6D00L -> PredefinedCustomColors.find { it.name == "Laranja" }
            0xFFFFC400L, 0xFFFFD600L, 0xFFEAB308L -> PredefinedCustomColors.find { it.name == "Amarelo" }
            0xFFFF9900L, 0xFFFFAB00L, 0xFFD97706L -> PredefinedCustomColors.find { it.name == "Dourado" }
            0xFFD44800L, 0xFF92400EL, 0xFF8D4004L -> PredefinedCustomColors.find { it.name == "Marrom" }
            0xFF00B2FFL, 0xFF0284C7L, 0xFF00F0FFL -> PredefinedCustomColors.find { it.name == "Ciano" }
            0xFF0066FFL, 0xFF0080FFL, 0xFF007AFFL -> PredefinedCustomColors.find { it.name == "Azul Claro" }
            0xFF7000FFL, 0xFF7C3AEDL -> PredefinedCustomColors.find { it.name == "Roxo" }
            0xFFA800FFL, 0xFF9333EAL -> PredefinedCustomColors.find { it.name == "Lilás" }
            0xFF00C853L -> PredefinedCustomColors.find { it.name == "Verde" }
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


