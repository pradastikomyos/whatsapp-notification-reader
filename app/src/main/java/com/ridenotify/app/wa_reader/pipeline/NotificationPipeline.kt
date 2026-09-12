package com.ridenotify.app.wa_reader.pipeline

import com.ridenotify.app.wa_reader.data.notification.NotificationDeduplicator
import com.ridenotify.app.wa_reader.data.notification.parser.NotificationParser
import com.ridenotify.app.wa_reader.data.notification.parser.WhatsAppNotificationParser
import com.ridenotify.app.wa_reader.data.settings.SettingsRepository
import com.ridenotify.app.wa_reader.listener.SerializedNotificationIngress
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.NotificationSnapshot
import com.ridenotify.app.wa_reader.model.ParsedNotification
import com.ridenotify.app.wa_reader.model.ReadingDecision
import com.ridenotify.app.wa_reader.model.SpeechRequest
import com.ridenotify.app.wa_reader.policy.ReadingPolicyEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Public contract for the notification processing pipeline.
 */
interface NotificationPipeline {
    val speechRequests: Flow<SpeechRequest>
    val diagnostics: Flow<PipelineDiagnostic>

    suspend fun process(snapshot: NotificationSnapshot)
    fun start(scope: CoroutineScope): Job
}

/**
 * Primary implementation of the ingress pipeline orchestrating snapshot parsing,
 * deduplication, settings resolution, reading policy evaluation, and diagnostic emission.
 */
class DefaultNotificationPipeline(
    private val ingress: SerializedNotificationIngress,
    private val parser: NotificationParser = WhatsAppNotificationParser(),
    private val deduplicator: NotificationDeduplicator = NotificationDeduplicator(),
    private val settingsRepository: SettingsRepository,
    private val policyEvaluator: ReadingPolicyEvaluator = ReadingPolicyEvaluator(),
    private val correlation: DiagnosticCorrelation = DiagnosticCorrelation(),
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val settingsTimeoutMillis: Long = DEFAULT_SETTINGS_TIMEOUT_MILLIS,
    speechRequestsCapacity: Int = DEFAULT_SPEECH_CAPACITY,
    diagnosticsCapacity: Int = DEFAULT_DIAGNOSTICS_CAPACITY,
) : NotificationPipeline {

    private val _speechRequests = Channel<SpeechRequest>(
        capacity = speechRequestsCapacity.also { require(it > 0) { "speechRequestsCapacity must be positive" } },
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val speechRequests: Flow<SpeechRequest> = _speechRequests.receiveAsFlow()

    private val _diagnostics = MutableSharedFlow<PipelineDiagnostic>(
        replay = DEFAULT_DIAGNOSTICS_REPLAY,
        extraBufferCapacity = diagnosticsCapacity.also { require(it > 0) { "diagnosticsCapacity must be positive" } },
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val diagnostics: Flow<PipelineDiagnostic> = _diagnostics.asSharedFlow()

    override suspend fun process(snapshot: NotificationSnapshot) {
        val parsed = parser.parse(snapshot)
        when (parsed) {
            is ParsedNotification.Messages -> processMessages(snapshot, parsed)
            else -> processNonMessage(snapshot, parsed)
        }
    }

    override fun start(scope: CoroutineScope): Job = scope.launch {
        ingress.snapshots.collect { snapshot ->
            process(snapshot)
        }
    }

    private suspend fun processMessages(
        snapshot: NotificationSnapshot,
        parsed: ParsedNotification.Messages,
    ) {
        val now = clockMillis()
        val newMessages = deduplicator.filterNew(snapshot.packageName, parsed.items)
        val newSet = newMessages.toSet()

        for (item in parsed.items) {
            if (item !in newSet) {
                _diagnostics.emit(
                    PipelineDiagnostic.DuplicateSuppressed(
                        correlationToken = correlation.tokenFor(item.conversationId),
                        conversationType = item.conversationType,
                        timestampMillis = now,
                    )
                )
            }
        }

        if (newMessages.isEmpty()) {
            return
        }

        val settings = loadSettings()
        if (settings == null) {
            _diagnostics.emit(
                PipelineDiagnostic.SettingsUnavailable(
                    correlationToken = correlation.tokenFor(snapshot),
                    timestampMillis = now,
                )
            )
            return
        }

        for (message in newMessages) {
            val messageNow = clockMillis()
            val token = correlation.tokenFor(message.conversationId)
            val decision = policyEvaluator.evaluate(message, settings)
            when (decision) {
                is ReadingDecision.Speak -> {
                    _speechRequests.trySend(decision.request)
                    _diagnostics.emit(
                        PipelineDiagnostic.PolicyEvaluated(
                            correlationToken = token,
                            outcome = DiagnosticOutcome.SPEAK,
                            conversationType = message.conversationType,
                            parseSource = parsed.source,
                            timestampMillis = messageNow,
                        )
                    )
                }
                else -> {
                    _diagnostics.emit(
                        PipelineDiagnostic.PolicyEvaluated(
                            correlationToken = token,
                            outcome = DiagnosticOutcome.from(decision),
                            conversationType = message.conversationType,
                            parseSource = parsed.source,
                            timestampMillis = messageNow,
                        )
                    )
                }
            }
        }
    }

    private suspend fun processNonMessage(
        snapshot: NotificationSnapshot,
        parsed: ParsedNotification,
    ) {
        val now = clockMillis()
        val correlationToken = correlation.tokenFor(snapshot)

        val settings = loadSettings()
        if (settings == null) {
            _diagnostics.emit(
                PipelineDiagnostic.SettingsUnavailable(
                    correlationToken = correlationToken,
                    timestampMillis = now,
                )
            )
            return
        }

        val decisions = policyEvaluator.evaluate(parsed, settings)
        val parserOutcome = DiagnosticParserOutcome.from(parsed)
        val unsupportedReason = (parsed as? ParsedNotification.Unsupported)?.reason

        for (decision in decisions) {
            _diagnostics.emit(
                PipelineDiagnostic.NonMessageEvaluated(
                    correlationToken = correlationToken,
                    parserOutcome = parserOutcome,
                    decision = DiagnosticOutcome.from(decision),
                    unsupportedReason = unsupportedReason,
                    timestampMillis = now,
                )
            )
        }
    }

    private suspend fun loadSettings(): AppSettings? =
        try {
            withTimeoutOrNull(settingsTimeoutMillis) {
                settingsRepository.getSettings()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }

    companion object {
        const val DEFAULT_SETTINGS_TIMEOUT_MILLIS = 2_000L
        const val DEFAULT_SPEECH_CAPACITY = 64
        const val DEFAULT_DIAGNOSTICS_CAPACITY = 128
        const val DEFAULT_DIAGNOSTICS_REPLAY = 64
    }
}
