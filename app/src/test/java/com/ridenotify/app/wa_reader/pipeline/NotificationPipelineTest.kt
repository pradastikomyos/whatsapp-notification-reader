package com.ridenotify.app.wa_reader.pipeline

import com.ridenotify.app.wa_reader.data.notification.NotificationDeduplicator
import com.ridenotify.app.wa_reader.data.notification.parser.WhatsAppNotificationParser
import com.ridenotify.app.wa_reader.data.settings.SettingsRepository
import com.ridenotify.app.wa_reader.listener.SerializedNotificationIngress
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.ConversationId
import com.ridenotify.app.wa_reader.model.ConversationType
import com.ridenotify.app.wa_reader.model.GroupReadMode
import com.ridenotify.app.wa_reader.model.MessagingStyleMessageSnapshot
import com.ridenotify.app.wa_reader.model.MessagingStyleSnapshot
import com.ridenotify.app.wa_reader.model.NotificationSnapshot
import com.ridenotify.app.wa_reader.model.ParseSource
import com.ridenotify.app.wa_reader.model.RidingState
import com.ridenotify.app.wa_reader.model.SenderSnapshot
import com.ridenotify.app.wa_reader.model.SpeechRequest
import com.ridenotify.app.wa_reader.policy.ReadingPolicyEvaluator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPipelineTest {

    private val now = 1_000_000L
    private val defaultClock = { now }

    @Test
    fun `end to end direct message speaks when policy allows`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings())
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.speechRequests.collect { speechItems.add(it) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.diagnostics.collect { diagnostics.add(it) }
        }

        pipeline.start(backgroundScope)

        val snapshot = directMessagingSnapshot(sender = "Budi", text = "Halo apa kabar", postTime = now)
        submit(ingress, snapshot)

        assertEquals(1, speechItems.size)
        val speech = speechItems.first()
        assertTrue(speech.text.contains("Halo apa kabar"))
        assertEquals(now, speech.postedAtMillis)

        assertEquals(1, diagnostics.size)
        val diag = assertIs<PipelineDiagnostic.PolicyEvaluated>(diagnostics.first())
        assertEquals(DiagnosticOutcome.SPEAK, diag.outcome)
        assertEquals(ConversationType.DIRECT, diag.conversationType)
        assertEquals(ParseSource.MESSAGING_STYLE, diag.parseSource)
    }

    @Test
    fun `end to end group message speaks under ALL_OBSERVED_GROUPS`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings(groupMode = GroupReadMode.ALL_OBSERVED_GROUPS))
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.speechRequests.collect { speechItems.add(it) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.diagnostics.collect { diagnostics.add(it) }
        }

        pipeline.start(backgroundScope)

        val snapshot = groupMessagingSnapshot(groupTitle = "Gowes Pagi", sender = "Siti", text = "Kumpul jam 6", postTime = now)
        submit(ingress, snapshot)

        assertEquals(1, speechItems.size)
        assertEquals(1, diagnostics.size)
        val diag = assertIs<PipelineDiagnostic.PolicyEvaluated>(diagnostics.first())
        assertEquals(DiagnosticOutcome.SPEAK, diag.outcome)
        assertEquals(ConversationType.GROUP, diag.conversationType)
    }

    @Test
    fun `skipReaderDisabled emits diagnostic and suppresses speech`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings(readerEnabled = false))
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.speechRequests.collect { speechItems.add(it) } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.diagnostics.collect { diagnostics.add(it) } }

        pipeline.start(backgroundScope)
        submit(ingress, directMessagingSnapshot(text = "Pesan rahasia"))

        assertEquals(0, speechItems.size)
        assertEquals(1, diagnostics.size)
        val diag = assertIs<PipelineDiagnostic.PolicyEvaluated>(diagnostics.first())
        assertEquals(DiagnosticOutcome.SKIP_READER_DISABLED, diag.outcome)
    }

    @Test
    fun `skipRidingModeInactive emits diagnostic and suppresses speech`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings(ridingState = RidingState.INACTIVE))
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.speechRequests.collect { speechItems.add(it) } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.diagnostics.collect { diagnostics.add(it) } }

        pipeline.start(backgroundScope)
        submit(ingress, directMessagingSnapshot(text = "Halo"))

        assertEquals(0, speechItems.size)
        assertEquals(1, diagnostics.size)
        val diag = assertIs<PipelineDiagnostic.PolicyEvaluated>(diagnostics.first())
        assertEquals(DiagnosticOutcome.SKIP_RIDING_MODE_INACTIVE, diag.outcome)
    }

    @Test
    fun `skipPrivateDisabled emits diagnostic and suppresses speech for direct chats`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings(readPrivate = false))
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.speechRequests.collect { speechItems.add(it) } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.diagnostics.collect { diagnostics.add(it) } }

        pipeline.start(backgroundScope)
        submit(ingress, directMessagingSnapshot(text = "Halo privat"))

        assertEquals(0, speechItems.size)
        assertEquals(1, diagnostics.size)
        val diag = assertIs<PipelineDiagnostic.PolicyEvaluated>(diagnostics.first())
        assertEquals(DiagnosticOutcome.SKIP_PRIVATE_DISABLED, diag.outcome)
    }

    @Test
    fun `skipGroupNotSelected emits diagnostic for unselected group or NO_GROUPS mode`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings(groupMode = GroupReadMode.NO_GROUPS))
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.speechRequests.collect { speechItems.add(it) } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.diagnostics.collect { diagnostics.add(it) } }

        pipeline.start(backgroundScope)
        submit(ingress, groupMessagingSnapshot(groupTitle = "Grup Umum", text = "Pengumuman"))

        assertEquals(0, speechItems.size)
        assertEquals(1, diagnostics.size)
        val diag = assertIs<PipelineDiagnostic.PolicyEvaluated>(diagnostics.first())
        assertEquals(DiagnosticOutcome.SKIP_GROUP_NOT_SELECTED, diag.outcome)
    }

    @Test
    fun `skipRedacted emits non message diagnostic and suppresses speech`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings())
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.speechRequests.collect { speechItems.add(it) } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.diagnostics.collect { diagnostics.add(it) } }

        pipeline.start(backgroundScope)
        // Redacted notification: title = "WhatsApp", text = "pesan baru"
        val redactedSnapshot = rawSnapshot(title = "WhatsApp", text = "pesan baru")
        submit(ingress, redactedSnapshot)

        assertEquals(0, speechItems.size)
        assertEquals(1, diagnostics.size)
        val diag = assertIs<PipelineDiagnostic.NonMessageEvaluated>(diagnostics.first())
        assertEquals(DiagnosticParserOutcome.REDACTED, diag.parserOutcome)
        assertEquals(DiagnosticOutcome.SKIP_REDACTED, diag.decision)
    }

    @Test
    fun `skipUnsupported emits non message diagnostic for summary, call, and attachment`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings())
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.speechRequests.collect { speechItems.add(it) } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.diagnostics.collect { diagnostics.add(it) } }

        pipeline.start(backgroundScope)

        // Summary notification
        submit(ingress, rawSnapshot(isGroupSummary = true, title = "WhatsApp", text = "2 pesan baru"))
        // Call notification
        submit(ingress, rawSnapshot(category = "call", title = "Panggilan WhatsApp", text = "Panggilan masuk"))
        // Attachment notification
        submit(ingress, rawSnapshot(title = "Budi", text = "Foto"))

        assertEquals(0, speechItems.size)
        assertEquals(3, diagnostics.size)

        val d1 = assertIs<PipelineDiagnostic.NonMessageEvaluated>(diagnostics[0])
        assertEquals(DiagnosticParserOutcome.SUMMARY, d1.parserOutcome)
        assertEquals(DiagnosticOutcome.SKIP_UNSUPPORTED, d1.decision)

        val d2 = assertIs<PipelineDiagnostic.NonMessageEvaluated>(diagnostics[1])
        assertEquals(DiagnosticParserOutcome.CALL, d2.parserOutcome)
        assertEquals(DiagnosticOutcome.SKIP_UNSUPPORTED, d2.decision)

        val d3 = assertIs<PipelineDiagnostic.NonMessageEvaluated>(diagnostics[2])
        assertEquals(DiagnosticParserOutcome.ATTACHMENT, d3.parserOutcome)
        assertEquals(DiagnosticOutcome.SKIP_UNSUPPORTED, d3.decision)
    }

    @Test
    fun `skipTooOld emits diagnostic for stale message beyond max age`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings())
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.speechRequests.collect { speechItems.add(it) } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.diagnostics.collect { diagnostics.add(it) } }

        pipeline.start(backgroundScope)

        // Message posted 200 seconds ago (max age is 180 seconds)
        submit(ingress, directMessagingSnapshot(text = "Pesan usang", postTime = now - 200_000L))

        assertEquals(0, speechItems.size)
        assertEquals(1, diagnostics.size)
        val diag = assertIs<PipelineDiagnostic.PolicyEvaluated>(diagnostics.first())
        assertEquals(DiagnosticOutcome.SKIP_TOO_OLD, diag.outcome)
    }

    @Test
    fun `duplicate message is suppressed before policy and emits duplicate diagnostic`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings())
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.speechRequests.collect { speechItems.add(it) } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.diagnostics.collect { diagnostics.add(it) } }

        pipeline.start(backgroundScope)

        val snapshot = directMessagingSnapshot(id = 10, sender = "Budi", text = "Halo sekali", postTime = now)
        // First submission: should speak
        submit(ingress, snapshot)
        // Second submission of identical notification: should be suppressed
        submit(ingress, snapshot)

        assertEquals(1, speechItems.size)
        assertEquals(2, diagnostics.size)

        val firstDiag = assertIs<PipelineDiagnostic.PolicyEvaluated>(diagnostics[0])
        assertEquals(DiagnosticOutcome.SPEAK, firstDiag.outcome)

        val secondDiag = assertIs<PipelineDiagnostic.DuplicateSuppressed>(diagnostics[1])
        assertEquals(ConversationType.DIRECT, secondDiag.conversationType)
    }

    @Test
    fun `partial duplicate burst speaks only new messages and suppresses duplicates`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings())
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.speechRequests.collect { speechItems.add(it) } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.diagnostics.collect { diagnostics.add(it) } }

        pipeline.start(backgroundScope)

        val senderPerson = SenderSnapshot(key = "budi_key", name = "Budi", isBot = false)
        val msg1 = MessagingStyleMessageSnapshot(text = "Pesan pertama", timestampMillis = now, sender = senderPerson)
        val msg2 = MessagingStyleMessageSnapshot(text = "Pesan kedua", timestampMillis = now + 1000, sender = senderPerson)

        // Snapshot 1 has only msg1
        val snapshot1 = NotificationSnapshot(
            packageName = "com.whatsapp",
            notificationKey = "key-burst",
            notificationId = 1,
            postTimeMillis = now,
            groupKey = null,
            title = "Budi",
            text = "Pesan pertama",
            bigText = null,
            textLines = emptyList(),
            subText = null,
            summaryText = null,
            category = "msg",
            isGroupSummary = false,
            conversationTitle = null,
            shortcutId = "shortcut-budi",
            messagingStyle = MessagingStyleSnapshot(
                userDisplayName = "Me",
                conversationTitle = null,
                isGroupConversation = false,
                messages = listOf(msg1),
            ),
        )
        submit(ingress, snapshot1)

        // Snapshot 2 has msg1 (duplicate) and msg2 (new)
        val snapshot2 = snapshot1.copy(
            postTimeMillis = now + 1000,
            messagingStyle = MessagingStyleSnapshot(
                userDisplayName = "Me",
                conversationTitle = null,
                isGroupConversation = false,
                messages = listOf(msg1, msg2),
            ),
        )
        submit(ingress, snapshot2)

        // Total speech requests should be 2: msg1 from snapshot1, msg2 from snapshot2
        assertEquals(2, speechItems.size)
        assertTrue(speechItems[0].text.contains("Pesan pertama"))
        assertTrue(speechItems[1].text.contains("Pesan kedua"))

        // Diagnostics should contain: 1) PolicyEvaluated(SPEAK), 2) DuplicateSuppressed, 3) PolicyEvaluated(SPEAK)
        assertEquals(3, diagnostics.size)
        assertIs<PipelineDiagnostic.PolicyEvaluated>(diagnostics[0])
        assertIs<PipelineDiagnostic.DuplicateSuppressed>(diagnostics[1])
        assertIs<PipelineDiagnostic.PolicyEvaluated>(diagnostics[2])
    }

    @Test
    fun `settings timeout fails closed and emits SettingsUnavailable diagnostic`() = runTest {
        val ingress = SerializedNotificationIngress()
        // Fake settings with 500ms delay
        val settings = FakeSettingsRepository(activeSettings(), delayMillis = 500L)
        val pipeline = createPipeline(
            ingress = ingress,
            settingsRepository = settings,
            settingsTimeoutMillis = 50L, // Timeout shorter than delay
        )

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.speechRequests.collect { speechItems.add(it) } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.diagnostics.collect { diagnostics.add(it) } }

        pipeline.start(backgroundScope)

        assertTrue(ingress.trySubmit(directMessagingSnapshot(text = "Pesan saat timeout")))
        testScheduler.advanceTimeBy(100L)
        testScheduler.runCurrent()

        assertEquals(0, speechItems.size)
        assertEquals(1, diagnostics.size)
        assertIs<PipelineDiagnostic.SettingsUnavailable>(diagnostics.first())
    }

    @Test
    fun `diagnostics enforce ADR-009 redaction invariants`() = runTest {
        val ingress = SerializedNotificationIngress()
        val settings = FakeSettingsRepository(activeSettings())
        val pipeline = createPipeline(ingress = ingress, settingsRepository = settings)

        val diagnostics = mutableListOf<PipelineDiagnostic>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.diagnostics.collect { diagnostics.add(it) } }

        pipeline.start(backgroundScope)

        val sensitiveSender = "SecretSenderName"
        val sensitiveText = "SuperSecretMessageBodyThatMustNeverAppearInLogs"
        val sensitiveTitle = "ConfidentialGroupChat"
        val rawShortcutId = "raw-shortcut-id-12345"

        submit(
            ingress,
            directMessagingSnapshot(
                sender = sensitiveSender,
                text = sensitiveText,
                shortcutId = rawShortcutId,
            ),
        )

        assertEquals(1, diagnostics.size)
        val diagString = diagnostics.first().toString()

        // Verify none of the sensitive content appears anywhere in toString()
        assertFalse(diagString.contains(sensitiveSender), "Sender name leaked in diagnostic string")
        assertFalse(diagString.contains(sensitiveText), "Message body leaked in diagnostic string")
        assertFalse(diagString.contains(sensitiveTitle), "Group title leaked in diagnostic string")
        assertFalse(diagString.contains(rawShortcutId), "Raw shortcut ID leaked in diagnostic string")

        // Verify correlation token is a truncated 8-char hex string
        val token = diagnostics.first().correlationToken
        assertNotNull(token)
        assertTrue(token.matches(Regex("^[0-9a-f]{8}$")), "Correlation token must be 8 hex chars")
    }

    // --- Test Helpers ---

    private fun TestScope.submit(ingress: SerializedNotificationIngress, snapshot: NotificationSnapshot) {
        assertTrue(ingress.trySubmit(snapshot))
        testScheduler.runCurrent()
    }

    private fun createPipeline(
        ingress: SerializedNotificationIngress,
        settingsRepository: SettingsRepository,
        settingsTimeoutMillis: Long = 2000L,
    ): NotificationPipeline = DefaultNotificationPipeline(
        ingress = ingress,
        parser = WhatsAppNotificationParser(),
        deduplicator = NotificationDeduplicator(clock = { now }),
        settingsRepository = settingsRepository,
        policyEvaluator = ReadingPolicyEvaluator(clockMillis = defaultClock),
        correlation = DiagnosticCorrelation(salt = "test-salt"),
        clockMillis = defaultClock,
        settingsTimeoutMillis = settingsTimeoutMillis,
    )

    private fun activeSettings(
        readerEnabled: Boolean = true,
        ridingState: RidingState = RidingState.ACTIVE,
        readPrivate: Boolean = true,
        groupMode: GroupReadMode = GroupReadMode.ALL_OBSERVED_GROUPS,
    ) = AppSettings(
        readerEnabled = readerEnabled,
        ridingState = ridingState,
        readPrivateMessages = readPrivate,
        groupReadMode = groupMode,
    )

    private fun directMessagingSnapshot(
        id: Int = 1,
        sender: String = "Budi",
        text: String = "Halo",
        postTime: Long = now,
        shortcutId: String = "shortcut-direct-1",
    ) = NotificationSnapshot(
        packageName = "com.whatsapp",
        notificationKey = "key-$id",
        notificationId = id,
        postTimeMillis = postTime,
        groupKey = null,
        title = sender,
        text = text,
        bigText = null,
        textLines = emptyList(),
        subText = null,
        summaryText = null,
        category = "msg",
        isGroupSummary = false,
        conversationTitle = null,
        shortcutId = shortcutId,
        messagingStyle = MessagingStyleSnapshot(
            userDisplayName = "Me",
            conversationTitle = null,
            isGroupConversation = false,
            messages = listOf(
                MessagingStyleMessageSnapshot(
                    text = text,
                    timestampMillis = postTime,
                    sender = SenderSnapshot(key = "${sender}_key", name = sender, isBot = false),
                )
            ),
        ),
    )

    private fun groupMessagingSnapshot(
        id: Int = 2,
        groupTitle: String = "Tim Gowes",
        sender: String = "Budi",
        text: String = "Siap",
        postTime: Long = now,
        shortcutId: String = "shortcut-group-1",
    ) = NotificationSnapshot(
        packageName = "com.whatsapp",
        notificationKey = "key-$id",
        notificationId = id,
        postTimeMillis = postTime,
        groupKey = null,
        title = groupTitle,
        text = "$sender: $text",
        bigText = null,
        textLines = emptyList(),
        subText = null,
        summaryText = null,
        category = "msg",
        isGroupSummary = false,
        conversationTitle = groupTitle,
        shortcutId = shortcutId,
        messagingStyle = MessagingStyleSnapshot(
            userDisplayName = "Me",
            conversationTitle = groupTitle,
            isGroupConversation = true,
            messages = listOf(
                MessagingStyleMessageSnapshot(
                    text = text,
                    timestampMillis = postTime,
                    sender = SenderSnapshot(key = "${sender}_key", name = sender, isBot = false),
                )
            ),
        ),
    )

    private fun rawSnapshot(
        packageName: String = "com.whatsapp",
        id: Int = 3,
        title: String? = null,
        text: String? = null,
        isGroupSummary: Boolean = false,
        category: String? = null,
    ) = NotificationSnapshot(
        packageName = packageName,
        notificationKey = "key-$id",
        notificationId = id,
        postTimeMillis = now,
        groupKey = null,
        title = title,
        text = text,
        bigText = null,
        textLines = emptyList(),
        subText = null,
        summaryText = null,
        category = category,
        isGroupSummary = isGroupSummary,
        conversationTitle = null,
        shortcutId = null,
        messagingStyle = null,
    )

    private class FakeSettingsRepository(
        initialSettings: AppSettings,
        private val delayMillis: Long = 0L,
    ) : SettingsRepository {
        var current = initialSettings
        private val flow = MutableStateFlow(initialSettings)

        override fun observeSettings(): Flow<AppSettings> = flow

        override suspend fun getSettings(): AppSettings {
            if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis)
            return current
        }

        override suspend fun setReaderEnabled(enabled: Boolean) {
            current = current.copy(readerEnabled = enabled)
            flow.value = current
        }

        override suspend fun setReadPrivateMessages(enabled: Boolean) {
            current = current.copy(readPrivateMessages = enabled)
            flow.value = current
        }

        override suspend fun setSpeechRate(rate: Float) {
            current = current.copy(speechRate = rate)
            flow.value = current
        }

        override suspend fun setRidingState(riding: RidingState) {
            current = current.copy(ridingState = riding)
            flow.value = current
        }

        override suspend fun setGroupReadMode(mode: GroupReadMode) {
            current = current.copy(groupReadMode = mode)
            flow.value = current
        }

        override suspend fun setSelectedConversationIds(ids: Set<ConversationId>) {
            current = current.copy(selectedConversationIds = ids)
            flow.value = current
        }

        override suspend fun setAnnounceSenderAndGroup(announce: Boolean) {
            current = current.copy(announceSenderAndGroup = announce)
            flow.value = current
        }

        override suspend fun performMigrationIfNeeded() {}

        override suspend fun resetAllSettings() {
            current = AppSettings()
            flow.value = current
        }
    }
}
