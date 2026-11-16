package com.example.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton controller to synchronize active audio/video playback state with background notification widget.
 */
object MediaPlaybackController {

    data class MediaInfo(
        val title: String,
        val subtitle: String,
        val isAudio: Boolean,
        val isPlaying: Boolean,
        val currentPositionMs: Long = 0L,
        val totalDurationMs: Long = 0L
    )

    private val _currentMedia = MutableStateFlow<MediaInfo?>(null)
    val currentMedia: StateFlow<MediaInfo?> = _currentMedia.asStateFlow()

    // Callbacks to interact with the currently active MediaPlayer / Video player
    var onTogglePlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
    var onRewind: (() -> Unit)? = null
    var onForward: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onSeekTo: ((Long) -> Unit)? = null

    fun updatePlayback(info: MediaInfo?) {
        _currentMedia.value = info
    }

    fun clear() {
        _currentMedia.value = null
        onTogglePlayPause = null
        onNext = null
        onPrevious = null
        onRewind = null
        onForward = null
        onStop = null
        onSeekTo = null
    }
}
