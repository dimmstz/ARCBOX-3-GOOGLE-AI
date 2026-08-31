package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.models.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

// High-capacity LRU Cache for decoded APK & App icons to maintain 120Hz scrolling
private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(16 * 1024, 64 * 1024) // 16MB to 64MB LRU cache
private val appIconMemoryCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return (value.byteCount / 1024).coerceAtLeast(1)
    }
}
private val nullIconCache = LruCache<String, Boolean>(1000)
private val appIconDispatcher = Dispatchers.IO

@Composable
fun ArcboxLogoIcon(
    modifier: Modifier = Modifier.size(40.dp),
    backgroundColor: Color = Color.Transparent,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val strokeW = (w * 0.13f).coerceAtLeast(2f)

            // Dynamic Arch gradient (Cyan -> Blue -> Indigo -> Violet)
            val archBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0099FF),
                    Color(0xFF0284C7),
                    Color(0xFF6366F1),
                    Color(0xFF8B5CF6)
                ),
                start = Offset(w * 0.2f, h * 0.8f),
                end = Offset(w * 0.8f, h * 0.8f)
            )

            // Left Leg of Arch 'A'
            drawLine(
                brush = archBrush,
                start = Offset(w * 0.24f, h * 0.78f),
                end = Offset(w * 0.50f, h * 0.22f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // Right Leg of Arch 'A'
            drawLine(
                brush = archBrush,
                start = Offset(w * 0.50f, h * 0.22f),
                end = Offset(w * 0.76f, h * 0.78f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // Storage Folder Tab (Cyan/Blue)
            val folderPath = Path().apply {
                moveTo(w * 0.36f, h * 0.50f)
                lineTo(w * 0.50f, h * 0.50f)
                lineTo(w * 0.54f, h * 0.54f)
                lineTo(w * 0.66f, h * 0.54f)
                lineTo(w * 0.66f, h * 0.62f)
                lineTo(w * 0.34f, h * 0.62f)
                lineTo(w * 0.34f, h * 0.52f)
                close()
            }
            drawPath(
                path = folderPath,
                color = Color(0xFF0284C7)
            )

            // Clean Drawer Box
            val boxLeft = w * 0.31f
            val boxTop = h * 0.58f
            val boxWidth = w * 0.38f
            val boxHeight = h * 0.22f
            val cornerR = CornerRadius(w * 0.04f, w * 0.04f)

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                cornerRadius = cornerR
            )

            // Drawer Box subtle outline
            drawRoundRect(
                color = Color(0xFFCBD5E1),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                cornerRadius = cornerR,
                style = Stroke(width = (w * 0.02f).coerceAtLeast(1f))
            )

            // Drawer Handle
            val handleWidth = w * 0.12f
            val handleHeight = h * 0.035f
            drawRoundRect(
                color = Color(0xFF94A3B8),
                topLeft = Offset(w * 0.5f - handleWidth / 2f, boxTop + boxHeight * 0.45f),
                size = Size(handleWidth, handleHeight),
                cornerRadius = CornerRadius(handleHeight / 2f, handleHeight / 2f)
            )
        }
    }
}

@Composable
fun AppIconImage(
    packageName: String?,
    apkPath: String,
    modifier: Modifier = Modifier.size(24.dp)
) {
    val context = LocalContext.current
    val isScrolling = LocalScrollActive.current
    val cacheKey = remember(packageName, apkPath) { packageName ?: apkPath }

    // Check fast memory cache synchronously (0ms instant lookup)
    var bitmap by remember(cacheKey) { mutableStateOf(appIconMemoryCache.get(cacheKey)) }

    // If cache miss, decode asynchronously on background worker
    if (bitmap == null && nullIconCache.get(cacheKey) != true) {
        LaunchedEffect(cacheKey) {
            val decoded = withContext(appIconDispatcher) {
                try {
                    val pm = context.packageManager
                    val drawable: Drawable? = if (apkPath.endsWith("Arcbox_v1.0.apk", ignoreCase = true)) {
                        try {
                            pm.getApplicationIcon(context.packageName)
                        } catch (_: Exception) {
                            null
                        }
                    } else if (!packageName.isNullOrEmpty()) {
                        try {
                            pm.getApplicationIcon(packageName)
                        } catch (_: Exception) {
                            val pkgInfo = pm.getPackageArchiveInfo(apkPath, 0)
                            pkgInfo?.applicationInfo?.let { appInfo ->
                                appInfo.sourceDir = apkPath
                                appInfo.publicSourceDir = apkPath
                                appInfo.loadIcon(pm)
                            }
                        }
                    } else {
                        val pkgInfo = pm.getPackageArchiveInfo(apkPath, 0)
                        pkgInfo?.applicationInfo?.let { appInfo ->
                            appInfo.sourceDir = apkPath
                            appInfo.publicSourceDir = apkPath
                            appInfo.loadIcon(pm)
                        }
                    }
                    if (drawable != null) {
                        val bmp = drawableToBitmap(drawable)
                        appIconMemoryCache.put(cacheKey, bmp)
                        bmp
                    } else {
                        nullIconCache.put(cacheKey, true)
                        null
                    }
                } catch (_: Exception) {
                    nullIconCache.put(cacheKey, true)
                    null
                }
            }
            if (decoded != null) {
                bitmap = decoded
            }
        }
    }

    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(
            Icons.Default.Android,
            contentDescription = null,
            tint = FileType.APK.getCategoryColor(),
            modifier = modifier
        )
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
        val src = drawable.bitmap
        if (src.width in 48..128 && src.height in 48..128) {
            return src
        }
        return Bitmap.createScaledBitmap(src, 72, 72, true)
    }
    val w = if (drawable.intrinsicWidth in 1..256) drawable.intrinsicWidth else 72
    val h = if (drawable.intrinsicHeight in 1..256) drawable.intrinsicHeight else 72
    val targetW = w.coerceIn(48, 96)
    val targetH = h.coerceIn(48, 96)
    val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
