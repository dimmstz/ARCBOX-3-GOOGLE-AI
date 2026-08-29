package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Custom Application class for Arcbox File Manager.
 * Configures an optimized, hardware-accelerated Coil ImageLoader with LRU Memory & Disk Caching,
 * a dedicated low-priority background thread pool for thumbnail decoding, and built-in VideoFrameDecoder
 * to guarantee fluid 60/120Hz scrolling without stutter or thread starvation.
 */
class ArcboxApplication : Application(), ImageLoaderFactory {

    private val thumbnailExecutor by lazy {
        Executors.newFixedThreadPool(2) { runnable ->
            Thread {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                runnable.run()
            }.apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
                name = "arcbox-thumb-worker"
            }
        }
    }

    private val thumbnailDispatcher by lazy {
        thumbnailExecutor.asCoroutineDispatcher()
    }

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
                        .maxSizeBytes(100L * 1024 * 1024)
                        .build()
                }
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .dispatcher(thumbnailDispatcher)
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
