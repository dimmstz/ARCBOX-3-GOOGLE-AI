package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * High-fidelity real brand icons for Cloud Storage Providers
 * (Google Drive, Mega, Microsoft OneDrive, Dropbox, MediaFire)
 */

@Composable
fun CloudBrandIcon(
    provider: CloudProvider,
    modifier: Modifier = Modifier.size(24.dp)
) {
    when (provider) {
        CloudProvider.GOOGLE_DRIVE -> GoogleDriveBrandIcon(modifier = modifier)
        CloudProvider.MEGA -> MegaBrandIcon(modifier = modifier)
        CloudProvider.ONEDRIVE -> OneDriveBrandIcon(modifier = modifier)
        CloudProvider.DROPBOX -> DropboxBrandIcon(modifier = modifier)
        CloudProvider.MEDIAFIRE -> MediaFireBrandIcon(modifier = modifier)
        CloudProvider.WEBDAV -> WebDavBrandIcon(modifier = modifier)
    }
}

@Composable
fun CloudBrandIconByName(
    nameOrId: String,
    modifier: Modifier = Modifier.size(24.dp)
) {
    val lower = nameOrId.lowercase()
    when {
        lower.contains("drive") || lower.contains("google") -> GoogleDriveBrandIcon(modifier = modifier)
        lower.contains("mega") -> MegaBrandIcon(modifier = modifier)
        lower.contains("onedrive") || lower.contains("microsoft") || lower.contains("sky") -> OneDriveBrandIcon(modifier = modifier)
        lower.contains("dropbox") -> DropboxBrandIcon(modifier = modifier)
        lower.contains("mediafire") || lower.contains("mfire") -> MediaFireBrandIcon(modifier = modifier)
        lower.contains("webdav") || lower.contains("nextcloud") || lower.contains("owncloud") || lower.contains("servidor") -> WebDavBrandIcon(modifier = modifier)
        else -> GoogleDriveBrandIcon(modifier = modifier)
    }
}

@Composable
fun WebDavBrandIcon(
    modifier: Modifier = Modifier.size(24.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val davColor = Color(0xFF0082C9)

        drawRoundRect(
            color = davColor,
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.25f, h * 0.25f)
        )

        // Draw cloud outline & server dots
        val p = Path().apply {
            moveTo(w * 0.30f, h * 0.65f)
            lineTo(w * 0.70f, h * 0.65f)
            cubicTo(w * 0.85f, h * 0.65f, w * 0.85f, h * 0.45f, w * 0.75f, h * 0.42f)
            cubicTo(w * 0.75f, h * 0.28f, w * 0.55f, h * 0.25f, w * 0.48f, h * 0.35f)
            cubicTo(w * 0.40f, h * 0.32f, w * 0.25f, h * 0.40f, w * 0.28f, h * 0.55f)
            cubicTo(w * 0.22f, h * 0.58f, w * 0.22f, h * 0.65f, w * 0.30f, h * 0.65f)
            close()
        }
        drawPath(p, Color.White)
    }
}

/**
 * Authentic Google Drive Triangular 3-color Vector Logo
 */
@Composable
fun GoogleDriveBrandIcon(
    modifier: Modifier = Modifier.size(24.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Real Google Brand Colors
        val yellowColor = Color(0xFFFFBA00) // Top-right arm
        val greenColor = Color(0xFF00AC47)  // Bottom arm
        val blueColor = Color(0xFF2684FC)   // Left arm

        // Normalized coordinate points for Google Drive polygon geometry
        // 1. Yellow side (Top Right)
        val yellowPath = Path().apply {
            moveTo(w * 0.38f, h * 0.05f)
            lineTo(w * 0.62f, h * 0.05f)
            lineTo(w * 0.96f, h * 0.64f)
            lineTo(w * 0.72f, h * 0.64f)
            close()
        }
        drawPath(yellowPath, yellowColor)

        // 2. Green side (Bottom)
        val greenPath = Path().apply {
            moveTo(w * 0.28f, h * 0.64f)
            lineTo(w * 0.96f, h * 0.64f)
            lineTo(w * 0.84f, h * 0.85f)
            lineTo(w * 0.16f, h * 0.85f)
            close()
        }
        drawPath(greenPath, greenColor)

        // 3. Blue side (Left diagonal)
        val bluePath = Path().apply {
            moveTo(w * 0.04f, h * 0.64f)
            lineTo(w * 0.38f, h * 0.05f)
            lineTo(w * 0.50f, h * 0.26f)
            lineTo(w * 0.16f, h * 0.85f)
            close()
        }
        drawPath(bluePath, blueColor)
    }
}

/**
 * Authentic MEGA Red Circle with White M Vector Logo
 */
@Composable
fun MegaBrandIcon(
    modifier: Modifier = Modifier.size(24.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = kotlin.math.min(w, h) / 2f
        val center = Offset(w / 2f, h / 2f)

        // Red Circle
        drawCircle(
            color = Color(0xFFD9272E),
            radius = radius,
            center = center
        )

        // White M Symbol
        val mPath = Path().apply {
            val left = center.x - radius * 0.55f
            val right = center.x + radius * 0.55f
            val top = center.y - radius * 0.44f
            val bottom = center.y + radius * 0.44f
            val stemWidth = radius * 0.24f
            val innerBottom = center.y + radius * 0.04f

            // Outer Left
            moveTo(left, bottom)
            lineTo(left, top)
            lineTo(left + stemWidth, top)
            lineTo(center.x, innerBottom)
            lineTo(right - stemWidth, top)
            lineTo(right, top)
            lineTo(right, bottom)
            lineTo(right - stemWidth * 0.9f, bottom)
            lineTo(right - stemWidth * 0.9f, top + radius * 0.32f)
            lineTo(center.x, bottom - radius * 0.16f)
            lineTo(left + stemWidth * 0.9f, top + radius * 0.32f)
            lineTo(left + stemWidth * 0.9f, bottom)
            close()
        }
        drawPath(mPath, Color.White)
    }
}

/**
 * Authentic Microsoft OneDrive Fluent Cloud Vector Logo
 */
@Composable
fun OneDriveBrandIcon(
    modifier: Modifier = Modifier.size(24.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Back/Top Cloud (Light/Vibrant Blue)
        val backCloudPath = Path().apply {
            moveTo(w * 0.42f, h * 0.62f)
            cubicTo(w * 0.42f, h * 0.38f, w * 0.56f, h * 0.20f, w * 0.72f, h * 0.20f)
            cubicTo(w * 0.88f, h * 0.20f, w * 0.98f, h * 0.34f, w * 0.98f, h * 0.52f)
            cubicTo(w * 0.98f, h * 0.68f, w * 0.86f, h * 0.78f, w * 0.70f, h * 0.78f)
            lineTo(w * 0.42f, h * 0.78f)
            close()
        }
        drawPath(
            backCloudPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF00A4EF), Color(0xFF0078D4)),
                start = Offset(w * 0.5f, 0f),
                end = Offset(w, h)
            )
        )

        // 2. Front/Bottom Cloud (Deep Azure Blue)
        val frontCloudPath = Path().apply {
            moveTo(w * 0.04f, h * 0.70f)
            cubicTo(w * 0.04f, h * 0.56f, w * 0.14f, h * 0.46f, w * 0.28f, h * 0.46f)
            cubicTo(w * 0.32f, h * 0.32f, w * 0.46f, h * 0.22f, w * 0.60f, h * 0.22f)
            cubicTo(w * 0.74f, h * 0.22f, w * 0.86f, h * 0.34f, w * 0.86f, h * 0.50f)
            cubicTo(w * 0.94f, h * 0.54f, w * 0.98f, h * 0.62f, w * 0.98f, h * 0.72f)
            cubicTo(w * 0.98f, h * 0.86f, w * 0.86f, h * 0.92f, w * 0.72f, h * 0.92f)
            lineTo(w * 0.26f, h * 0.92f)
            cubicTo(w * 0.12f, h * 0.92f, w * 0.04f, h * 0.82f, w * 0.04f, h * 0.70f)
            close()
        }
        drawPath(
            frontCloudPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF28A8EA), Color(0xFF005A9E)),
                start = Offset(0f, h * 0.2f),
                end = Offset(w * 0.8f, h)
            )
        )
    }
}

/**
 * Authentic Dropbox 5-Rhombus Open Box Vector Logo
 */
@Composable
fun DropboxBrandIcon(
    modifier: Modifier = Modifier.size(24.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dropboxBlue = Color(0xFF0061FF)

        val cx = w / 2f
        val cy = h * 0.48f

        val rw = w * 0.26f
        val rh = h * 0.16f

        // Top Left Rhombus
        val tl = Path().apply {
            moveTo(cx - rw, cy - rh * 2f)
            lineTo(cx, cy - rh)
            lineTo(cx - rw, cy)
            lineTo(cx - rw * 2f, cy - rh)
            close()
        }
        drawPath(tl, dropboxBlue)

        // Top Right Rhombus
        val tr = Path().apply {
            moveTo(cx + rw, cy - rh * 2f)
            lineTo(cx + rw * 2f, cy - rh)
            lineTo(cx + rw, cy)
            lineTo(cx, cy - rh)
            close()
        }
        drawPath(tr, dropboxBlue)

        // Mid Left Rhombus
        val ml = Path().apply {
            moveTo(cx - rw, cy)
            lineTo(cx, cy + rh)
            lineTo(cx - rw, cy + rh * 2f)
            lineTo(cx - rw * 2f, cy + rh)
            close()
        }
        drawPath(ml, dropboxBlue)

        // Mid Right Rhombus
        val mr = Path().apply {
            moveTo(cx + rw, cy)
            lineTo(cx + rw * 2f, cy + rh)
            lineTo(cx + rw, cy + rh * 2f)
            lineTo(cx, cy + rh)
            close()
        }
        drawPath(mr, dropboxBlue)

        // Bottom Flap (Chevron)
        val bottomFlap = Path().apply {
            moveTo(cx, cy + rh * 1.15f)
            lineTo(cx + rw, cy + rh * 2.15f)
            lineTo(cx, cy + rh * 3.15f)
            lineTo(cx - rw, cy + rh * 2.15f)
            close()
        }
        drawPath(bottomFlap, dropboxBlue)
    }
}

/**
 * Authentic MediaFire Dual Flame Vector Logo
 */
@Composable
fun MediaFireBrandIcon(
    modifier: Modifier = Modifier.size(24.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val mfBlue = Color(0xFF1262D3)

        // Blue Badge Background
        drawRoundRect(
            color = mfBlue,
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.25f, h * 0.25f)
        )

        // White Dual-Flame Flame/M symbol
        val flamePath = Path().apply {
            // Left flame
            moveTo(w * 0.22f, h * 0.72f)
            cubicTo(w * 0.22f, h * 0.52f, w * 0.35f, h * 0.36f, w * 0.42f, h * 0.26f)
            cubicTo(w * 0.45f, h * 0.38f, w * 0.48f, h * 0.48f, w * 0.48f, h * 0.62f)
            cubicTo(w * 0.48f, h * 0.72f, w * 0.38f, h * 0.80f, w * 0.28f, h * 0.78f)
            close()

            // Right flame
            moveTo(w * 0.78f, h * 0.72f)
            cubicTo(w * 0.78f, h * 0.52f, w * 0.65f, h * 0.36f, w * 0.58f, h * 0.26f)
            cubicTo(w * 0.55f, h * 0.38f, w * 0.52f, h * 0.48f, w * 0.52f, h * 0.62f)
            cubicTo(w * 0.52f, h * 0.72f, w * 0.62f, h * 0.80f, w * 0.72f, h * 0.78f)
            close()
        }
        drawPath(flamePath, Color.White)
    }
}
