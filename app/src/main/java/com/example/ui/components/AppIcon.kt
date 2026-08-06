package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.models.FileType

@Composable
fun AppIconImage(
    packageName: String?,
    apkPath: String,
    modifier: Modifier = Modifier.size(24.dp)
) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName, apkPath) {
        try {
            val pm = context.packageManager
            val drawable: Drawable? = if (!packageName.isNullOrEmpty()) {
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
            drawable?.let { drawableToBitmap(it).asImageBitmap() }
        } catch (_: Exception) {
            null
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
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
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
