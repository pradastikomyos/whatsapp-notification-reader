package com.ridenotify.app.wa_reader

import android.service.notification.NotificationListenerService

// The FQCN is an upgrade contract because Android grants access by component; see ADR-001.
class MyNotificationListener : NotificationListenerService()
