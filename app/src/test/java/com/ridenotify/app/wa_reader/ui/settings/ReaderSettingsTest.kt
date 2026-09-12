package com.ridenotify.app.wa_reader.ui.settings

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

class ReaderSettingsTest {
    @Test
    fun `validated settings persist through one shared repository`() = runTest {
        val settings = ReaderSettingsRepository(AppSettings())
        val controller = ReaderSettingsController(settings)

        assertEquals(ReaderSettingsUpdateResult.APPLIED, controller.setReadPrivateMessages(false))
        assertEquals(ReaderSettingsUpdateResult.APPLIED, controller.setAnnounceSender(false))

        assertEquals(false, settings.current.readPrivateMessages)
        assertEquals(false, settings.current.announceSender)
    }

    @Test
    fun `write failure is reported and cancellation is propagated`() = runTest {
        val failed = ReaderSettingsRepository(AppSettings()).apply { failure = IllegalStateException("store unavailable") }
        assertEquals(
            ReaderSettingsUpdateResult.FAILED,
            ReaderSettingsController(failed).setReadPrivateMessages(false),
        )

        val cancelled = ReaderSettingsRepository(AppSettings()).apply {
            failure = CancellationException("cancelled")
        }
        assertFailsWith<CancellationException> {
            ReaderSettingsController(cancelled).setAnnounceSender(false)
        }
    }
}

private class ReaderSettingsRepository(initial: AppSettings) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    val current get() = state.value
    var failure: Throwable? = null

    override fun observeSettings(): Flow<AppSettings> = state
    override suspend fun getSettings(): AppSettings = state.value
    override suspend fun setReaderEnabled(enabled: Boolean) = update { it.copy(readerEnabled = enabled) }
    override suspend fun setReadPrivateMessages(enabled: Boolean) = update { it.copy(readPrivateMessages = enabled) }
    override suspend fun setSpeechRate(rate: Float) = update { it.copy(speechRate = rate) }
    override suspend fun setRidingState(riding: RidingState) = update { it.copy(ridingState = riding) }
    override suspend fun setAnnounceSender(announce: Boolean) = update {
        it.copy(announceSender = announce)
    }
    override suspend fun performMigrationIfNeeded() = Unit
    override suspend fun resetAllSettings() = update { AppSettings() }

    private fun update(transform: (AppSettings) -> AppSettings) {
        failure?.let { throw it }
        state.value = transform(state.value)
    }
}
