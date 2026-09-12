package com.ridenotify.app.wa_reader.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ridenotify.app.wa_reader.R
import com.ridenotify.app.wa_reader.data.settings.SettingsRepository
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.ui.components.InlineStatus
import com.ridenotify.app.wa_reader.ui.components.LoadingStatus
import com.ridenotify.app.wa_reader.ui.components.PreferenceSwitchRow
import com.ridenotify.app.wa_reader.ui.components.ScreenColumn
import com.ridenotify.app.wa_reader.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ReaderSettingsUiState(val settings: AppSettings?) {
    val isLoading get() = settings == null
}

enum class ReaderSettingsUpdateResult { APPLIED, FAILED }

class ReaderSettingsController(
    private val settingsRepository: SettingsRepository,
) {
    suspend fun setReadPrivateMessages(enabled: Boolean) = update {
        settingsRepository.setReadPrivateMessages(enabled)
    }

    suspend fun setAnnounceSender(enabled: Boolean) = update {
        settingsRepository.setAnnounceSender(enabled)
    }

    private suspend fun update(action: suspend () -> Unit): ReaderSettingsUpdateResult = try {
        action()
        ReaderSettingsUpdateResult.APPLIED
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        ReaderSettingsUpdateResult.FAILED
    }
}

@Composable
fun ReaderSettingsRoute(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    wideLayout: Boolean = false,
) {
    val settings by settingsRepository.observeSettings().collectAsState(initial = null)
    val controller = remember(settingsRepository) { ReaderSettingsController(settingsRepository) }
    val scope = rememberCoroutineScope()
    var updateFailed by remember { mutableStateOf(false) }

    ReaderSettingsScreen(
        state = ReaderSettingsUiState(settings),
        updateFailed = updateFailed,
        onPrivateMessagesChanged = { enabled ->
            scope.launch {
                updateFailed = controller.setReadPrivateMessages(enabled) == ReaderSettingsUpdateResult.FAILED
            }
        },
        onAnnouncementChanged = { enabled ->
            scope.launch {
                updateFailed = controller.setAnnounceSender(enabled) == ReaderSettingsUpdateResult.FAILED
            }
        },
        modifier = modifier,
        wideLayout = wideLayout,
    )
}

@Composable
fun ReaderSettingsScreen(
    state: ReaderSettingsUiState,
    onPrivateMessagesChanged: (Boolean) -> Unit,
    onAnnouncementChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    updateFailed: Boolean = false,
    wideLayout: Boolean = false,
) {
    ScreenColumn(modifier = modifier, wideLayout = wideLayout) {
        if (state.isLoading) {
            LoadingStatus(stringResource(R.string.settings_loading))
        } else {
            val settings = state.settings!!
            PrivateMessagesControl(settings.readPrivateMessages, onPrivateMessagesChanged)
            AnnouncementControl(settings.announceSender, onAnnouncementChanged)
            FixedLocaleCard()
            FixedQueuePolicyCard()
        }
        if (updateFailed) {
            InlineStatus(
                title = stringResource(R.string.update_failed_heading),
                detail = stringResource(R.string.settings_update_failed),
                warning = true,
            )
        }
    }
}

@Composable
private fun PrivateMessagesControl(enabled: Boolean, onChanged: (Boolean) -> Unit) {
    PreferenceSwitchRow(
        title = stringResource(R.string.settings_private_heading),
        detail = stringResource(R.string.settings_private_detail),
        checked = enabled,
        onCheckedChange = onChanged,
    )
}

@Composable
private fun AnnouncementControl(enabled: Boolean, onChanged: (Boolean) -> Unit) {
    PreferenceSwitchRow(
        title = stringResource(R.string.settings_announcement_heading),
        detail = stringResource(R.string.settings_announcement_detail),
        checked = enabled,
        onCheckedChange = onChanged,
    )
}

@Composable
private fun FixedLocaleCard() {
    SectionCard(
        title = stringResource(R.string.settings_locale_heading),
        detail = stringResource(R.string.settings_locale_detail),
    )
}

@Composable
private fun FixedQueuePolicyCard() {
    SectionCard(
        title = stringResource(R.string.settings_queue_heading),
        detail = stringResource(R.string.settings_queue_detail),
    )
}
