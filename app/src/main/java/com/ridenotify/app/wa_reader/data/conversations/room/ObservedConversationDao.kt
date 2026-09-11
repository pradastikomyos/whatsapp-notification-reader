package com.ridenotify.app.wa_reader.data.conversations.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservedConversationDao {
    @Query("SELECT * FROM observed_conversations ORDER BY last_seen_at_millis DESC, conversation_key ASC")
    fun observeAll(): Flow<List<ObservedConversationEntity>>

    @Query("SELECT * FROM observed_conversations WHERE conversation_key = :conversationKey")
    suspend fun get(conversationKey: String): ObservedConversationEntity?

    @Upsert
    suspend fun upsert(conversation: ObservedConversationEntity)

    @Query("UPDATE observed_conversations SET selection_state = :selected WHERE conversation_key = :conversationKey")
    suspend fun setSelected(conversationKey: String, selected: Boolean)

    @Query("DELETE FROM observed_conversations")
    suspend fun deleteAll()
}
