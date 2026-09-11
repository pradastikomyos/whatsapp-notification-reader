package com.ridenotify.app.wa_reader.model

data class NotificationSnapshot(
    val packageName: String,
    val notificationKey: String,
    val notificationId: Int,
    val postTimeMillis: Long,
    val groupKey: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val textLines: List<String>,
    val subText: String?,
    val summaryText: String?,
    val category: String?,
    val isGroupSummary: Boolean,
    val conversationTitle: String?,
    val shortcutId: String?,
    val messagingStyle: MessagingStyleSnapshot?,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(notificationKey.isNotBlank()) { "notificationKey must not be blank" }
        require(postTimeMillis >= 0) { "postTimeMillis must not be negative" }
    }
}

data class MessagingStyleSnapshot(
    val userDisplayName: String?,
    val isGroupConversation: Boolean,
    val conversationTitle: String?,
    val messages: List<MessagingStyleMessageSnapshot>,
)

data class MessagingStyleMessageSnapshot(
    val text: String?,
    val timestampMillis: Long,
    val sender: SenderSnapshot?,
) {
    init {
        require(timestampMillis >= 0) { "timestampMillis must not be negative" }
    }
}

data class SenderSnapshot(
    val key: String?,
    val name: String?,
    val isBot: Boolean,
)
