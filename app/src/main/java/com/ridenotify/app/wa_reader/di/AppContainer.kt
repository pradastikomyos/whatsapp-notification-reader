package com.ridenotify.app.wa_reader.di

import android.content.Context
import com.ridenotify.app.wa_reader.data.settings.DataStoreSettingsRepository
import com.ridenotify.app.wa_reader.data.settings.SettingsRepository
import com.ridenotify.app.wa_reader.data.conversations.ConversationRepository
import com.ridenotify.app.wa_reader.data.conversations.room.ConversationDatabase
import com.ridenotify.app.wa_reader.data.conversations.room.RoomConversationRepository
import com.ridenotify.app.wa_reader.data.notification.NotificationDeduplicator
import com.ridenotify.app.wa_reader.data.notification.parser.NotificationParser
import com.ridenotify.app.wa_reader.data.notification.parser.WhatsAppNotificationParser
import com.ridenotify.app.wa_reader.listener.ListenerConnectionTracker
import com.ridenotify.app.wa_reader.listener.SerializedNotificationIngress
import com.ridenotify.app.wa_reader.pipeline.DefaultNotificationPipeline
import com.ridenotify.app.wa_reader.pipeline.DiagnosticCorrelation
import com.ridenotify.app.wa_reader.pipeline.NotificationPipeline
import com.ridenotify.app.wa_reader.policy.ReadingPolicyEvaluator

class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext

    // Settings repository with DataStore backend.
    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(applicationContext)
    }

    private val conversationDatabase: ConversationDatabase by lazy {
        ConversationDatabase.create(applicationContext)
    }

    val conversationRepository: ConversationRepository by lazy {
        RoomConversationRepository(conversationDatabase.observedConversationDao())
    }

    val listenerConnectionTracker = ListenerConnectionTracker()
    val notificationIngress = SerializedNotificationIngress()

    val notificationDeduplicator by lazy { NotificationDeduplicator() }
    val notificationParser: NotificationParser by lazy { WhatsAppNotificationParser() }
    val readingPolicyEvaluator by lazy { ReadingPolicyEvaluator() }
    val diagnosticCorrelation by lazy { DiagnosticCorrelation() }

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
}
