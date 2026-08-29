package com.example.ui.components

import androidx.compose.runtime.compositionLocalOf

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
