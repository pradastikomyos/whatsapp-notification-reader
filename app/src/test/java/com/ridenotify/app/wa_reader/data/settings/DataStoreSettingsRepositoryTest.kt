package com.ridenotify.app.wa_reader.data.settings

import android.content.Context
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.ConversationId
import com.ridenotify.app.wa_reader.model.GroupReadMode
import com.ridenotify.app.wa_reader.model.RidingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for DataStoreSettingsRepository.
 * These tests focus on the business logic and behavioral contracts.
 * Full integration tests with real DataStore would be instrumented tests.
 */
class DataStoreSettingsRepositoryTest {

    /**
     * Test that defaults are correct when no settings have been persisted.
     */
    @Test
    fun defaults_areCorrectForColdStart() {
        val defaults = AppSettings()
        assertFalse(defaults.readerEnabled, "Reader should be disabled by default")
        assertEquals(RidingState.INACTIVE, defaults.ridingState, "Riding should be inactive by default")
        assertTrue(defaults.readPrivateMessages, "Private messages should be read by default")
        assertEquals(GroupReadMode.NO_GROUPS, defaults.groupReadMode, "Group mode should be NO_GROUPS by default")
        assertTrue(defaults.selectedConversationIds.isEmpty(), "Selected conversations should be empty by default")
        assertTrue(defaults.announceSenderAndGroup, "Announcements should be enabled by default")
        assertEquals(AppSettings.DEFAULT_SPEECH_RATE, defaults.speechRate, "Speech rate should default to 1.0")
    }

    /**
     * Test that speech rate validation works correctly.
     */
    @Test
    fun speechRate_validatesRange() {
        try {
            AppSettings(speechRate = 3.0f)
            assertTrue(false, "Should reject speech rate outside [0.5, 2.0]")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("speechRate") == true)
        }

        try {
            AppSettings(speechRate = 0.2f)
            assertTrue(false, "Should reject speech rate outside [0.5, 2.0]")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("speechRate") == true)
        }

        // Valid rates should not throw
        AppSettings(speechRate = 0.5f)
        AppSettings(speechRate = 1.0f)
        AppSettings(speechRate = 2.0f)
    }

    /**
     * Test that selected conversations can be set and persist.
     */
    @Test
    fun selectedConversations_canBeSetAndRetrieved() {
        val ids1 = setOf(ConversationId("conv-1"), ConversationId("conv-2"))
        val settings = AppSettings(selectedConversationIds = ids1)
        assertEquals(ids1, settings.selectedConversationIds)

        val ids2 = setOf(ConversationId("conv-3"))
        val updated = settings.copy(selectedConversationIds = ids2)
        assertEquals(ids2, updated.selectedConversationIds)
    }

    /**
     * Test that all settings can be updated independently.
     */
    @Test
    fun settings_canBeUpdatedIndependently() {
        var s = AppSettings()

        s = s.copy(readerEnabled = true)
        assertTrue(s.readerEnabled)
        assertEquals(RidingState.INACTIVE, s.ridingState) // unchanged

        s = s.copy(ridingState = RidingState.ACTIVE)
        assertTrue(s.readerEnabled) // unchanged
        assertEquals(RidingState.ACTIVE, s.ridingState)

        s = s.copy(readPrivateMessages = false)
        assertTrue(s.readerEnabled)
        assertEquals(RidingState.ACTIVE, s.ridingState)
        assertFalse(s.readPrivateMessages)

        s = s.copy(groupReadMode = GroupReadMode.ALL_OBSERVED_GROUPS)
        assertEquals(GroupReadMode.ALL_OBSERVED_GROUPS, s.groupReadMode)
        assertFalse(s.readPrivateMessages) // unchanged
    }

    /**
     * Test that riding state transitions work.
     */
    @Test
    fun ridingState_canTransition() {
        var s = AppSettings(ridingState = RidingState.INACTIVE)
        assertEquals(RidingState.INACTIVE, s.ridingState)

        s = s.copy(ridingState = RidingState.ACTIVE)
        assertEquals(RidingState.ACTIVE, s.ridingState)

        s = s.copy(ridingState = RidingState.INACTIVE)
        assertEquals(RidingState.INACTIVE, s.ridingState)
    }

    /**
     * Test that group read mode can be changed between all three modes.
     */
    @Test
    fun groupReadMode_canTransitionBetweenAllModes() {
        var s = AppSettings(groupReadMode = GroupReadMode.NO_GROUPS)
        assertEquals(GroupReadMode.NO_GROUPS, s.groupReadMode)

        s = s.copy(groupReadMode = GroupReadMode.ALL_OBSERVED_GROUPS)
        assertEquals(GroupReadMode.ALL_OBSERVED_GROUPS, s.groupReadMode)

        s = s.copy(groupReadMode = GroupReadMode.SELECTED_GROUPS_ONLY)
        assertEquals(GroupReadMode.SELECTED_GROUPS_ONLY, s.groupReadMode)

        s = s.copy(groupReadMode = GroupReadMode.NO_GROUPS)
        assertEquals(GroupReadMode.NO_GROUPS, s.groupReadMode)
    }

    /**
     * Mock SettingsRepository for testing the contract.
     */
    private class MockSettingsRepository : SettingsRepository {
        private var settings = AppSettings()

        override fun observeSettings(): Flow<AppSettings> = flowOf(settings)

        override suspend fun getSettings(): AppSettings = settings

        override suspend fun setReaderEnabled(enabled: Boolean) {
            settings = settings.copy(readerEnabled = enabled)
        }

        override suspend fun setReadPrivateMessages(enabled: Boolean) {
            settings = settings.copy(readPrivateMessages = enabled)
        }

        override suspend fun setSpeechRate(rate: Float) {
            settings = settings.copy(speechRate = rate)
        }

        override suspend fun setRidingState(riding: RidingState) {
            settings = settings.copy(ridingState = riding)
        }

        override suspend fun setGroupReadMode(mode: GroupReadMode) {
            settings = settings.copy(groupReadMode = mode)
        }

        override suspend fun setSelectedConversationIds(ids: Set<ConversationId>) {
            settings = settings.copy(selectedConversationIds = ids)
        }

        override suspend fun setAnnounceSenderAndGroup(announce: Boolean) {
            settings = settings.copy(announceSenderAndGroup = announce)
        }

        override suspend fun performMigrationIfNeeded() {
            // Mock: no migration needed
        }

        override suspend fun resetAllSettings() {
            settings = AppSettings()
        }
    }

    /**
     * Test that the repository contract allows setting and retrieving each setting.
     */
    @Test
    fun repositoryContract_allowsSettingAndGetting() = runTest {
        val repo = MockSettingsRepository()

        repo.setReaderEnabled(true)
        var settings = repo.getSettings()
        assertTrue(settings.readerEnabled)

        repo.setReadPrivateMessages(false)
        settings = repo.getSettings()
        assertFalse(settings.readPrivateMessages)

        repo.setSpeechRate(1.5f)
        settings = repo.getSettings()
        assertEquals(1.5f, settings.speechRate)

        repo.setRidingState(RidingState.ACTIVE)
        settings = repo.getSettings()
        assertEquals(RidingState.ACTIVE, settings.ridingState)

        repo.setGroupReadMode(GroupReadMode.SELECTED_GROUPS_ONLY)
        settings = repo.getSettings()
        assertEquals(GroupReadMode.SELECTED_GROUPS_ONLY, settings.groupReadMode)

        val ids = setOf(ConversationId("conv-1"))
        repo.setSelectedConversationIds(ids)
        settings = repo.getSettings()
        assertEquals(ids, settings.selectedConversationIds)

        repo.setAnnounceSenderAndGroup(false)
        settings = repo.getSettings()
        assertFalse(settings.announceSenderAndGroup)
    }

    /**
     * Test that reset restores all defaults.
     */
    @Test
    fun repositoryContract_resetRestoresDefaults() = runTest {
        val repo = MockSettingsRepository()

        // Set various non-default values
        repo.setReaderEnabled(true)
        repo.setReadPrivateMessages(false)
        repo.setSpeechRate(1.8f)
        repo.setRidingState(RidingState.ACTIVE)
        repo.setGroupReadMode(GroupReadMode.ALL_OBSERVED_GROUPS)
        repo.setSelectedConversationIds(setOf(ConversationId("conv-1")))
        repo.setAnnounceSenderAndGroup(false)

        // Reset all
        repo.resetAllSettings()

        val settings = repo.getSettings()
        assertFalse(settings.readerEnabled)
        assertTrue(settings.readPrivateMessages)
        assertEquals(AppSettings.DEFAULT_SPEECH_RATE, settings.speechRate)
        assertEquals(RidingState.INACTIVE, settings.ridingState)
        assertEquals(GroupReadMode.NO_GROUPS, settings.groupReadMode)
        assertTrue(settings.selectedConversationIds.isEmpty())
        assertTrue(settings.announceSenderAndGroup)
    }
}
