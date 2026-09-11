package com.ridenotify.app.wa_reader.policy

import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.ConversationType
import com.ridenotify.app.wa_reader.model.GroupReadMode
import com.ridenotify.app.wa_reader.model.ParsedMessage
import com.ridenotify.app.wa_reader.model.ParsedNotification
import com.ridenotify.app.wa_reader.model.ReadingDecision
import com.ridenotify.app.wa_reader.model.RidingState
import com.ridenotify.app.wa_reader.model.SpeechRequest

class ReadingPolicyEvaluator(
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val formatter: SpeechTextFormatter = SpeechTextFormatter(),
) {
    fun evaluate(notification: ParsedNotification, settings: AppSettings): List<ReadingDecision> =
        when (notification) {
            is ParsedNotification.Messages -> notification.items.map { evaluate(it, settings) }
            ParsedNotification.Redacted -> listOf(baseGate(settings) ?: ReadingDecision.SkipRedacted)
            else -> listOf(baseGate(settings) ?: ReadingDecision.SkipUnsupported)
        }

    fun evaluate(message: ParsedMessage, settings: AppSettings): ReadingDecision {
        baseGate(settings)?.let { return it }

        if (message.conversationType == ConversationType.DIRECT && !settings.readPrivateMessages) {
            return ReadingDecision.SkipPrivateDisabled
        }
        if (message.conversationType == ConversationType.GROUP && !groupIsAllowed(message, settings)) {
            return ReadingDecision.SkipGroupNotSelected
        }
        if (messageAgeMillis(message) > MAX_MESSAGE_AGE_MILLIS) {
            return ReadingDecision.SkipTooOld
        }
        val text = formatter.format(message, settings.announceSenderAndGroup)
            ?: return ReadingDecision.SkipUnsupported
        return ReadingDecision.Speak(
            SpeechRequest(message.conversationId, text, message.postedAtMillis),
        )
    }

    private fun baseGate(settings: AppSettings): ReadingDecision? = when {
        !settings.readerEnabled -> ReadingDecision.SkipReaderDisabled
        settings.ridingState == RidingState.INACTIVE -> ReadingDecision.SkipRidingModeInactive
        else -> null
    }

    private fun groupIsAllowed(message: ParsedMessage, settings: AppSettings): Boolean =
        when (settings.groupReadMode) {
            GroupReadMode.ALL_OBSERVED_GROUPS -> true
            GroupReadMode.SELECTED_GROUPS_ONLY -> message.conversationId in settings.selectedConversationIds
            GroupReadMode.NO_GROUPS -> false
        }

    private fun messageAgeMillis(message: ParsedMessage): Long {
        val now = clockMillis()
        return if (now <= message.postedAtMillis) 0 else now - message.postedAtMillis
    }

    companion object {
        const val MAX_MESSAGE_AGE_MILLIS = 180_000L
    }
}
