package com.example.ui.components

import android.os.Process
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/**
 * Ultra-smooth hardware-accelerated Fast Scrollbar for LazyColumn.
 * Draws directly in the Draw phase with 0 recompositions or layout passes.
 */
fun Modifier.fastScrollbar(
    state: LazyListState,
    thumbColor: Color = Color(0xFF6750A4).copy(alpha = 0.7f)
): Modifier = composed {
    val isScrolling = state.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScrolling) 120 else 400),
        label = "ScrollbarAlpha"
    )

    drawWithContent {
        drawContent()
        if (alpha > 0.01f) {
            val layoutInfo = state.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo.size
            if (totalItems > visibleItems && totalItems > 0) {
                val scrollbarWidth = 4.5f * density
                val scrollbarRightPadding = 3f * density
                val viewHeight = size.height
                val thumbHeight = (viewHeight * (visibleItems.toFloat() / totalItems.toFloat()))
                    .coerceIn(36f * density, viewHeight * 0.6f)

                val firstIndex = state.firstVisibleItemIndex
                val scrollProgress = (firstIndex.toFloat() / (totalItems - visibleItems).coerceAtLeast(1).toFloat())
                    .coerceIn(0f, 1f)

                val thumbTop = scrollProgress * (viewHeight - thumbHeight)
                val thumbLeft = size.width - scrollbarWidth - scrollbarRightPadding

                drawRoundRect(
                    color = thumbColor.copy(alpha = thumbColor.alpha * alpha),
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(scrollbarWidth, thumbHeight),
                    cornerRadius = CornerRadius(scrollbarWidth / 2f, scrollbarWidth / 2f)
                )
            }
        }
    }
}

/**
 * Ultra-smooth hardware-accelerated Fast Scrollbar for LazyVerticalGrid.
 * Draws directly in the Draw phase with 0 recompositions or layout passes.
 */
fun Modifier.fastGridScrollbar(
    state: LazyGridState,
    thumbColor: Color = Color(0xFF6750A4).copy(alpha = 0.7f)
): Modifier = composed {
    val isScrolling = state.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (isScrolling) 1f else 0f,
        animationSpec = tween(durationMillis = if (isScrolling) 120 else 400),
        label = "GridScrollbarAlpha"
    )

    drawWithContent {
        drawContent()
        if (alpha > 0.01f) {
            val layoutInfo = state.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo.size
            if (totalItems > visibleItems && totalItems > 0) {
                val scrollbarWidth = 4.5f * density
                val scrollbarRightPadding = 3f * density
                val viewHeight = size.height
                val thumbHeight = (viewHeight * (visibleItems.toFloat() / totalItems.toFloat()))
                    .coerceIn(36f * density, viewHeight * 0.6f)

                val firstIndex = state.firstVisibleItemIndex
                val scrollProgress = (firstIndex.toFloat() / (totalItems - visibleItems).coerceAtLeast(1).toFloat())
                    .coerceIn(0f, 1f)

                val thumbTop = scrollProgress * (viewHeight - thumbHeight)
                val thumbLeft = size.width - scrollbarWidth - scrollbarRightPadding

                drawRoundRect(
                    color = thumbColor.copy(alpha = thumbColor.alpha * alpha),
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(scrollbarWidth, thumbHeight),
                    cornerRadius = CornerRadius(scrollbarWidth / 2f, scrollbarWidth / 2f)
                )
            }
        }
    }
}
