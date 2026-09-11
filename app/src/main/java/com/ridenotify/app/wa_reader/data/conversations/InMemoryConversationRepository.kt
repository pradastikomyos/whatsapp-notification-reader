package com.ridenotify.app.wa_reader.data.conversations

import com.ridenotify.app.wa_reader.model.ConversationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Deterministic reference implementation of the ADR-004 repository contract. */
class InMemoryConversationRepository : ConversationRepository {
    private val mutex = Mutex()
    private val rows = linkedMapOf<ConversationId, ObservedConversation>()
    private val collisionEvidence = mutableMapOf<ConversationId, MutableSet<String>>()
    private val state = MutableStateFlow<List<ObservedConversation>>(emptyList())

    override fun observeAll(): Flow<List<ObservedConversation>> = state.asStateFlow()

    override suspend fun get(conversationId: ConversationId): ObservedConversation? =
        mutex.withLock { rows[conversationId] }

    override suspend fun recordObserved(
        observation: ConversationObservation,
    ): ObservedConversation? = mutex.withLock {
        if (!observation.isGroupConversation) return@withLock null
        val id = ConversationIdentity.from(observation) ?: return@withLock null
        val displayTitle = ConversationIdentity.displayTitle(observation) ?: return@withLock null
        val old = rows[id]
        val evidence = observation.collisionEvidenceKey?.trim()?.takeIf(String::isNotEmpty)
        val evidenceSet = collisionEvidence.getOrPut(id) { linkedSetOf() }
        val isFallbackIdentity = observation.shortcutId.isNullOrBlank()
        val isNewConflictingEvidence = isFallbackIdentity &&
            evidence != null && evidenceSet.isNotEmpty() && evidence !in evidenceSet
        if (evidence != null) evidenceSet += evidence

        val updated = if (old == null) {
            ObservedConversation(
                conversationId = id,
                packageName = observation.packageName.trim(),
                displayTitle = displayTitle,
                shortcutId = observation.shortcutId?.trim()?.takeIf(String::isNotEmpty),
                firstSeenAtMillis = observation.observedAtMillis,
                lastSeenAtMillis = observation.observedAtMillis,
                isSelected = false,
                collisionDetected = false,
                collisionCount = 0,
            )
        } else if (isNewConflictingEvidence) {
            old.copy(
                lastSeenAtMillis = maxOf(old.lastSeenAtMillis, observation.observedAtMillis),
                collisionDetected = true,
                collisionCount = old.collisionCount + 1,
            )
        } else {
            if (observation.observedAtMillis < old.lastSeenAtMillis) old else old.copy(
                displayTitle = displayTitle,
                shortcutId = observation.shortcutId?.trim()?.takeIf(String::isNotEmpty),
                lastSeenAtMillis = observation.observedAtMillis,
            )
        }
        rows[id] = updated
        publish()
        updated
    }

    override suspend fun setSelected(conversationId: ConversationId, selected: Boolean) {
        mutex.withLock {
            val old = rows[conversationId] ?: return@withLock
            rows[conversationId] = old.copy(isSelected = selected)
            publish()
        }
    }

    override suspend fun reset() {
        mutex.withLock {
            rows.clear()
            collisionEvidence.clear()
            publish()
        }
    }

    private fun publish() {
        state.value = rows.values.sortedWith(
            compareByDescending<ObservedConversation> { it.lastSeenAtMillis }
                .thenBy { it.conversationId.value },
        )
    }
}
