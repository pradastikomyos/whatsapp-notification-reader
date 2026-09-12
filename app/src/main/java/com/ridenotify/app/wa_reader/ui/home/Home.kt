package com.ridenotify.app.wa_reader.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
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
import com.ridenotify.app.wa_reader.speech.SpeechCoordinator
import com.ridenotify.app.wa_reader.speech.TestSpeechRequestResult
import com.ridenotify.app.wa_reader.speech.TestSpeechState
import com.ridenotify.app.wa_reader.speech.TtsEngine
import com.ridenotify.app.wa_reader.speech.TtsEngineState
import com.ridenotify.app.wa_reader.ui.components.InlineStatus
import com.ridenotify.app.wa_reader.ui.components.LoadingStatus
import com.ridenotify.app.wa_reader.ui.components.PreferenceSwitchRow
import com.ridenotify.app.wa_reader.ui.components.ScreenColumn
import com.ridenotify.app.wa_reader.ui.components.SectionCard
import com.ridenotify.app.wa_reader.ui.riding.RidingModeController
import com.ridenotify.app.wa_reader.ui.riding.RidingModeUpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class HomeUiState(
    val settings: AppSettings?,
    val ttsState: TtsEngineState,
    val testSpeechState: TestSpeechState,
) {
    val isLoading get() = settings == null
    val canTestSpeech get() = settings != null && ttsState is TtsEngineState.Ready
}

enum class HomeUpdateResult { APPLIED, FAILED }

class HomeSettingsController(
    private val settingsRepository: SettingsRepository,
) {
    suspend fun setReaderEnabled(enabled: Boolean): HomeUpdateResult = update {
        settingsRepository.setReaderEnabled(enabled)
    }

    suspend fun setSpeechRate(rate: Float): HomeUpdateResult = update {
        settingsRepository.setSpeechRate(rate)
    }

    private suspend fun update(action: suspend () -> Unit): HomeUpdateResult = try {
        action()
        HomeUpdateResult.APPLIED
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        HomeUpdateResult.FAILED
    }
}

@Composable
fun HomeRoute(
    settingsRepository: SettingsRepository,
    ttsEngine: TtsEngine,
    speechCoordinator: SpeechCoordinator,
    modifier: Modifier = Modifier,
    wideLayout: Boolean = false,
) {
    val settings by settingsRepository.observeSettings().collectAsState(initial = null)
    val ttsState by ttsEngine.state.collectAsState()
    val testSpeechState by speechCoordinator.testSpeechState.collectAsState()
    val homeController = remember(settingsRepository) { HomeSettingsController(settingsRepository) }
    val ridingController = remember(settingsRepository) { RidingModeController(settingsRepository) }
    val scope = rememberCoroutineScope()
    var updateFailed by remember { mutableStateOf(false) }
    val testSpeechText = stringResource(R.string.home_test_speech_text)

    HomeScreen(
        state = HomeUiState(settings, ttsState, testSpeechState),
        updateFailed = updateFailed,
        onReaderChanged = { enabled ->
            scope.launch { updateFailed = homeController.setReaderEnabled(enabled) == HomeUpdateResult.FAILED }
        },
        onRidingChanged = { active ->
            scope.launch { updateFailed = ridingController.setActive(active) == RidingModeUpdateResult.FAILED }
        },
        onSpeechRateChanged = { rate ->
            scope.launch { updateFailed = homeController.setSpeechRate(rate) == HomeUpdateResult.FAILED }
        },
        onTestSpeech = {
            scope.launch {
                updateFailed = speechCoordinator.testSpeech(testSpeechText) == TestSpeechRequestResult.REJECTED
            }
        },
        modifier = modifier,
        wideLayout = wideLayout,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onReaderChanged: (Boolean) -> Unit,
    onRidingChanged: (Boolean) -> Unit,
    onSpeechRateChanged: (Float) -> Unit,
    onTestSpeech: () -> Unit,
    modifier: Modifier = Modifier,
    updateFailed: Boolean = false,
    wideLayout: Boolean = false,
) {
    ScreenColumn(modifier = modifier, wideLayout = wideLayout) {
        if (state.isLoading) {
            LoadingStatus(stringResource(R.string.home_loading))
        } else {
            val settings = state.settings!!
            ReaderControl(settings.readerEnabled, onReaderChanged)
            RidingQuickControl(settings.ridingState, onRidingChanged)
            SpeechRateControl(settings.speechRate, onSpeechRateChanged)
            TestSpeechControl(state.canTestSpeech, state.testSpeechState, onTestSpeech)
        }
        if (updateFailed) {
            InlineStatus(
                title = stringResource(R.string.update_failed_heading),
                detail = stringResource(R.string.home_update_failed),
                warning = true,
            )
        }
    }
}

@Composable
private fun ReaderControl(readerEnabled: Boolean, onReaderChanged: (Boolean) -> Unit) {
    SectionCard(
        title = stringResource(R.string.home_reader_heading),
    ) {
        PreferenceSwitchRow(
            title = stringResource(if (readerEnabled) R.string.control_enabled else R.string.control_disabled),
            detail = stringResource(if (readerEnabled) R.string.home_reader_active else R.string.home_reader_inactive),
            checked = readerEnabled,
            onCheckedChange = onReaderChanged,
        )
    }
}

@Composable
private fun RidingQuickControl(ridingState: RidingState, onRidingChanged: (Boolean) -> Unit) {
    val active = ridingState == RidingState.ACTIVE
    SectionCard(
        title = stringResource(R.string.home_riding_heading),
    ) {
        PreferenceSwitchRow(
            title = stringResource(if (active) R.string.control_enabled else R.string.control_disabled),
            detail = stringResource(if (active) R.string.home_riding_active else R.string.home_riding_inactive),
            checked = active,
            onCheckedChange = onRidingChanged,
        )
    }
}

@Composable
private fun SpeechRateControl(speechRate: Float, onSpeechRateChanged: (Float) -> Unit) {
    SectionCard(
        title = stringResource(R.string.home_speech_rate_heading),
        detail = stringResource(R.string.home_speech_rate_detail, speechRate),
    ) {
        Slider(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            value = speechRate,
            onValueChange = onSpeechRateChanged,
            valueRange = AppSettings.MIN_SPEECH_RATE..AppSettings.MAX_SPEECH_RATE,
            steps = 14,
        )
    }
}

@Composable
private fun TestSpeechControl(
    canTestSpeech: Boolean,
    testSpeechState: TestSpeechState,
    onTestSpeech: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.home_test_speech_heading),
        detail = stringResource(testSpeechDescription(testSpeechState, canTestSpeech)),
    ) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            enabled = canTestSpeech && testSpeechState !is TestSpeechState.Pending &&
                testSpeechState !is TestSpeechState.Speaking,
            onClick = onTestSpeech,
        ) {
            Text(stringResource(R.string.home_test_speech_button))
        }
    }
}

internal fun testSpeechDescription(state: TestSpeechState, canTestSpeech: Boolean): Int = when (state) {
    TestSpeechState.NotRun -> if (canTestSpeech) {
        R.string.home_test_speech_not_run
    } else {
        R.string.home_test_speech_unavailable
    }
    TestSpeechState.Pending -> R.string.home_test_speech_pending
    TestSpeechState.Speaking -> R.string.home_test_speech_speaking
    TestSpeechState.Succeeded -> R.string.home_test_speech_succeeded
    TestSpeechState.Failed -> R.string.home_test_speech_failed
}
