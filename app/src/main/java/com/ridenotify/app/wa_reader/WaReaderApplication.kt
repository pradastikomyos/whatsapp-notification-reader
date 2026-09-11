package com.ridenotify.app.wa_reader

import android.app.Application
import com.ridenotify.app.wa_reader.di.AppContainer

class WaReaderApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
