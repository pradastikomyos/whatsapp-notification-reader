package com.ridenotify.app.wa_reader.model

sealed interface ReadingDecision {
    data class Speak(val request: SpeechRequest) : ReadingDecision
    data object SkipReaderDisabled : ReadingDecision
    data object SkipRidingModeInactive : ReadingDecision
    data object SkipPrivateDisabled : ReadingDecision
    data object SkipGroupReadingDisabled : ReadingDecision
    data object SkipRedacted : ReadingDecision
    data object SkipUnsupported : ReadingDecision
    data object SkipTooOld : ReadingDecision
}

data class SpeechRequest(
    val conversationId: ConversationId,
    val text: String,
    val postedAtMillis: Long,
) {
    init {
        require(text.isNotBlank()) { "Speech text must not be blank" }
        require(text.length <= MAX_TEXT_LENGTH) {
            "Speech text must not exceed $MAX_TEXT_LENGTH characters"
        }
        require(postedAtMillis >= 0) { "postedAtMillis must not be negative" }
    }

    companion object {
        const val MAX_TEXT_LENGTH = 240
    }
}
