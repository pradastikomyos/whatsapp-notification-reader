package com.ridenotify.app.wa_reader.listener

import android.content.ComponentName
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Query abstraction: returns true if this app's notification listener is still
 * in the system-enabled listener set. Injected so tests never touch real Android APIs.
 */
fun interface EnabledListenerCheck {
    fun isEnabled(): Boolean
}

/**
 * Decides whether to request a rebind from the OS after a listener disconnection.
 * `requestRebind()` is only valid when the system severed the binding (e.g. process
 * restart, system hiccup) while the user's permission is still granted. If the user
 * revoked access, calling `requestRebind()` would have no effect and is misleading.
 * `startService()` must never be used — see ARCHITECTURE.md Background Rules.
 */
interface ListenerRebindController {
    /** Clears recovery backoff after Android has actually rebound the listener. */
    fun onConnected() {}

    /**
     * Called from `onListenerDisconnected`. Returns true if a rebind was requested;
     * returns false when access is unavailable, the attempt is throttled, or Android rejects it.
     */
    fun onDisconnected(componentName: ComponentName): Boolean
}

/**
 * Production implementation. Checks the enabled-listener package set via
 * [NotificationManagerCompat.getEnabledListenerPackages] to distinguish an OS-caused
 * disconnection from a user-revoked one, then calls the static
 * [NotificationListenerService.requestRebind] only when permitted.
 *
 * @param enabledCheck injected for testability; defaults to real package-set query.
 * @param clockMillis monotonic clock used to rate-limit rebind attempts.
 * @param minimumIntervalMillis minimum delay between rebind attempts.
 * @param requestRebind injected for testability; defaults to real static rebind call.
 */
class DefaultListenerRebindController(
    private val enabledCheck: EnabledListenerCheck,
    private val clockMillis: () -> Long = SystemClock::elapsedRealtime,
    private val minimumIntervalMillis: Long = DEFAULT_MINIMUM_INTERVAL_MILLIS,
    private val requestRebind: (ComponentName) -> Unit = { NotificationListenerService.requestRebind(it) },
) : ListenerRebindController {
    private var lastRequestAtMillis: Long? = null

    init {
        require(minimumIntervalMillis > 0) { "minimumIntervalMillis must be positive" }
    }

    @Synchronized
    override fun onConnected() {
        lastRequestAtMillis = null
    }

    @Synchronized
    override fun onDisconnected(componentName: ComponentName): Boolean {
        return try {
            if (!enabledCheck.isEnabled()) return false

            val now = clockMillis()
            val lastRequest = lastRequestAtMillis
            if (lastRequest != null && now - lastRequest < minimumIntervalMillis) return false

            // Record every attempt, including one rejected by the platform, to prevent storms.
            lastRequestAtMillis = now
            requestRebind(componentName)
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    companion object {
        const val DEFAULT_MINIMUM_INTERVAL_MILLIS = 30_000L

        /** Convenience factory used by [AppContainer]. */
        fun forContext(context: android.content.Context): DefaultListenerRebindController =
            DefaultListenerRebindController(
                enabledCheck = EnabledListenerCheck {
                    context.packageName in
                        NotificationManagerCompat.getEnabledListenerPackages(context)
                },
            )
    }
}
