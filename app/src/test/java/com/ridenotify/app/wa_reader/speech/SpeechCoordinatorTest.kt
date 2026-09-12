package com.ridenotify.app.wa_reader.speech

import com.ridenotify.app.wa_reader.model.ConversationId
import com.ridenotify.app.wa_reader.model.SpeechRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechCoordinatorTest {
    @Test
    fun `same conversation burst is merged when the active utterance completes`() = runTest {
        val fixture = Fixture(backgroundScope, now = 1_000L)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("a", "pertama", 1_000L))
        runCurrent()
        fixture.coordinator.enqueue(fixture.request("a", "kedua", 1_001L))
        fixture.coordinator.enqueue(fixture.request("a", "ketiga", 1_002L))

        fixture.tts.completeLatest()
        runCurrent()

        assertEquals(listOf("pertama", "kedua, ketiga"), fixture.tts.spokenText)
        assertEquals(2, fixture.focus.requestCount)
    }

    @Test
    fun `aggregation keeps original arrival order when post times arrive out of order`() = runTest {
        val fixture = Fixture(backgroundScope, now = 1_000L)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("active", "active", 1_000L))
        runCurrent()
        fixture.coordinator.enqueue(fixture.request("burst", "arrived pertama", 1_020L))
        fixture.coordinator.enqueue(fixture.request("burst", "arrived kedua", 1_010L))

        fixture.tts.completeLatest()
        runCurrent()

        assertEquals(listOf("active", "arrived pertama, arrived kedua"), fixture.tts.spokenText)
    }

    @Test
    fun `overflow drops oldest pending but never active speech`() = runTest {
        val fixture = Fixture(backgroundScope, now = 10_000L)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("active", "active", 10_000L))
        runCurrent()
        repeat(21) { index ->
            fixture.coordinator.enqueue(fixture.request("c$index", "m$index", 10_001L + index))
        }

        fixture.tts.completeLatest()
        runCurrent()

        assertEquals(listOf("active", "m1"), fixture.tts.spokenText)
    }

    @Test
    fun `focus loss skips active and waits for gain before next item`() = runTest {
        val fixture = Fixture(backgroundScope, now = 50_000L)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("a", "active", 50_000L))
        runCurrent()
        fixture.coordinator.enqueue(fixture.request("b", "next", 50_001L))

        fixture.focus.dispatch(AudioFocusChange.LOST_TRANSIENTLY)
        runCurrent()
        assertEquals(listOf("active"), fixture.tts.spokenText)
        assertEquals(1, fixture.tts.stopCount)

        fixture.focus.dispatch(AudioFocusChange.GAINED)
        runCurrent()
        assertEquals(listOf("active", "next"), fixture.tts.spokenText)
    }

    @Test
    fun `permanent loss after transient loss releases focus and continues queue`() = runTest {
        val fixture = Fixture(backgroundScope, now = 50_000L)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("a", "active", 50_000L))
        runCurrent()
        fixture.coordinator.enqueue(fixture.request("b", "next", 50_001L))

        fixture.focus.dispatch(AudioFocusChange.LOST_TRANSIENTLY)
        runCurrent()
        fixture.focus.dispatch(AudioFocusChange.LOST)
        runCurrent()

        assertEquals(1, fixture.focus.releaseCount)
        assertEquals(listOf("active", "next"), fixture.tts.spokenText)
    }

    @Test
    fun `reader disable stops active and flushes pending content`() = runTest {
        val fixture = Fixture(backgroundScope, now = 90_000L)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("a", "active", 90_000L))
        runCurrent()
        fixture.coordinator.enqueue(fixture.request("b", "discard", 90_001L))

        fixture.coordinator.updateEligibility(isReaderEnabled = false, isRidingActive = true)
        fixture.coordinator.updateEligibility(isReaderEnabled = true, isRidingActive = true)
        runCurrent()

        assertEquals(listOf("active"), fixture.tts.spokenText)
        assertEquals(1, fixture.tts.stopCount)
        assertEquals(1, fixture.focus.releaseCount)
        assertTrue(fixture.gate.stopCount >= 1)
    }

    @Test
    fun `test speech bypasses reader and riding gates but uses shared playback`() = runTest {
        val fixture = Fixture(backgroundScope, now = 90_000L)

        val result = fixture.coordinator.testSpeech("Ini adalah uji suara RideNotify")
        runCurrent()

        assertEquals(TestSpeechRequestResult.ACCEPTED, result)
        assertEquals(TestSpeechState.Speaking, fixture.coordinator.testSpeechState.value)
        assertEquals(listOf("Ini adalah uji suara RideNotify"), fixture.tts.spokenText)
        assertEquals(1, fixture.focus.requestCount)

        fixture.tts.completeLatest()
        runCurrent()

        assertEquals(TestSpeechState.Succeeded, fixture.coordinator.testSpeechState.value)
        assertEquals(1, fixture.focus.releaseCount)
    }

    @Test
    fun `test speech is queued behind the one active utterance`() = runTest {
        val fixture = Fixture(backgroundScope, now = 90_000L)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("message", "notifikasi", 90_000L))
        runCurrent()

        assertEquals(
            TestSpeechRequestResult.ACCEPTED,
            fixture.coordinator.testSpeech("uji suara"),
        )
        assertEquals(TestSpeechState.Pending, fixture.coordinator.testSpeechState.value)

        fixture.tts.completeLatest()
        runCurrent()

        assertEquals(listOf("notifikasi", "uji suara"), fixture.tts.spokenText)
        assertEquals(TestSpeechState.Speaking, fixture.coordinator.testSpeechState.value)
    }

    @Test
    fun `pending test speech is failed when user disables the reader`() = runTest {
        val fixture = Fixture(backgroundScope, now = 90_000L)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("message", "notifikasi", 90_000L))
        runCurrent()
        fixture.coordinator.testSpeech("uji suara")

        fixture.coordinator.updateEligibility(isReaderEnabled = false, isRidingActive = true)

        assertEquals(TestSpeechState.Failed, fixture.coordinator.testSpeechState.value)
    }

    @Test
    fun `settings stream failure fails closed and flushes authorization`() = runTest {
        val tts = FakeTts()
        val coordinator = SpeechCoordinator(
            ttsEngine = tts,
            audioFocusController = FakeFocus(),
            foregroundPlaybackGate = FakeGate(),
            scope = backgroundScope,
            clockMillis = { 1_000L },
        )
        coordinator.start(
            speechRequests = emptyFlow(),
            settings = flow {
                emit(
                    com.ridenotify.app.wa_reader.model.AppSettings(
                        readerEnabled = true,
                        ridingState = com.ridenotify.app.wa_reader.model.RidingState.ACTIVE,
                    ),
                )
                error("settings unavailable")
            },
        )
        runCurrent()

        coordinator.enqueue(
            SpeechRequest(ConversationId("direct"), "tidak boleh dibaca", postedAtMillis = 1_000L),
        )
        assertTrue(tts.spokenText.isEmpty())
    }

    @Test
    fun `coordinator cancellation stops speech and releases platform resources`() = runTest {
        val tts = FakeTts()
        val focus = FakeFocus()
        val gate = FakeGate()
        val coordinator = SpeechCoordinator(
            ttsEngine = tts,
            audioFocusController = focus,
            foregroundPlaybackGate = gate,
            scope = backgroundScope,
            clockMillis = { 1_000L },
        )
        val job = coordinator.start(
            speechRequests = emptyFlow(),
            settings = MutableSharedFlow(),
        )
        runCurrent()
        coordinator.updateEligibility(isReaderEnabled = true, isRidingActive = true)
        coordinator.enqueue(SpeechRequest(ConversationId("direct"), "aktif", 1_000L))
        runCurrent()

        job.cancelAndJoin()

        assertEquals(1, tts.stopCount)
        assertEquals(1, focus.releaseCount)
        assertTrue(gate.stopCount >= 1)

        coordinator.enqueue(SpeechRequest(ConversationId("direct"), "setelah restart", 1_000L))
        assertEquals(listOf("aktif"), tts.spokenText)
    }

    @Test
    fun `reader disable while foreground promotion is suspended invalidates selected item`() = runTest {
        val gate = SuspendingGate()
        val fixture = Fixture(backgroundScope, now = 1_000L, gate = gate)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("direct", "selected", 1_000L))
        runCurrent()
        assertTrue(gate.entered.isCompleted)

        fixture.coordinator.updateEligibility(isReaderEnabled = false, isRidingActive = true)
        gate.promotion.complete(ForegroundPlaybackPromotion.Promoted)
        runCurrent()

        assertTrue(fixture.tts.spokenText.isEmpty())
        assertEquals(0, fixture.focus.requestCount)
        assertTrue(gate.stopCount >= 1)
    }

    @Test
    fun `riding disable while foreground promotion is suspended invalidates selected item`() = runTest {
        val gate = SuspendingGate()
        val fixture = Fixture(backgroundScope, now = 1_000L, gate = gate)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("direct", "selected", 1_000L))
        runCurrent()

        fixture.coordinator.updateEligibility(isReaderEnabled = true, isRidingActive = false)
        gate.promotion.complete(ForegroundPlaybackPromotion.Promoted)
        runCurrent()

        assertTrue(fixture.tts.spokenText.isEmpty())
        assertEquals(0, fixture.focus.requestCount)
    }

    @Test
    fun `explicit clear while foreground promotion is suspended cannot speak stale item`() = runTest {
        val gate = SuspendingGate()
        val fixture = Fixture(backgroundScope, now = 1_000L, gate = gate)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("direct", "selected", 1_000L))
        runCurrent()

        fixture.coordinator.stopAndClear()
        gate.promotion.complete(ForegroundPlaybackPromotion.Promoted)
        runCurrent()

        assertTrue(fixture.tts.spokenText.isEmpty())
        assertEquals(0, fixture.focus.requestCount)
        assertTrue(gate.stopCount >= 1)
    }

    @Test
    fun `duplicate test speech is busy while foreground promotion is suspended`() = runTest {
        val gate = SuspendingGate()
        val fixture = Fixture(backgroundScope, now = 1_000L, gate = gate)
        assertEquals(TestSpeechRequestResult.ACCEPTED, fixture.coordinator.testSpeech("first test"))
        runCurrent()
        assertTrue(gate.entered.isCompleted)

        assertEquals(TestSpeechRequestResult.BUSY, fixture.coordinator.testSpeech("duplicate test"))
        assertEquals(TestSpeechState.Pending, fixture.coordinator.testSpeechState.value)

        fixture.coordinator.stopAndClear()
    }

    @Test
    fun `explicit clear fails preparing test speech during suspended promotion`() = runTest {
        val gate = SuspendingGate()
        val fixture = Fixture(backgroundScope, now = 1_000L, gate = gate)
        fixture.coordinator.testSpeech("test speech")
        runCurrent()

        fixture.coordinator.stopAndClear()

        assertEquals(TestSpeechState.Failed, fixture.coordinator.testSpeechState.value)
        assertEquals(0, fixture.focus.requestCount)
    }

    @Test
    fun `eligibility flush fails preparing test speech during suspended promotion`() = runTest {
        val gate = SuspendingGate()
        val fixture = Fixture(backgroundScope, now = 1_000L, gate = gate)
        fixture.coordinator.testSpeech("test speech")
        runCurrent()

        fixture.coordinator.updateEligibility(isReaderEnabled = false, isRidingActive = false)

        assertEquals(TestSpeechState.Failed, fixture.coordinator.testSpeechState.value)
        assertEquals(0, fixture.focus.requestCount)
    }

    @Test
    fun `transient focus timeout clears interrupted queue and permits fresh work`() = runTest {
        val fixture = Fixture(backgroundScope, now = 50_000L, interruptionTimeoutMillis = 100L)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("a", "active", 50_000L))
        runCurrent()
        fixture.coordinator.enqueue(fixture.request("b", "expired pending", 50_001L))

        fixture.focus.dispatch(AudioFocusChange.LOST_TRANSIENTLY)
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(1, fixture.focus.releaseCount)
        assertTrue(fixture.gate.stopCount >= 1)
        fixture.coordinator.enqueue(fixture.request("c", "fresh", 50_002L))
        runCurrent()
        assertEquals(listOf("active", "fresh"), fixture.tts.spokenText)
    }

    @Test
    fun `focus gain cancels transient timeout and resumes pending work`() = runTest {
        val fixture = Fixture(backgroundScope, now = 50_000L, interruptionTimeoutMillis = 100L)
        fixture.enable()
        fixture.coordinator.enqueue(fixture.request("a", "active", 50_000L))
        runCurrent()
        fixture.coordinator.enqueue(fixture.request("b", "resumed", 50_001L))
        fixture.focus.dispatch(AudioFocusChange.LOST_TRANSIENTLY)
        runCurrent()

        advanceTimeBy(50L)
        fixture.focus.dispatch(AudioFocusChange.GAINED)
        runCurrent()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(listOf("active", "resumed"), fixture.tts.spokenText)
        assertEquals(0, fixture.focus.releaseCount)
    }

    private class Fixture(
        scope: kotlinx.coroutines.CoroutineScope,
        private val now: Long,
        val gate: CountingGate = FakeGate(),
        interruptionTimeoutMillis: Long = SpeechCoordinator.INTERRUPTION_TIMEOUT_MILLIS,
    ) {
        val tts = FakeTts()
        val focus = FakeFocus()
        val coordinator = SpeechCoordinator(
            ttsEngine = tts,
            audioFocusController = focus,
            foregroundPlaybackGate = gate,
            scope = scope,
            clockMillis = { now },
            interruptionTimeoutMillis = interruptionTimeoutMillis,
        )

        init {
            coordinator.start(emptyFlow(), MutableSharedFlow())
        }

        suspend fun enable() {
            coordinator.updateEligibility(isReaderEnabled = true, isRidingActive = true)
        }

        fun request(conversation: String, text: String, postedAt: Long) = SpeechRequest(
            conversationId = ConversationId(conversation),
            text = text,
            postedAtMillis = postedAt,
        )
    }

    private class FakeTts : TtsEngine {
        override val state = MutableStateFlow<TtsEngineState>(TtsEngineState.Ready(AndroidTtsEngine.INDONESIAN_LOCALE))
        private val events = MutableSharedFlow<TtsUtteranceEvent>(replay = 1, extraBufferCapacity = 8)
        override val utteranceEvents: Flow<TtsUtteranceEvent> = events
        val spokenText = mutableListOf<String>()
        private val utteranceIds = mutableListOf<String>()
        var stopCount = 0

        override fun speak(text: String, speechRate: Float): TtsSpeakResult {
            val id = "utterance-${utteranceIds.size}"
            spokenText += text
            utteranceIds += id
            return TtsSpeakResult.Accepted(id)
        }

        suspend fun completeLatest() {
            events.emit(TtsUtteranceEvent.Completed(utteranceIds.last()))
        }

        override fun stop() {
            stopCount++
        }

        override fun shutdown() = Unit
    }

    private class FakeFocus : AudioFocusController {
        private var listener: AudioFocusChangeListener? = null
        var requestCount = 0
        var releaseCount = 0

        override fun requestFocus(listener: AudioFocusChangeListener): AudioFocusRequestResult {
            requestCount++
            this.listener = listener
            return AudioFocusRequestResult.GRANTED
        }

        override fun releaseFocusAfterTerminalUtterance() {
            releaseCount++
            listener = null
        }

        fun dispatch(change: AudioFocusChange) {
            listener?.onAudioFocusChange(change)
        }
    }

    private interface CountingGate : ForegroundPlaybackGate {
        val stopCount: Int
    }

    private class FakeGate : CountingGate {
        override var stopCount = 0
        override suspend fun ensurePromoted() = ForegroundPlaybackPromotion.NotRequired
        override suspend fun stop() {
            stopCount++
        }
    }

    private class SuspendingGate : CountingGate {
        val entered = CompletableDeferred<Unit>()
        val promotion = CompletableDeferred<ForegroundPlaybackPromotion>()
        override var stopCount = 0

        override suspend fun ensurePromoted(): ForegroundPlaybackPromotion {
            entered.complete(Unit)
            return promotion.await()
        }

        override suspend fun stop() {
            stopCount++
        }
    }
}
