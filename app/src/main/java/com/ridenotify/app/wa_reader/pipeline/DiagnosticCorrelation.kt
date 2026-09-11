package com.ridenotify.app.wa_reader.pipeline

import com.ridenotify.app.wa_reader.model.ConversationId
import com.ridenotify.app.wa_reader.model.NotificationSnapshot
import java.security.MessageDigest
import java.util.UUID

/**
 * Ephemeral, process-salted correlation token generator per ADR-009.
 *
 * Emits a one-way truncated hash (8 hex characters) salted with a process-local
 * random secret so events can be correlated within a single process session
 * without leaking raw identifiers or allowing correlation across process restarts.
 */
class DiagnosticCorrelation(
    private val salt: String = UUID.randomUUID().toString(),
) {
    fun tokenFor(conversationId: ConversationId): String = hash(conversationId.value)

    fun tokenFor(snapshot: NotificationSnapshot): String = hash(snapshot.notificationKey)

    fun tokenFor(key: String): String = hash(key)

    private fun hash(rawIdentifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt::$rawIdentifier".toByteArray(Charsets.UTF_8))
        return bytes.take(TOKEN_BYTE_LENGTH).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TOKEN_BYTE_LENGTH = 4 // 8 hex characters
    }
}
