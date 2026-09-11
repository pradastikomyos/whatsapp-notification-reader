package com.ridenotify.app.wa_reader.data.notification.parser

import com.ridenotify.app.wa_reader.model.NotificationSnapshot
import com.ridenotify.app.wa_reader.model.ParsedNotification

fun interface NotificationParser {
    fun parse(snapshot: NotificationSnapshot): ParsedNotification
}
