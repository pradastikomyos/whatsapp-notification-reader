package com.ridenotify.app.wa_reader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.ConversationId
import com.ridenotify.app.wa_reader.model.GroupReadMode
import com.ridenotify.app.wa_reader.model.RidingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val SETTINGS_DATASTORE_NAME = "app_settings"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME
)

// Preference keys for DataStore.
private val KEY_READER_ENABLED = booleanPreferencesKey("reader_enabled")
private val KEY_RIDING_STATE = stringPreferencesKey("riding_state")
private val KEY_READ_PRIVATE_MESSAGES = booleanPreferencesKey("read_private_messages")
private val KEY_GROUP_READ_MODE = stringPreferencesKey("group_read_mode")
private val KEY_SELECTED_CONVERSATION_IDS = stringSetPreferencesKey("selected_conversation_ids")
private val KEY_ANNOUNCE_SENDER_AND_GROUP = booleanPreferencesKey("announce_sender_and_group")
private val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
private val KEY_MIGRATION_COMPLETED = booleanPreferencesKey("migration_completed")

// Old Flutter SharedPreferences keys with the "flutter." prefix added by the plugin.
private const val OLD_PREFS_FILE_NAME = "FlutterSharedPreferences"
private const val OLD_KEY_IS_SERVICE_ACTIVE = "flutter.isServiceActive"
private const val OLD_KEY_READ_PRIVATE_MESSAGES = "flutter.readPrivateMessages"
private const val OLD_KEY_SPEECH_RATE = "flutter.speechRate"
private const val OLD_KEY_SELECTED_GROUPS = "flutter.selectedGroups"
private const val OLD_KEY_IS_RIDING_MODE_ACTIVE = "flutter.isRidingModeActive"
private const val OLD_KEY_AUTO_START_DRIVING = "flutter.autoStartDriving"
private const val OLD_KEY_USE_BLUETOOTH = "flutter.useBluetooth"
private const val FLUTTER_DOUBLE_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"

/**
 * DataStore-based settings repository with old SharedPreferences migration support.
 *
 * Migration performs exactly per ADR-001:
 * - Migrate isServiceActive and readPrivateMessages values directly
 * - Transform speechRate from Flutter plugin units (0.5-2.0) with verification
 * - Reset all group-related, riding-mode, and unimplemented settings per safety rules
 *
 * Migration is performed once on first access and marked complete by KEY_MIGRATION_COMPLETED.
 */
class DataStoreSettingsRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.dataStore
) : SettingsRepository {
    private val appContext = context.applicationContext
    private val migrationMutex = Mutex()

    /**
     * Observe settings as a Flow. The flow emits the latest persisted state
     * whenever any setting changes, and new collectors receive the current state.
     */
    override fun observeSettings(): Flow<AppSettings> = flow {
        performMigrationIfNeeded()
        emitAll(dataStore.data.map(::decodeSettings))
    }

    /**
     * Get current settings synchronously. Caller should prefer observeSettings() for reactive scenarios.
     */
    override suspend fun getSettings(): AppSettings {
        return observeSettings().first()
    }

    override suspend fun setReaderEnabled(enabled: Boolean) {
        editSettings { prefs ->
            prefs[KEY_READER_ENABLED] = enabled
        }
    }

    override suspend fun setReadPrivateMessages(enabled: Boolean) {
        editSettings { prefs ->
            prefs[KEY_READ_PRIVATE_MESSAGES] = enabled
        }
    }

    override suspend fun setSpeechRate(rate: Float) {
        require(rate in AppSettings.MIN_SPEECH_RATE..AppSettings.MAX_SPEECH_RATE) {
            "speechRate must be between ${AppSettings.MIN_SPEECH_RATE} and ${AppSettings.MAX_SPEECH_RATE}"
        }
        editSettings { prefs ->
            prefs[KEY_SPEECH_RATE] = rate
        }
    }

    override suspend fun setRidingState(riding: RidingState) {
        editSettings { prefs ->
            prefs[KEY_RIDING_STATE] = riding.name
        }
    }

    override suspend fun setGroupReadMode(mode: GroupReadMode) {
        editSettings { prefs ->
            prefs[KEY_GROUP_READ_MODE] = mode.name
        }
    }

    override suspend fun setSelectedConversationIds(ids: Set<ConversationId>) {
        editSettings { prefs ->
            prefs[KEY_SELECTED_CONVERSATION_IDS] = ids.map { it.value }.toSet()
        }
    }

    override suspend fun setAnnounceSenderAndGroup(announce: Boolean) {
        editSettings { prefs ->
            prefs[KEY_ANNOUNCE_SENDER_AND_GROUP] = announce
        }
    }

    /**
     * Perform old SharedPreferences migration from the Flutter app exactly once.
     *
     * Migration table per ADR-001:
     * - MIGRATE: flutter.isServiceActive → readerEnabled
     * - MIGRATE: flutter.readPrivateMessages → readPrivateMessages
     * - TRANSFORM: flutter.speechRate → speechRate (verify units match first)
     * - RESET: flutter.selectedGroups (discard; no direct mapping to new group policy)
     * - RESET: flutter.isRidingModeActive (await ADR-007 semantics)
     * - RESET: flutter.autoStartDriving (not implemented in v1; UI-only in old app)
     * - RESET: flutter.useBluetooth (not implemented in v1; UI-only in old app)
     *
     * All keys are read with the "flutter." prefix as added by the Flutter shared_preferences
     * plugin on Android. If an old key is not found, the default value is used.
     *
     * After migration, KEY_MIGRATION_COMPLETED is set to prevent re-running on subsequent starts.
     */
    override suspend fun performMigrationIfNeeded() {
        migrationMutex.withLock {
            dataStore.edit { prefs ->
                migrateIfNeeded(prefs)
            }
        }
    }

    /**
     * Reset all settings to their defaults.
     * Group selection is also reset to empty.
     */
    override suspend fun resetAllSettings() {
        editSettings { prefs ->
            prefs.clear()
            prefs[KEY_READER_ENABLED] = false
            prefs[KEY_RIDING_STATE] = RidingState.INACTIVE.name
            prefs[KEY_READ_PRIVATE_MESSAGES] = true
            prefs[KEY_GROUP_READ_MODE] = GroupReadMode.NO_GROUPS.name
            prefs[KEY_SELECTED_CONVERSATION_IDS] = emptySet()
            prefs[KEY_ANNOUNCE_SENDER_AND_GROUP] = true
            prefs[KEY_SPEECH_RATE] = AppSettings.DEFAULT_SPEECH_RATE
            prefs[KEY_MIGRATION_COMPLETED] = true // Don't re-migrate after reset
        }
    }

    private suspend fun editSettings(transform: (MutablePreferences) -> Unit) {
        migrationMutex.withLock {
            dataStore.edit { prefs ->
                migrateIfNeeded(prefs)
                transform(prefs)
            }
        }
    }

    private fun migrateIfNeeded(prefs: MutablePreferences) {
        if (prefs[KEY_MIGRATION_COMPLETED] == true) return

        val legacyValues = appContext
            .getSharedPreferences(OLD_PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .all
        prefs[KEY_READER_ENABLED] = legacyValues[OLD_KEY_IS_SERVICE_ACTIVE] as? Boolean ?: false
        prefs[KEY_READ_PRIVATE_MESSAGES] =
            legacyValues[OLD_KEY_READ_PRIVATE_MESSAGES] as? Boolean ?: true
        prefs[KEY_SPEECH_RATE] = decodeLegacySpeechRate(legacyValues[OLD_KEY_SPEECH_RATE])

        // Group and riding preferences are intentionally reset per ADR-001.
        prefs[KEY_RIDING_STATE] = RidingState.INACTIVE.name
        prefs[KEY_GROUP_READ_MODE] = GroupReadMode.NO_GROUPS.name
        prefs[KEY_SELECTED_CONVERSATION_IDS] = emptySet()
        prefs[KEY_ANNOUNCE_SENDER_AND_GROUP] = true
        prefs[KEY_MIGRATION_COMPLETED] = true
    }

    private fun decodeSettings(prefs: Preferences): AppSettings {
        val speechRate = prefs[KEY_SPEECH_RATE]
            ?.takeIf { it.isFinite() && it in AppSettings.MIN_SPEECH_RATE..AppSettings.MAX_SPEECH_RATE }
            ?: AppSettings.DEFAULT_SPEECH_RATE
        val selectedIds = prefs[KEY_SELECTED_CONVERSATION_IDS]
            .orEmpty()
            .mapNotNullTo(mutableSetOf()) { value ->
                value.takeIf(String::isNotBlank)?.let(::ConversationId)
            }

        return AppSettings(
            readerEnabled = prefs[KEY_READER_ENABLED] ?: false,
            ridingState = enumValueOrDefault(prefs[KEY_RIDING_STATE], RidingState.INACTIVE),
            readPrivateMessages = prefs[KEY_READ_PRIVATE_MESSAGES] ?: true,
            groupReadMode = enumValueOrDefault(prefs[KEY_GROUP_READ_MODE], GroupReadMode.NO_GROUPS),
            selectedConversationIds = selectedIds,
            announceSenderAndGroup = prefs[KEY_ANNOUNCE_SENDER_AND_GROUP] ?: true,
            speechRate = speechRate,
        )
    }

    private fun decodeLegacySpeechRate(value: Any?): Float {
        val rate = when (value) {
            is Number -> value.toFloat()
            is String -> value.removePrefix(FLUTTER_DOUBLE_PREFIX).toFloatOrNull()
            else -> null
        }
        return rate
            ?.takeIf(Float::isFinite)
            ?.coerceIn(AppSettings.MIN_SPEECH_RATE, AppSettings.MAX_SPEECH_RATE)
            ?: AppSettings.DEFAULT_SPEECH_RATE
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
