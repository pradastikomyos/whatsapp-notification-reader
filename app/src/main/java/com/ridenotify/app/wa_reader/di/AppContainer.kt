package com.ridenotify.app.wa_reader.di

import android.content.Context
import com.ridenotify.app.wa_reader.data.settings.DataStoreSettingsRepository
import com.ridenotify.app.wa_reader.data.settings.SettingsRepository
import com.ridenotify.app.wa_reader.data.conversations.ConversationRepository
import com.ridenotify.app.wa_reader.data.conversations.room.ConversationDatabase
import com.ridenotify.app.wa_reader.data.conversations.room.RoomConversationRepository

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
}
