package com.ridenotify.app.wa_reader.model

@JvmInline
value class ConversationId(val value: String) {
    init {
        require(value.isNotBlank()) { "ConversationId must not be blank" }
    }
}

enum class ConversationType {
    DIRECT,
    GROUP,
}

enum class RidingState {
    ACTIVE,
    INACTIVE,
}
