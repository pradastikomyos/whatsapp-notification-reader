package com.ridenotify.app.wa_reader.data.notification.parser

import com.ridenotify.app.wa_reader.model.ConversationId
import com.ridenotify.app.wa_reader.model.ConversationType
import com.ridenotify.app.wa_reader.model.MessagingStyleMessageSnapshot
import com.ridenotify.app.wa_reader.model.NotificationSnapshot
import com.ridenotify.app.wa_reader.model.ParseSource
import com.ridenotify.app.wa_reader.model.ParsedMessage
import com.ridenotify.app.wa_reader.model.ParsedNotification
import com.ridenotify.app.wa_reader.model.UnsupportedReason
import java.text.Normalizer
import java.util.Locale

/** Pure, fail-closed implementation of the ordered parser contract in ADR-003. */
class WhatsAppNotificationParser : NotificationParser {
    override fun parse(snapshot: NotificationSnapshot): ParsedNotification {
        if (snapshot.packageName !in ALLOWED_PACKAGES) {
            return ParsedNotification.Unsupported(UnsupportedReason.PACKAGE_NOT_ALLOWED)
        }
        if (snapshot.isGroupSummary) return ParsedNotification.Summary
        if (snapshot.category.equals(CALL_CATEGORY, ignoreCase = true)) return ParsedNotification.Call
        if (isGeneratedSummary(snapshot)) return ParsedNotification.Summary

        val style = snapshot.messagingStyle
        if (style != null && style.messages.isNotEmpty()) {
            if (style.messages.any { it.text.isNullOrBlank() }) {
                return ParsedNotification.Unsupported(UnsupportedReason.EMPTY_CONTENT)
            }
            if (style.messages.all { isAttachmentPlaceholder(it.text.orEmpty()) }) {
                return ParsedNotification.Attachment
            }

            val items = style.messages.mapNotNull { message ->
                message.toParsed(snapshot, style.isGroupConversation)
            }
            if (items.size != style.messages.size) {
                return ParsedNotification.Unsupported(UnsupportedReason.UNRECOGNIZED_NOTIFICATION)
            }
            return ParsedNotification.Messages(ParseSource.MESSAGING_STYLE, items)
        }

        if (style != null || !snapshot.shortcutId.isNullOrBlank() || !snapshot.conversationTitle.isNullOrBlank()) {
            val body = preferredBody(snapshot)
                ?: return ParsedNotification.Unsupported(UnsupportedReason.EMPTY_CONTENT)
            if (isAttachmentPlaceholder(body)) return ParsedNotification.Attachment
            val legacyGroupEvidence = distinctLegacySenders(snapshot.textLines).size >= 2
            if (style == null && !legacyGroupEvidence) {
                return ParsedNotification.Unsupported(UnsupportedReason.UNRECOGNIZED_NOTIFICATION)
            }
            val isGroup = style?.isGroupConversation ?: true
            return messagesResult(
                snapshot = snapshot,
                source = ParseSource.CONVERSATION_METADATA,
                body = stripSenderPrefix(body, isGroup),
                isGroup = isGroup,
                senderName = senderPrefix(body).takeIf { isGroup },
                senderKey = null,
            )
        }

        if (isRedacted(snapshot)) return ParsedNotification.Redacted
        if (isAttachmentPlaceholder(snapshot.text.orEmpty())) return ParsedNotification.Attachment

        val body = preferredBody(snapshot)
            ?: return ParsedNotification.Unsupported(UnsupportedReason.EMPTY_CONTENT)
        val senders = distinctLegacySenders(snapshot.textLines)
        val isGroup = senders.size >= 2
        if (!isGroup && snapshot.title.isNullOrBlank()) {
            return ParsedNotification.Unsupported(UnsupportedReason.AMBIGUOUS_LEGACY_CONTENT)
        }
        return messagesResult(
            snapshot = snapshot,
            source = ParseSource.LEGACY_TEXT,
            body = stripSenderPrefix(body, isGroup),
            isGroup = isGroup,
            senderName = senderPrefix(body).takeIf { isGroup },
            senderKey = null,
        )
    }

    private fun MessagingStyleMessageSnapshot.toParsed(
        snapshot: NotificationSnapshot,
        isGroup: Boolean,
    ): ParsedMessage? {
        val title = snapshot.messagingStyle?.conversationTitle?.trim()?.takeIf(String::isNotEmpty)
            ?: snapshot.conversationTitle?.trim()?.takeIf(String::isNotEmpty)
            ?: snapshot.title?.trim()?.takeIf(String::isNotEmpty)
        val id = conversationId(snapshot, isGroup, title, sender?.key.takeUnless { isGroup }) ?: return null
        return ParsedMessage(
            conversationId = id,
            conversationType = if (isGroup) ConversationType.GROUP else ConversationType.DIRECT,
            conversationTitle = title,
            senderDisplayName = sender?.name?.trim()?.takeIf(String::isNotEmpty),
            body = text.orEmpty().trim(),
            postedAtMillis = timestampMillis,
        )
    }

    private fun messagesResult(
        snapshot: NotificationSnapshot,
        source: ParseSource,
        body: String,
        isGroup: Boolean,
        senderName: String?,
        senderKey: String?,
    ): ParsedNotification {
        val title = snapshot.conversationTitle?.trim()?.takeIf(String::isNotEmpty)
            ?: snapshot.title?.trim()?.takeIf(String::isNotEmpty)
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty()) return ParsedNotification.Unsupported(UnsupportedReason.EMPTY_CONTENT)
        val id = conversationId(snapshot, isGroup, title, senderKey)
            ?: return ParsedNotification.Unsupported(UnsupportedReason.UNRECOGNIZED_NOTIFICATION)
        return ParsedNotification.Messages(
            source,
            listOf(
                ParsedMessage(
                    conversationId = id,
                    conversationType = if (isGroup) ConversationType.GROUP else ConversationType.DIRECT,
                    conversationTitle = title,
                    senderDisplayName = senderName,
                    body = trimmedBody,
                    postedAtMillis = snapshot.postTimeMillis,
                ),
            ),
        )
    }

    private fun conversationId(
        snapshot: NotificationSnapshot,
        isGroup: Boolean,
        title: String?,
        senderKey: String?,
    ): ConversationId? {
        val shortcut = snapshot.shortcutId?.trim()?.takeIf(String::isNotEmpty)
        if (shortcut != null) return ConversationId("${snapshot.packageName}::shortcut::$shortcut")

        val normalizedTitle = title?.trim()?.replace(WHITESPACE, " ")
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFC) }
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val kind = if (isGroup) "group" else "direct"
        val normalizedSenderKey = senderKey?.trim()?.takeIf(String::isNotEmpty) ?: "unknown"
        return ConversationId("${snapshot.packageName}::fallback::$kind::$normalizedTitle::$normalizedSenderKey")
    }

    private fun preferredBody(snapshot: NotificationSnapshot): String? =
        snapshot.text?.trim()?.takeIf(String::isNotEmpty)
            ?: snapshot.bigText?.trim()?.takeIf(String::isNotEmpty)
            ?: snapshot.textLines.lastOrNull { it.isNotBlank() }?.trim()

    private fun distinctLegacySenders(lines: List<String>): Set<String> =
        lines.mapNotNull(::senderPrefix).map(String::trim).filter(String::isNotEmpty).toSet()

    private fun senderPrefix(value: String): String? {
        val separator = value.indexOf(LEGACY_SEPARATOR)
        if (separator <= 0 || separator == value.lastIndex) return null
        return value.substring(0, separator).trim().takeIf { it.matches(LEGACY_SENDER) }
    }

    private fun stripSenderPrefix(value: String, enabled: Boolean): String {
        if (!enabled) return value
        val separator = value.indexOf(LEGACY_SEPARATOR)
        return if (separator > 0 && separator < value.lastIndex) value.substring(separator + 1).trim() else value
    }

    private fun isRedacted(snapshot: NotificationSnapshot): Boolean =
        snapshot.messagingStyle == null && snapshot.shortcutId.isNullOrBlank() &&
            snapshot.conversationTitle.isNullOrBlank() &&
            snapshot.title?.trim().equals(WHATSAPP_TITLE, ignoreCase = true) &&
            snapshot.text?.trim()?.lowercase(Locale.ROOT) in REDACTION_PLACEHOLDERS

    private fun isAttachmentPlaceholder(value: String): Boolean =
        value.trim().lowercase(Locale.ROOT) in ATTACHMENT_PLACEHOLDERS

    private fun isGeneratedSummary(snapshot: NotificationSnapshot): Boolean =
        snapshot.title?.trim().equals(WHATSAPP_TITLE, ignoreCase = true) &&
            snapshot.text?.trim()?.lowercase(Locale.ROOT)?.matches(SUMMARY_PATTERN) == true

    private companion object {
        val ALLOWED_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        val ATTACHMENT_PLACEHOLDERS = setOf("photo", "foto", "video", "document", "dokumen")
        val REDACTION_PLACEHOLDERS = setOf("new message", "pesan baru")
        val SUMMARY_PATTERN = Regex("\\d+ (new messages|pesan baru)")
        val WHITESPACE = Regex("\\s+")
        val LEGACY_SENDER = Regex("[\\p{L}\\p{M}\\p{N} ._+'’()\\-]{1,80}")
        const val CALL_CATEGORY = "call"
        const val WHATSAPP_TITLE = "WhatsApp"
        const val LEGACY_SEPARATOR = ':'
    }
}
