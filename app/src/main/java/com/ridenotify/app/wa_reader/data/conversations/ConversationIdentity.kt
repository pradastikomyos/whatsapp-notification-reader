package com.ridenotify.app.wa_reader.data.conversations

import com.ridenotify.app.wa_reader.model.ConversationId
import java.text.Normalizer

object ConversationIdentity {
    private val whitespace = Regex("\\s+")

    fun from(observation: ConversationObservation): ConversationId? {
        val packageName = observation.packageName.trim()
        val shortcutId = observation.shortcutId?.trim()?.takeIf(String::isNotEmpty)
        if (shortcutId != null) {
            return ConversationId("$packageName::shortcut::$shortcutId")
        }

        val normalizedTitle = displayTitle(observation) ?: return null
        val kind = if (observation.isGroupConversation) "group" else "direct"
        val senderKey = observation.senderKey?.trim()?.takeIf(String::isNotEmpty) ?: "unknown"
        return ConversationId("$packageName::fallback::$kind::$normalizedTitle::$senderKey")
    }

    fun displayTitle(observation: ConversationObservation): String? =
        (observation.conversationTitle?.takeIf(String::isNotBlank)
            ?: observation.title?.takeIf(String::isNotBlank))
            ?.let { Normalizer.normalize(it.trim().replace(whitespace, " "), Normalizer.Form.NFC) }
            ?.takeIf(String::isNotEmpty)
}
