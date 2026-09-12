package com.ridenotify.app.wa_reader.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Owns the platform audio-focus lease for the single utterance currently being
 * played by [SpeechCoordinator]. Call [releaseFocusAfterTerminalUtterance]
 * only after that utterance has reached a terminal state (done, error, timeout,
 * or explicit stop).
 */
interface AudioFocusController {
    /** Requests transient, speech-appropriate focus for one utterance. */
    fun requestFocus(listener: AudioFocusChangeListener): AudioFocusRequestResult

    /**
     * Releases a previously granted lease after the owning utterance is terminal.
     * This is idempotent so competing terminal callbacks cannot abandon twice.
     */
    fun releaseFocusAfterTerminalUtterance()
}

/** Result of a request made for the current utterance. */
enum class AudioFocusRequestResult {
    GRANTED,
    DENIED,
    ALREADY_ACTIVE,
}

/**
 * Platform focus changes expressed without exposing Android framework constants
 * to the coordinator. Speech treats a duck request as a transient loss: it must
 * stop instead of changing global or per-stream volume.
 */
enum class AudioFocusChange {
    GAINED,
    LOST,
    LOST_TRANSIENTLY,
}

fun interface AudioFocusChangeListener {
    fun onAudioFocusChange(change: AudioFocusChange)
}

/** Thin platform seam used by [AndroidAudioFocusController] tests. */
internal interface AudioFocusPlatform {
    fun requestTransientSpeechFocus(
        configuration: SpeechAudioFocusConfiguration,
        onFocusChange: (Int) -> Unit,
    ): Int
    fun abandonFocus()
}

/** Android-specific request values selected by ADR-006 for spoken navigation-like guidance. */
internal data class SpeechAudioFocusConfiguration(
    val focusGain: Int = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
    val usage: Int = AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
    val contentType: Int = AudioAttributes.CONTENT_TYPE_SPEECH,
)

/**
 * Android implementation for API 26+. It owns at most one focus lease and
 * deliberately keeps that lease through a loss callback; the coordinator first
 * stops the active utterance, then calls [releaseFocusAfterTerminalUtterance].
 */
class AndroidAudioFocusController internal constructor(
    private val platform: AudioFocusPlatform,
) : AudioFocusController {
    private val lock = Any()
    private var nextGeneration = 0L
    private var activeFocus: ActiveFocus? = null

    override fun requestFocus(listener: AudioFocusChangeListener): AudioFocusRequestResult {
        return synchronized(lock) {
            if (activeFocus != null) return@synchronized AudioFocusRequestResult.ALREADY_ACTIVE
            val focus = ActiveFocus(++nextGeneration, listener).also { activeFocus = it }

            val platformResult = try {
                platform.requestTransientSpeechFocus(SpeechAudioFocusConfiguration()) { platformChange ->
                    dispatchPlatformFocusChange(focus.generation, platformChange)
                }
            } catch (_: RuntimeException) {
                AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }

            val result = if (
                platformResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED && activeFocus == focus
            ) {
                AudioFocusRequestResult.GRANTED
            } else {
                AudioFocusRequestResult.DENIED
            }
            if (result != AudioFocusRequestResult.GRANTED && activeFocus == focus) {
                activeFocus = null
            }
            result
        }
    }

    override fun releaseFocusAfterTerminalUtterance() {
        synchronized(lock) {
            if (activeFocus == null) return@synchronized
            activeFocus = null

            // A terminal state must never crash the coordinator or leak into a retry loop.
            try {
                platform.abandonFocus()
            } catch (_: RuntimeException) {
                // The platform has already been detached from this terminal utterance.
            }
        }
    }

    private fun dispatchPlatformFocusChange(generation: Long, platformChange: Int) {
        val change = when (platformChange) {
            AudioManager.AUDIOFOCUS_GAIN -> AudioFocusChange.GAINED
            AudioManager.AUDIOFOCUS_LOSS -> AudioFocusChange.LOST
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> AudioFocusChange.LOST_TRANSIENTLY
            else -> return
        }

        // Serialize callback dispatch with terminal release so stale callbacks from
        // a just-abandoned request cannot affect the next queued utterance.
        synchronized(lock) {
            activeFocus
                ?.takeIf { it.generation == generation }
                ?.listener
                ?.onAudioFocusChange(change)
        }
    }

    private data class ActiveFocus(
        val generation: Long,
        val listener: AudioFocusChangeListener,
    )

    companion object {
        fun forContext(context: Context): AndroidAudioFocusController {
            val audioManager = requireNotNull(
                context.applicationContext.getSystemService(AudioManager::class.java),
            ) { "AudioManager is unavailable" }
            return AndroidAudioFocusController(AndroidAudioFocusPlatform(audioManager))
        }
    }
}

private class AndroidAudioFocusPlatform(
    private val audioManager: AudioManager,
) : AudioFocusPlatform {
    private var activeRequest: AudioFocusRequest? = null

    override fun requestTransientSpeechFocus(
        configuration: SpeechAudioFocusConfiguration,
        onFocusChange: (Int) -> Unit,
    ): Int {
        val request = AudioFocusRequest.Builder(configuration.focusGain)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(configuration.usage)
                    .setContentType(configuration.contentType)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change -> onFocusChange(change) }
            .build()
        activeRequest = request

        return try {
            audioManager.requestAudioFocus(request).also { requestResult ->
                if (requestResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    activeRequest = null
                }
            }
        } catch (error: RuntimeException) {
            activeRequest = null
            throw error
        }
    }

    override fun abandonFocus() {
        val request = activeRequest ?: return
        try {
            audioManager.abandonAudioFocusRequest(request)
        } finally {
            activeRequest = null
        }
    }
}
