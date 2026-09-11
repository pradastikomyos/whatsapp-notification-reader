package com.ridenotify.app.wa_reader.model

data class AppSettings(
    val readerEnabled: Boolean = false,
    val ridingState: RidingState = RidingState.INACTIVE,
    val readPrivateMessages: Boolean = true,
    val groupReadMode: GroupReadMode = GroupReadMode.NO_GROUPS,
    val selectedConversationIds: Set<ConversationId> = emptySet(),
    val announceSenderAndGroup: Boolean = true,
    val speechRate: Float = DEFAULT_SPEECH_RATE,
) {
    init {
        require(speechRate in MIN_SPEECH_RATE..MAX_SPEECH_RATE) {
            "speechRate must be between $MIN_SPEECH_RATE and $MAX_SPEECH_RATE"
        }
    }

    companion object {
        const val MIN_SPEECH_RATE = 0.5f
        const val DEFAULT_SPEECH_RATE = 1.0f
        const val MAX_SPEECH_RATE = 2.0f
        const val LOCALE_TAG = "id-ID"
    }
}
