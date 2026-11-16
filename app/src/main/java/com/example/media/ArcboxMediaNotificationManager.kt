package com.example.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.MainActivity
import com.example.R
import com.example.util.PermissionHelper

/**
 * Manages media notification widget for background music and video playback.
 */
object ArcboxMediaNotificationManager {

    private const val CHANNEL_ID = "arcbox_media_playback_channel"
    private const val CHANNEL_NAME = "Reprodução de Mídia"
    private const val NOTIFICATION_ID = 10091

    const val ACTION_PLAY_PAUSE = "com.example.ACTION_MEDIA_PLAY_PAUSE"
    const val ACTION_PREV = "com.example.ACTION_MEDIA_PREV"
    const val ACTION_NEXT = "com.example.ACTION_MEDIA_NEXT"
    const val ACTION_STOP = "com.example.ACTION_MEDIA_STOP"
    const val ACTION_REWIND = "com.example.ACTION_MEDIA_REWIND"
    const val ACTION_FORWARD = "com.example.ACTION_MEDIA_FORWARD"

    private fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager?.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Controles de reprodução de áudio e vídeo em segundo plano"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }

    private fun getPendingIntent(context: Context, actionName: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MediaNotificationReceiver::class.java).apply {
            action = actionName
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun getContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun showPlaybackNotification(
        context: Context,
        title: String,
        subtitle: String,
        isPlaying: Boolean,
        isAudio: Boolean
    ) {
        if (!PermissionHelper.hasNotificationPermission(context)) {
            return
        }

        try {
            ensureChannelCreated(context)

            val prevIntent = getPendingIntent(context, ACTION_PREV, 101)
            val playPauseIntent = getPendingIntent(context, ACTION_PLAY_PAUSE, 102)
            val nextIntent = getPendingIntent(context, ACTION_NEXT, 103)
            val stopIntent = getPendingIntent(context, ACTION_STOP, 104)

            val playPauseIcon = if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
            val playPauseTitle = if (isPlaying) "Pausar" else "Reproduzir"

            val mediaTypeLabel = if (isAudio) "Música em Reprodução" else "Vídeo em Reprodução"

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_media_notification)
                .setContentTitle(title)
                .setContentText(subtitle.ifBlank { mediaTypeLabel })
                .setSubText(if (isAudio) "Áudio" else "Vídeo")
                .setContentIntent(getContentIntent(context))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_media_prev, "Anterior", prevIntent)
                .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
                .addAction(R.drawable.ic_media_next, "Próximo", nextIntent)
                .addAction(R.drawable.ic_media_stop, "Fechar", stopIntent)
                .setStyle(
                    MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(stopIntent)
                )

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideNotification(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
