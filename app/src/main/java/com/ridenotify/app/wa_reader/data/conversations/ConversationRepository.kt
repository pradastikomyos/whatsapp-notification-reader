package com.ridenotify.app.wa_reader.data.conversations

import com.ridenotify.app.wa_reader.model.ConversationId
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeAll(): Flow<List<ObservedConversation>>

    suspend fun get(conversationId: ConversationId): ObservedConversation?

    /** Records group metadata only. This operation must never select a conversation. */
    suspend fun recordObserved(observation: ConversationObservation): ObservedConversation?

    /** Selection is changed only by an explicit user/settings action. */
    suspend fun setSelected(conversationId: ConversationId, selected: Boolean)

    /** Removes the complete observed-conversation catalogue. */
    suspend fun reset()
}
