package com.ridenotify.app.wa_reader.speech

import com.ridenotify.app.wa_reader.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Testable boundary around the process-owned Android TTS engine. */
interface TtsEngine {
    val state: StateFlow<TtsEngineState>
    val utteranceEvents: Flow<TtsUtteranceEvent>

    fun speak(text: String, speechRate: Float): TtsSpeakResult
    fun stop()
    fun shutdown()
}

sealed interface TtsEngineState {
    data object Initializing : TtsEngineState
    /**
     * The engine can attempt Indonesian speech after locale and voice checks.
     * A completed synthetic test utterance remains the authoritative playback check (ADR-011).
     */
    data class Ready(val locale: Locale) : TtsEngineState
    data class Unavailable(val reason: TtsUnavailableReason) : TtsEngineState
    data object Shutdown : TtsEngineState
}

enum class TtsUnavailableReason {
    INITIALIZATION_FAILED,
    INDONESIAN_LOCALE_UNAVAILABLE,
    INDONESIAN_VOICE_UNAVAILABLE,
    LANGUAGE_CONFIGURATION_FAILED,
    PLATFORM_ERROR,
}

sealed interface TtsSpeakResult {
    data class Accepted(val utteranceId: String) : TtsSpeakResult
    data class Rejected(val reason: TtsSpeakRejection) : TtsSpeakResult
}

enum class TtsSpeakRejection { ENGINE_NOT_READY, ENGINE_BUSY, INVALID_REQUEST, ENGINE_ERROR }

sealed interface TtsUtteranceEvent {
    val utteranceId: String

    data class Started(override val utteranceId: String) : TtsUtteranceEvent
    data class Completed(override val utteranceId: String) : TtsUtteranceEvent
    data class Failed(
        override val utteranceId: String,
        val reason: TtsFailure,
    ) : TtsUtteranceEvent

    data class Stopped(override val utteranceId: String) : TtsUtteranceEvent
}

enum class TtsFailure { ENGINE_ERROR, TIMEOUT }

internal fun isValidSpeechRate(speechRate: Float): Boolean =
    speechRate.isFinite() && speechRate in AppSettings.MIN_SPEECH_RATE..AppSettings.MAX_SPEECH_RATE
