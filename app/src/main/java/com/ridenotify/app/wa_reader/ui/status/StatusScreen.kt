package com.ridenotify.app.wa_reader.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ridenotify.app.wa_reader.R
import com.ridenotify.app.wa_reader.ui.components.InlineStatus
import com.ridenotify.app.wa_reader.ui.components.ScreenColumn
import com.ridenotify.app.wa_reader.ui.components.SectionCard

@Composable
fun StatusScreen(
    status: OnboardingStatus,
    onOpenNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier,
    wideLayout: Boolean = false,
) {
    ScreenColumn(modifier = modifier, wideLayout = wideLayout) {
        Text(
            text = stringResource(R.string.onboarding_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard(
            title = stringResource(status.notificationAccess.titleRes),
            detail = stringResource(status.notificationAccess.detailRes),
            prominent = true,
            warning = status.notificationAccess.severity == StatusSeverity.WARNING,
        )
        Button(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            onClick = onOpenNotificationAccess,
        ) {
            Text(stringResource(R.string.open_notification_access_settings))
        }
        Text(
            text = stringResource(R.string.notification_access_guidance),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = stringResource(R.string.current_status_heading), style = MaterialTheme.typography.titleLarge)
        SectionCard(title = stringResource(R.string.status_system_heading)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusRow(status.listenerConnection)
                StatusRow(status.reader)
                StatusRow(status.riding)
                StatusRow(status.tts)
            }
        }
    }
}

@Composable
private fun StatusRow(status: StatusPresentation) {
    InlineStatus(
        title = stringResource(status.titleRes),
        detail = stringResource(status.detailRes),
        warning = status.severity == StatusSeverity.WARNING,
    )
}
