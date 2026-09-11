package com.ridenotify.app.wa_reader.policy

import com.ridenotify.app.wa_reader.model.ConversationId
import com.ridenotify.app.wa_reader.model.ConversationType
import com.ridenotify.app.wa_reader.model.ParsedMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeechTextFormatterTest {
    private val formatter = SpeechTextFormatter()

    @Test
    fun `Indonesian announcements cover direct group and missing metadata`() {
        val cases = listOf(
            message(ConversationType.DIRECT, "Sari", "Sari") to "Pesan dari Sari. Halo dunia",
            message(ConversationType.GROUP, "Tim", "Budi") to "Pesan di grup Tim dari Budi. Halo dunia",
            message(ConversationType.GROUP, "Tim", null) to "Pesan di grup Tim. Halo dunia",
            message(ConversationType.GROUP, null, "Budi") to "Pesan dari Budi. Halo dunia",
            message(ConversationType.DIRECT, null, null) to "Halo dunia",
        )

        cases.forEach { (message, expected) -> assertEquals(expected, formatter.format(message, true)) }
        assertEquals("Halo dunia", formatter.format(message(ConversationType.GROUP, "Tim", "Budi"), false))
    }

    @Test
    fun `sanitizer normalizes whitespace and removes controls without rewriting content`() {
        val message = message(
            ConversationType.DIRECT,
            " Sa\u200Bri ",
            " Sa\u0000ri ",
            body = "  Alamat:\r\nJalan\tMelati https://example.test/a:b  ",
        )

        assertEquals(
            "Pesan dari Sa ri. Alamat: Jalan Melati https://example.test/a:b",
            formatter.format(message, true),
        )
    }

    @Test
    fun `blank sanitized body is rejected`() {
        assertNull(formatter.format(message(ConversationType.DIRECT, "Sari", "Sari", "\u0000\u200B"), true))
    }

    @Test
    fun `length cap uses word boundary without ellipsis`() {
        val text = assertNotNull(
            formatter.format(message(ConversationType.DIRECT, null, null, "kata ".repeat(60)), false),
        )

        assertTrue(text.length <= 240)
        assertTrue(text.endsWith("kata"))
        assertTrue(!text.endsWith("..."))
    }

    @Test
    fun `unbroken content is hard cut and a long announcement cannot erase body`() {
        val unbroken = assertNotNull(
            formatter.format(message(ConversationType.DIRECT, null, null, "x".repeat(241)), false),
        )
        assertEquals(240, unbroken.length)

        val longName = "n".repeat(240)
        assertEquals(
            "Halo dunia",
            formatter.format(message(ConversationType.DIRECT, null, longName), true),
        )
        assertEquals(
            "Pesan penting",
            formatter.format(message(ConversationType.DIRECT, null, longName, "Pesan penting"), true),
        )
    }

    @Test
    fun `truncation keeps complete boundary word and never splits surrogate pair`() {
        val completeBoundary = "a".repeat(235) + " kata "
        assertEquals(completeBoundary.trimEnd(), formatter.format(
            message(ConversationType.DIRECT, null, null, completeBoundary + "lanjut"),
            false,
        ))

        val surrogateBoundary = "a".repeat(239) + "\uD83D\uDE00"
        val result = assertNotNull(formatter.format(
            message(ConversationType.DIRECT, null, null, surrogateBoundary),
            false,
        ))
        assertEquals(239, result.length)
        assertTrue(!result.last().isHighSurrogate())
    }

    @Test
    fun `sanitizer preserves emoji joiners and separates format controls`() {
        val familyEmoji = "\uD83D\uDC69\u200D\uD83D\uDC67"
        assertEquals(
            "$familyEmoji kata aman",
            formatter.format(
                message(ConversationType.DIRECT, null, null, "$familyEmoji kata\u2066aman"),
                false,
            ),
        )
    }

    private fun message(
        type: ConversationType,
        title: String?,
        sender: String?,
        body: String = "Halo dunia",
    ) = ParsedMessage(ConversationId("id"), type, title, sender, body, 1)
}
