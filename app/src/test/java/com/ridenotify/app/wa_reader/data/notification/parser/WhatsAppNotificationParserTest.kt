package com.ridenotify.app.wa_reader.data.notification.parser

import com.ridenotify.app.wa_reader.model.ConversationType
import com.ridenotify.app.wa_reader.model.MessagingStyleMessageSnapshot
import com.ridenotify.app.wa_reader.model.MessagingStyleSnapshot
import com.ridenotify.app.wa_reader.model.NotificationSnapshot
import com.ridenotify.app.wa_reader.model.ParseSource
import com.ridenotify.app.wa_reader.model.ParsedNotification
import com.ridenotify.app.wa_reader.model.SenderSnapshot
import com.ridenotify.app.wa_reader.model.UnsupportedReason
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsAppNotificationParserTest {
    private val parser = WhatsAppNotificationParser()

    @Test
    fun fixtureCorpus_matchesEveryDeclaredOutcomeAndStructuredPayload() {
        val fixtureDirectory = sequenceOf(File("docs/fixtures"), File("../docs/fixtures"))
            .first { it.isDirectory }
        val fixtures = fixtureDirectory.listFiles { file -> file.extension == "json" }
            .orEmpty().sortedBy(File::getName)

        assertEquals(14, fixtures.size, "Every ADR-003 fixture must remain mapped by this test")
        fixtures.forEach { file ->
            val root = Json.parse(file.readText()).obj()
            val expected = root.string("expectedParserOutcome")
            root.array("captures").forEachIndexed { index, captureValue ->
                val capture = captureValue.obj()
                val snapshot = capture.toSnapshot()
                val actual = parser.parse(snapshot)
                val context = "${file.name} capture[$index]"

                when (expected) {
                    "MessagingStyleMessages" -> {
                        val messages = assertIs<ParsedNotification.Messages>(actual, context)
                        assertEquals(ParseSource.MESSAGING_STYLE, messages.source, context)
                        val expectedMessages = capture.objOrNull("messagingStyle")!!.array("messages")
                        assertEquals(expectedMessages.size, messages.items.size, context)
                        expectedMessages.zip(messages.items).forEach { (expectedMessage, item) ->
                            val expectedObject = expectedMessage.obj()
                            assertEquals(expectedObject.string("text"), item.body, context)
                            assertEquals(expectedObject.long("timestampMillis"), item.postedAtMillis, context)
                            assertEquals(
                                expectedObject.objOrNull("sender")?.stringOrNull("name"),
                                item.senderDisplayName,
                                context,
                            )
                        }
                    }
                    "Attachment" -> assertIs<ParsedNotification.Attachment>(actual, context)
                    "Summary" -> assertIs<ParsedNotification.Summary>(actual, context)
                    "Redacted" -> assertIs<ParsedNotification.Redacted>(actual, context)
                    "ConversationMetadata" -> assertEquals(ParseSource.CONVERSATION_METADATA, assertIs<ParsedNotification.Messages>(actual).source)
                    "LegacyTextFallback" -> assertEquals(ParseSource.LEGACY_TEXT, assertIs<ParsedNotification.Messages>(actual).source)
                    "Call" -> assertIs<ParsedNotification.Call>(actual, context)
                    "Security" -> assertIs<ParsedNotification.Security>(actual, context)
                    "Unsupported" -> assertIs<ParsedNotification.Unsupported>(actual, context)
                    else -> error("Unknown expectedParserOutcome '$expected' in ${file.name}")
                }
            }
        }
    }

    @Test
    fun messagingStyle_winsOverDisagreeingLegacyFields() {
        val result = assertIs<ParsedNotification.Messages>(
            parser.parse(snapshot(title = "Wrong", text = "Wrong: legacy", shortcutId = "chat-1", style = style(false, "Clean body"))),
        )
        assertEquals(ParseSource.MESSAGING_STYLE, result.source)
        assertEquals("Clean body", result.items.single().body)
        assertEquals(ConversationType.DIRECT, result.items.single().conversationType)
    }

    @Test
    fun structuredMetadata_isUsedWhenMessageListIsAbsent() {
        val emptyDirectStyle = MessagingStyleSnapshot("Me", false, "Sari", emptyList())
        val result = assertIs<ParsedNotification.Messages>(
            parser.parse(snapshot(title = "Sari", text = "Halo", shortcutId = "chat-1", style = emptyDirectStyle)),
        )
        assertEquals(ParseSource.CONVERSATION_METADATA, result.source)
        assertEquals("com.whatsapp::shortcut::chat-1", result.items.single().conversationId.value)
    }

    @Test
    fun ambiguousConversationMetadata_failsClosed() {
        val result = assertIs<ParsedNotification.Unsupported>(
            parser.parse(snapshot(title = "Sari", text = "Halo", shortcutId = "chat-1")),
        )

        assertEquals(UnsupportedReason.UNRECOGNIZED_NOTIFICATION, result.reason)
    }

    @Test
    fun structuredGroupFlag_isUsedWhenMessageListIsAbsent() {
        val emptyGroupStyle = MessagingStyleSnapshot("Me", true, "Tim", emptyList())

        val result = assertIs<ParsedNotification.Messages>(
            parser.parse(snapshot(title = "Tim", text = "Budi: Halo", style = emptyGroupStyle)),
        )

        assertEquals(ParseSource.CONVERSATION_METADATA, result.source)
        assertEquals(ConversationType.GROUP, result.items.single().conversationType)
        assertEquals("Halo", result.items.single().body)
    }

    @Test
    fun messagingStyleWithoutConversationIdentity_failsClosed() {
        val styleWithoutIdentity = MessagingStyleSnapshot(
            userDisplayName = "Me",
            isGroupConversation = false,
            conversationTitle = null,
            messages = listOf(MessagingStyleMessageSnapshot("Halo", 99, null)),
        )

        val result = assertIs<ParsedNotification.Unsupported>(
            parser.parse(snapshot(style = styleWithoutIdentity)),
        )

        assertEquals(UnsupportedReason.UNRECOGNIZED_NOTIFICATION, result.reason)
    }

    @Test
    fun groupWithoutShortcut_usesOneConversationIdentityAcrossSenders() {
        val groupStyle = MessagingStyleSnapshot(
            userDisplayName = "Me",
            isGroupConversation = true,
            conversationTitle = "Tim",
            messages = listOf(
                MessagingStyleMessageSnapshot("Satu", 98, SenderSnapshot("sender-a", "Sari", false)),
                MessagingStyleMessageSnapshot("Dua", 99, SenderSnapshot("sender-b", "Budi", false)),
            ),
        )

        val result = assertIs<ParsedNotification.Messages>(parser.parse(snapshot(style = groupStyle)))

        assertEquals(1, result.items.map { it.conversationId }.distinct().size)
        assertTrue(result.items.singleOrNull() == null)
    }

    @Test
    fun generatedSummaryBanner_isNotParsedAsMessage() {
        listOf("3 new messages", "3 pesan baru").forEach { banner ->
            assertIs<ParsedNotification.Summary>(
                parser.parse(snapshot(title = "WhatsApp", text = banner)),
            )
        }
    }

    @Test
    fun legacyDirectMessageWithColon_remainsDirectAndBodyIsNotSplit() {
        val result = assertIs<ParsedNotification.Messages>(
            parser.parse(snapshot(title = "Fitri", text = "Alamat: Jalan Melati")),
        )
        assertEquals(ParseSource.LEGACY_TEXT, result.source)
        assertEquals(ConversationType.DIRECT, result.items.single().conversationType)
        assertEquals("Alamat: Jalan Melati", result.items.single().body)
        assertNull(result.items.single().senderDisplayName)
    }

    @Test
    fun legacyGroup_requiresMultipleDistinctSenderPrefixes() {
        val result = assertIs<ParsedNotification.Messages>(
            parser.parse(
                snapshot(
                    title = "Tim",
                    text = "Budi: Kedua",
                    textLines = listOf("Sari: Pertama", "Budi: Kedua"),
                ),
            ),
        )
        assertEquals(ConversationType.GROUP, result.items.single().conversationType)
        assertEquals("Budi", result.items.single().senderDisplayName)
        assertEquals("Kedua", result.items.single().body)
    }

    @Test
    fun packageAndAmbiguousContent_failClosed() {
        assertEquals(
            UnsupportedReason.PACKAGE_NOT_ALLOWED,
            assertIs<ParsedNotification.Unsupported>(parser.parse(snapshot(packageName = "com.example", title = "A", text = "B"))).reason,
        )
        assertEquals(
            UnsupportedReason.AMBIGUOUS_LEGACY_CONTENT,
            assertIs<ParsedNotification.Unsupported>(parser.parse(snapshot(title = null, text = "Budi: halo"))).reason,
        )
    }

    @Test
    fun osGroupKey_neverMakesAChatAGroup() {
        val result = assertIs<ParsedNotification.Messages>(
            parser.parse(snapshot(title = "Sari", text = "Halo", groupKey = "looks-like-a-group")),
        )
        assertEquals(ConversationType.DIRECT, result.items.single().conversationType)
    }

    private fun snapshot(
        packageName: String = "com.whatsapp",
        title: String? = null,
        text: String? = null,
        textLines: List<String> = emptyList(),
        groupKey: String? = null,
        shortcutId: String? = null,
        style: MessagingStyleSnapshot? = null,
    ) = NotificationSnapshot(
        packageName, "key", 1, 100, groupKey, title, text, null, textLines,
        null, null, "msg", false, style?.conversationTitle, shortcutId, style,
    )

    private fun style(group: Boolean, body: String) = MessagingStyleSnapshot(
        userDisplayName = "Me",
        isGroupConversation = group,
        conversationTitle = "Conversation",
        messages = listOf(MessagingStyleMessageSnapshot(body, 99, SenderSnapshot("sender", "Sender", false))),
    )
}

private fun Map<String, Any?>.toSnapshot(): NotificationSnapshot {
    val style = objOrNull("messagingStyle")?.let { value ->
        MessagingStyleSnapshot(
            userDisplayName = value.stringOrNull("userDisplayName"),
            isGroupConversation = value.boolean("isGroupConversation"),
            conversationTitle = value.stringOrNull("conversationTitle"),
            messages = value.array("messages").map { messageValue ->
                val message = messageValue.obj()
                val sender = message.objOrNull("sender")
                MessagingStyleMessageSnapshot(
                    text = message.stringOrNull("text"),
                    timestampMillis = message.long("timestampMillis"),
                    sender = sender?.let {
                        SenderSnapshot(it.stringOrNull("key"), it.stringOrNull("name"), it.boolean("isBot"))
                    },
                )
            },
        )
    }
    return NotificationSnapshot(
        packageName = string("packageName"), notificationKey = string("notificationKey"),
        notificationId = long("notificationId").toInt(), postTimeMillis = long("postTimeMillis"),
        groupKey = stringOrNull("groupKey"), title = stringOrNull("title"), text = stringOrNull("text"),
        bigText = stringOrNull("bigText"), textLines = array("textLines").map { it as String },
        subText = stringOrNull("subText"), summaryText = stringOrNull("summaryText"),
        category = stringOrNull("category"), isGroupSummary = boolean("isGroupSummary"),
        conversationTitle = stringOrNull("conversationTitle"), shortcutId = stringOrNull("shortcutId"),
        messagingStyle = style,
    )
}

@Suppress("UNCHECKED_CAST")
private fun Any?.obj(): Map<String, Any?> = this as Map<String, Any?>
private fun Map<String, Any?>.objOrNull(key: String) = this[key]?.obj()
private fun Map<String, Any?>.array(key: String) = this[key] as List<Any?>
private fun Map<String, Any?>.string(key: String) = this[key] as String
private fun Map<String, Any?>.stringOrNull(key: String) = this[key] as String?
private fun Map<String, Any?>.long(key: String) = this[key] as Long
private fun Map<String, Any?>.boolean(key: String) = this[key] as Boolean

/** Small dependency-free JSON reader kept test-only so production parsing remains pure Kotlin. */
private class Json private constructor(private val source: String) {
    private var offset = 0

    fun value(): Any? {
        whitespace()
        return when (source[offset]) {
            '{' -> objectValue()
            '[' -> arrayValue()
            '"' -> stringValue()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> numberValue()
        }
    }

    private fun objectValue(): Map<String, Any?> {
        offset++
        val result = linkedMapOf<String, Any?>()
        whitespace()
        if (take('}')) return result
        while (true) {
            whitespace()
            val key = stringValue()
            whitespace(); check(take(':'))
            result[key] = value()
            whitespace()
            if (take('}')) return result
            check(take(','))
        }
    }

    private fun arrayValue(): List<Any?> {
        offset++
        val result = mutableListOf<Any?>()
        whitespace()
        if (take(']')) return result
        while (true) {
            result += value()
            whitespace()
            if (take(']')) return result
            check(take(','))
        }
    }

    private fun stringValue(): String {
        check(take('"'))
        val result = StringBuilder()
        while (true) {
            val char = source[offset++]
            when (char) {
                '"' -> return result.toString()
                '\\' -> {
                    val escaped = source[offset++]
                    result.append(
                        when (escaped) {
                            '"', '\\', '/' -> escaped
                            'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                            'u' -> source.substring(offset, offset + 4).toInt(16).toChar().also { offset += 4 }
                            else -> error("Invalid JSON escape")
                        },
                    )
                }
                else -> result.append(char)
            }
        }
    }

    private fun numberValue(): Long {
        val start = offset
        while (offset < source.length && source[offset] in "-0123456789") offset++
        return source.substring(start, offset).toLong()
    }

    private fun literal(token: String, value: Any?): Any? {
        check(source.startsWith(token, offset)); offset += token.length; return value
    }

    private fun whitespace() { while (offset < source.length && source[offset].isWhitespace()) offset++ }
    private fun take(char: Char) = source.getOrNull(offset) == char && true.also { offset++ }

    companion object { fun parse(source: String) = Json(source).value() }
}
