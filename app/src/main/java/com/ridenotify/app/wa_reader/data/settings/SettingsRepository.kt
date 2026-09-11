package com.ridenotify.app.wa_reader.data.settings

import com.ridenotify.app.wa_reader.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository for persistent application settings.
 *
 * All settings are atomic: updates never partially fail. All getters return Flow
 * for observation-based UI and pipeline state management. This interface must
 * never expose settings for unapproved features or placeholder toggles.
 */
interface SettingsRepository {
    /**
     * Observe the current application settings as a Flow.
     * Cold collectors receive the latest persisted state.
     */
    fun observeSettings(): Flow<AppSettings>

    /**
     * Get the current settings synchronously (if cached) or block briefly.
     * Prefer observeSettings() for reactive scenarios.
     */
    suspend fun getSettings(): AppSettings

    /**
     * Update a specific setting atomically.
     *
     * @throws IllegalArgumentException if the value violates model invariants
     */
    suspend fun setReaderEnabled(enabled: Boolean)

    suspend fun setReadPrivateMessages(enabled: Boolean)

    suspend fun setSpeechRate(rate: Float)

    suspend fun setRidingState(riding: com.ridenotify.app.wa_reader.model.RidingState)

    suspend fun setGroupReadMode(mode: com.ridenotify.app.wa_reader.model.GroupReadMode)

    suspend fun setSelectedConversationIds(ids: Set<com.ridenotify.app.wa_reader.model.ConversationId>)

    suspend fun setAnnounceSenderAndGroup(announce: Boolean)

    /**
     * Perform old SharedPreferences migration if needed on first access.
     * This is called automatically by the repository; the caller need not invoke it.
     */
    suspend fun performMigrationIfNeeded()

    /**
     * Reset all settings to their defaults.
     * Group selection is also reset to empty.
     */
    suspend fun resetAllSettings()
}
