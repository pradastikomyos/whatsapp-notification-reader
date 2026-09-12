package com.ridenotify.app.wa_reader.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidTtsEngineTest {

    @Test
    fun `initialization verifies both Indonesian language and installed voice`() = runTest {
        val factory = FakeClientFactory()
        val engine = engine(factory)

        assertEquals(TtsEngineState.Initializing, engine.state.value)
        factory.initialize(TextToSpeech.SUCCESS)

        val ready = assertIs<TtsEngineState.Ready>(engine.state.value)
        assertEquals(Locale.forLanguageTag("id-ID"), ready.locale)
        assertEquals("local-id", factory.client.voiceSetTo?.name)
    }

    @Test
    fun `missing Indonesian voice fails closed even when language query is available`() = runTest {
        val factory = FakeClientFactory()
        factory.client.voices = setOf(factory.client.voice(locale = Locale.US))
        val engine = engine(factory)

        factory.initialize(TextToSpeech.SUCCESS)

        assertEquals(
            TtsEngineState.Unavailable(TtsUnavailableReason.INDONESIAN_VOICE_UNAVAILABLE),
            engine.state.value,
        )
        assertEquals(
            TtsSpeakResult.Rejected(TtsSpeakRejection.ENGINE_NOT_READY),
            engine.speak("pesan uji", 1f),
        )
    }

    @Test
    fun `network-required Indonesian voice is rejected`() = runTest {
        val factory = FakeClientFactory()
        factory.client.voices = setOf(
            factory.client.voice(name = "network-id", networkRequired = true),
        )
        val engine = engine(factory)

        factory.initialize(TextToSpeech.SUCCESS)

        assertEquals(
            TtsEngineState.Unavailable(TtsUnavailableReason.INDONESIAN_VOICE_UNAVAILABLE),
            engine.state.value,
        )
        assertEquals(null, factory.client.voiceSetTo)
    }

    @Test
    fun `installed local Indonesian voice is selected instead of network voice`() = runTest {
        val factory = FakeClientFactory()
        factory.client.voices = setOf(
            factory.client.voice(name = "network-id", networkRequired = true),
            factory.client.voice(name = "local-id"),
        )
        val engine = engine(factory)

        factory.initialize(TextToSpeech.SUCCESS)

        assertIs<TtsEngineState.Ready>(engine.state.value)
        assertEquals("local-id", factory.client.voiceSetTo?.name)
    }

    @Test
    fun `not-installed Indonesian voice is rejected`() = runTest {
        val factory = FakeClientFactory()
        factory.client.voices = setOf(factory.client.voice(installed = false))
        val engine = engine(factory)

        factory.initialize(TextToSpeech.SUCCESS)

        assertEquals(
            TtsEngineState.Unavailable(TtsUnavailableReason.INDONESIAN_VOICE_UNAVAILABLE),
            engine.state.value,
        )
    }

    @Test
    fun `initialization failure and language configuration failures remain unavailable`() = runTest {
        val initFactory = FakeClientFactory()
        val initEngine = engine(initFactory)
        initFactory.initialize(TextToSpeech.ERROR)
        assertEquals(
            TtsEngineState.Unavailable(TtsUnavailableReason.INITIALIZATION_FAILED),
            initEngine.state.value,
        )

        val localeFactory = FakeClientFactory().also {
            it.client.voiceResult = TextToSpeech.ERROR
        }
        val localeEngine = engine(localeFactory)
        localeFactory.initialize(TextToSpeech.SUCCESS)
        assertEquals(
            TtsEngineState.Unavailable(TtsUnavailableReason.LANGUAGE_CONFIGURATION_FAILED),
            localeEngine.state.value,
        )
    }

    @Test
    fun `platform exceptions and invalid requests fail closed`() = runTest {
        val factory = FakeClientFactory().also {
            it.client.throwOnLanguageQuery = true
        }
        val engine = engine(factory)
        factory.initialize(TextToSpeech.SUCCESS)

        assertEquals(
            TtsEngineState.Unavailable(TtsUnavailableReason.PLATFORM_ERROR),
            engine.state.value,
        )
        assertEquals(
            TtsSpeakResult.Rejected(TtsSpeakRejection.INVALID_REQUEST),
            engine.speak("", 1f),
        )
        assertEquals(
            TtsSpeakResult.Rejected(TtsSpeakRejection.INVALID_REQUEST),
            engine.speak("pesan", Float.NaN),
        )
    }

    @Test
    fun `accepted utterances use unique IDs and expose progress callbacks`() = runTest {
        val factory = FakeClientFactory()
        val engine = engine(factory)
        factory.initialize(TextToSpeech.SUCCESS)
        val events = mutableListOf<TtsUtteranceEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.utteranceEvents.collect(events::add)
        }

        val first = assertIs<TtsSpeakResult.Accepted>(engine.speak("pesan pertama", 1.25f))
        assertEquals(1.25f, factory.client.speechRate)
        factory.client.emit(TtsUtteranceEvent.Started(first.utteranceId))
        factory.client.emit(TtsUtteranceEvent.Completed(first.utteranceId))
        val second = assertIs<TtsSpeakResult.Accepted>(engine.speak("pesan kedua", 0.75f))

        assertTrue(first.utteranceId != second.utteranceId)
        assertEquals(
            listOf(
                TtsUtteranceEvent.Started(first.utteranceId),
                TtsUtteranceEvent.Completed(first.utteranceId),
            ),
            events,
        )
        collector.cancel()
    }

    @Test
    fun `busy and native speak failure have deterministic results`() = runTest {
        val factory = FakeClientFactory()
        val engine = engine(factory)
        factory.initialize(TextToSpeech.SUCCESS)
        val accepted = assertIs<TtsSpeakResult.Accepted>(engine.speak("satu", 1f))
        assertEquals(
            TtsSpeakResult.Rejected(TtsSpeakRejection.ENGINE_BUSY),
            engine.speak("dua", 1f),
        )
        factory.client.emit(TtsUtteranceEvent.Completed(accepted.utteranceId))
        factory.client.speakResult = TextToSpeech.ERROR
        val failed = engine.speak("tiga", 1f)

        assertEquals(TtsSpeakResult.Rejected(TtsSpeakRejection.ENGINE_ERROR), failed)
    }

    @Test
    fun `timeout and explicit stop stop native speech and terminate once`() = runTest {
        val factory = FakeClientFactory()
        val engine = engine(factory, timeoutMillis = 100)
        factory.initialize(TextToSpeech.SUCCESS)
        val events = mutableListOf<TtsUtteranceEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.utteranceEvents.collect(events::add)
        }

        val timedOut = assertIs<TtsSpeakResult.Accepted>(engine.speak("macet", 1f))
        advanceTimeBy(100)
        runCurrent()
        assertTrue(factory.client.stopCalls == 1)
        assertTrue(events.contains(TtsUtteranceEvent.Failed(timedOut.utteranceId, TtsFailure.TIMEOUT)))

        val stopped = assertIs<TtsSpeakResult.Accepted>(engine.speak("berhenti", 1f))
        engine.stop()
        assertTrue(events.contains(TtsUtteranceEvent.Stopped(stopped.utteranceId)))
        collector.cancel()
    }

    @Test
    fun `shutdown releases client and ignores late initialization or progress`() = runTest {
        val factory = FakeClientFactory()
        val engine = engine(factory)

        engine.shutdown()
        factory.initialize(TextToSpeech.SUCCESS)
        factory.client.emit(TtsUtteranceEvent.Completed("late"))

        assertEquals(TtsEngineState.Shutdown, engine.state.value)
        assertEquals(1, factory.client.shutdownCalls)
        assertEquals(
            TtsSpeakResult.Rejected(TtsSpeakRejection.ENGINE_NOT_READY),
            engine.speak("tidak boleh", 1f),
        )
    }

    private fun TestScope.engine(factory: FakeClientFactory, timeoutMillis: Long = 45_000): AndroidTtsEngine =
        AndroidTtsEngine(
            context = RuntimeEnvironment.getApplication(),
            scope = backgroundScope,
            clientFactory = factory,
            utteranceTimeoutMillis = timeoutMillis,
        )

    private class FakeClientFactory : TextToSpeechClientFactory {
        val client = FakeTextToSpeechClient()
        private lateinit var onInitialized: (Int) -> Unit

        override fun create(context: Context, onInitialized: (Int) -> Unit): TextToSpeechClient {
            this.onInitialized = onInitialized
            return client
        }

        fun initialize(status: Int) = onInitialized(status)
    }

    private class FakeTextToSpeechClient : TextToSpeechClient {
        var languageAvailability = TextToSpeech.LANG_AVAILABLE
        var throwOnLanguageQuery = false
        var voices: Set<TtsVoiceMetadata> = setOf(voice())
        var voiceResult = TextToSpeech.SUCCESS
        var speakResult = TextToSpeech.SUCCESS
        var voiceSetTo: TtsVoiceMetadata? = null
        var speechRate: Float? = null
        var stopCalls = 0
        var shutdownCalls = 0
        private var progressListener: ((TtsUtteranceEvent) -> Unit)? = null

        override fun isLanguageAvailable(locale: Locale): Int {
            if (throwOnLanguageQuery) error("engine unavailable")
            return languageAvailability
        }
        override fun availableVoices(): Set<TtsVoiceMetadata> = voices
        override fun setVoice(voice: TtsVoiceMetadata): Int {
            voiceSetTo = voice
            return voiceResult
        }

        fun voice(
            name: String = "local-id",
            locale: Locale = Locale.forLanguageTag("id-ID"),
            installed: Boolean = true,
            networkRequired: Boolean = false,
        ) = TtsVoiceMetadata(name, locale, installed, networkRequired)

        override fun setSpeechRate(rate: Float) {
            speechRate = rate
        }

        override fun speak(text: String, utteranceId: String): Int = speakResult
        override fun stop() {
            stopCalls++
        }

        override fun shutdown() {
            shutdownCalls++
        }

        override fun setProgressListener(listener: (TtsUtteranceEvent) -> Unit) {
            progressListener = listener
        }

        fun emit(event: TtsUtteranceEvent) {
            progressListener?.invoke(event)
        }
    }
}
