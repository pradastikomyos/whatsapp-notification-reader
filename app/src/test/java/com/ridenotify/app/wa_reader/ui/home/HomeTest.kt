package com.ridenotify.app.wa_reader.ui.home

import com.ridenotify.app.wa_reader.R
import com.ridenotify.app.wa_reader.data.settings.SettingsRepository
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.RidingState
import com.ridenotify.app.wa_reader.speech.TestSpeechState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HomeTest {
    @Test
    fun `reader toggle and speech rate update the shared settings source`() = runTest {
        val settings = HomeSettingsRepository(AppSettings())
        val controller = HomeSettingsController(settings)

        assertEquals(HomeUpdateResult.APPLIED, controller.setReaderEnabled(true))
        assertEquals(HomeUpdateResult.APPLIED, controller.setSpeechRate(1.4f))

        assertEquals(true, settings.current.readerEnabled)
        assertEquals(1.4f, settings.current.speechRate)
    }

    @Test
    fun `failed settings write is reported`() = runTest {
        val settings = HomeSettingsRepository(AppSettings()).apply { failure = IllegalStateException("store unavailable") }

        assertEquals(HomeUpdateResult.FAILED, HomeSettingsController(settings).setReaderEnabled(true))
        assertEquals(false, settings.current.readerEnabled)
    }

    @Test
    fun `cancellation is propagated`() = runTest {
        val settings = HomeSettingsRepository(AppSettings()).apply {
            failure = CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> { HomeSettingsController(settings).setSpeechRate(1.1f) }
    }

    @Test
    fun `test speech status never claims success before completion`() {
        assertEquals(R.string.home_test_speech_not_run, testSpeechDescription(TestSpeechState.NotRun, true))
        assertEquals(R.string.home_test_speech_pending, testSpeechDescription(TestSpeechState.Pending, true))
        assertEquals(R.string.home_test_speech_speaking, testSpeechDescription(TestSpeechState.Speaking, true))
        assertEquals(R.string.home_test_speech_succeeded, testSpeechDescription(TestSpeechState.Succeeded, true))
        assertEquals(R.string.home_test_speech_failed, testSpeechDescription(TestSpeechState.Failed, true))
        assertEquals(R.string.home_test_speech_unavailable, testSpeechDescription(TestSpeechState.NotRun, false))
    }
}

private class HomeSettingsRepository(initial: AppSettings) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    val current get() = state.value
    var failure: Throwable? = null

    override fun observeSettings(): Flow<AppSettings> = state
    override suspend fun getSettings(): AppSettings = state.value
    override suspend fun setReaderEnabled(enabled: Boolean) = updateAfterFailure { it.copy(readerEnabled = enabled) }
    override suspend fun setReadPrivateMessages(enabled: Boolean) = updateAfterFailure { it.copy(readPrivateMessages = enabled) }
    override suspend fun setSpeechRate(rate: Float) = updateAfterFailure { it.copy(speechRate = rate) }
    override suspend fun setRidingState(riding: RidingState) = updateAfterFailure { it.copy(ridingState = riding) }
    override suspend fun setAnnounceSender(announce: Boolean) = updateAfterFailure {
        it.copy(announceSender = announce)
    }
    override suspend fun performMigrationIfNeeded() = Unit
    override suspend fun resetAllSettings() = updateAfterFailure { AppSettings() }

    private fun updateAfterFailure(transform: (AppSettings) -> AppSettings) {
        failure?.let { throw it }
        state.value = transform(state.value)
    }
}
