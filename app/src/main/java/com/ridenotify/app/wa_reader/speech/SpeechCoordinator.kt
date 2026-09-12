package com.ridenotify.app.wa_reader.speech

import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.RidingState
import com.ridenotify.app.wa_reader.model.SpeechRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The sole owner of pending notification speech and the one active utterance.
 *
 * Policy has already approved every [SpeechRequest] passed here. This class only
 * applies ADR-005's bounded queue, expiry, aggregation, and interruption rules.
 */
class SpeechCoordinator(
    private val ttsEngine: TtsEngine,
    private val audioFocusController: AudioFocusController,
    private val foregroundPlaybackGate: ForegroundPlaybackGate,
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxPendingItems: Int = MAX_PENDING_ITEMS,
    private val maxMessageAgeMillis: Long = MAX_MESSAGE_AGE_MILLIS,
    private val interruptionTimeoutMillis: Long = INTERRUPTION_TIMEOUT_MILLIS,
) {
    private val mutex = Mutex()
    private val pending = mutableListOf<QueuedRequest>()
    private var nextSequence = 0L
    private var active: ActiveUtterance? = null
    private var preparing: PreparingUtterance? = null
    private var promotionJob: Job? = null
    private var interruptionTimeoutJob: Job? = null
    private var foregroundStopInProgress = false
    private var readerEnabled = false
    private var ridingActive = false
    private var waitingForFocusGain = false
    private var currentSpeechRate = AppSettings.DEFAULT_SPEECH_RATE
    private var focusHeld = false
    private var pendingTestSpeech: String? = null
    private val mutableTestSpeechState = MutableStateFlow<TestSpeechState>(TestSpeechState.NotRun)

    val testSpeechState: StateFlow<TestSpeechState> = mutableTestSpeechState.asStateFlow()

    init {
        require(maxPendingItems > 0) { "maxPendingItems must be positive" }
        require(maxMessageAgeMillis > 0) { "maxMessageAgeMillis must be positive" }
        require(interruptionTimeoutMillis > 0) { "interruptionTimeoutMillis must be positive" }
    }

    /** Starts one application-lifetime collector for requests, settings, and TTS events. */
    fun start(
        speechRequests: Flow<SpeechRequest>,
        settings: Flow<AppSettings>,
    ): Job = scope.launch {
        try {
            coroutineScope {
                launch { ttsEngine.utteranceEvents.collect(::onTtsEvent) }
                launch { speechRequests.collect(::enqueue) }
                launch {
                    try {
                        settings.collect { appSettings ->
                            updateEligibility(
                                isReaderEnabled = appSettings.readerEnabled,
                                isRidingActive = appSettings.ridingState == RidingState.ACTIVE,
                                speechRate = appSettings.speechRate,
                            )
                        }
                        // A completed settings stream cannot authorize continued speech.
                        updateEligibility(isReaderEnabled = false, isRidingActive = false)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        // Repository failure must stop existing audio as well as reject new requests.
                        updateEligibility(isReaderEnabled = false, isRidingActive = false)
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                updateEligibility(isReaderEnabled = false, isRidingActive = false)
            }
        }
    }

    suspend fun enqueue(request: SpeechRequest) {
        mutex.withLock {
            if (!readerEnabled || !ridingActive) return
            evictExpiredLocked()
            pending += QueuedRequest(request, nextSequence++)
            pending.sortWith(QUEUED_ORDER)
            while (pending.size > maxPendingItems) pending.removeAt(0)
            drainLocked()
        }
    }

    /** Runs one synthetic utterance through the same focus and playback path as notifications. */
    suspend fun testSpeech(text: String): TestSpeechRequestResult = mutex.withLock {
        if (text.isBlank()) return@withLock TestSpeechRequestResult.INVALID_REQUEST
        if (pendingTestSpeech != null || preparing?.request?.isTest == true || active?.isTest == true) {
            return@withLock TestSpeechRequestResult.BUSY
        }

        pendingTestSpeech = text
        mutableTestSpeechState.value = TestSpeechState.Pending
        drainLocked()
        when (mutableTestSpeechState.value) {
            TestSpeechState.Failed -> TestSpeechRequestResult.REJECTED
            else -> TestSpeechRequestResult.ACCEPTED
        }
    }

    /** Applies the immediate queue-flush behavior for reader/riding disable transitions. */
    suspend fun updateEligibility(
        isReaderEnabled: Boolean,
        isRidingActive: Boolean,
        speechRate: Float = currentSpeechRate,
    ) {
        var stopForeground = false
        mutex.withLock {
            val becameIneligible = (readerEnabled && !isReaderEnabled) || (ridingActive && !isRidingActive)
            readerEnabled = isReaderEnabled
            ridingActive = isRidingActive
            currentSpeechRate = speechRate
            if (becameIneligible || !readerEnabled || !ridingActive) {
                stopForeground = flushLocked()
            } else {
                stopForeground = drainLocked()
            }
        }
        if (stopForeground) stopForegroundAndResume()
    }

    /** Explicitly discards pending content and stops active speech. */
    suspend fun stopAndClear() {
        val stopForeground = mutex.withLock { flushLocked() }
        if (stopForeground) stopForegroundAndResume()
    }

    private suspend fun onTtsEvent(event: TtsUtteranceEvent) {
        var stopForeground = false
        mutex.withLock {
            val current = active ?: return
            if (event.utteranceId != current.utteranceId) return
            when (event) {
                is TtsUtteranceEvent.Started -> Unit
                is TtsUtteranceEvent.Completed -> {
                    if (current.isTest) mutableTestSpeechState.value = TestSpeechState.Succeeded
                    stopForeground = completeActiveLocked()
                }
                is TtsUtteranceEvent.Failed,
                is TtsUtteranceEvent.Stopped,
                -> {
                    if (current.isTest) mutableTestSpeechState.value = TestSpeechState.Failed
                    stopForeground = completeActiveLocked()
                }
            }
        }
        if (stopForeground) stopForegroundAndResume()
    }

    private fun onFocusChange(change: AudioFocusChange) {
        scope.launch {
            var stopForeground = false
            mutex.withLock {
                when (change) {
                    AudioFocusChange.GAINED -> {
                        cancelInterruptionTimeoutLocked()
                        waitingForFocusGain = false
                        stopForeground = drainLocked()
                    }
                    AudioFocusChange.LOST_TRANSIENTLY -> {
                        waitingForFocusGain = true
                        if (active != null) {
                            if (active?.isTest == true) mutableTestSpeechState.value = TestSpeechState.Failed
                            runCatching { ttsEngine.stop() }
                            completeActiveLocked(releaseFocus = false)
                        }
                        startInterruptionTimeoutLocked()
                    }
                    AudioFocusChange.LOST -> {
                        cancelInterruptionTimeoutLocked()
                        waitingForFocusGain = false
                        if (active != null) {
                            if (active?.isTest == true) mutableTestSpeechState.value = TestSpeechState.Failed
                            runCatching { ttsEngine.stop() }
                            stopForeground = completeActiveLocked()
                        } else {
                            // A permanent loss can follow a transient loss after the active
                            // utterance was already stopped while retaining the focus lease.
                            releaseFocusLocked()
                            stopForeground = drainLocked()
                        }
                    }
                }
            }
            if (stopForeground) stopForegroundAndResume()
        }
    }

    /** Selects one item while locked, then performs suspendable platform promotion outside it. */
    private fun drainLocked(): Boolean {
        if (active != null || preparing != null || waitingForFocusGain || foregroundStopInProgress) return false
        val testSpeech = pendingTestSpeech
        if (testSpeech == null && (!readerEnabled || !ridingActive)) return false
        evictExpiredLocked()
        val combined = if (testSpeech != null) {
            pendingTestSpeech = null
            AggregatedRequest(text = testSpeech, speechRate = currentSpeechRate, isTest = true)
        } else {
            dequeueAggregateLocked()
        } ?: run {
            releaseFocusLocked()
            return requestForegroundStopLocked()
        }

        val selected = PreparingUtterance(combined)
        preparing = selected
        promotionJob = scope.launch { promoteAndSpeak(selected) }
        return false
    }

    private suspend fun promoteAndSpeak(selected: PreparingUtterance) {
        val promotion = ensureForegroundPromotion()
        var stopForeground = false
        mutex.withLock {
            // Disable/cancel or another process event may have invalidated this exact item.
            if (preparing !== selected) return@withLock
            preparing = null
            promotionJob = null
            val combined = selected.request
            if ((!combined.isTest && (!readerEnabled || !ridingActive)) ||
                promotion == ForegroundPlaybackPromotion.Failed
            ) {
                if (combined.isTest) mutableTestSpeechState.value = TestSpeechState.Failed
                stopForeground = drainLocked()
                return@withLock
            }

            if (!focusHeld) {
                when (requestAudioFocus()) {
                    AudioFocusRequestResult.DENIED -> {
                        if (combined.isTest) mutableTestSpeechState.value = TestSpeechState.Failed
                        stopForeground = drainLocked()
                        return@withLock
                    }
                    AudioFocusRequestResult.ALREADY_ACTIVE -> {
                        // This should only be possible after a platform race; do not overlap speech.
                        if (combined.isTest) mutableTestSpeechState.value = TestSpeechState.Failed
                        waitingForFocusGain = true
                        startInterruptionTimeoutLocked()
                        return@withLock
                    }
                    AudioFocusRequestResult.GRANTED -> focusHeld = true
                }
            }

            when (val result = speak(combined)) {
                is TtsSpeakResult.Accepted -> {
                    active = ActiveUtterance(result.utteranceId, isTest = combined.isTest)
                    if (combined.isTest) mutableTestSpeechState.value = TestSpeechState.Speaking
                }
                is TtsSpeakResult.Rejected -> {
                    if (combined.isTest) mutableTestSpeechState.value = TestSpeechState.Failed
                    releaseFocusLocked()
                    stopForeground = drainLocked()
                }
            }
        }
        if (stopForeground) stopForegroundAndResume()
    }

    private fun completeActiveLocked(releaseFocus: Boolean = true): Boolean {
        if (active == null) return false
        cancelInterruptionTimeoutLocked()
        active = null
        if (releaseFocus) releaseFocusLocked()
        return drainLocked()
    }

    private fun flushLocked(): Boolean {
        pending.clear()
        if (pendingTestSpeech != null || preparing?.request?.isTest == true) {
            mutableTestSpeechState.value = TestSpeechState.Failed
        }
        pendingTestSpeech = null
        preparing = null
        promotionJob?.cancel()
        promotionJob = null
        cancelInterruptionTimeoutLocked()
        waitingForFocusGain = false
        if (active?.isTest == true) mutableTestSpeechState.value = TestSpeechState.Failed
        if (active != null) runCatching { ttsEngine.stop() }
        active = null
        releaseFocusLocked()
        return requestForegroundStopLocked()
    }

    private fun startInterruptionTimeoutLocked() {
        interruptionTimeoutJob?.cancel()
        interruptionTimeoutJob = scope.launch {
            delay(interruptionTimeoutMillis)
            var stopForeground = false
            mutex.withLock {
                if (!waitingForFocusGain) return@withLock
                waitingForFocusGain = false
                pending.clear()
                if (pendingTestSpeech != null) mutableTestSpeechState.value = TestSpeechState.Failed
                pendingTestSpeech = null
                interruptionTimeoutJob = null
                releaseFocusLocked()
                stopForeground = requestForegroundStopLocked()
            }
            if (stopForeground) stopForegroundAndResume()
        }
    }

    private fun requestForegroundStopLocked(): Boolean {
        if (foregroundStopInProgress) return false
        foregroundStopInProgress = true
        return true
    }

    private suspend fun stopForegroundAndResume() {
        try {
            foregroundPlaybackGate.stop()
        } catch (_: RuntimeException) {
            // Cleanup is fail-closed; coordinator state still has to become drainable again.
        }
        mutex.withLock {
            foregroundStopInProgress = false
            val hasEligibleWork = pendingTestSpeech != null ||
                (pending.isNotEmpty() && readerEnabled && ridingActive)
            if (hasEligibleWork) drainLocked()
        }
    }

    private fun cancelInterruptionTimeoutLocked() {
        interruptionTimeoutJob?.cancel()
        interruptionTimeoutJob = null
    }

    private fun releaseFocusLocked() {
        if (!focusHeld) return
        focusHeld = false
        runCatching { audioFocusController.releaseFocusAfterTerminalUtterance() }
    }

    private fun evictExpiredLocked() {
        val now = clockMillis()
        pending.removeAll { queued -> now - queued.request.postedAtMillis > maxMessageAgeMillis }
    }

    private fun dequeueAggregateLocked(): AggregatedRequest? {
        val first = pending.removeFirstOrNull() ?: return null
        val matches = pending
            .filter { it.request.conversationId == first.request.conversationId }
        pending.removeAll(matches.toSet())
        val aggregate = (matches + first).sortedBy { it.sequence }
        val text = truncateAtWordBoundary(
            buildString {
                aggregate.forEachIndexed { index, queued ->
                    if (index > 0) append(PAUSE_MARKER)
                    append(queued.request.text)
                }
            },
        )
        return AggregatedRequest(text = text, speechRate = currentSpeechRate)
    }

    private fun truncateAtWordBoundary(text: String): String {
        if (text.length <= SpeechRequest.MAX_TEXT_LENGTH) return text
        val boundary = text.lastIndexOfAny(WHITESPACE, SpeechRequest.MAX_TEXT_LENGTH - 1)
        return if (boundary > 0) text.substring(0, boundary) else text.take(SpeechRequest.MAX_TEXT_LENGTH)
    }

    private data class QueuedRequest(val request: SpeechRequest, val sequence: Long)
    private data class ActiveUtterance(val utteranceId: String, val isTest: Boolean)
    private data class AggregatedRequest(val text: String, val speechRate: Float, val isTest: Boolean = false)
    private class PreparingUtterance(val request: AggregatedRequest)

    private suspend fun ensureForegroundPromotion(): ForegroundPlaybackPromotion = try {
        foregroundPlaybackGate.ensurePromoted()
    } catch (error: CancellationException) {
        throw error
    } catch (_: RuntimeException) {
        ForegroundPlaybackPromotion.Failed
    }

    private fun requestAudioFocus(): AudioFocusRequestResult = try {
        audioFocusController.requestFocus(::onFocusChange)
    } catch (_: RuntimeException) {
        AudioFocusRequestResult.DENIED
    }

    private fun speak(request: AggregatedRequest): TtsSpeakResult = try {
        ttsEngine.speak(request.text, request.speechRate)
    } catch (_: RuntimeException) {
        TtsSpeakResult.Rejected(TtsSpeakRejection.ENGINE_ERROR)
    }

    companion object {
        private val QUEUED_ORDER = compareBy<QueuedRequest>({ it.request.postedAtMillis }, { it.sequence })
        val WHITESPACE = charArrayOf(' ', '\t', '\n', '\r')
        const val PAUSE_MARKER = ", "
        const val MAX_PENDING_ITEMS = 20
        const val MAX_MESSAGE_AGE_MILLIS = 180_000L
        // Long enough for ordinary navigation prompts/call transients, but bounded for cleanup.
        const val INTERRUPTION_TIMEOUT_MILLIS = 30_000L
    }
}

sealed interface TestSpeechState {
    data object NotRun : TestSpeechState
    data object Pending : TestSpeechState
    data object Speaking : TestSpeechState
    data object Succeeded : TestSpeechState
    data object Failed : TestSpeechState
}

enum class TestSpeechRequestResult { ACCEPTED, BUSY, INVALID_REQUEST, REJECTED }
