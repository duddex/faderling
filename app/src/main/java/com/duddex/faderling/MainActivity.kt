package com.duddex.faderling

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity(), FaderView.Listener {

    private lateinit var store: AssignmentStore
    private lateinit var sessionStatus: TextView
    private val faders = mutableListOf<FaderView>()
    private var assignments: List<Assignment> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        findViewById<LinearLayout>(R.id.root).padForSystemBars()

        store = AssignmentStore(this)
        sessionStatus = findViewById(R.id.sessionStatus)
        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val container = findViewById<LinearLayout>(R.id.faders)
        repeat(Assignment.FADER_COUNT) {
            val fader = FaderView(this).apply { listener = this@MainActivity }
            container.addView(fader, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            faders += fader
        }
        showSession(null)
    }

    override fun onResume() {
        super.onResume()
        assignments = store.loadAll()
        faders.forEachIndexed { i, fader -> fader.name = assignments[i].name }
    }

    private fun showSession(peerName: String?) {
        if (peerName == null) {
            sessionStatus.text = getString(R.string.session_none)
            sessionStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.status_dot_off, 0, 0, 0)
        } else {
            sessionStatus.text = getString(R.string.session_connected, peerName)
            sessionStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.status_dot_on, 0, 0, 0)
        }
    }

    override fun onFaderMoved(fader: FaderView, value: Int) {
    }

    override fun onFaderReleased(fader: FaderView, value: Int) {
    }
}
