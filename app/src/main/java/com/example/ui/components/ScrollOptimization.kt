package com.example.ui.components

import android.os.Process
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * CompositionLocal indicating whether user scrolling (drag or inertial fling) is currently active.
 */
val LocalScrollActive = compositionLocalOf { false }

object ArcboxScheduler {
    private val threadFactory = java.util.concurrent.ThreadFactory { runnable: Runnable ->
        Thread({
            // Set Linux process nice level to background (+10) to prevent starving UI and RenderThread
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (_: Throwable) {}
            runnable.run()
        }, "arcbox-bg-optimized-worker")
    }

    // Dedicated background thread pool for metadata and thumbnail processing
    @JvmField
    val metadataAndThumbnailDispatcher = java.util.concurrent.Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
        threadFactory
    ).asCoroutineDispatcher()
}
