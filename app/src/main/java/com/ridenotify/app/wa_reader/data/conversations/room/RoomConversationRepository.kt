package com.ridenotify.app.wa_reader.data.conversations.room

import com.ridenotify.app.wa_reader.data.conversations.ConversationIdentity
import com.ridenotify.app.wa_reader.data.conversations.ConversationObservation
import com.ridenotify.app.wa_reader.data.conversations.ConversationRepository
import com.ridenotify.app.wa_reader.data.conversations.ObservedConversation
import com.ridenotify.app.wa_reader.model.ConversationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/** Room-backed, metadata-only observed group catalogue required by ADR-004. */
class RoomConversationRepository(
    private val dao: ObservedConversationDao,
) : ConversationRepository {
    private val mutex = Mutex()

    override fun observeAll(): Flow<List<ObservedConversation>> =
        dao.observeAll().map { entities -> entities.map(ObservedConversationEntity::toModel) }

    override suspend fun get(conversationId: ConversationId): ObservedConversation? =
        dao.get(conversationId.value)?.toModel()

    override suspend fun recordObserved(
        observation: ConversationObservation,
    ): ObservedConversation? = mutex.withLock {
        if (!observation.isGroupConversation) return@withLock null
        val id = ConversationIdentity.from(observation) ?: return@withLock null
        val displayTitle = ConversationIdentity.displayTitle(observation) ?: return@withLock null
        val oldEntity = dao.get(id.value)
        val old = oldEntity?.toModel()
        val evidenceHash = observation.collisionEvidenceKey
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.sha256()
        val conflicting = observation.shortcutId.isNullOrBlank() &&
            evidenceHash != null && oldEntity?.collisionEvidenceHash != null &&
            evidenceHash != oldEntity.collisionEvidenceHash

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
        } else if (conflicting) {
            old.copy(
                lastSeenAtMillis = maxOf(old.lastSeenAtMillis, observation.observedAtMillis),
                collisionDetected = true,
                collisionCount = old.collisionCount + 1,
            )
        } else {
            old.copy(
                displayTitle = displayTitle,
                shortcutId = observation.shortcutId?.trim()?.takeIf(String::isNotEmpty),
                lastSeenAtMillis = maxOf(old.lastSeenAtMillis, observation.observedAtMillis),
            )
        }
        dao.upsert(
            updated.toEntity().copy(
                collisionEvidenceHash = oldEntity?.collisionEvidenceHash ?: evidenceHash,
            ),
        )
        updated
    }

    override suspend fun setSelected(conversationId: ConversationId, selected: Boolean) {
        mutex.withLock {
            dao.setSelected(conversationId.value, selected)
        }
    }

    override suspend fun reset() = mutex.withLock {
        dao.deleteAll()
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
