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

enum class GroupReadMode {
    ALL_OBSERVED_GROUPS,
    SELECTED_GROUPS_ONLY,
    NO_GROUPS,
}

enum class RidingState {
    ACTIVE,
    INACTIVE,
}
