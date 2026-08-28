package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.models.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// High-capacity LRU Cache for decoded APK & App icons to maintain 120Hz scrolling
private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(16 * 1024, 64 * 1024) // 16MB to 64MB LRU cache
private val appIconMemoryCache = object : LruCache<String, Bitmap>(cacheSizeKb) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return (value.byteCount / 1024).coerceAtLeast(1)
    }
}
private val nullIconCache = LruCache<String, Boolean>(1000)
private val appIconDispatcher = Dispatchers.IO.limitedParallelism(2)

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
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Arcbox Logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun AppIconImage(
    packageName: String?,
    apkPath: String,
    modifier: Modifier = Modifier.size(24.dp)
) {
    val context = LocalContext.current
    val cacheKey = remember(packageName, apkPath) { packageName ?: apkPath }

    // Check fast memory cache synchronously
    var bitmap by remember(cacheKey) { mutableStateOf(appIconMemoryCache.get(cacheKey)) }

    // If cache miss, decode asynchronously on appIconDispatcher
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
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
