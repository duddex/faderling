package com.duddex.faderling

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.Random

/**
 * Plays the held Notes one at a time while the Arpeggiator is on. Runs on the main thread; the Notes are
 * released by [stop], which MainActivity calls when the Keyboard Page or the App leaves the screen.
 */
class ArpEngine(
    private val noteOn: (channel: Int, note: Int, velocity: Int) -> Unit,
    private val noteOff: (channel: Int, note: Int) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val token = Any()
    private val random = Random()
    private var notes: List<Int> = emptyList()
    private var settings = ArpSettings()
    private var channel = 1
    private var velocity = 100
    private var sequence: List<Int> = emptyList()
    private var step = 0
    private var stepAt = 0L
    private var soundingNote: Int? = null
    private var previousRandom: Int? = null

    var isRunning = false
        private set

    /** Called whenever the Arp starts or stops, e.g. to update the Keyboard tab indicator. */
    var onRunningChanged: (() -> Unit)? = null

    fun start(notes: List<Int>, settings: ArpSettings, channel: Int, velocity: Int) {
        stop()
        this.notes = notes
        this.settings = settings
        this.channel = channel
        this.velocity = velocity
        if (notes.isEmpty()) return
        sequence = buildSequence()
        if (sequence.isEmpty()) return
        isRunning = true
        step = 0
        previousRandom = null
        stepAt = SystemClock.uptimeMillis()
        handler.postAtTime(tick, token, stepAt)
        onRunningChanged?.invoke()
    }

    /** The held Notes changed; a sounding Note finishes its Gate and the next step uses the new set. */
    fun updateNotes(notes: List<Int>) {
        this.notes = notes
        if (!isRunning) return
        if (notes.isEmpty()) {
            stop()
            return
        }
        sequence = buildSequence()
        if (sequence.isEmpty()) {
            stop()
            return
        }
        step %= sequence.size
    }

    fun updateSettings(settings: ArpSettings) {
        this.settings = settings
        if (!isRunning) return
        sequence = buildSequence()
        if (sequence.isEmpty()) {
            stop()
            return
        }
        step %= sequence.size
    }

    fun setChannelVelocity(channel: Int, velocity: Int) {
        this.channel = channel
        this.velocity = velocity
    }

    fun stop() {
        val wasRunning = isRunning
        isRunning = false
        handler.removeCallbacksAndMessages(token)
        endSoundingNote()
        if (wasRunning) onRunningChanged?.invoke()
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val now = SystemClock.uptimeMillis()
            handler.removeCallbacks(gateOff)
            endSoundingNote()
            val note = nextNote()
            if (note == null) {
                stop()
                return
            }
            noteOn(channel, note, velocity)
            soundingNote = note
            handler.postAtTime(gateOff, token, now + Arpeggiator.gateMillis(settings))
            val interval = Arpeggiator.stepMillis(settings)
            val next = stepAt + interval
            stepAt = if (next > now) next else now + interval
            handler.postAtTime(tick, token, stepAt)
        }
    }

    private val gateOff: Runnable = object : Runnable {
        override fun run() {
            endSoundingNote()
        }
    }

    private fun endSoundingNote() {
        val note = soundingNote ?: return
        soundingNote = null
        noteOff(channel, note)
    }

    private fun nextNote(): Int? {
        if (sequence.isEmpty()) return null
        if (settings.pattern == ArpPattern.RANDOM) {
            val note = Arpeggiator.randomNote(sequence, previousRandom, random)
            previousRandom = note
            return note
        }
        val note = sequence[step]
        step = (step + 1) % sequence.size
        return note
    }

    private fun buildSequence(): List<Int> = Arpeggiator.sequence(notes, settings.pattern, settings.octaves)
}
