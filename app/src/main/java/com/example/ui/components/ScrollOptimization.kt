package com.example.ui.components

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * CompositionLocal indicating whether user scrolling (drag or inertial fling) is currently active.
 *
 * Used across the Arcbox UI (FileGridList, AppIcon, TrashBin) to decouple scrolling physics
 * from disk I/O, BitmapFactory decodes, and video frame extraction.
 *
 * - When active (isScrolling = true): Non-cached thumbnail decoding is deferred. Lightweight
 *   vector placeholders render with zero CPU overhead, ensuring a locked 60/120Hz frame rate.
 * - When settled (isScrolling = false): Newly visible items smoothly trigger background decoding
 *   on a dedicated, low-priority worker thread pool.
 * - Items already resident in Coil's in-memory LRU cache display instantly with 0ms delay,
 *   even during high-velocity scrolling.
 */
val LocalScrollActive = compositionLocalOf { false }

object ArcboxScheduler {
    private val threadFactory = java.util.concurrent.ThreadFactory { runnable: Runnable ->
        Thread(runnable, "arcbox-bg-optimized-worker").apply {
            priority = Thread.NORM_PRIORITY - 1 // slightly lower priority to avoid starving UI thread
        }
    }

    // Dedicated Thread Pool for metadata and thumbnail processing, isolated from other Dispatchers
    @JvmField
    val metadataAndThumbnailDispatcher = java.util.concurrent.Executors.newFixedThreadPool(4, threadFactory)
        .asCoroutineDispatcher()
}
