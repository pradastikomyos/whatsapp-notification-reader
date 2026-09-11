package com.ridenotify.spike.audiobackground

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Disposable spike-only logger (P0-T05). Writes strictly synthetic, redacted
 * trial metadata - never real WhatsApp message text or sender names - to
 * Logcat (tag [TAG]) and to a small file so results survive after the
 * activity is closed or the process is killed.
 *
 * This class must never be referenced by the production `app` module.
 */
object ExperimentLog {
    const val TAG = "SpikeAudioExperiment"
    private const val FILE_NAME = "experiment_log.txt"
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun append(context: Context, line: String) {
        val stamped = "${formatter.format(Date())} $line"
        Log.i(TAG, stamped)
        runCatching {
            File(context.filesDir, FILE_NAME).appendText(stamped + "\n")
        }
    }

    @Synchronized
    fun read(context: Context): String {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return "(no results yet)"
        return file.readText().ifBlank { "(no results yet)" }
    }

    @Synchronized
    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
