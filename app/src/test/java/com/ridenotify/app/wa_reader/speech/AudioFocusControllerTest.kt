package com.ridenotify.app.wa_reader.speech

import android.media.AudioAttributes
import android.media.AudioManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioFocusControllerTest {

    @Test
    fun `requests transient speech focus with the approved attributes`() {
        val platform = FakeAudioFocusPlatform()
        val controller = AndroidAudioFocusController(platform)

        assertEquals(AudioFocusRequestResult.GRANTED, controller.requestFocus { })

        assertEquals(1, platform.requestCount)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, platform.requestedConfiguration?.focusGain)
        assertEquals(
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
            platform.requestedConfiguration?.usage,
        )
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, platform.requestedConfiguration?.contentType)
    }

    @Test
    fun `denied focus leaves no active lease and does not abandon`() {
        val platform = FakeAudioFocusPlatform(requestResult = AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        val controller = AndroidAudioFocusController(platform)

        assertEquals(AudioFocusRequestResult.DENIED, controller.requestFocus { })
        controller.releaseFocusAfterTerminalUtterance()

        assertEquals(0, platform.abandonCount)
        platform.requestResult = AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        assertEquals(AudioFocusRequestResult.GRANTED, controller.requestFocus { })
        assertEquals(2, platform.requestCount)
    }

    @Test
    fun `request exception fails closed and a later utterance can retry`() {
        val platform = FakeAudioFocusPlatform(throwOnRequest = true)
        val controller = AndroidAudioFocusController(platform)

        assertEquals(AudioFocusRequestResult.DENIED, controller.requestFocus { })

        platform.throwOnRequest = false
        assertEquals(AudioFocusRequestResult.GRANTED, controller.requestFocus { })
        assertEquals(2, platform.requestCount)
    }

    @Test
    fun `loss delivered during request prevents the utterance from starting`() {
        val platform = FakeAudioFocusPlatform(changeBeforeRequestReturns = AudioManager.AUDIOFOCUS_LOSS)
        val controller = AndroidAudioFocusController(platform)

        val result = controller.requestFocus { change ->
            if (change == AudioFocusChange.LOST) {
                controller.releaseFocusAfterTerminalUtterance()
            }
        }

        assertEquals(AudioFocusRequestResult.DENIED, result)
        assertEquals(1, platform.abandonCount)
    }

    @Test
    fun `second request is rejected until the terminal utterance releases focus`() {
        val platform = FakeAudioFocusPlatform()
        val controller = AndroidAudioFocusController(platform)

        assertEquals(AudioFocusRequestResult.GRANTED, controller.requestFocus { })
        assertEquals(AudioFocusRequestResult.ALREADY_ACTIVE, controller.requestFocus { })
        assertEquals(1, platform.requestCount)

        controller.releaseFocusAfterTerminalUtterance()
        controller.releaseFocusAfterTerminalUtterance()

        assertEquals(1, platform.abandonCount)
        assertEquals(AudioFocusRequestResult.GRANTED, controller.requestFocus { })
    }

    @Test
    fun `loss is delivered without abandoning before the terminal utterance state`() {
        val platform = FakeAudioFocusPlatform()
        val controller = AndroidAudioFocusController(platform)
        val changes = mutableListOf<AudioFocusChange>()

        controller.requestFocus(changes::add)
        platform.dispatch(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        platform.dispatch(AudioManager.AUDIOFOCUS_LOSS)

        assertEquals(
            listOf(AudioFocusChange.LOST_TRANSIENTLY, AudioFocusChange.LOST),
            changes,
        )
        assertEquals(0, platform.abandonCount)

        controller.releaseFocusAfterTerminalUtterance()
        assertEquals(1, platform.abandonCount)
    }

    @Test
    fun `duck requests are treated as transient loss and gain is surfaced`() {
        val platform = FakeAudioFocusPlatform()
        val controller = AndroidAudioFocusController(platform)
        val changes = mutableListOf<AudioFocusChange>()

        controller.requestFocus(changes::add)
        platform.dispatch(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        platform.dispatch(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(
            listOf(AudioFocusChange.LOST_TRANSIENTLY, AudioFocusChange.GAINED),
            changes,
        )
    }

    @Test
    fun `stale platform callbacks are ignored after terminal release`() {
        val platform = FakeAudioFocusPlatform()
        val controller = AndroidAudioFocusController(platform)
        val firstChanges = mutableListOf<AudioFocusChange>()
        val secondChanges = mutableListOf<AudioFocusChange>()

        controller.requestFocus(firstChanges::add)
        val firstCallback = platform.currentCallback()
        controller.releaseFocusAfterTerminalUtterance()
        controller.requestFocus(secondChanges::add)

        firstCallback(AudioManager.AUDIOFOCUS_LOSS)

        assertTrue(firstChanges.isEmpty())
        assertTrue(secondChanges.isEmpty())
    }

    private class FakeAudioFocusPlatform(
        var requestResult: Int = AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
        var throwOnRequest: Boolean = false,
        var changeBeforeRequestReturns: Int? = null,
    ) : AudioFocusPlatform {
        var requestCount = 0
        var abandonCount = 0
        var requestedConfiguration: SpeechAudioFocusConfiguration? = null
        private val callbacks = mutableListOf<(Int) -> Unit>()

        override fun requestTransientSpeechFocus(
            configuration: SpeechAudioFocusConfiguration,
            onFocusChange: (Int) -> Unit,
        ): Int {
            requestCount++
            requestedConfiguration = configuration
            if (throwOnRequest) error("AudioManager unavailable")
            callbacks += onFocusChange
            changeBeforeRequestReturns?.let(onFocusChange)
            return requestResult
        }

        override fun abandonFocus() {
            abandonCount++
        }

        fun dispatch(change: Int) {
            callbacks.last()(change)
        }

        fun currentCallback(): (Int) -> Unit = callbacks.last()
    }
}
