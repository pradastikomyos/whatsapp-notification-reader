package com.ridenotify.app.wa_reader.di

import android.content.Context
import com.ridenotify.app.wa_reader.data.settings.DataStoreSettingsRepository
import com.ridenotify.app.wa_reader.data.settings.SettingsRepository
import com.ridenotify.app.wa_reader.data.settings.ThemeSettingsRepository
import com.ridenotify.app.wa_reader.data.notification.NotificationDeduplicator
import com.ridenotify.app.wa_reader.data.notification.parser.NotificationParser
import com.ridenotify.app.wa_reader.data.notification.parser.WhatsAppNotificationParser
import com.ridenotify.app.wa_reader.listener.DefaultListenerRebindController
import com.ridenotify.app.wa_reader.listener.ListenerConnectionTracker
import com.ridenotify.app.wa_reader.listener.ListenerRebindController
import com.ridenotify.app.wa_reader.listener.SerializedNotificationIngress
import com.ridenotify.app.wa_reader.pipeline.DefaultNotificationPipeline
import com.ridenotify.app.wa_reader.pipeline.DiagnosticCorrelation
import com.ridenotify.app.wa_reader.pipeline.NotificationPipeline
import com.ridenotify.app.wa_reader.policy.ReadingPolicyEvaluator
import com.ridenotify.app.wa_reader.speech.AndroidAudioFocusController
import com.ridenotify.app.wa_reader.speech.AndroidForegroundPlaybackGate
import com.ridenotify.app.wa_reader.speech.AndroidTtsEngine
import com.ridenotify.app.wa_reader.speech.SpeechCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pipelineJob: Job? = null
    private var speechCoordinatorJob: Job? = null

    // Settings repository with DataStore backend.
    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(applicationContext)
    }
    val themeSettingsRepository by lazy { ThemeSettingsRepository(applicationContext) }

    val listenerConnectionTracker = ListenerConnectionTracker()
    var listenerRebindController: ListenerRebindController =
        DefaultListenerRebindController.forContext(applicationContext)
        private set
    val notificationIngress = SerializedNotificationIngress()

    val notificationDeduplicator by lazy { NotificationDeduplicator() }
    val notificationParser: NotificationParser by lazy { WhatsAppNotificationParser() }
    val readingPolicyEvaluator by lazy { ReadingPolicyEvaluator() }
    val diagnosticCorrelation by lazy { DiagnosticCorrelation() }

    // The UI observes engine readiness only; playback remains coordinator-owned.
    val ttsEngine by lazy { AndroidTtsEngine(applicationContext, applicationScope) }
    private val audioFocusController by lazy { AndroidAudioFocusController.forContext(applicationContext) }
    private val foregroundPlaybackGate by lazy { AndroidForegroundPlaybackGate(applicationContext) }
    val speechCoordinator by lazy {
        SpeechCoordinator(
            ttsEngine = ttsEngine,
            audioFocusController = audioFocusController,
            foregroundPlaybackGate = foregroundPlaybackGate,
            scope = applicationScope,
        )
    }

    val notificationPipeline: NotificationPipeline by lazy {
        DefaultNotificationPipeline(
            ingress = notificationIngress,
            parser = notificationParser,
            deduplicator = notificationDeduplicator,
            settingsRepository = settingsRepository,
            policyEvaluator = readingPolicyEvaluator,
            correlation = diagnosticCorrelation,
        )
    }

    /** Starts the UI-independent pipeline once for this application process. */
    @Synchronized
    fun start() {
        if (speechCoordinatorJob?.isActive != true) {
            speechCoordinatorJob = speechCoordinator.start(
                speechRequests = notificationPipeline.speechRequests,
                settings = settingsRepository.observeSettings(),
            )
        }
        if (pipelineJob?.isActive != true) {
            pipelineJob = notificationPipeline.start(applicationScope)
        }
    }

    internal fun replaceListenerRebindControllerForTest(controller: ListenerRebindController) {
        listenerRebindController = controller
    }
}
