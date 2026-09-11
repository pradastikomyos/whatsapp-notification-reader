package com.ridenotify.app.wa_reader.data.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.ridenotify.app.wa_reader.model.MessagingStyleMessageSnapshot
import com.ridenotify.app.wa_reader.model.MessagingStyleSnapshot
import com.ridenotify.app.wa_reader.model.NotificationSnapshot
import com.ridenotify.app.wa_reader.model.SenderSnapshot

class NotificationSnapshotExtractor {
    fun extract(statusBarNotification: StatusBarNotification): NotificationSnapshot {
        val notification = statusBarNotification.notification
        val extras = notification.extras

        return NotificationSnapshot(
            packageName = statusBarNotification.packageName,
            notificationKey = statusBarNotification.key,
            notificationId = statusBarNotification.id,
            postTimeMillis = statusBarNotification.postTime,
            groupKey = notification.group,
            title = extras.charSequenceString(Notification.EXTRA_TITLE),
            text = extras.charSequenceString(Notification.EXTRA_TEXT),
            bigText = extras.charSequenceString(Notification.EXTRA_BIG_TEXT),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.map(CharSequence::toString)
                .orEmpty(),
            subText = extras.charSequenceString(Notification.EXTRA_SUB_TEXT),
            summaryText = extras.charSequenceString(Notification.EXTRA_SUMMARY_TEXT),
            category = notification.category,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            conversationTitle = extras.charSequenceString(Notification.EXTRA_CONVERSATION_TITLE),
            shortcutId = notification.shortcutId,
            messagingStyle = extractMessagingStyle(notification),
        )
    }

    private fun extractMessagingStyle(notification: Notification): MessagingStyleSnapshot? {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?: return null
        return MessagingStyleSnapshot(
            userDisplayName = style.user.name?.toString(),
            isGroupConversation = style.isGroupConversation,
            conversationTitle = style.conversationTitle?.toString(),
            messages = style.messages.map { message ->
                MessagingStyleMessageSnapshot(
                    text = message.text?.toString(),
                    timestampMillis = message.timestamp.coerceAtLeast(0),
                    sender = extractSender(message),
                )
            },
        )
    }

    private fun extractSender(message: NotificationCompat.MessagingStyle.Message): SenderSnapshot? =
        message.person?.toSnapshot()

    private fun Person.toSnapshot() = SenderSnapshot(
        key = key,
        name = name?.toString(),
        isBot = isBot,
    )

    private fun android.os.Bundle.charSequenceString(key: String): String? =
        getCharSequence(key)?.toString()
}
