package com.ridenotify.app.wa_reader.listener

import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ridenotify.app.wa_reader.data.notification.NotificationDeduplicator
import com.ridenotify.app.wa_reader.data.notification.parser.WhatsAppNotificationParser
import com.ridenotify.app.wa_reader.data.settings.DataStoreSettingsRepository
import com.ridenotify.app.wa_reader.data.settings.SettingsRepository
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.MessagingStyleMessageSnapshot
import com.ridenotify.app.wa_reader.model.MessagingStyleSnapshot
import com.ridenotify.app.wa_reader.model.NotificationSnapshot
import com.ridenotify.app.wa_reader.model.RidingState
import com.ridenotify.app.wa_reader.model.SenderSnapshot
import com.ridenotify.app.wa_reader.model.SpeechRequest
import com.ridenotify.app.wa_reader.pipeline.DefaultNotificationPipeline
import com.ridenotify.app.wa_reader.pipeline.DiagnosticCorrelation
import com.ridenotify.app.wa_reader.pipeline.DiagnosticOutcome
import com.ridenotify.app.wa_reader.pipeline.PipelineDiagnostic
import com.ridenotify.app.wa_reader.policy.ReadingPolicyEvaluator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ListenerRecoveryIntegrationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val testComponent = ComponentName(
        "com.ridenotify.app.wa_reader",
        "com.ridenotify.app.wa_reader.MyNotificationListener",
    )

    private fun sampleMessagingSnapshot(
        id: Int = 1,
        sender: String = "Budi",
        text: String = "Halo apa kabar",
        postTime: Long = 1_000_000L,
    ): NotificationSnapshot = NotificationSnapshot(
        packageName = "com.whatsapp",
        notificationKey = "0|com.whatsapp|$id|null|10001",
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
        shortcutId = "shortcut-$id",
        messagingStyle = MessagingStyleSnapshot(
            userDisplayName = "Me",
            conversationTitle = null,
            isGroupConversation = false,
            messages = listOf(
                MessagingStyleMessageSnapshot(
                    text = text,
                    timestampMillis = postTime,
                    sender = SenderSnapshot(key = "${sender}_key", name = sender, isBot = false),
                ),
            ),
        ),
    )

    @Test
    fun `process recreation reloads persisted settings without activity or guessed defaults`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication().applicationContext
        val prefsFile = File(temporaryFolder.root, "recovery_settings.preferences_pb")

        // First process lifecycle: save non-default settings (readerEnabled = false)
        val scope1 = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val ds1 = PreferenceDataStoreFactory.create(scope = scope1) { prefsFile }
            val repo1 = DataStoreSettingsRepository(context, ds1)
            repo1.setReaderEnabled(false)
            assertEquals(false, repo1.getSettings().readerEnabled)
        } finally {
            scope1.cancel()
        }

        // Second process lifecycle: simulated cold start with brand new instances
        // No Activity reference, no static channel, no guessed defaults
        val scope2 = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val ds2 = PreferenceDataStoreFactory.create(scope = scope2) { prefsFile }
            val repo2 = DataStoreSettingsRepository(context, ds2)

            val ingress = SerializedNotificationIngress()
            val pipeline = DefaultNotificationPipeline(
                ingress = ingress,
                parser = WhatsAppNotificationParser(),
                deduplicator = NotificationDeduplicator(),
                settingsRepository = repo2,
                policyEvaluator = ReadingPolicyEvaluator(clockMillis = { 1_000_000L }),
                correlation = DiagnosticCorrelation(salt = "recovery-salt"),
                clockMillis = { 1_000_000L },
            )

            val speechItems = mutableListOf<SpeechRequest>()
            val diagnostics = mutableListOf<PipelineDiagnostic>()

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                pipeline.speechRequests.collect { speechItems.add(it) }
            }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                pipeline.diagnostics.collect { diagnostics.add(it) }
            }

            pipeline.start(backgroundScope)

            // Submit notification
            assertTrue(ingress.trySubmit(sampleMessagingSnapshot()))
            testScheduler.runCurrent()

            // Verify: Speech must be suppressed because persisted readerEnabled is FALSE.
            // If it had used guessed default (or corrupted defaults), behavior would differ.
            assertEquals(0, speechItems.size)
            assertEquals(1, diagnostics.size)
            val diag = assertIs<PipelineDiagnostic.PolicyEvaluated>(diagnostics.first())
            assertEquals(DiagnosticOutcome.SKIP_READER_DISABLED, diag.outcome)
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun `notification arrival before settings snapshot fails closed after bounded timeout`() = runTest {
        val ingress = SerializedNotificationIngress()

        // Hanging settings repository: never resolves within the timeout
        val blockingSettings = object : SettingsRepository {
            override fun observeSettings(): Flow<AppSettings> = emptyFlow()
            override suspend fun getSettings(): AppSettings {
                delay(10_000L) // Exceeds bounded timeout
                return AppSettings()
            }
            override suspend fun setReaderEnabled(enabled: Boolean) {}
            override suspend fun setReadPrivateMessages(enabled: Boolean) {}
            override suspend fun setSpeechRate(rate: Float) {}
            override suspend fun setRidingState(riding: RidingState) {}
            override suspend fun setAnnounceSender(announce: Boolean) {}
            override suspend fun performMigrationIfNeeded() {}
            override suspend fun resetAllSettings() {}
        }

        val pipeline = DefaultNotificationPipeline(
            ingress = ingress,
            parser = WhatsAppNotificationParser(),
            deduplicator = NotificationDeduplicator(),
            settingsRepository = blockingSettings,
            policyEvaluator = ReadingPolicyEvaluator(clockMillis = { 1_000_000L }),
            correlation = DiagnosticCorrelation(salt = "recovery-salt"),
            clockMillis = { 1_000_000L },
            settingsTimeoutMillis = 100L,
        )

        val speechItems = mutableListOf<SpeechRequest>()
        val diagnostics = mutableListOf<PipelineDiagnostic>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.speechRequests.collect { speechItems.add(it) }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.diagnostics.collect { diagnostics.add(it) }
        }

        pipeline.start(backgroundScope)

        assertTrue(ingress.trySubmit(sampleMessagingSnapshot()))
        testScheduler.advanceTimeBy(150L)
        testScheduler.runCurrent()

        // Critical fail-closed contract: zero speech, SettingsUnavailable diagnostic
        assertEquals(0, speechItems.size)
        assertEquals(1, diagnostics.size)
        assertIs<PipelineDiagnostic.SettingsUnavailable>(diagnostics.first())
    }

    @Test
    fun `listener disconnect and recovery sequence follows connection tracker and rebind controller`() {
        val tracker = ListenerConnectionTracker()
        assertEquals(ListenerConnectionState.DISCONNECTED, tracker.state.value)

        // 1. Initial connection
        tracker.connected()
        assertEquals(ListenerConnectionState.CONNECTED, tracker.state.value)

        // 2. OS disconnects while permission is still granted
        var rebindRequestedComponent: ComponentName? = null
        var isEnabled = true
        val rebindController = DefaultListenerRebindController(
            enabledCheck = { isEnabled },
            requestRebind = { component -> rebindRequestedComponent = component },
        )

        // Simulate onListenerDisconnected
        tracker.disconnected()
        if (rebindController.onDisconnected(testComponent)) {
            tracker.rebindRequested()
        }

        assertEquals(ListenerConnectionState.REBIND_REQUESTED, tracker.state.value)
        assertEquals(testComponent, rebindRequestedComponent)

        // 3. OS re-establishes connection
        rebindController.onConnected()
        tracker.connected()
        assertEquals(ListenerConnectionState.CONNECTED, tracker.state.value)

        // 4. User revoked permission: disconnect must NOT request rebind
        isEnabled = false
        rebindRequestedComponent = null
        tracker.disconnected()
        val rebindAttempted = rebindController.onDisconnected(testComponent)
        if (rebindAttempted) {
            tracker.rebindRequested()
        }

        assertFalse(rebindAttempted)
        assertEquals(ListenerConnectionState.DISCONNECTED, tracker.state.value)
        assertEquals(null, rebindRequestedComponent)

        // 5. Service onDestroy marks disconnected
        tracker.disconnected()
        assertEquals(ListenerConnectionState.DISCONNECTED, tracker.state.value)
    }
}
