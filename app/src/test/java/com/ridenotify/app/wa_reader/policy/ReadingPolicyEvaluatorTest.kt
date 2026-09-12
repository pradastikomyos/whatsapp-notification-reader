package com.ridenotify.app.wa_reader.policy

import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.ConversationId
import com.ridenotify.app.wa_reader.model.ConversationType
import com.ridenotify.app.wa_reader.model.ParseSource
import com.ridenotify.app.wa_reader.model.ParsedMessage
import com.ridenotify.app.wa_reader.model.ParsedNotification
import com.ridenotify.app.wa_reader.model.ReadingDecision
import com.ridenotify.app.wa_reader.model.RidingState
import com.ridenotify.app.wa_reader.model.UnsupportedReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReadingPolicyEvaluatorTest {
    private val now = 1_000_000L
    private val evaluator = ReadingPolicyEvaluator(clockMillis = { now })
    private val enabled = AppSettings(readerEnabled = true, ridingState = RidingState.ACTIVE)

    @Test
    fun `reader and riding gates have fixed precedence`() {
        val cases = listOf(
            AppSettings() to ReadingDecision.SkipReaderDisabled,
            enabled.copy(readerEnabled = false) to ReadingDecision.SkipReaderDisabled,
            enabled.copy(ridingState = RidingState.INACTIVE, readPrivateMessages = false) to
                ReadingDecision.SkipRidingModeInactive,
        )

        cases.forEach { (settings, expected) ->
            assertEquals(expected, evaluator.evaluate(message(), settings))
        }
    }

    @Test
    fun `private policy precedes stale check`() {
        val stale = message(postedAt = now - 200_000)

        assertEquals(
            ReadingDecision.SkipPrivateDisabled,
            evaluator.evaluate(stale, enabled.copy(readPrivateMessages = false)),
        )
    }

    @Test
    fun `group reading is unconditionally disabled`() {
        assertEquals(ReadingDecision.SkipGroupReadingDisabled, evaluator.evaluate(groupMessage(), enabled))
        assertEquals(
            ReadingDecision.SkipGroupReadingDisabled,
            evaluator.evaluate(groupMessage(), AppSettings()),
        )
        assertIs<ReadingDecision.Speak>(evaluator.evaluate(message(), enabled))
    }

    @Test
    fun `stale boundary and future timestamps are deterministic`() {
        listOf(now - 179_999, now - 180_000, now, now + 1).forEach { postedAt ->
            assertIs<ReadingDecision.Speak>(evaluator.evaluate(message(postedAt = postedAt), enabled))
        }
        assertEquals(ReadingDecision.SkipTooOld, evaluator.evaluate(message(postedAt = now - 180_001), enabled))
    }

    @Test
    fun `non-message parser outcomes fail closed after base gates`() {
        assertEquals(
            listOf(ReadingDecision.SkipRedacted),
            evaluator.evaluate(ParsedNotification.Redacted, enabled),
        )
        listOf(
            ParsedNotification.Summary,
            ParsedNotification.Call,
            ParsedNotification.Security,
            ParsedNotification.Attachment,
            ParsedNotification.Unsupported(UnsupportedReason.EMPTY_CONTENT),
        ).forEach { outcome ->
            assertEquals(listOf(ReadingDecision.SkipUnsupported), evaluator.evaluate(outcome, enabled))
        }
        assertEquals(
            listOf(ReadingDecision.SkipReaderDisabled),
            evaluator.evaluate(ParsedNotification.Redacted, enabled.copy(readerEnabled = false)),
        )
    }

    @Test
    fun `every parsed message receives its own decision`() {
        val result = evaluator.evaluate(
            ParsedNotification.Messages(ParseSource.MESSAGING_STYLE, listOf(message(), message(id = "second"))),
            enabled,
        )

        assertEquals(2, result.size)
        result.forEach { assertIs<ReadingDecision.Speak>(it) }
    }

    private fun message(id: String = "direct", postedAt: Long = now) = ParsedMessage(
        ConversationId(id), ConversationType.DIRECT, "Sari", "Sari", "Halo", postedAt,
    )

    private fun groupMessage() = ParsedMessage(
        ConversationId("group"), ConversationType.GROUP, "Tim", "Budi", "Halo", now,
    )
}
