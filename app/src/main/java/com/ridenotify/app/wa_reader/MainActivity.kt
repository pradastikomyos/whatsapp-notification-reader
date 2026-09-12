package com.ridenotify.app.wa_reader

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import com.ridenotify.app.wa_reader.ui.RideNotifyApp

class MainActivity : ComponentActivity() {
    private var notificationAccessGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateNotificationAccess()
        setContent {
            RideNotifyApp(
                container = (application as WaReaderApplication).appContainer,
                notificationAccessGranted = notificationAccessGranted,
                onOpenNotificationAccess = ::openNotificationAccessSettings,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationAccess()
    }

    private fun updateNotificationAccess() {
        notificationAccessGranted = isNotificationAccessGranted(packageName) {
            NotificationManagerCompat.getEnabledListenerPackages(this)
        }
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}

internal fun isNotificationAccessGranted(
    packageName: String,
    enabledListenerPackages: () -> Set<String>,
): Boolean = try {
    packageName in enabledListenerPackages()
} catch (_: RuntimeException) {
    false
}
