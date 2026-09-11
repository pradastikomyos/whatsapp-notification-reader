package com.ridenotify.app.wa_reader.di

import android.content.Context
import com.ridenotify.app.wa_reader.data.settings.DataStoreSettingsRepository
import com.ridenotify.app.wa_reader.data.settings.SettingsRepository

class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext

    // Settings repository with DataStore backend.
    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(applicationContext)
    }
}
