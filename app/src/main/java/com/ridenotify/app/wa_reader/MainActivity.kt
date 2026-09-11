package com.ridenotify.app.wa_reader

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {
    private var notificationAccessGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateNotificationAccess()
        setContent {
            WaReaderApp(notificationAccessGranted = notificationAccessGranted)
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationAccess()
    }

    private fun updateNotificationAccess() {
        notificationAccessGranted = packageName in
            NotificationManagerCompat.getEnabledListenerPackages(this)
    }
}

@Composable
fun WaReaderApp(notificationAccessGranted: Boolean) {
    val context = LocalContext.current

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "RideNotify",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Pembaca notifikasi WhatsApp saat berkendara",
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (notificationAccessGranted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (notificationAccessGranted) {
                                "Akses notifikasi aktif"
                            } else {
                                "Akses notifikasi belum aktif"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            modifier = Modifier.padding(top = 8.dp),
                            text = if (notificationAccessGranted) {
                                "RideNotify sudah diizinkan membaca notifikasi masuk."
                            } else {
                                "Aktifkan RideNotify di menu Akses notifikasi agar pesan masuk dapat dibaca."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                        )
                    },
                ) {
                    Text("Buka pengaturan notifikasi")
                }
                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = "Izin ini berada di Akses aplikasi khusus Android, bukan di daftar izin biasa.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WaReaderAppPreview() {
    WaReaderApp(notificationAccessGranted = false)
}
