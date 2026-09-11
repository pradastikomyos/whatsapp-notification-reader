package com.ridenotify.app.wa_reader.data.conversations.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ridenotify.app.wa_reader.data.conversations.ObservedConversation
import com.ridenotify.app.wa_reader.model.ConversationId

@Entity(tableName = "observed_conversations")
data class ObservedConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "conversation_key")
    val conversationKey: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    val kind: String = GROUP_KIND,
    @ColumnInfo(name = "display_title")
    val displayTitle: String,
    @ColumnInfo(name = "shortcut_id")
    val shortcutId: String?,
    @ColumnInfo(name = "first_seen_at_millis")
    val firstSeenAtMillis: Long,
    @ColumnInfo(name = "last_seen_at_millis")
    val lastSeenAtMillis: Long,
    @ColumnInfo(name = "selection_state")
    val selectionState: Boolean,
    @ColumnInfo(name = "collision_detected")
    val collisionDetected: Boolean,
    @ColumnInfo(name = "collision_count")
    val collisionCount: Int,
    @ColumnInfo(name = "collision_evidence_hash")
    val collisionEvidenceHash: String?,
) {
    init {
        require(kind == GROUP_KIND) { "Only group conversations may be persisted" }
    }

    fun toModel() = ObservedConversation(
        conversationId = ConversationId(conversationKey),
        packageName = packageName,
        displayTitle = displayTitle,
        shortcutId = shortcutId,
        firstSeenAtMillis = firstSeenAtMillis,
        lastSeenAtMillis = lastSeenAtMillis,
        isSelected = selectionState,
        collisionDetected = collisionDetected,
        collisionCount = collisionCount,
    )

    companion object {
        const val GROUP_KIND = "group"
    }
}

internal fun ObservedConversation.toEntity() = ObservedConversationEntity(
    conversationKey = conversationId.value,
    packageName = packageName,
    displayTitle = displayTitle,
    shortcutId = shortcutId,
    firstSeenAtMillis = firstSeenAtMillis,
    lastSeenAtMillis = lastSeenAtMillis,
    selectionState = isSelected,
    collisionDetected = collisionDetected,
    collisionCount = collisionCount,
    collisionEvidenceHash = null,
)
