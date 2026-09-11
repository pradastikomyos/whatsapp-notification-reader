package com.ridenotify.app.wa_reader.data.conversations.room

import androidx.room.Room
import com.ridenotify.app.wa_reader.data.conversations.ConversationObservation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomConversationRepositoryTest {
    private lateinit var database: ConversationDatabase
    private lateinit var repository: RoomConversationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ConversationDatabase::class.java,
        ).build()
        repository = RoomConversationRepository(database.observedConversationDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `discovery persists groups unselected and ignores direct messages`() = runTest {
        assertNull(repository.recordObserved(observation(isGroup = false)))

        val group = repository.recordObserved(observation())!!

        assertFalse(group.isSelected)
        assertEquals(listOf(group), repository.observeAll().first())
    }

    @Test
    fun `shortcut rename preserves first seen identity and explicit selection`() = runTest {
        val original = repository.recordObserved(observation(title = "Nama Lama"))!!
        repository.setSelected(original.conversationId, true)

        val renamed = repository.recordObserved(observation(title = "Nama Baru", observedAt = 25))!!

        assertEquals(original.conversationId, renamed.conversationId)
        assertEquals("Nama Baru", renamed.displayTitle)
        assertTrue(renamed.isSelected)
        assertEquals(10, renamed.firstSeenAtMillis)
        assertEquals(25, renamed.lastSeenAtMillis)
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun `fallback rename persists separate unselected row`() = runTest {
        val original = repository.recordObserved(observation(shortcutId = null, title = "Nama Lama"))!!
        repository.setSelected(original.conversationId, true)

        val renamed = repository.recordObserved(
            observation(shortcutId = null, title = "Nama Baru", observedAt = 25),
        )!!

        assertEquals(2, repository.observeAll().first().size)
        assertTrue(repository.get(original.conversationId)!!.isSelected)
        assertFalse(renamed.isSelected)
    }

    @Test
    fun `fallback collision is persisted without changing existing metadata`() = runTest {
        val initial = repository.recordObserved(
            observation(shortcutId = null, evidence = "fingerprint-a"),
        )!!

        val collided = repository.recordObserved(
            observation(shortcutId = null, evidence = "fingerprint-b", observedAt = 30),
        )!!

        assertEquals(initial.displayTitle, collided.displayTitle)
        assertTrue(collided.collisionDetected)
        assertEquals(1, collided.collisionCount)
        assertTrue(repository.get(initial.conversationId)!!.collisionDetected)
    }

    @Test
    fun `fallback collision remains detectable after repository recreation`() = runTest {
        val initial = repository.recordObserved(
            observation(shortcutId = null, evidence = "fingerprint-a"),
        )!!
        repository = RoomConversationRepository(database.observedConversationDao())

        val collided = repository.recordObserved(
            observation(shortcutId = null, evidence = "fingerprint-b", observedAt = 30),
        )!!

        assertEquals(initial.displayTitle, collided.displayTitle)
        assertTrue(collided.collisionDetected)
        assertEquals(1, collided.collisionCount)
    }

    @Test
    fun `full reset clears every persisted row`() = runTest {
        repository.recordObserved(observation())
        repository.recordObserved(observation(shortcutId = "other", title = "Lain"))

        repository.reset()

        assertTrue(repository.observeAll().first().isEmpty())
    }

    private fun observation(
        isGroup: Boolean = true,
        shortcutId: String? = "stable-id",
        title: String = "Komunitas",
        evidence: String? = "sender-set",
        observedAt: Long = 10,
    ) = ConversationObservation(
        packageName = "com.whatsapp",
        isGroupConversation = isGroup,
        conversationTitle = title,
        title = null,
        shortcutId = shortcutId,
        senderKey = "stable-sender-key",
        collisionEvidenceKey = evidence,
        observedAtMillis = observedAt,
    )
}
