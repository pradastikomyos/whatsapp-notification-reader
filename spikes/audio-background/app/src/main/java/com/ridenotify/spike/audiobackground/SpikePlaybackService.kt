package com.ridenotify.spike.audiobackground

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Disposable spike-only short-lived mediaPlayback foreground service
 * (P0-T05). Exists only to measure whether it can legally be started from an
 * actual NotificationListenerService callback on the approved API levels,
 * and to compare completion behavior against the NO_FGS trial mode.
 *
 * Must never be referenced by the production `app` module.
 */
class SpikePlaybackService : Service() {

    private var experiment: AudioFocusTtsExperiment? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trigger = intent?.getStringExtra(EXTRA_TRIGGER) ?: "unknown"

        val startedForeground = runCatching {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        if (startedForeground.isFailure) {
            ExperimentLog.append(
                this,
                "fgs-start-failed error=${startedForeground.exceptionOrNull()?.javaClass?.simpleName} mode=WITH_FGS"
            )
            stopSelf()
            return START_NOT_STICKY
        }
        ExperimentLog.append(this, "fgs-start-succeeded mode=WITH_FGS")

        experiment = AudioFocusTtsExperiment(this)
        experiment?.run(trigger = trigger, mode = TrialMode.WITH_FGS) {
            ExperimentLog.append(this, "fgs-stop-on-drain mode=WITH_FGS")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio spike playback",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio spike running")
            .setContentText("Speaking a synthetic test phrase")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .build()

    companion object {
        const val EXTRA_TRIGGER = "trigger"
        private const val CHANNEL_ID = "spike_playback"
        private const val NOTIFICATION_ID = 42
    }
}
