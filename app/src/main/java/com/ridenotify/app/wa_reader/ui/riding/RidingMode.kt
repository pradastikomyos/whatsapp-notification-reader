package com.ridenotify.app.wa_reader.ui.riding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ridenotify.app.wa_reader.R
import com.ridenotify.app.wa_reader.data.settings.SettingsRepository
import com.ridenotify.app.wa_reader.model.AppSettings
import com.ridenotify.app.wa_reader.model.RidingState
import com.ridenotify.app.wa_reader.ui.components.InlineStatus
import com.ridenotify.app.wa_reader.ui.components.LoadingStatus
import com.ridenotify.app.wa_reader.ui.components.ScreenColumn
import com.ridenotify.app.wa_reader.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class RidingModeUiState(
    val isLoading: Boolean,
    val ridingState: RidingState?,
    val readerEnabled: Boolean?,
) {
    companion object {
        fun from(settings: AppSettings?): RidingModeUiState = if (settings == null) {
            RidingModeUiState(isLoading = true, ridingState = null, readerEnabled = null)
        } else {
            RidingModeUiState(
                isLoading = false,
                ridingState = settings.ridingState,
                readerEnabled = settings.readerEnabled,
            )
        }
    }
}

enum class RidingModeUpdateResult { APPLIED, FAILED }

/** The sole P5-T05 write path: one direct user action updates the DataStore state. */
class RidingModeController(
    private val settingsRepository: SettingsRepository,
) {
    suspend fun setActive(active: Boolean): RidingModeUpdateResult = try {
        settingsRepository.setRidingState(if (active) RidingState.ACTIVE else RidingState.INACTIVE)
        RidingModeUpdateResult.APPLIED
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        RidingModeUpdateResult.FAILED
    }
}

@Composable
fun RidingModeRoute(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    wideLayout: Boolean = false,
) {
    val settings by settingsRepository.observeSettings().collectAsState(initial = null)
    val controller = remember(settingsRepository) { RidingModeController(settingsRepository) }
    val scope = rememberCoroutineScope()
    var updateFailed by remember { mutableStateOf(false) }

    RidingModeScreen(
        state = RidingModeUiState.from(settings),
        updateFailed = updateFailed,
        onRidingChanged = { active ->
            scope.launch {
                updateFailed = controller.setActive(active) == RidingModeUpdateResult.FAILED
            }
        },
        modifier = modifier,
        wideLayout = wideLayout,
    )
}

@Composable
fun RidingModeScreen(
    state: RidingModeUiState,
    onRidingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    updateFailed: Boolean = false,
    wideLayout: Boolean = false,
) {
    ScreenColumn(modifier = modifier, wideLayout = wideLayout) {
        Text(
            text = stringResource(R.string.riding_mode_description),
            style = MaterialTheme.typography.bodyMedium,
        )

        when {
            state.isLoading -> LoadingStatus(stringResource(R.string.riding_mode_loading))
            else -> RidingModeContent(
                ridingState = state.ridingState!!,
                readerEnabled = state.readerEnabled!!,
                onRidingChanged = onRidingChanged,
            )
        }
        if (updateFailed) {
            InlineStatus(
                title = stringResource(R.string.update_failed_heading),
                detail = stringResource(R.string.riding_mode_update_failed),
                warning = true,
            )
        }
    }
}

@Composable
private fun RidingModeContent(
    ridingState: RidingState,
    readerEnabled: Boolean,
    onRidingChanged: (Boolean) -> Unit,
) {
    val active = ridingState == RidingState.ACTIVE
    SectionCard(
        title = stringResource(if (active) R.string.riding_mode_active else R.string.riding_mode_inactive),
        detail = stringResource(
            if (active) R.string.riding_mode_active_detail else R.string.riding_mode_inactive_detail,
        ),
        prominent = true,
    ) {
        Button(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            onClick = { onRidingChanged(!active) },
        ) {
            Text(
                stringResource(
                    if (active) R.string.riding_mode_deactivate_action else R.string.riding_mode_activate_action,
                ),
            )
        }
    }

    SectionCard(
        title = stringResource(R.string.riding_mode_effective_state_heading),
        detail = stringResource(effectiveStateText(readerEnabled, ridingState)),
    ) {
        Text(
            text = stringResource(
                if (readerEnabled) R.string.riding_mode_reader_enabled else R.string.riding_mode_reader_disabled,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun effectiveStateText(readerEnabled: Boolean, ridingState: RidingState): Int = when {
    !readerEnabled -> R.string.riding_mode_effective_reader_disabled
    ridingState == RidingState.INACTIVE -> R.string.riding_mode_effective_riding_inactive
    else -> R.string.riding_mode_effective_active
}
