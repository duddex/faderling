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

class MainActivity : Activity(), ControlListener, KeyboardView.Listener, PadGridView.Listener, XyPadView.Listener {

    private class ControlRef(val kind: ControlKind, val index: Int)

    private lateinit var store: AssignmentStore
    private lateinit var endpoint: RtpMidiEndpoint
    private lateinit var sessionStatus: TextView
    private lateinit var tabs: List<TextView>
    private lateinit var pages: List<View>
    private var currentPage = 0
    private val handler = Handler(Looper.getMainLooper())

    private val faders = mutableListOf<FaderView>()
    private val buttons = mutableListOf<ButtonView>()
    private val refs = HashMap<View, ControlRef>()
    private var assignments: Map<ControlKind, List<Assignment>> = emptyMap()

    private lateinit var keyboard: KeyboardView
    private lateinit var keyboardRange: TextView
    private lateinit var octaveDown: TextView
    private lateinit var octaveUp: TextView
    private var keyboardSettings = KeyboardSettings()
    private var lowestNote = Keyboard.DEFAULT_LOWEST_NOTE

    /** Low screen (phone in landscape, split screen): one-octave Keyboard. */
    private val compact by lazy { ScreenLayout.isCompact(resources.configuration.screenHeightDp) }

    /** Key index → note and channel it was started with, so its Note Off matches even after an octave shift. */
    private val soundingNotes = HashMap<Int, Pair<Int, Int>>()

    private lateinit var padGrid: PadGridView
    private val lfoStrips = mutableListOf<LfoStripView>()
    private val xyPadViews = mutableListOf<XyPadView>()
    private var xyAssignments: List<XyAssignment> = (0 until XyPads.COUNT).map(XyPads::default)
    private var padAssignments: List<PadAssignment> = (0 until Pads.COUNT).map(Pads::default)
    private var padSettings = PadSettings()

    /** Pad index → note and channel it was started with, so its Note Off matches even if settings change. */
    private val soundingPads = HashMap<Int, Pair<Int, Int>>()

    /** channel * 128 + note → pending repeat of its Note Off. */
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
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, currentPage))
        }

        pages = listOf(findViewById(R.id.faders), findViewById(R.id.buttonsPage), findViewById(R.id.keyboardPage), findViewById(R.id.padsPage), findViewById(R.id.lfosPage), findViewById(R.id.xyPage))
        tabs = listOf(R.id.tabFaders, R.id.tabButtons, R.id.tabKeyboard, R.id.tabPads, R.id.tabLfos, R.id.tabXy).mapIndexed { page, id ->
            findViewById<TextView>(id).apply { setOnClickListener { showPage(page) } }
        }

        addControls(listOf(findViewById(R.id.faders)), ControlKind.FADER, faders) { FaderView(this).apply { listener = this@MainActivity } }
        addControls(listOf(findViewById(R.id.buttonsRow1), findViewById(R.id.buttonsRow2)), ControlKind.BUTTON, buttons) { ButtonView(this).apply { listener = this@MainActivity } }

        keyboard = KeyboardView(this, if (compact) Keyboard.COMPACT_KEY_COUNT else Keyboard.KEY_COUNT).apply { listener = this@MainActivity }
        lowestNote = if (compact) Keyboard.COMPACT_DEFAULT_LOWEST_NOTE else Keyboard.DEFAULT_LOWEST_NOTE
        findViewById<FrameLayout>(R.id.keyboardContainer).addView(keyboard)
        keyboardRange = findViewById(R.id.keyboardRange)
        octaveDown = findViewById<TextView>(R.id.octaveDown).apply { setOnClickListener { shiftOctave(-1) } }
        octaveUp = findViewById<TextView>(R.id.octaveUp).apply { setOnClickListener { shiftOctave(1) } }
        showKeyboardRange()

        padGrid = PadGridView(this).apply { listener = this@MainActivity }
        findViewById<FrameLayout>(R.id.padsPage).addView(padGrid)

        val lfoEngine = (application as FaderlingApp).lfos
        val lfosPage = findViewById<LinearLayout>(R.id.lfosPage)
        repeat(Lfos.COUNT) { i ->
            val strip = LfoStripView(this, i, lfoEngine)
            lfosPage.addView(strip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            lfoStrips += strip
        }

        val xyPage = findViewById<LinearLayout>(R.id.xyPage)
        repeat(XyPads.COUNT) {
            val pad = XyPadView(this).apply { listener = this@MainActivity }
            xyPage.addView(pad, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            xyPadViews += pad
        }

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
        if (page != currentPage) {
            keyboard.releaseAll()
            padGrid.releaseAll()
        }
        currentPage = page
        pages.forEachIndexed { i, v -> v.visibility = if (i == page) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { i, t -> t.isSelected = i == page }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, currentPage)
    }

    private fun shiftOctave(octaves: Int) {
        lowestNote = Keyboard.shift(lowestNote, octaves, keyboard.keyCount)
        showKeyboardRange()
    }

    private fun showKeyboardRange() {
        keyboardRange.text = getString(
            R.string.keyboard_range,
            Keyboard.noteName(lowestNote),
            Keyboard.noteName(lowestNote + keyboard.keyCount - 1),
        )
        octaveDown.isEnabled = lowestNote > Keyboard.MIN_LOWEST_NOTE
        octaveUp.isEnabled = lowestNote < Keyboard.maxLowestNote(keyboard.keyCount)
        octaveDown.alpha = if (octaveDown.isEnabled) 1f else 0.4f
        octaveUp.alpha = if (octaveUp.isEnabled) 1f else 0.4f
    }

    override fun onKeyPressed(index: Int) {
        val note = lowestNote + index
        val channel = keyboardSettings.channel
        soundingNotes[index] = note to channel
        noteOn(channel, note, keyboardSettings.velocity)
    }

    override fun onKeyReleased(index: Int) {
        val (note, channel) = soundingNotes.remove(index) ?: return
        noteOff(channel, note)
    }

    override fun onPadPressed(index: Int) {
        val (note, channel) = soundingPads.getOrPut(index) { padAssignments[index].note to padSettings.channel }
        noteOn(channel, note, padSettings.velocity)
    }

    override fun onPadReleased(index: Int) {
        val (note, channel) = soundingPads.remove(index) ?: return
        noteOff(channel, note)
    }

    private fun noteOn(channel: Int, note: Int, velocity: Int) {
        pendingNoteOffs.remove(channel * 128 + note)?.let(handler::removeCallbacks)
        endpoint.sendNoteOn(channel, note, velocity)
    }

    /** Sends Note Off now and once more shortly after, unless the same note is played again in between. */
    private fun noteOff(channel: Int, note: Int) {
        endpoint.sendNoteOff(channel, note)
        val key = channel * 128 + note
        val repeat = Runnable {
            pendingNoteOffs.remove(key)
            endpoint.sendNoteOff(channel, note)
        }
        pendingNoteOffs.put(key, repeat)?.let(handler::removeCallbacks)
        handler.postDelayed(repeat, RESEND_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        val previous = assignments
        assignments = ControlKind.entries.associateWith { store.loadAll(it) }

        faders.forEachIndexed { i, v -> v.name = assignment(ControlKind.FADER, i).name }
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
        padAssignments = store.loadPads()
        padSettings = store.loadPadSettings()
        padGrid.names = padAssignments.map { it.name }
        (application as FaderlingApp).lfos.reloadAssignments()
        lfoStrips.forEach { it.invalidate() }
        xyAssignments = store.loadXyAssignments()
        xyPadViews.forEachIndexed { i, v -> v.name = xyAssignments[i].name }
    }

    override fun onXyChanged(view: XyPadView, x: Int?, y: Int?) {
        handler.removeCallbacksAndMessages(view)
        val a = xyAssignments[xyPadViews.indexOf(view)]
        x?.let { endpoint.sendControlChange(a.channel, a.ccX, it) }
        y?.let { endpoint.sendControlChange(a.channel, a.ccY, it) }
    }

    override fun onXyReleased(view: XyPadView, x: Int, y: Int) {
        handler.postAtTime({
            val a = xyAssignments[xyPadViews.indexOf(view)]
            endpoint.sendControlChange(a.channel, a.ccX, x)
            endpoint.sendControlChange(a.channel, a.ccY, y)
        }, view, SystemClock.uptimeMillis() + RESEND_DELAY_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.hideSystemBars()
    }

    override fun onPause() {
        keyboard.releaseAll()
        padGrid.releaseAll()
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
