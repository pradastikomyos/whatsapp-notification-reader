package com.ridenotify.app.wa_reader.ui.status

import androidx.annotation.StringRes
import com.ridenotify.app.wa_reader.R
import com.ridenotify.app.wa_reader.listener.ListenerConnectionState
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.RidingState
import com.ridenotify.app.wa_reader.speech.TtsEngineState

data class OnboardingStatus(
    val notificationAccess: StatusPresentation,
    val listenerConnection: StatusPresentation,
    val reader: StatusPresentation,
    val riding: StatusPresentation,
    val tts: StatusPresentation,
) {
    companion object {
        fun from(
            notificationAccessGranted: Boolean,
            listenerConnection: ListenerConnectionState,
            settings: AppSettings?,
            ttsState: TtsEngineState,
        ): OnboardingStatus = OnboardingStatus(
            notificationAccess = notificationAccessGranted.presentation(),
            listenerConnection = listenerConnection.presentation(),
            reader = settings?.readerEnabled?.presentationReader() ?: loadingPresentation(),
            riding = settings?.ridingState?.presentationRiding() ?: loadingPresentation(),
            tts = ttsState.presentation(),
        )
    }
}

data class StatusPresentation(
    @param:StringRes val titleRes: Int,
    @param:StringRes val detailRes: Int,
    val severity: StatusSeverity = StatusSeverity.NORMAL,
)

enum class StatusSeverity { NORMAL, WARNING }

private fun Boolean.presentation(): StatusPresentation = if (this) {
    StatusPresentation(R.string.status_notification_access_active, R.string.status_notification_access_active_detail)
} else {
    StatusPresentation(
        R.string.status_notification_access_inactive,
        R.string.status_notification_access_inactive_detail,
        StatusSeverity.WARNING,
    )
}

private fun ListenerConnectionState.presentation(): StatusPresentation = when (this) {
    ListenerConnectionState.CONNECTED ->
        StatusPresentation(R.string.status_listener_connected, R.string.status_listener_connected_detail)
    ListenerConnectionState.REBIND_REQUESTED -> StatusPresentation(
        R.string.status_listener_reconnecting,
        R.string.status_listener_reconnecting_detail,
        StatusSeverity.WARNING,
    )
    ListenerConnectionState.DISCONNECTED -> StatusPresentation(
        R.string.status_listener_disconnected,
        R.string.status_listener_disconnected_detail,
        StatusSeverity.WARNING,
    )
}

private fun Boolean.presentationReader(): StatusPresentation = if (this) {
    StatusPresentation(R.string.status_reader_active, R.string.status_reader_active_detail)
} else {
    StatusPresentation(
        R.string.status_reader_inactive,
        R.string.status_reader_inactive_detail,
        StatusSeverity.WARNING,
    )
}

private fun RidingState.presentationRiding(): StatusPresentation = when (this) {
    RidingState.ACTIVE -> StatusPresentation(R.string.status_riding_active, R.string.status_riding_active_detail)
    RidingState.INACTIVE -> StatusPresentation(R.string.status_riding_inactive, R.string.status_riding_inactive_detail)
}

private fun TtsEngineState.presentation(): StatusPresentation = when (this) {
    TtsEngineState.Initializing -> StatusPresentation(R.string.status_tts_initializing, R.string.status_tts_initializing_detail)
    is TtsEngineState.Ready -> StatusPresentation(R.string.status_tts_ready, R.string.status_tts_ready_detail)
    is TtsEngineState.Unavailable -> StatusPresentation(
        R.string.status_tts_unavailable,
        R.string.status_tts_unavailable_detail,
        StatusSeverity.WARNING,
    )
    TtsEngineState.Shutdown -> StatusPresentation(
        R.string.status_tts_unavailable,
        R.string.status_tts_shutdown_detail,
        StatusSeverity.WARNING,
    )
}

private fun loadingPresentation() =
    StatusPresentation(R.string.status_loading, R.string.status_loading_detail)
