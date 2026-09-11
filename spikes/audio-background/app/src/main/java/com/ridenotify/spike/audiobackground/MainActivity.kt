package com.ridenotify.spike.audiobackground

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

/**
 * Disposable spike-only UI (P0-T05). Configuration surface for the audio/FGS
 * experiment only - not production UI and not part of ARCHITECTURE.md's UI
 * tree. Closing this activity must not be required for the listener/TTS
 * trial to run, matching the production activity-independence rule.
 */
class MainActivity : Activity() {

    private lateinit var logTextView: TextView
    private lateinit var modeRadioGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logTextView = findViewById(R.id.logTextView)
        modeRadioGroup = findViewById(R.id.modeRadioGroup)
        val noFgsRadioButton = findViewById<RadioButton>(R.id.noFgsRadioButton)
        val withFgsRadioButton = findViewById<RadioButton>(R.id.withFgsRadioButton)

        when (TrialMode.read(this)) {
            TrialMode.NO_FGS -> noFgsRadioButton.isChecked = true
            TrialMode.WITH_FGS -> withFgsRadioButton.isChecked = true
        }

        modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == withFgsRadioButton.id) {
                TrialMode.WITH_FGS
            } else {
                TrialMode.NO_FGS
            }
            TrialMode.write(this, mode)
            ExperimentLog.append(this, "mode-selected=$mode")
        }

        findViewById<Button>(R.id.openListenerSettingsButton).setOnClickListener {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        findViewById<Button>(R.id.refreshLogButton).setOnClickListener {
            refreshLog()
        }

        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            ExperimentLog.clear(this)
            refreshLog()
        }

        refreshLog()
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
    }

    private fun refreshLog() {
        logTextView.text = ExperimentLog.read(this)
    }
}
