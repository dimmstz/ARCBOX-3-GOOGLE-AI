package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import kotlinx.coroutines.Dispatchers

/**
 * Custom Application class for Arcbox File Manager.
 * Configures an optimized, hardware-accelerated Coil ImageLoader with LRU Memory & Disk Caching
 * and built-in VideoFrameDecoder to ensure silky-smooth 60/120Hz scrolling performance.
 */
class ArcboxApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return try {
            val cacheFolder = java.io.File(cacheDir, "arcbox_thumbnails").apply { mkdirs() }
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .strongReferencesEnabled(true)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheFolder)
                        .maxSizeBytes(100L * 1024 * 1024)
                        .build()
                }
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .dispatcher(Dispatchers.IO)
                .allowHardware(true)
                .allowRgb565(true)
                .respectCacheHeaders(false)
                .crossfade(false)
                .build()
        } catch (e: Throwable) {
            ImageLoader.Builder(this)
                .crossfade(false)
                .build()
        }
    }
}
