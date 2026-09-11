package com.ridenotify.spike.audiobackground

import android.content.Context

/** Disposable spike enum (P0-T05). Not used by the production app. */
enum class TrialMode {
    NO_FGS,
    WITH_FGS;

    companion object {
        private const val PREFS = "spike_prefs"
        private const val KEY_MODE = "mode"

        fun read(context: Context): TrialMode {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_MODE, NO_FGS.name) ?: NO_FGS.name
            return runCatching { valueOf(name) }.getOrDefault(NO_FGS)
        }

        fun write(context: Context, mode: TrialMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, mode.name)
                .apply()
        }
    }
}
