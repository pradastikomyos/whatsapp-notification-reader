package com.ridenotify.app.wa_reader.model

sealed interface ParsedNotification {
    data class Messages(
        val source: ParseSource,
        val items: List<ParsedMessage>,
    ) : ParsedNotification {
        init {
            require(items.isNotEmpty()) { "A parsed message result must contain at least one item" }
        }
    }

    data object Summary : ParsedNotification
    data object Call : ParsedNotification
    data object Security : ParsedNotification
    data object Attachment : ParsedNotification
    data object Redacted : ParsedNotification
    data class Unsupported(val reason: UnsupportedReason) : ParsedNotification
}

data class ParsedMessage(
    val conversationId: ConversationId,
    val conversationType: ConversationType,
    val conversationTitle: String?,
    val senderDisplayName: String?,
    val body: String,
    val postedAtMillis: Long,
) {
    init {
        require(body.isNotBlank()) { "Parsed message body must not be blank" }
        require(postedAtMillis >= 0) { "postedAtMillis must not be negative" }
    }
}

enum class ParseSource {
    MESSAGING_STYLE,
    CONVERSATION_METADATA,
    LEGACY_TEXT,
}

enum class UnsupportedReason {
    PACKAGE_NOT_ALLOWED,
    EMPTY_CONTENT,
    AMBIGUOUS_LEGACY_CONTENT,
    UNSUPPORTED_LOCALE,
    UNRECOGNIZED_NOTIFICATION,
}
