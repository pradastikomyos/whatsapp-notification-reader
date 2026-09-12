package com.ridenotify.app.wa_reader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.RidingState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DataStoreSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication().applicationContext
        context.getSharedPreferences(OLD_PREFS_FILE, Context.MODE_PRIVATE).edit().clear().commit()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStoreFile = File(temporaryFolder.root, "settings.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { dataStoreFile }
        repository = DataStoreSettingsRepository(context, dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        context.getSharedPreferences(OLD_PREFS_FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun firstRead_runsMigrationAndReturnsSafeDefaults() = runTest {
        assertEquals(AppSettings(), repository.getSettings())
    }

    @Test
    fun firstObservedValue_runsLegacyMigration() = runTest {
        legacyPreferences().edit()
            .putBoolean("flutter.isServiceActive", true)
            .putBoolean("flutter.readPrivateMessages", false)
            .putString("flutter.speechRate", FLUTTER_DOUBLE_PREFIX + "1.35")
            .putString("flutter.selectedGroups", "must-not-migrate")
            .putBoolean("flutter.isRidingModeActive", true)
            .commit()

        val settings = repository.observeSettings().first()

        assertTrue(settings.readerEnabled)
        assertFalse(settings.readPrivateMessages)
        assertEquals(1.35f, settings.speechRate)
        assertEquals(RidingState.INACTIVE, settings.ridingState)
        assertFalse(legacyPreferences().contains("flutter.selectedGroups"))
    }

    @Test
    fun firstWrite_runsMigrationBeforeApplyingUpdate() = runTest {
        legacyPreferences().edit()
            .putBoolean("flutter.isServiceActive", true)
            .putBoolean("flutter.readPrivateMessages", false)
            .commit()

        repository.setReaderEnabled(false)

        val settings = repository.getSettings()
        assertFalse(settings.readerEnabled)
        assertFalse(settings.readPrivateMessages)
    }

    @Test
    fun migration_isIdempotent() = runTest {
        legacyPreferences().edit().putBoolean("flutter.isServiceActive", true).commit()
        repository.performMigrationIfNeeded()
        legacyPreferences().edit().putBoolean("flutter.isServiceActive", false).commit()

        repository.performMigrationIfNeeded()

        assertTrue(repository.getSettings().readerEnabled)
    }

    @Test
    fun concurrentFirstAccess_migratesOnceWithoutRacing() = runTest {
        legacyPreferences().edit().putBoolean("flutter.isServiceActive", true).commit()

        coroutineScope {
            List(8) { async { repository.performMigrationIfNeeded() } }.forEach { it.await() }
        }

        assertTrue(repository.getSettings().readerEnabled)
    }

    @Test
    fun legacySpeechRate_isClampedAndMalformedValueUsesDefault() = runTest {
        legacyPreferences().edit()
            .putString("flutter.speechRate", FLUTTER_DOUBLE_PREFIX + "8.0")
            .commit()
        repository.performMigrationIfNeeded()
        assertEquals(AppSettings.MAX_SPEECH_RATE, repository.getSettings().speechRate)

        val secondStoreFile = File(temporaryFolder.root, "malformed.preferences_pb")
        val secondStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { secondStoreFile }
        val secondRepository = DataStoreSettingsRepository(context, secondStore)
        legacyPreferences().edit()
            .putString("flutter.speechRate", FLUTTER_DOUBLE_PREFIX + "not-a-number")
            .commit()

        assertEquals(AppSettings.DEFAULT_SPEECH_RATE, secondRepository.getSettings().speechRate)
    }

    @Test
    fun setReaderEnabled_persistsValue() = runTest {
        repository.setReaderEnabled(true)
        assertTrue(repository.getSettings().readerEnabled)
    }

    @Test
    fun setReadPrivateMessages_persistsValue() = runTest {
        repository.setReadPrivateMessages(false)
        assertFalse(repository.getSettings().readPrivateMessages)
    }

    @Test
    fun setSpeechRate_persistsValue() = runTest {
        repository.setSpeechRate(1.5f)
        assertEquals(1.5f, repository.getSettings().speechRate)
    }

    @Test
    fun setRidingState_persistsValue() = runTest {
        repository.setRidingState(RidingState.ACTIVE)
        assertEquals(RidingState.ACTIVE, repository.getSettings().ridingState)
    }

    @Test
    fun setAnnounceSender_persistsValue() = runTest {
        repository.setAnnounceSender(false)
        assertFalse(repository.getSettings().announceSender)
    }

    @Test
    fun invalidSpeechRate_isRejectedWithoutChangingStoredValue() = runTest {
        assertFailsWith<IllegalArgumentException> { repository.setSpeechRate(Float.NaN) }
        assertFailsWith<IllegalArgumentException> { repository.setSpeechRate(2.1f) }
        assertEquals(AppSettings.DEFAULT_SPEECH_RATE, repository.getSettings().speechRate)
    }

    @Test
    fun retirementMigration_runsWhenOldMigrationMarkerIsAlreadyTrue() = runTest {
        legacyPreferences().edit().putString("flutter.selectedGroups", "retired").commit()
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey("migration_completed")] = true
            preferences[booleanPreferencesKey("announce_sender_and_group")] = false
            preferences[stringPreferencesKey("group_read_mode")] = "ALL_OBSERVED_GROUPS"
            preferences[stringSetPreferencesKey("selected_conversation_ids")] = setOf("retired-id")
        }

        val settings = repository.getSettings()
        val storedKeyNames = dataStore.data.first().asMap().keys.map { it.name }.toSet()

        assertFalse(settings.announceSender)
        assertTrue("announce_sender" in storedKeyNames)
        assertFalse("announce_sender_and_group" in storedKeyNames)
        assertFalse("group_read_mode" in storedKeyNames)
        assertFalse("selected_conversation_ids" in storedKeyNames)
        assertFalse(legacyPreferences().contains("flutter.selectedGroups"))
    }

    @Test
    fun corruptPersistedValues_fallBackWithoutTerminatingFlow() = runTest {
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey("migration_completed")] = true
            preferences[stringPreferencesKey("riding_state")] = "UNKNOWN"
            preferences[floatPreferencesKey("speech_rate")] = Float.NaN
        }

        val settings = repository.getSettings()

        assertEquals(RidingState.INACTIVE, settings.ridingState)
        assertEquals(AppSettings.DEFAULT_SPEECH_RATE, settings.speechRate)
    }

    @Test
    fun reset_restoresDefaultsAndDoesNotRemigrateLegacyValues() = runTest {
        legacyPreferences().edit().putBoolean("flutter.isServiceActive", true).commit()

        repository.resetAllSettings()

        assertEquals(AppSettings(), repository.getSettings())
    }

    private fun legacyPreferences() =
        context.getSharedPreferences(OLD_PREFS_FILE, Context.MODE_PRIVATE)

    private companion object {
        const val OLD_PREFS_FILE = "FlutterSharedPreferences"
        const val FLUTTER_DOUBLE_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
    }
}
