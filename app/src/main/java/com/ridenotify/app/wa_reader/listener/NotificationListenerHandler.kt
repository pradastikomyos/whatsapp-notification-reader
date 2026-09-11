package com.ridenotify.app.wa_reader.listener

import com.ridenotify.app.wa_reader.model.NotificationSnapshot

class NotificationListenerHandler(
    private val ingress: NotificationIngress,
) {
    fun notificationPosted(
        packageName: String,
        extractSnapshot: () -> NotificationSnapshot,
    ): Boolean {
        if (packageName !in ALLOWED_PACKAGES) return false
        val snapshot = try {
            extractSnapshot()
        } catch (_: RuntimeException) {
            return false
        }
        return ingress.trySubmit(snapshot)
    }

    companion object {
        val ALLOWED_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
