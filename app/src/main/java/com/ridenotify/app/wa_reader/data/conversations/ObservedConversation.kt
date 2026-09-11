package com.ridenotify.app.wa_reader.data.conversations

import com.ridenotify.app.wa_reader.model.ConversationId

/** Metadata-only group catalogue row. Message and attachment content never belongs here. */
data class ObservedConversation(
    val conversationId: ConversationId,
    val packageName: String,
    val displayTitle: String,
    val shortcutId: String?,
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long,
    val isSelected: Boolean,
    val collisionDetected: Boolean,
    val collisionCount: Int,
) {
    init {
        require(packageName.isNotBlank())
        require(displayTitle.isNotBlank())
        require(firstSeenAtMillis >= 0)
        require(lastSeenAtMillis >= firstSeenAtMillis)
        require(collisionCount >= 0)
        require(collisionDetected == (collisionCount > 0))
    }
}
/** The metadata available when a notification is observed. */
data class ConversationObservation(
    val packageName: String,
    val isGroupConversation: Boolean,
    val conversationTitle: String?,
    val title: String?,
    val shortcutId: String?,
    val senderKey: String?,
    val collisionEvidenceKey: String? = senderKey,
    val observedAtMillis: Long,
) {
    init {
        require(packageName.isNotBlank())
        require(observedAtMillis >= 0)
    }
}
