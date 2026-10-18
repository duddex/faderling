package com.duddex.faderling

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity(), ControlListener {

    private class ControlRef(val kind: ControlKind, val index: Int)

    private lateinit var store: AssignmentStore
    private lateinit var endpoint: RtpMidiEndpoint
    private lateinit var sessionStatus: TextView
    private lateinit var tabFaders: TextView
    private lateinit var tabKnobsButtons: TextView
    private lateinit var fadersPage: View
    private lateinit var knobsButtonsPage: View
    private val handler = Handler(Looper.getMainLooper())

    private val faders = mutableListOf<FaderView>()
    private val knobs = mutableListOf<KnobView>()
    private val buttons = mutableListOf<ButtonView>()
    private val refs = HashMap<View, ControlRef>()
    private var assignments: Map<ControlKind, List<Assignment>> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        findViewById<LinearLayout>(R.id.root).padForSystemBars()

        store = AssignmentStore(this)
        endpoint = (application as FaderlingApp).endpoint
        sessionStatus = findViewById(R.id.sessionStatus)
        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        fadersPage = findViewById(R.id.faders)
        knobsButtonsPage = findViewById(R.id.knobsButtons)
        tabFaders = findViewById<TextView>(R.id.tabFaders).apply { setOnClickListener { showPage(0) } }
        tabKnobsButtons = findViewById<TextView>(R.id.tabKnobsButtons).apply { setOnClickListener { showPage(1) } }

        addControls(listOf(findViewById(R.id.faders)), ControlKind.FADER, faders) { FaderView(this).apply { listener = this@MainActivity } }
        addControls(listOf(findViewById(R.id.knobsRow1), findViewById(R.id.knobsRow2)), ControlKind.KNOB, knobs) { KnobView(this).apply { listener = this@MainActivity } }
        addControls(listOf(findViewById(R.id.buttons)), ControlKind.BUTTON, buttons) { ButtonView(this).apply { listener = this@MainActivity } }

        showPage(savedInstanceState?.getInt(STATE_PAGE) ?: 0)
        showSession(null)
    }

    /** Distributes the Controls of [kind] evenly over [rows], filling them left to right, top to bottom. */
    private fun <T : View> addControls(rows: List<LinearLayout>, kind: ControlKind, list: MutableList<T>, create: () -> T) {
        val perRow = (kind.count + rows.size - 1) / rows.size
        repeat(kind.count) { i ->
            val view = create()
            val height = if (rows[i / perRow].layoutParams.height == LinearLayout.LayoutParams.WRAP_CONTENT) {
                LinearLayout.LayoutParams.WRAP_CONTENT
            } else {
                LinearLayout.LayoutParams.MATCH_PARENT
            }
            rows[i / perRow].addView(view, LinearLayout.LayoutParams(0, height, 1f))
            list += view
            refs[view] = ControlRef(kind, i)
        }
    }

    private fun showPage(page: Int) {
        fadersPage.visibility = if (page == 0) View.VISIBLE else View.GONE
        knobsButtonsPage.visibility = if (page == 1) View.VISIBLE else View.GONE
        tabFaders.isSelected = page == 0
        tabKnobsButtons.isSelected = page == 1
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, if (knobsButtonsPage.visibility == View.VISIBLE) 1 else 0)
    }

    override fun onResume() {
        super.onResume()
        val previous = assignments
        assignments = ControlKind.entries.associateWith { store.loadAll(it) }

        faders.forEachIndexed { i, v -> v.name = assignment(ControlKind.FADER, i).name }
        knobs.forEachIndexed { i, v -> v.name = assignment(ControlKind.KNOB, i).name }
        buttons.forEachIndexed { i, v ->
            val a = assignment(ControlKind.BUTTON, i)
            val old = previous[ControlKind.BUTTON]?.getOrNull(i)
            if (old != null && old.mode != a.mode && v.reset()) {
                handler.removeCallbacksAndMessages(v)
                endpoint.sendControlChange(old.channel, old.cc, ButtonView.OFF)
            }
            v.name = a.name
            v.mode = a.mode
        }
    }

    override fun onStart() {
        super.onStart()
        endpoint.listener = RtpMidiEndpoint.SessionsListener { names -> showSession(names.takeIf { it.isNotEmpty() }?.joinToString(", ")) }
    }

    override fun onStop() {
        endpoint.listener = null
        super.onStop()
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

    override fun onControlChanged(control: View, value: Int) {
        handler.removeCallbacksAndMessages(control)
        send(control, value)
    }

    override fun onControlReleased(control: View, value: Int) {
        handler.postAtTime({ send(control, value) }, control, SystemClock.uptimeMillis() + RESEND_DELAY_MS)
    }

    private fun send(control: View, value: Int) {
        val ref = refs[control] ?: return
        val a = assignment(ref.kind, ref.index)
        endpoint.sendControlChange(a.channel, a.cc, value)
    }

    private fun assignment(kind: ControlKind, index: Int): Assignment =
        assignments[kind]?.getOrNull(index) ?: Assignment.default(kind, index)

    private companion object {
        const val RESEND_DELAY_MS = 150L
        const val STATE_PAGE = "page"
    }
}
