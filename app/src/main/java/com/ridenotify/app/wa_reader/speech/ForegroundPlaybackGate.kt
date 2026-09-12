package com.ridenotify.app.wa_reader.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * A gate around the Android 15 foreground-service requirement for audio focus.
 *
 * A caller must await [ensurePromoted] before it requests audio focus. On API
 * 26-34 no foreground service is needed; on API 35+ this returns [Promoted]
 * only after [SpeechPlaybackService] has successfully called startForeground.
 */
interface ForegroundPlaybackGate {
    suspend fun ensurePromoted(): ForegroundPlaybackPromotion

    /** Releases the short-lived service after the speech queue drains. */
    suspend fun stop()
}

sealed interface ForegroundPlaybackPromotion {
    /** API 26-34 path. The caller may use its normal in-process focus flow. */
    data object NotRequired : ForegroundPlaybackPromotion

    /** API 35+ service promotion completed; requesting audio focus is now legal. */
    data object Promoted : ForegroundPlaybackPromotion

    /** Starting, binding, or promoting the service failed. The item must be skipped. */
    data object Failed : ForegroundPlaybackPromotion
}

/** Android implementation used by the application-scoped speech coordinator. */
class AndroidForegroundPlaybackGate(
    context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val promotionTimeoutMillis: Long = PROMOTION_TIMEOUT_MILLIS,
) : ForegroundPlaybackGate {
    private val applicationContext = context.applicationContext
    private val mutex = Mutex()
    private var activeConnection: ServiceConnection? = null
    private var activeBinder: SpeechPlaybackService.LocalBinder? = null

    override suspend fun ensurePromoted(): ForegroundPlaybackPromotion = mutex.withLock {
        if (sdkInt < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return@withLock ForegroundPlaybackPromotion.NotRequired
        }

        activeBinder?.let { binder ->
            return@withLock when (binder.currentPromotion()) {
                SpeechPlaybackService.PromotionState.PROMOTED -> ForegroundPlaybackPromotion.Promoted
                SpeechPlaybackService.PromotionState.FAILED -> ForegroundPlaybackPromotion.Failed
                SpeechPlaybackService.PromotionState.PENDING -> awaitExistingPromotion(binder)
            }
        }

        val intent = SpeechPlaybackService.startIntent(applicationContext)
        try {
            applicationContext.startForegroundService(intent)
        } catch (_: RuntimeException) {
            return@withLock ForegroundPlaybackPromotion.Failed
        }

        val connectionResult = CompletableDeferred<SpeechPlaybackService.LocalBinder?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                connectionResult.complete(service as? SpeechPlaybackService.LocalBinder)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                connectionResult.complete(null)
            }

            override fun onBindingDied(name: ComponentName) {
                connectionResult.complete(null)
            }

            override fun onNullBinding(name: ComponentName) {
                connectionResult.complete(null)
            }
        }

        val bound = try {
            applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (_: RuntimeException) {
            false
        }
        if (!bound) {
            applicationContext.stopService(intent)
            return@withLock ForegroundPlaybackPromotion.Failed
        }

        val binder = try {
            withTimeout(promotionTimeoutMillis) { connectionResult.await() }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (error: CancellationException) {
            safeUnbind(connection)
            applicationContext.stopService(intent)
            throw error
        }
        if (binder == null) {
            safeUnbind(connection)
            applicationContext.stopService(intent)
            return@withLock ForegroundPlaybackPromotion.Failed
        }

        val promotion = try {
            awaitExistingPromotion(binder)
        } catch (error: CancellationException) {
            // This connection belongs only to this in-flight promotion. Do not leave a
            // short-lived mediaPlayback FGS running when its owning speech work is cancelled.
            safeUnbind(connection)
            applicationContext.stopService(intent)
            throw error
        }

        return@withLock when (promotion) {
            ForegroundPlaybackPromotion.Promoted -> {
                activeConnection = connection
                activeBinder = binder
                ForegroundPlaybackPromotion.Promoted
            }
            else -> {
                safeUnbind(connection)
                applicationContext.stopService(intent)
                ForegroundPlaybackPromotion.Failed
            }
        }
    }

    override suspend fun stop() {
        mutex.withLock {
            activeBinder?.stopAfterQueueDrain()
            activeConnection?.let(::safeUnbind)
            activeBinder = null
            activeConnection = null
        }
    }

    private suspend fun awaitExistingPromotion(
        binder: SpeechPlaybackService.LocalBinder,
    ): ForegroundPlaybackPromotion = try {
        when (withTimeout(promotionTimeoutMillis) { binder.awaitPromotion() }) {
            SpeechPlaybackService.PromotionState.PROMOTED -> ForegroundPlaybackPromotion.Promoted
            SpeechPlaybackService.PromotionState.FAILED,
            SpeechPlaybackService.PromotionState.PENDING,
            -> ForegroundPlaybackPromotion.Failed
        }
    } catch (_: TimeoutCancellationException) {
        ForegroundPlaybackPromotion.Failed
    }

    private fun safeUnbind(connection: ServiceConnection) {
        try {
            applicationContext.unbindService(connection)
        } catch (_: IllegalArgumentException) {
            // A disconnect can win the race with cleanup; there is nothing left to release.
        }
    }

    private companion object {
        const val PROMOTION_TIMEOUT_MILLIS = 5_000L
    }
}
