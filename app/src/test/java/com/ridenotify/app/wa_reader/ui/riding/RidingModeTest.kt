package com.ridenotify.app.wa_reader.ui.riding

import com.ridenotify.app.wa_reader.R
import com.ridenotify.app.wa_reader.data.settings.SettingsRepository
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.RidingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RidingModeTest {
    @Test
    fun `loading state does not guess riding or reader values`() {
        val state = RidingModeUiState.from(null)

        assertTrue(state.isLoading)
        assertEquals(null, state.ridingState)
        assertEquals(null, state.readerEnabled)
    }

    @Test
    fun `manual activation persists active riding state`() = runTest {
        val settings = RidingSettingsRepository(AppSettings(readerEnabled = false))

        val result = RidingModeController(settings).setActive(true)

        assertEquals(RidingModeUpdateResult.APPLIED, result)
        assertEquals(RidingState.ACTIVE, settings.current.ridingState)
        assertFalse(settings.current.readerEnabled)
    }

    @Test
    fun `manual deactivation persists inactive riding state without changing reader`() = runTest {
        val settings = RidingSettingsRepository(
            AppSettings(readerEnabled = true, ridingState = RidingState.ACTIVE),
        )

        val result = RidingModeController(settings).setActive(false)

        assertEquals(RidingModeUpdateResult.APPLIED, result)
        assertEquals(RidingState.INACTIVE, settings.current.ridingState)
        assertTrue(settings.current.readerEnabled)
    }

    @Test
    fun `effective state distinguishes reader and riding gates`() {
        assertEquals(
            R.string.riding_mode_effective_reader_disabled,
            effectiveStateText(readerEnabled = false, ridingState = RidingState.ACTIVE),
        )
        assertEquals(
            R.string.riding_mode_effective_riding_inactive,
            effectiveStateText(readerEnabled = true, ridingState = RidingState.INACTIVE),
        )
        assertEquals(
            R.string.riding_mode_effective_active,
            effectiveStateText(readerEnabled = true, ridingState = RidingState.ACTIVE),
        )
    }

    @Test
    fun `write failure is reported without changing state`() = runTest {
        val settings = RidingSettingsRepository(AppSettings()).apply { failure = IllegalStateException("store unavailable") }

        val result = RidingModeController(settings).setActive(true)

        assertEquals(RidingModeUpdateResult.FAILED, result)
        assertEquals(RidingState.INACTIVE, settings.current.ridingState)
    }

    @Test
    fun `cancellation is propagated`() = runTest {
        val settings = RidingSettingsRepository(AppSettings()).apply {
            failure = CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> { RidingModeController(settings).setActive(true) }
    }
}

private class RidingSettingsRepository(initial: AppSettings) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    val current get() = state.value
    var failure: Throwable? = null

    override fun observeSettings(): Flow<AppSettings> = state
    override suspend fun getSettings(): AppSettings = state.value
    override suspend fun setReaderEnabled(enabled: Boolean) = update { it.copy(readerEnabled = enabled) }
    override suspend fun setReadPrivateMessages(enabled: Boolean) = update { it.copy(readPrivateMessages = enabled) }
    override suspend fun setSpeechRate(rate: Float) = update { it.copy(speechRate = rate) }
    override suspend fun setRidingState(riding: RidingState) {
        failure?.let { throw it }
        update { it.copy(ridingState = riding) }
    }
    override suspend fun setAnnounceSender(announce: Boolean) = update {
        it.copy(announceSender = announce)
    }
    override suspend fun performMigrationIfNeeded() = Unit
    override suspend fun resetAllSettings() = update { AppSettings() }

    private fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}
