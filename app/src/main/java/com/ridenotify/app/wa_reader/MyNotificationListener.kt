package com.ridenotify.app.wa_reader

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ridenotify.app.wa_reader.data.notification.NotificationSnapshotExtractor
import com.ridenotify.app.wa_reader.listener.NotificationListenerHandler

// The FQCN is an upgrade contract because Android grants access by component; see ADR-001.
class MyNotificationListener : NotificationListenerService() {
    private val container get() = (application as WaReaderApplication).appContainer
    private val extractor = NotificationSnapshotExtractor()
    private val handler by lazy { NotificationListenerHandler(container.notificationIngress) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        container.listenerConnectionTracker.connected()
    }

    override fun onListenerDisconnected() {
        container.listenerConnectionTracker.disconnected()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification?) {
        val notification = statusBarNotification ?: return
        handler.notificationPosted(notification.packageName) {
            extractor.extract(notification)
        }
    }

    override fun onDestroy() {
        container.listenerConnectionTracker.disconnected()
        super.onDestroy()
    }
}
