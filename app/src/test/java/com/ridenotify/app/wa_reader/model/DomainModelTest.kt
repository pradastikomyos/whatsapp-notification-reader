package com.ridenotify.app.wa_reader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelTest {
    @Test
    fun settingsDefaultsAreConservative() {
        val settings = AppSettings()

        assertFalse(settings.readerEnabled)
        assertEquals(RidingState.INACTIVE, settings.ridingState)
        assertTrue(settings.readPrivateMessages)
        assertTrue(settings.announceSender)
        assertEquals(AppSettings.DEFAULT_SPEECH_RATE, settings.speechRate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankConversationIdIsRejected() {
        ConversationId(" ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun parsedMessagesCannotBeEmpty() {
        ParsedNotification.Messages(ParseSource.MESSAGING_STYLE, emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankParsedMessageBodyIsRejected() {
        ParsedMessage(
            conversationId = ConversationId("synthetic"),
            conversationType = ConversationType.DIRECT,
            conversationTitle = null,
            senderDisplayName = null,
            body = "",
            postedAtMillis = 1,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeSpeechRateIsRejected() {
        AppSettings(speechRate = 2.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedSpeechRequestIsRejected() {
        SpeechRequest(
            conversationId = ConversationId("synthetic"),
            text = "a".repeat(SpeechRequest.MAX_TEXT_LENGTH + 1),
            postedAtMillis = 1,
        )
    }
}
