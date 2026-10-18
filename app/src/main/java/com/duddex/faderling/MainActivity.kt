package com.duddex.faderling

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity(), ControlListener, KeyboardView.Listener {

    private class ControlRef(val kind: ControlKind, val index: Int)

    private lateinit var store: AssignmentStore
    private lateinit var endpoint: RtpMidiEndpoint
    private lateinit var sessionStatus: TextView
    private lateinit var tabs: List<TextView>
    private lateinit var pages: List<View>
    private var currentPage = 0
    private val handler = Handler(Looper.getMainLooper())

    private val faders = mutableListOf<FaderView>()
    private val knobs = mutableListOf<KnobView>()
    private val buttons = mutableListOf<ButtonView>()
    private val refs = HashMap<View, ControlRef>()
    private var assignments: Map<ControlKind, List<Assignment>> = emptyMap()

    private lateinit var keyboard: KeyboardView
    private lateinit var keyboardRange: TextView
    private lateinit var octaveDown: TextView
    private lateinit var octaveUp: TextView
    private var keyboardSettings = KeyboardSettings()
    private var lowestNote = Keyboard.DEFAULT_LOWEST_NOTE

    /** Key index → note and channel it was started with, so its Note Off matches even after an octave shift. */
    private val soundingNotes = HashMap<Int, Pair<Int, Int>>()

    /** Note → pending repeat of its Note Off. */
    private val pendingNoteOffs = HashMap<Int, Runnable>()

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

        pages = listOf(findViewById(R.id.faders), findViewById(R.id.knobsButtons), findViewById(R.id.keyboardPage))
        tabs = listOf(R.id.tabFaders, R.id.tabKnobsButtons, R.id.tabKeyboard).mapIndexed { page, id ->
            findViewById<TextView>(id).apply { setOnClickListener { showPage(page) } }
        }

        addControls(listOf(findViewById(R.id.faders)), ControlKind.FADER, faders) { FaderView(this).apply { listener = this@MainActivity } }
        addControls(listOf(findViewById(R.id.knobsRow1), findViewById(R.id.knobsRow2)), ControlKind.KNOB, knobs) { KnobView(this).apply { listener = this@MainActivity } }
        addControls(listOf(findViewById(R.id.buttons)), ControlKind.BUTTON, buttons) { ButtonView(this).apply { listener = this@MainActivity } }

        keyboard = KeyboardView(this).apply { listener = this@MainActivity }
        findViewById<FrameLayout>(R.id.keyboardContainer).addView(keyboard)
        keyboardRange = findViewById(R.id.keyboardRange)
        octaveDown = findViewById<TextView>(R.id.octaveDown).apply { setOnClickListener { shiftOctave(-1) } }
        octaveUp = findViewById<TextView>(R.id.octaveUp).apply { setOnClickListener { shiftOctave(1) } }
        showKeyboardRange()

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
        if (page != currentPage) keyboard.releaseAll()
        currentPage = page
        pages.forEachIndexed { i, v -> v.visibility = if (i == page) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { i, t -> t.isSelected = i == page }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, currentPage)
    }

    private fun shiftOctave(octaves: Int) {
        lowestNote = Keyboard.shift(lowestNote, octaves)
        showKeyboardRange()
    }

    private fun showKeyboardRange() {
        keyboardRange.text = getString(
            R.string.keyboard_range,
            Keyboard.noteName(lowestNote),
            Keyboard.noteName(lowestNote + Keyboard.KEY_COUNT - 1),
        )
        octaveDown.isEnabled = lowestNote > Keyboard.MIN_LOWEST_NOTE
        octaveUp.isEnabled = lowestNote < Keyboard.MAX_LOWEST_NOTE
        octaveDown.alpha = if (octaveDown.isEnabled) 1f else 0.4f
        octaveUp.alpha = if (octaveUp.isEnabled) 1f else 0.4f
    }

    override fun onKeyPressed(index: Int) {
        val note = lowestNote + index
        val channel = keyboardSettings.channel
        pendingNoteOffs.remove(note)?.let(handler::removeCallbacks)
        soundingNotes[index] = note to channel
        endpoint.sendNoteOn(channel, note, keyboardSettings.velocity)
    }

    override fun onKeyReleased(index: Int) {
        val (note, channel) = soundingNotes.remove(index) ?: return
        endpoint.sendNoteOff(channel, note)
        val repeat = Runnable {
            pendingNoteOffs.remove(note)
            endpoint.sendNoteOff(channel, note)
        }
        pendingNoteOffs[note] = repeat
        handler.postDelayed(repeat, RESEND_DELAY_MS)
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
        keyboardSettings = store.loadKeyboard()
    }

    override fun onPause() {
        keyboard.releaseAll()
        super.onPause()
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
