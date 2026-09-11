package com.ridenotify.spike.audiobackground

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Disposable spike-only listener (P0-T05). Mirrors only the concept from
 * ARCHITECTURE.md's package allowlist and fast-callback rule; it must never
 * be treated as production code. It never cancels or mutates WhatsApp
 * notifications and never reads/logs message text or sender names - only
 * the fact that a supported-package notification was observed.
 */
class SpikeNotificationListenerService : NotificationListenerService() {

    private var inProcessExperiment: AudioFocusTtsExperiment? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        ExperimentLog.append(this, "listener-connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ExperimentLog.append(this, "listener-disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") {
            return
        }

        val mode = TrialMode.read(applicationContext)
        ExperimentLog.append(applicationContext, "notification-observed package=${sbn.packageName} mode=$mode")

        when (mode) {
            TrialMode.NO_FGS -> {
                inProcessExperiment = AudioFocusTtsExperiment(applicationContext)
                inProcessExperiment?.run(trigger = "listener-callback", mode = TrialMode.NO_FGS) {
                    ExperimentLog.append(applicationContext, "no-fgs-trial-complete")
                }
            }

            TrialMode.WITH_FGS -> {
                val intent = Intent(applicationContext, SpikePlaybackService::class.java)
                    .putExtra(SpikePlaybackService.EXTRA_TRIGGER, "listener-callback")
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        applicationContext.startForegroundService(intent)
                    } else {
                        applicationContext.startService(intent)
                    }
                }.onFailure { error ->
                    ExperimentLog.append(
                        applicationContext,
                        "start-foreground-service-call-failed error=${error.javaClass.simpleName} message=${error.message} mode=WITH_FGS"
                    )
                }
            }
        }
    }
}
