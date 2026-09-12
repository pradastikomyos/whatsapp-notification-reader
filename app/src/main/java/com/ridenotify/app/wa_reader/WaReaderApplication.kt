package com.ridenotify.app.wa_reader

import android.app.Application
import com.ridenotify.app.wa_reader.di.AppContainer

class WaReaderApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        retireObservedConversationsDatabase(this)
        appContainer = AppContainer(this)
        appContainer.start()
    }
}

internal fun retireObservedConversationsDatabase(application: Application): Boolean =
    application.deleteDatabase("observed_conversations.db")
