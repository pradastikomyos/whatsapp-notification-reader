package com.ridenotify.app.wa_reader.data.notification

import com.ridenotify.app.wa_reader.model.ConversationId
import com.ridenotify.app.wa_reader.model.ConversationType
import com.ridenotify.app.wa_reader.model.ParsedMessage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotificationDeduplicatorTest {
    private var now = 1_000L

    @Test
    fun exactCallbackRepeatIsSuppressed() {
        val deduplicator = deduplicator()
        val message = message(body = "Hello", postedAt = 100L)

        assertEquals(listOf(message), deduplicator.filterNew(WHATSAPP, listOf(message)))
        assertTrue(deduplicator.filterNew(WHATSAPP, listOf(message)).isEmpty())
    }

    @Test
    fun bundledUpdateReturnsOnlyNewlyAppendedMessage() {
        val deduplicator = deduplicator()
        val old = message(body = "First", postedAt = 100L)
        val appended = message(body = "Second", postedAt = 200L)

        assertEquals(listOf(old), deduplicator.filterNew(WHATSAPP, listOf(old)))
        assertEquals(listOf(appended), deduplicator.filterNew(WHATSAPP, listOf(old, appended)))
    }

    @Test
    fun distinctRapidMessagesFromSameConversationSurvive() {
        val deduplicator = deduplicator()
        val messages = listOf(
            message(body = "Same body", postedAt = 100L),
            message(body = "Same body", postedAt = 101L),
            message(body = "Different body", postedAt = 101L),
        )

        assertEquals(messages, deduplicator.filterNew(WHATSAPP, messages))
    }

    @Test
    fun fingerprintIncludesPackageConversationAndSender() {
        val deduplicator = deduplicator()
        val original = message(body = "Hello", postedAt = 100L)

        assertEquals(listOf(original), deduplicator.filterNew(WHATSAPP, listOf(original)))
        assertEquals(listOf(original), deduplicator.filterNew(WHATSAPP_BUSINESS, listOf(original)))
        assertEquals(
            1,
            deduplicator.filterNew(WHATSAPP, listOf(original.copy(conversationId = ConversationId("other")))).size,
        )
        assertEquals(
            1,
            deduplicator.filterNew(WHATSAPP, listOf(original.copy(senderDisplayName = "Other"))).size,
        )
    }

    @Test
    fun insignificantWhitespaceAndUnicodeCompositionAreNormalized() {
        val deduplicator = deduplicator()
        val composed = message(body = "caf\u00e9 hello", postedAt = 100L)
        val decomposed = composed.copy(body = "  cafe\u0301\n\thello  ")

        deduplicator.filterNew(" COM.WHATSAPP ", listOf(composed))

        assertTrue(deduplicator.filterNew(WHATSAPP, listOf(decomposed)).isEmpty())
    }

    @Test
    fun entryExpiresExactlyAtWindowBoundary() {
        val deduplicator = deduplicator(windowMillis = 50L)
        val message = message()
        deduplicator.filterNew(WHATSAPP, listOf(message))

        now += 49L
        assertTrue(deduplicator.filterNew(WHATSAPP, listOf(message)).isEmpty())
        now += 1L
        assertEquals(listOf(message), deduplicator.filterNew(WHATSAPP, listOf(message)))
    }

    @Test
    fun oldestEntryIsEvictedWhenCapacityIsExceeded() {
        val deduplicator = deduplicator(capacity = 2)
        val first = message(body = "one", postedAt = 1L)
        val second = message(body = "two", postedAt = 2L)
        val third = message(body = "three", postedAt = 3L)
        deduplicator.filterNew(WHATSAPP, listOf(first, second, third))

        assertEquals(listOf(first), deduplicator.filterNew(WHATSAPP, listOf(first)))
        assertTrue(deduplicator.filterNew(WHATSAPP, listOf(third)).isEmpty())
    }

    @Test
    fun clearAllowsImmediateReplay() {
        val deduplicator = deduplicator()
        val message = message()
        deduplicator.filterNew(WHATSAPP, listOf(message))

        deduplicator.clear()

        assertEquals(listOf(message), deduplicator.filterNew(WHATSAPP, listOf(message)))
    }

    @Test
    fun invalidConfigurationAndPackageAreRejected() {
        assertFailsWith<IllegalArgumentException> { deduplicator(capacity = 0) }
        assertFailsWith<IllegalArgumentException> { deduplicator(windowMillis = 0) }
        assertFailsWith<IllegalArgumentException> { deduplicator().filterNew(" ", listOf(message())) }
    }

    private fun deduplicator(
        capacity: Int = 10,
        windowMillis: Long = 1_000L,
    ) = NotificationDeduplicator(capacity, windowMillis) { now }

    private fun message(
        body: String = "Hello",
        postedAt: Long = 100L,
    ) = ParsedMessage(
        conversationId = ConversationId("conversation"),
        conversationType = ConversationType.DIRECT,
        conversationTitle = "Conversation",
        senderDisplayName = "Sender",
        body = body,
        postedAtMillis = postedAt,
    )

    private companion object {
        const val WHATSAPP = "com.whatsapp"
        const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    }
}
