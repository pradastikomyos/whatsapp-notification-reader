package com.ridenotify.app.wa_reader.data.conversations

import com.ridenotify.app.wa_reader.model.ConversationId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryConversationRepositoryTest {
    @Test
    fun `identity prefers shortcut and normalizes fallback title without case folding`() {
        val shortcut = observation(shortcutId = " group-42 ", conversationTitle = "Old")
        assertEquals("com.whatsapp::shortcut::group-42", ConversationIdentity.from(shortcut)?.value)

        val fallback = observation(
            shortcutId = null,
            conversationTitle = "  Tim\tBUDI  ",
            senderKey = null,
        )
        assertEquals(
            "com.whatsapp::fallback::group::Tim BUDI::unknown",
            ConversationIdentity.from(fallback)?.value,
        )
    }

    @Test
    fun `discovery stores groups only and never selects`() = runTest {
        val repository = InMemoryConversationRepository()
        assertNull(repository.recordObserved(observation(isGroup = false)))
        val discovered = repository.recordObserved(observation())!!

        assertFalse(discovered.isSelected)
        assertEquals(listOf(discovered), repository.observeAll().first())
    }

    @Test
    fun `shortcut rename updates title while preserving identity and selection`() = runTest {
        val repository = InMemoryConversationRepository()
        val original = repository.recordObserved(observation(conversationTitle = "Lama"))!!
        repository.setSelected(original.conversationId, true)

        val renamed = repository.recordObserved(
            observation(conversationTitle = "Baru", observedAt = 20),
        )!!

        assertEquals(original.conversationId, renamed.conversationId)
        assertEquals("Baru", renamed.displayTitle)
        assertTrue(renamed.isSelected)
        assertEquals(10, renamed.firstSeenAtMillis)
        assertEquals(20, renamed.lastSeenAtMillis)
    }

    @Test
    fun `fallback rename creates a separate unselected row`() = runTest {
        val repository = InMemoryConversationRepository()
        val old = repository.recordObserved(observation(shortcutId = null, conversationTitle = "Lama"))!!
        repository.setSelected(old.conversationId, true)

        val renamed = repository.recordObserved(
            observation(shortcutId = null, conversationTitle = "Baru", observedAt = 20),
        )!!

        assertEquals(2, repository.observeAll().first().size)
        assertTrue(repository.get(old.conversationId)!!.isSelected)
        assertFalse(renamed.isSelected)
    }

    @Test
    fun `conflicting evidence flags collision without overwriting display metadata`() = runTest {
        val repository = InMemoryConversationRepository()
        val initial = repository.recordObserved(
            observation(
                shortcutId = null,
                conversationTitle = "Original",
                senderKey = "stable-fallback-component",
                collisionEvidence = "sender-set-a",
            ),
        )!!

        val collided = repository.recordObserved(
            observation(
                shortcutId = null,
                conversationTitle = "Original",
                senderKey = "stable-fallback-component",
                collisionEvidence = "sender-set-b",
                observedAt = 20,
            ),
        )!!

        assertEquals(initial.conversationId, collided.conversationId)
        assertEquals("Original", collided.displayTitle)
        assertTrue(collided.collisionDetected)
        assertEquals(1, collided.collisionCount)
        assertEquals(20, collided.lastSeenAtMillis)
    }

    @Test
    fun `rows have indefinite retention until full reset`() = runTest {
        val repository = InMemoryConversationRepository()
        repository.recordObserved(observation())

        assertEquals(1, repository.observeAll().first().size)
        repository.reset()
        assertTrue(repository.observeAll().first().isEmpty())
        assertNull(repository.get(ConversationId("com.whatsapp::shortcut::stable-id")))
    }

    private fun observation(
        isGroup: Boolean = true,
        shortcutId: String? = "stable-id",
        conversationTitle: String? = "Komunitas",
        senderKey: String? = "sender-key",
        collisionEvidence: String? = senderKey,
        observedAt: Long = 10,
    ) = ConversationObservation(
        packageName = "com.whatsapp",
        isGroupConversation = isGroup,
        conversationTitle = conversationTitle,
        title = null,
        shortcutId = shortcutId,
        senderKey = senderKey,
        collisionEvidenceKey = collisionEvidence,
        observedAtMillis = observedAt,
    )
}
