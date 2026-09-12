package com.ridenotify.app.wa_reader.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/** Native [TextToSpeech] adapter. Its process/service owner calls [shutdown] when released. */
class AndroidTtsEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val clientFactory: TextToSpeechClientFactory = AndroidTextToSpeechClientFactory,
    private val utteranceTimeoutMillis: Long = DEFAULT_UTTERANCE_TIMEOUT_MILLIS,
) : TtsEngine {
    private val lock = Any()
    private val _state = MutableStateFlow<TtsEngineState>(TtsEngineState.Initializing)
    private val _utteranceEvents = MutableSharedFlow<TtsUtteranceEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)

    private var client: TextToSpeechClient? = null
    private var activeUtteranceId: String? = null
    private var timeoutJob: Job? = null
    private var isShutdown = false
    private var pendingInitializationStatus: Int? = null

    override val state: StateFlow<TtsEngineState> = _state.asStateFlow()
    override val utteranceEvents: Flow<TtsUtteranceEvent> = _utteranceEvents.asSharedFlow()

    init {
        require(utteranceTimeoutMillis > 0) { "utteranceTimeoutMillis must be positive" }
        try {
            val createdClient = clientFactory.create(context, ::onInitialized)
            synchronized(lock) {
                client = createdClient
            }
            createdClient.setProgressListener(::onProgress)
            val pendingStatus = synchronized(lock) {
                pendingInitializationStatus.also { pendingInitializationStatus = null }
            }
            pendingStatus?.let(::onInitialized)
        } catch (_: Exception) {
            _state.value = TtsEngineState.Unavailable(TtsUnavailableReason.PLATFORM_ERROR)
        }
    }

    override fun speak(text: String, speechRate: Float): TtsSpeakResult {
        if (text.isBlank() || !isValidSpeechRate(speechRate)) {
            return TtsSpeakResult.Rejected(TtsSpeakRejection.INVALID_REQUEST)
        }

        val utteranceId = UUID.randomUUID().toString()
        var rejected: TtsSpeakRejection? = null
        synchronized(lock) {
            if (_state.value !is TtsEngineState.Ready) {
                rejected = TtsSpeakRejection.ENGINE_NOT_READY
            } else if (activeUtteranceId != null) {
                rejected = TtsSpeakRejection.ENGINE_BUSY
            } else {
                val activeClient = client
                if (activeClient == null) {
                    rejected = TtsSpeakRejection.ENGINE_NOT_READY
                } else {
                    try {
                        activeClient.setSpeechRate(speechRate)
                        activeUtteranceId = utteranceId
                        val result = activeClient.speak(text, utteranceId)
                        if (result != TextToSpeech.SUCCESS) {
                            activeUtteranceId = null
                            rejected = TtsSpeakRejection.ENGINE_ERROR
                        } else if (activeUtteranceId == utteranceId) {
                            timeoutJob = scope.launch {
                                delay(utteranceTimeoutMillis)
                                onTimedOut(utteranceId)
                            }
                        }
                    } catch (_: Exception) {
                        activeUtteranceId = null
                        rejected = TtsSpeakRejection.ENGINE_ERROR
                    }
                }
            }
        }

        // A synchronous platform rejection has no active utterance to await. Returning it as
        // Accepted would leave the coordinator waiting for an event that can never arrive.
        return rejected?.let(TtsSpeakResult::Rejected) ?: TtsSpeakResult.Accepted(utteranceId)
    }

    override fun stop() {
        val stoppedId = synchronized(lock) {
            val id = activeUtteranceId ?: return
            activeUtteranceId = null
            timeoutJob?.cancel()
            timeoutJob = null
            runCatching { client?.stop() }
            id
        }
        emit(TtsUtteranceEvent.Stopped(stoppedId))
    }

    override fun shutdown() {
        val stoppedId: String?
        val clientToShutdown: TextToSpeechClient?
        synchronized(lock) {
            if (isShutdown) return
            isShutdown = true
            stoppedId = activeUtteranceId
            activeUtteranceId = null
            timeoutJob?.cancel()
            timeoutJob = null
            clientToShutdown = client
            client = null
            _state.value = TtsEngineState.Shutdown
        }
        runCatching { clientToShutdown?.stop() }
        runCatching { clientToShutdown?.shutdown() }
        stoppedId?.let { emit(TtsUtteranceEvent.Stopped(it)) }
    }

    private fun onInitialized(status: Int) {
        synchronized(lock) {
            if (isShutdown || _state.value !is TtsEngineState.Initializing) return
            if (client == null) {
                // Android initializes asynchronously, but this also keeps synchronous test/OEM
                // callbacks from being interpreted as a missing engine.
                pendingInitializationStatus = status
                return
            }
            if (status != TextToSpeech.SUCCESS) {
                _state.value = TtsEngineState.Unavailable(TtsUnavailableReason.INITIALIZATION_FAILED)
                return
            }

            val activeClient = client
            _state.value = try {
                when {
                    activeClient == null -> TtsEngineState.Unavailable(TtsUnavailableReason.PLATFORM_ERROR)
                    activeClient.isLanguageAvailable(INDONESIAN_LOCALE) < TextToSpeech.LANG_AVAILABLE ->
                        TtsEngineState.Unavailable(TtsUnavailableReason.INDONESIAN_LOCALE_UNAVAILABLE)
                    else -> {
                        val localVoice = activeClient.availableVoices()
                            .filter(::isUsableIndonesianVoice)
                            .minByOrNull(TtsVoiceMetadata::name)
                        when {
                            localVoice == null ->
                                TtsEngineState.Unavailable(TtsUnavailableReason.INDONESIAN_VOICE_UNAVAILABLE)
                            activeClient.setVoice(localVoice) != TextToSpeech.SUCCESS ->
                                TtsEngineState.Unavailable(TtsUnavailableReason.LANGUAGE_CONFIGURATION_FAILED)
                            else -> TtsEngineState.Ready(INDONESIAN_LOCALE)
                        }
                    }
                }
            } catch (_: Exception) {
                TtsEngineState.Unavailable(TtsUnavailableReason.PLATFORM_ERROR)
            }
        }
    }

    private fun onTimedOut(utteranceId: String) {
        val timedOut = synchronized(lock) {
            if (activeUtteranceId != utteranceId) return
            activeUtteranceId = null
            timeoutJob = null
            runCatching { client?.stop() }
            true
        }
        if (timedOut) emit(TtsUtteranceEvent.Failed(utteranceId, TtsFailure.TIMEOUT))
    }

    private fun onProgress(event: TtsUtteranceEvent) {
        val accepted = synchronized(lock) {
            if (activeUtteranceId != event.utteranceId) return
            when (event) {
                is TtsUtteranceEvent.Completed,
                is TtsUtteranceEvent.Failed,
                is TtsUtteranceEvent.Stopped -> {
                    activeUtteranceId = null
                    timeoutJob?.cancel()
                    timeoutJob = null
                }
                is TtsUtteranceEvent.Started -> Unit
            }
            true
        }
        if (accepted) emit(event)
    }

    private fun emit(event: TtsUtteranceEvent) {
        _utteranceEvents.tryEmit(event)
    }

    private fun isUsableIndonesianVoice(voice: TtsVoiceMetadata): Boolean =
        voice.locale.language == INDONESIAN_LOCALE.language &&
            voice.locale.country == INDONESIAN_LOCALE.country &&
            voice.isInstalled &&
            !voice.isNetworkConnectionRequired

    companion object {
        val INDONESIAN_LOCALE: Locale = Locale.forLanguageTag("id-ID")
        const val DEFAULT_UTTERANCE_TIMEOUT_MILLIS = 45_000L
        private const val EVENT_BUFFER_CAPACITY = 16
    }
}

/** Small wrapper so the adapter can be state-tested without a framework engine. */
interface TextToSpeechClient {
    fun isLanguageAvailable(locale: Locale): Int
    fun availableVoices(): Set<TtsVoiceMetadata>
    fun setVoice(voice: TtsVoiceMetadata): Int
    fun setSpeechRate(rate: Float)
    fun speak(text: String, utteranceId: String): Int
    fun stop()
    fun shutdown()
    fun setProgressListener(listener: (TtsUtteranceEvent) -> Unit)
}

data class TtsVoiceMetadata(
    val name: String,
    val locale: Locale,
    val isInstalled: Boolean,
    val isNetworkConnectionRequired: Boolean,
)

fun interface TextToSpeechClientFactory {
    fun create(context: Context, onInitialized: (Int) -> Unit): TextToSpeechClient
}

private object AndroidTextToSpeechClientFactory : TextToSpeechClientFactory {
    override fun create(context: Context, onInitialized: (Int) -> Unit): TextToSpeechClient {
        val textToSpeech = TextToSpeech(context) { status -> onInitialized(status) }
        return AndroidTextToSpeechClient(textToSpeech)
    }
}

private class AndroidTextToSpeechClient(
    private val textToSpeech: TextToSpeech,
) : TextToSpeechClient {
    private var progressListener: ((TtsUtteranceEvent) -> Unit)? = null

    init {
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { progressListener?.invoke(TtsUtteranceEvent.Started(it)) }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { progressListener?.invoke(TtsUtteranceEvent.Completed(it)) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { progressListener?.invoke(TtsUtteranceEvent.Failed(it, TtsFailure.ENGINE_ERROR)) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { progressListener?.invoke(TtsUtteranceEvent.Failed(it, TtsFailure.ENGINE_ERROR)) }
            }
        })
    }

    override fun isLanguageAvailable(locale: Locale): Int = textToSpeech.isLanguageAvailable(locale)
    override fun availableVoices(): Set<TtsVoiceMetadata> = textToSpeech.voices.orEmpty().mapTo(linkedSetOf()) { voice ->
        TtsVoiceMetadata(
            name = voice.name,
            locale = voice.locale,
            isInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features,
            isNetworkConnectionRequired = voice.isNetworkConnectionRequired,
        )
    }

    override fun setVoice(voice: TtsVoiceMetadata): Int {
        val platformVoice = textToSpeech.voices.orEmpty().firstOrNull { it.name == voice.name }
            ?: return TextToSpeech.ERROR
        return textToSpeech.setVoice(platformVoice)
    }
    override fun setSpeechRate(rate: Float) {
        textToSpeech.setSpeechRate(rate)
    }

    override fun speak(text: String, utteranceId: String): Int =
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

    override fun stop() {
        textToSpeech.stop()
    }

    override fun shutdown() {
        textToSpeech.shutdown()
    }

    override fun setProgressListener(listener: (TtsUtteranceEvent) -> Unit) {
        progressListener = listener
    }
}
