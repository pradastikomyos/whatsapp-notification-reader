package com.ridenotify.app.wa_reader.pipeline

import com.ridenotify.app.wa_reader.model.ConversationType
import com.ridenotify.app.wa_reader.model.ParseSource
import com.ridenotify.app.wa_reader.model.ParsedNotification
import com.ridenotify.app.wa_reader.model.ReadingDecision
import com.ridenotify.app.wa_reader.model.UnsupportedReason

/**
 * Coarse diagnostic outcome values conforming to ADR-009 privacy rules.
 * Never includes message text, sender names, group titles, or raw IDs.
 */
enum class DiagnosticOutcome {
    SPEAK,
    SKIP_READER_DISABLED,
    SKIP_RIDING_MODE_INACTIVE,
    SKIP_PRIVATE_DISABLED,
    SKIP_GROUP_NOT_SELECTED,
    SKIP_REDACTED,
    SKIP_UNSUPPORTED,
    SKIP_TOO_OLD;

    companion object {
        fun from(decision: ReadingDecision): DiagnosticOutcome = when (decision) {
            is ReadingDecision.Speak -> SPEAK
            ReadingDecision.SkipReaderDisabled -> SKIP_READER_DISABLED
            ReadingDecision.SkipRidingModeInactive -> SKIP_RIDING_MODE_INACTIVE
            ReadingDecision.SkipPrivateDisabled -> SKIP_PRIVATE_DISABLED
            ReadingDecision.SkipGroupNotSelected -> SKIP_GROUP_NOT_SELECTED
            ReadingDecision.SkipRedacted -> SKIP_REDACTED
            ReadingDecision.SkipUnsupported -> SKIP_UNSUPPORTED
            ReadingDecision.SkipTooOld -> SKIP_TOO_OLD
        }
    }
}

/**
 * Coarse parser outcome categories for non-message notifications.
 */
enum class DiagnosticParserOutcome {
    SUMMARY,
    CALL,
    SECURITY,
    ATTACHMENT,
    REDACTED,
    UNSUPPORTED;

    companion object {
        fun from(parsed: ParsedNotification): DiagnosticParserOutcome = when (parsed) {
            ParsedNotification.Summary -> SUMMARY
            ParsedNotification.Call -> CALL
            ParsedNotification.Security -> SECURITY
            ParsedNotification.Attachment -> ATTACHMENT
            ParsedNotification.Redacted -> REDACTED
            is ParsedNotification.Unsupported -> UNSUPPORTED
            is ParsedNotification.Messages -> error("Messages outcome should be evaluated per-message")
        }
    }
}

/**
 * Structured diagnostic event emitted by the ingress pipeline.
 *
 * In accordance with ADR-009:
 * - Contains only outcome enums, coarse platform/type metadata, and ephemeral correlation hashes.
 * - Structurally forbids message text, sender display names, group titles, or raw identifiers.
 */
sealed interface PipelineDiagnostic {
    val correlationToken: String?
    val timestampMillis: Long

    /**
     * Emitted when a message is identified as a duplicate and suppressed before policy evaluation.
     */
    data class DuplicateSuppressed(
        override val correlationToken: String,
        val conversationType: ConversationType,
        override val timestampMillis: Long,
    ) : PipelineDiagnostic

    /**
     * Emitted when a new message has been evaluated by the reading policy.
     */
    data class PolicyEvaluated(
        override val correlationToken: String,
        val outcome: DiagnosticOutcome,
        val conversationType: ConversationType,
        val parseSource: ParseSource,
        override val timestampMillis: Long,
    ) : PipelineDiagnostic

    /**
     * Emitted when a non-message notification has been evaluated.
     */
    data class NonMessageEvaluated(
        override val correlationToken: String?,
        val parserOutcome: DiagnosticParserOutcome,
        val decision: DiagnosticOutcome,
        val unsupportedReason: UnsupportedReason? = null,
        override val timestampMillis: Long,
    ) : PipelineDiagnostic

    /**
     * Emitted when settings cannot be loaded within the bounded wait time (pipeline fails closed).
     */
    data class SettingsUnavailable(
        override val correlationToken: String?,
        override val timestampMillis: Long,
    ) : PipelineDiagnostic
}
