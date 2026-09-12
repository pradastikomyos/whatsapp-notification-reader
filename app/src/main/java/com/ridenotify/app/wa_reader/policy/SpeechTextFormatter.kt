package com.ridenotify.app.wa_reader.policy

import com.ridenotify.app.wa_reader.model.ConversationType
import com.ridenotify.app.wa_reader.model.ParsedMessage
import com.ridenotify.app.wa_reader.model.SpeechRequest

class SpeechTextFormatter(
    private val sanitizer: MessageSanitizer = MessageSanitizer(),
) {
    fun format(message: ParsedMessage, announceSender: Boolean): String? {
        if (message.conversationType == ConversationType.GROUP) return null
        val body = sanitizer.sanitize(message.body).takeIf(String::isNotEmpty) ?: return null
        if (!announceSender) return body.truncateForSpeech()

        val sender = message.senderDisplayName?.let(sanitizer::sanitize)?.takeIf(String::isNotEmpty)
        val introduction = sender?.let { "Pesan dari $it." }
        val combined = introduction?.let { "$it $body" } ?: body
        val truncated = combined.truncateForSpeech()
        val bodyStart = introduction?.length?.plus(1) ?: 0
        return if (truncated.length <= bodyStart) {
            body.truncateForSpeech()
        } else {
            truncated
        }
    }

    private fun String.truncateForSpeech(): String {
        if (length <= SpeechRequest.MAX_TEXT_LENGTH) return this
        var candidate = take(SpeechRequest.MAX_TEXT_LENGTH)
        if (candidate.last().isHighSurrogate() && this[SpeechRequest.MAX_TEXT_LENGTH].isLowSurrogate()) {
            candidate = candidate.dropLast(1)
        }
        if (this[SpeechRequest.MAX_TEXT_LENGTH].isWhitespace()) return candidate.trimEnd()
        val boundary = candidate.indexOfLast(Char::isWhitespace)
        return if (boundary > 0) candidate.take(boundary).trimEnd() else candidate
    }
}
