package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Neutros de Fundo (Slate / Dark Mode)
val ArcboxDarkBg = Color(0xFF0F172A)
val ArcboxDarkCard = Color(0xFF1E293B)
val ArcboxDarkBorder = Color(0xFF334155)

val ArcboxLightBg = Color(0xFFF0F7FF) // Branco Gelo: branco com leve tom de azul claro
val ArcboxLightCard = Color(0xFFFFFFFF)
val ArcboxLightBorder = Color(0xFFD8E6F5)

// Accent Colors (Chave de Destaque Arcbox)
val ArcboxBlue = Color(0xFF2563EB)
val ArcboxEmerald = Color(0xFF059669)
val ArcboxFuchsia = Color(0xFFC026D3)
val ArcboxRose = Color(0xFFE11D48)
val ArcboxAmber = Color(0xFFD97706)
val ArcboxIndigo = Color(0xFF4F46E5)

// Cores por Categoria de Arquivo
val ColorFolder = Color(0xFF3B82F6)
val ColorImage = Color(0xFF10B981)
val ColorVideo = Color(0xFFF59E0B)
val ColorAudio = Color(0xFFEC4899)
val ColorDocument = Color(0xFF6366F1)
val ColorApk = Color(0xFF8B5CF6)
val ColorArchive = Color(0xFF14B8A6)

// Accent Preset Colors
enum class AccentColorOption(val label: String, val color: Color, val darkColor: Color) {
    AZUL_CLARO("Azul Claro", Color(0xFF0284C7), Color(0xFF38BDF8)),
    ROSA("Rosa", Color(0xFFDB2777), Color(0xFFF472B6)),
    AMARELO("Amarelo", Color(0xFFCA8A04), Color(0xFFFACC15)),
    VERMELHO("Vermelho", Color(0xFFDC2626), Color(0xFFF87171)),
    VERDE_LIMA("Verde Lima", Color(0xFF65A30D), Color(0xA3E635)),
    PRETO("Preto", Color(0xFF18181B), Color(0xFF71717A)),
    BRANCO_GELO("Branco Gelo", Color(0xFF0EA5E9), Color(0xFF7DD3FC))
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
val FileColorCode = Color(0xFF06B6D4)
val FileColorTrash = Color(0xFF64748B)


