package com.ridenotify.spike.audiobackground

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Disposable spike-only experiment (P0-T05). Prototypes listener-triggered
 * TTS + AudioFocusRequest with the activity absent. Speaks a fixed synthetic
 * phrase only - never real notification content - since this spike validates
 * platform mechanics, not the production parser/policy pipeline.
 *
 * Must never be referenced by the production `app` module.
 */
class AudioFocusTtsExperiment(private val context: Context) {

    private val synthenticPhrase = "Pesan WhatsApp baru dari kontak uji."

    fun run(trigger: String, mode: TrialMode, onComplete: () -> Unit) {
        val startedAtMs = System.currentTimeMillis()
        ExperimentLog.append(context, "trial-start trigger=$trigger mode=$mode")

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        var focusGranted = false
        var focusRequest: AudioFocusRequest? = null

        val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
            ExperimentLog.append(context, "audio-focus-change=$change mode=$mode")
        }

        runCatching {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            val result = audioManager.requestAudioFocus(request)
            focusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            ExperimentLog.append(
                context,
                "audio-focus-request result=$result granted=$focusGranted mode=$mode"
            )
        }.onFailure { error ->
            ExperimentLog.append(context, "audio-focus-request FAILED error=${error.javaClass.simpleName} mode=$mode")
        }

        if (!focusGranted) {
            ExperimentLog.append(context, "trial-skip-speech reason=no-audio-focus mode=$mode")
            ExperimentLog.append(context, "trial-end durationMs=${System.currentTimeMillis() - startedAtMs} mode=$mode")
            onComplete()
            return
        }

        var tts: TextToSpeech? = null
        val utteranceId = UUID.randomUUID().toString()

        fun releaseFocusAndFinish(outcome: String) {
            focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
            runCatching { tts?.shutdown() }
            ExperimentLog.append(context, "utterance-outcome=$outcome mode=$mode")
            ExperimentLog.append(
                context,
                "trial-end durationMs=${System.currentTimeMillis() - startedAtMs} mode=$mode"
            )
            onComplete()
        }

        tts = TextToSpeech(context) { initStatus ->
            if (initStatus != TextToSpeech.SUCCESS) {
                ExperimentLog.append(context, "tts-init-failed status=$initStatus mode=$mode")
                releaseFocusAndFinish("tts-init-failed")
                return@TextToSpeech
            }

            val currentTts = tts ?: return@TextToSpeech
            val locale = Locale("id", "ID")
            val availability = currentTts.isLanguageAvailable(locale)
            ExperimentLog.append(context, "tts-language-availability=$availability mode=$mode")

            if (availability < TextToSpeech.LANG_AVAILABLE) {
                ExperimentLog.append(context, "trial-skip-speech reason=id-locale-unavailable mode=$mode")
                releaseFocusAndFinish("locale-unavailable")
                return@TextToSpeech
            }

            currentTts.language = locale
            currentTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    ExperimentLog.append(context, "utterance-start id=$utteranceId mode=$mode")
                }

                override fun onDone(utteranceId: String?) {
                    releaseFocusAndFinish("done")
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    releaseFocusAndFinish("error-legacy")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    ExperimentLog.append(context, "utterance-error code=$errorCode mode=$mode")
                    releaseFocusAndFinish("error-$errorCode")
                }
            })

            val speakResult = currentTts.speak(synthenticPhrase, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            ExperimentLog.append(context, "tts-speak-call-result=$speakResult mode=$mode")
        }
    }

    companion object {
        /** Kept for documentation: min SDK for AudioFocusRequest is API 26. */
        val MIN_SUPPORTED_SDK = Build.VERSION_CODES.O
    }
}
