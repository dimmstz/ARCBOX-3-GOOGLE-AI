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
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // Allocate 30% of available app memory for fast thumbnail caching
                    .maxSizePercent(0.30)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("arcbox_thumbnails"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB disk cache
                    .build()
            }
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .fetcherDispatcher(Dispatchers.IO.limitedParallelism(6))
            .decoderDispatcher(Dispatchers.IO.limitedParallelism(3))
            .transformationDispatcher(Dispatchers.Default.limitedParallelism(2))
            .allowHardware(false)
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }
}
