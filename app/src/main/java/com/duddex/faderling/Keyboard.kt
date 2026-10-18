package com.duddex.faderling

/** Settings of the Keyboard: MIDI channel (1–16) and the fixed velocity of every Note On (1–127). */
data class KeyboardSettings(val channel: Int = 1, val velocity: Int = 100) {
    companion object {
        val VELOCITY_RANGE = 1..127
    }
}

object Keyboard {
    const val KEY_COUNT = 25
    const val DEFAULT_LOWEST_NOTE = 48
    const val MIN_LOWEST_NOTE = 0

    /** Highest lowest note that keeps all 25 Keys within MIDI 0–127, on a C. */
    const val MAX_LOWEST_NOTE = 96

    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** Whether the Key at [index] (0 = lowest C) is a black key. */
    fun isBlack(index: Int): Boolean = (index % 12) in intArrayOf(1, 3, 6, 8, 10)

    /** Note name in Ableton's convention, where note 60 (middle C) is "C3". */
    fun noteName(note: Int): String = NAMES[note % 12] + (note / 12 - 2)

    fun shift(lowestNote: Int, octaves: Int): Int = (lowestNote + 12 * octaves).coerceIn(MIN_LOWEST_NOTE, MAX_LOWEST_NOTE)
}
