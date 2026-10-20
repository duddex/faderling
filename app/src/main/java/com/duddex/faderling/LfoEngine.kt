package com.duddex.faderling

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Runs the 8 LFOs while the app is visible, independent of the Page shown. Each running LFO advances its phase
 * every tick and sends its CC when the value changed, at most every [Lfos.MIN_SEND_INTERVAL_MS].
 */
class LfoEngine(private val store: AssignmentStore, private val endpoint: RtpMidiEndpoint) {

    private class State {
        var running = false
        var phase = 0.0
        var value: Int? = null
        var lastSentValue: Int? = null
        var lastSentAt = 0L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val states = List(Lfos.COUNT) { State() }
    private var lastTick = 0L
    private var active = false

    var assignments: List<LfoAssignment> = store.loadLfoAssignments()
        private set
    private val ranges: MutableList<LfoRange> = store.loadLfoRanges().toMutableList()

    fun range(index: Int): LfoRange = ranges[index]
    fun isRunning(index: Int): Boolean = states[index].running

    /** Current CC value, or null if the LFO has not run yet. */
    fun value(index: Int): Int? = states[index].value

    /** Re-reads names, CCs and channels after the settings were edited. */
    fun reloadAssignments() {
        assignments = store.loadLfoAssignments()
    }

    fun setRange(index: Int, range: LfoRange, save: Boolean) {
        ranges[index] = range
        if (save) store.saveLfoRange(index, range)
    }

    fun setRunning(index: Int, running: Boolean) {
        val s = states[index]
        if (s.running == running) return
        s.running = running
        if (running) {
            s.phase = 0.0
            s.lastSentValue = null
            s.lastSentAt = 0L
            update(index, SystemClock.uptimeMillis())
        }
    }

    /** Starts ticking; called when the app becomes visible. LFOs themselves stay off until switched on. */
    fun start() {
        if (active) return
        active = true
        lastTick = SystemClock.uptimeMillis()
        handler.post(tick)
    }

    /** Stops ticking and switches every LFO off; called when the app leaves the screen. */
    fun stop() {
        active = false
        handler.removeCallbacks(tick)
        states.forEach { it.running = false }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTick) / 1000.0
            lastTick = now
            states.forEachIndexed { i, s ->
                if (s.running) {
                    s.phase = (s.phase + dt / ranges[i].speedSeconds) % 1.0
                    update(i, now)
                }
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun update(index: Int, now: Long) {
        val s = states[index]
        val v = Lfos.value(ranges[index], s.phase)
        s.value = v
        if (v != s.lastSentValue && now - s.lastSentAt >= Lfos.MIN_SEND_INTERVAL_MS) {
            val a = assignments[index]
            endpoint.sendControlChange(a.channel, a.cc, v)
            s.lastSentValue = v
            s.lastSentAt = now
        }
    }

    private companion object {
        const val TICK_MS = 10L
    }
}
