package com.ridenotify.app.wa_reader.ui.status

import com.ridenotify.app.wa_reader.R
import com.ridenotify.app.wa_reader.isNotificationAccessGranted
import com.ridenotify.app.wa_reader.listener.ListenerConnectionState
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.speech.TtsEngineState
import com.ridenotify.app.wa_reader.speech.TtsUnavailableReason
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingStatusTest {
    @Test
    fun `notification access lookup fails closed`() {
        assertEquals(
            false,
            isNotificationAccessGranted("com.ridenotify.app.wa_reader") {
                error("settings provider unavailable")
            },
        )
    }

    @Test
    fun `denied access and disconnected listener are distinct warnings`() {
        val status = onboardingStatus(
            notificationAccessGranted = false,
            listenerConnection = ListenerConnectionState.DISCONNECTED,
        )

        assertEquals(R.string.status_notification_access_inactive, status.notificationAccess.titleRes)
        assertEquals(StatusSeverity.WARNING, status.notificationAccess.severity)
        assertEquals(R.string.status_listener_disconnected, status.listenerConnection.titleRes)
        assertEquals(StatusSeverity.WARNING, status.listenerConnection.severity)
    }

    @Test
    fun `granted access and connected listener are distinct ready states`() {
        val status = onboardingStatus(
            notificationAccessGranted = true,
            listenerConnection = ListenerConnectionState.CONNECTED,
        )

        assertEquals(R.string.status_notification_access_active, status.notificationAccess.titleRes)
        assertEquals(R.string.status_listener_connected, status.listenerConnection.titleRes)
        assertEquals(StatusSeverity.NORMAL, status.notificationAccess.severity)
        assertEquals(StatusSeverity.NORMAL, status.listenerConnection.severity)
    }

    @Test
    fun `reconnecting listener remains visible while access is granted`() {
        val status = onboardingStatus(
            notificationAccessGranted = true,
            listenerConnection = ListenerConnectionState.REBIND_REQUESTED,
        )

        assertEquals(R.string.status_listener_reconnecting, status.listenerConnection.titleRes)
        assertEquals(StatusSeverity.WARNING, status.listenerConnection.severity)
    }

    @Test
    fun `unavailable Indonesian voice is a separate warning`() {
        val status = OnboardingStatus.from(
            notificationAccessGranted = true,
            listenerConnection = ListenerConnectionState.CONNECTED,
            settings = AppSettings(),
            ttsState = TtsEngineState.Unavailable(TtsUnavailableReason.INDONESIAN_VOICE_UNAVAILABLE),
        )

        assertEquals(R.string.status_tts_unavailable, status.tts.titleRes)
        assertEquals(StatusSeverity.WARNING, status.tts.severity)
    }

    @Test
    fun `settings loading does not display guessed reader or riding values`() {
        val status = OnboardingStatus.from(
            notificationAccessGranted = true,
            listenerConnection = ListenerConnectionState.CONNECTED,
            settings = null,
            ttsState = TtsEngineState.Ready(Locale.forLanguageTag("id-ID")),
        )

        assertEquals(R.string.status_loading, status.reader.titleRes)
        assertEquals(R.string.status_loading, status.riding.titleRes)
        assertEquals(R.string.status_tts_ready, status.tts.titleRes)
    }

    private fun onboardingStatus(
        notificationAccessGranted: Boolean,
        listenerConnection: ListenerConnectionState,
    ): OnboardingStatus = OnboardingStatus.from(
        notificationAccessGranted = notificationAccessGranted,
        listenerConnection = listenerConnection,
        settings = AppSettings(),
        ttsState = TtsEngineState.Initializing,
    )
}
