package com.example.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver to handle playback widget actions from notification drawer.
 */
class MediaNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            ArcboxMediaNotificationManager.ACTION_PLAY_PAUSE -> {
                MediaPlaybackController.onTogglePlayPause?.invoke()
            }
            ArcboxMediaNotificationManager.ACTION_PREV -> {
                MediaPlaybackController.onPrevious?.invoke() ?: MediaPlaybackController.onRewind?.invoke()
            }
            ArcboxMediaNotificationManager.ACTION_NEXT -> {
                MediaPlaybackController.onNext?.invoke() ?: MediaPlaybackController.onForward?.invoke()
            }
            ArcboxMediaNotificationManager.ACTION_REWIND -> {
                MediaPlaybackController.onRewind?.invoke()
            }
            ArcboxMediaNotificationManager.ACTION_FORWARD -> {
                MediaPlaybackController.onForward?.invoke()
            }
            ArcboxMediaNotificationManager.ACTION_STOP -> {
                MediaPlaybackController.onStop?.invoke()
                if (context != null) {
                    ArcboxMediaNotificationManager.hideNotification(context)
                }
            }
        }
    }
}
