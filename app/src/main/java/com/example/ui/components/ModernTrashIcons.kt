package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modern, beautifully styled Empty Trash Hero illustration.
 * Features layered glowing rings, sleek geometric trash bin with floating lid,
 * subtle clean aura, and status indicator.
 */
@Composable
fun ModernEmptyTrashHero(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    // Subtle gentle floating animation
    val infiniteTransition = rememberInfiniteTransition(label = "trash_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    Box(
        modifier = modifier
            .size(size)
            .offset(y = floatOffset.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer ambient glow ring
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.12f),
                            primary.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Middle container with crisp white card & subtle border
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            ),
            shadowElevation = 4.dp,
            modifier = Modifier.size(size * 0.78f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Vector Canvas drawing of the modern trash can
                ModernTrashCanCanvas(
                    modifier = Modifier.size(size * 0.46f),
                    accentColor = primary
                )
            }
        }

        // Small modern "clean / verified" badge at the bottom-right
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.background),
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-size * 0.08f), y = (-size * 0.08f))
                .size(size * 0.28f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF10B981), // Emerald clean
                                Color(0xFF059669)
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.18f)
                )
            }
        }
    }
}

/**
 * Modern Vector Canvas of a sleek, contemporary trash can with rounded lid,
 * handle, tapered bin body, and vertical groove lines.
 */
@Composable
fun ModernTrashCanCanvas(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    binColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
    isOpenLid: Boolean = false
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val strokeWidth = w * 0.065f

        // 1. Bin body (Tapered rounded polygon / path)
        val bodyTop = h * 0.34f
        val bodyBottom = h * 0.92f
        val bodyTopLeft = w * 0.18f
        val bodyTopRight = w * 0.82f
        val bodyBottomLeft = w * 0.24f
        val bodyBottomRight = w * 0.76f
        val bodyCornerRadius = w * 0.08f

        val bodyPath = Path().apply {
            moveTo(bodyTopLeft, bodyTop)
            lineTo(bodyTopRight, bodyTop)
            lineTo(bodyBottomRight, bodyBottom - bodyCornerRadius)
            quadraticTo(bodyBottomRight, bodyBottom, bodyBottomRight - bodyCornerRadius, bodyBottom)
            lineTo(bodyBottomLeft + bodyCornerRadius, bodyBottom)
            quadraticTo(bodyBottomLeft, bodyBottom, bodyBottomLeft, bodyBottom - bodyCornerRadius)
            close()
        }

        // Fill body with subtle gradient
        drawPath(
            path = bodyPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.12f),
                    accentColor.copy(alpha = 0.25f)
                ),
                startY = bodyTop,
                endY = bodyBottom
            )
        )

        // Draw body outline
        drawPath(
            path = bodyPath,
            color = accentColor.copy(alpha = 0.9f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 2. Vertical inner grooves (Modern slatted look)
        val grooveTop = h * 0.44f
        val grooveBottom = h * 0.82f
        val grooveStroke = strokeWidth * 0.85f

        // Center line
        drawLine(
            color = accentColor.copy(alpha = 0.75f),
            start = Offset(w * 0.5f, grooveTop),
            end = Offset(w * 0.5f, grooveBottom),
            strokeWidth = grooveStroke,
            cap = StrokeCap.Round
        )

        // Left line
        drawLine(
            color = accentColor.copy(alpha = 0.55f),
            start = Offset(w * 0.35f, grooveTop + h * 0.02f),
            end = Offset(w * 0.37f, grooveBottom - h * 0.02f),
            strokeWidth = grooveStroke,
            cap = StrokeCap.Round
        )

        // Right line
        drawLine(
            color = accentColor.copy(alpha = 0.55f),
            start = Offset(w * 0.65f, grooveTop + h * 0.02f),
            end = Offset(w * 0.63f, grooveBottom - h * 0.02f),
            strokeWidth = grooveStroke,
            cap = StrokeCap.Round
        )

        // 3. Lid & Handle (Slightly tilted if open, sleek flat-rounded if closed)
        val lidY = if (isOpenLid) h * 0.22f else h * 0.26f
        val lidLeft = w * 0.10f
        val lidRight = w * 0.90f
        val lidHeight = h * 0.08f

        // Lid bar
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(lidLeft, lidY),
            size = Size(lidRight - lidLeft, lidHeight),
            cornerRadius = CornerRadius(lidHeight / 2f, lidHeight / 2f)
        )

        // Lid top handle
        val handleWidth = w * 0.28f
        val handleHeight = h * 0.12f
        val handleLeft = (w - handleWidth) / 2f
        val handleTop = lidY - handleHeight + h * 0.03f

        val handlePath = Path().apply {
            moveTo(handleLeft, lidY)
            lineTo(handleLeft, handleTop + handleHeight * 0.3f)
            quadraticTo(handleLeft, handleTop, handleLeft + handleHeight * 0.3f, handleTop)
            lineTo(handleLeft + handleWidth - handleHeight * 0.3f, handleTop)
            quadraticTo(handleLeft + handleWidth, handleTop, handleLeft + handleWidth, handleTop + handleHeight * 0.3f)
            lineTo(handleLeft + handleWidth, lidY)
        }

        drawPath(
            path = handlePath,
            color = accentColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

/**
 * Modern icon wrapper for delete / trash operations with styled container badge.
 */
@Composable
fun ModernDeleteIconBadge(
    modifier: Modifier = Modifier,
    isDanger: Boolean = true,
    size: Dp = 36.dp
) {
    val bgColor = if (isDanger) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    val tintColor = if (isDanger) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(bgColor)
            .border(
                1.dp,
                tintColor.copy(alpha = 0.25f),
                RoundedCornerShape(size * 0.3f)
            ),
        contentAlignment = Alignment.Center
    ) {
        ModernTrashCanCanvas(
            modifier = Modifier.size(size * 0.55f),
            accentColor = tintColor
        )
    }
}
