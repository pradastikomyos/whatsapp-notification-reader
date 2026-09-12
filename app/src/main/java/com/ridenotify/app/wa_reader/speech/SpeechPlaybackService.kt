package com.ridenotify.app.wa_reader.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.ridenotify.app.wa_reader.R
import kotlinx.coroutines.CompletableDeferred

/**
 * Short-lived API 35+ foreground host for speech playback.
 *
 * It contains no TTS or audio-focus logic. The coordinator first waits for its
 * [LocalBinder] to report promotion, then owns focus and TTS. It calls
 * [LocalBinder.stopAfterQueueDrain] when there is no queued or active speech.
 */
class SpeechPlaybackService : Service() {
    private val promotion = CompletableDeferred<PromotionState>()
    private val binder = LocalBinder()
    @Volatile
    private var promotionState = PromotionState.PENDING

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAfterQueueDrain()
            return START_NOT_STICKY
        }

        if (!promotion.isCompleted) {
            promote()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        if (!promotion.isCompleted) {
            promotionState = PromotionState.FAILED
            promotion.complete(PromotionState.FAILED)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun promote() {
        val promoted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            true
        } catch (_: RuntimeException) {
            false
        }

        promotionState = if (promoted) PromotionState.PROMOTED else PromotionState.FAILED
        promotion.complete(promotionState)
        if (!promoted) {
            stopSelf()
        }
    }

    private fun stopAfterQueueDrain() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.speech_playback_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun buildNotification(): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.sym_def_app_icon)
        .setContentTitle(getString(R.string.speech_playback_notification_title))
        .setContentText(getString(R.string.speech_playback_notification_text))
        .setOngoing(true)
        .setCategory(Notification.CATEGORY_SERVICE)
        .build()

    inner class LocalBinder : Binder() {
        suspend fun awaitPromotion(): PromotionState = promotion.await()

        fun currentPromotion(): PromotionState = promotionState

        fun stopAfterQueueDrain() {
            this@SpeechPlaybackService.stopAfterQueueDrain()
        }
    }

    enum class PromotionState {
        PENDING,
        PROMOTED,
        FAILED,
    }

    companion object {
        private const val ACTION_START = "com.ridenotify.app.wa_reader.action.START_SPEECH_PLAYBACK"
        private const val ACTION_STOP = "com.ridenotify.app.wa_reader.action.STOP_SPEECH_PLAYBACK"
        private const val CHANNEL_ID = "speech_playback"
        private const val NOTIFICATION_ID = 3_501

        fun startIntent(context: Context): Intent = Intent(context, SpeechPlaybackService::class.java)
            .setAction(ACTION_START)

        fun stopIntent(context: Context): Intent = Intent(context, SpeechPlaybackService::class.java)
            .setAction(ACTION_STOP)
    }
}
