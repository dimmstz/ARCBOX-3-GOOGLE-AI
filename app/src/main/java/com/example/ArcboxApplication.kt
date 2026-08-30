package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers

/**
 * Custom Application class for Arcbox File Manager.
 * Configures an optimized, hardware-accelerated Coil ImageLoader with LRU Memory & Disk Caching,
 * using standard Dispatchers for async decoding to guarantee fluid 60/120Hz scrolling without stutter.
 */
class ArcboxApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return try {
            val cacheFolder = java.io.File(cacheDir, "arcbox_thumbnails").apply {
                try { mkdirs() } catch (_: Exception) {}
            }
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
                        .maxSizeBytes(128L * 1024 * 1024)
                        .build()
                }
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .fetcherDispatcher(Dispatchers.IO)
                .decoderDispatcher(Dispatchers.Default)
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
