package com.duddex.faderling

import java.util.Random
import kotlin.math.roundToLong

/** The order in which the Arpeggiator plays the held Notes. */
enum class ArpPattern {
    UP,
    DOWN,
    UP_DOWN,
    RANDOM,
    AS_PLAYED,
}

/** Length of one Arpeggiator step as a fraction of a quarter note. */
enum class ArpRate(val label: String, val quarterNotes: Double) {
    QUARTER("1/4", 1.0),
    EIGHTH("1/8", 0.5),
    EIGHTH_TRIPLET("1/8T", 1.0 / 3),
    SIXTEENTH("1/16", 0.25),
    SIXTEENTH_TRIPLET("1/16T", 1.0 / 6),
    THIRTY_SECOND("1/32", 0.125),
}

/** Settings of the Arpeggiator; its Tempo is internal because Faderling never receives MIDI clock. */
data class ArpSettings(
    val pattern: ArpPattern = ArpPattern.UP,
    val bpm: Int = 120,
    val rate: ArpRate = ArpRate.SIXTEENTH,
    val octaves: Int = 1,
    val gatePercent: Int = 50,
) {
    companion object {
        val BPM_RANGE = 40..240
        val OCTAVE_RANGE = 1..4
        val GATE_RANGE = 10..90
    }
}

/** Pure Arpeggiator maths: step and Gate time, and the order of the Notes. */
object Arpeggiator {

    /** Time one step lasts at [settings]'s Tempo and Rate, in milliseconds. */
    fun stepMillis(settings: ArpSettings): Long =
        (60_000.0 / settings.bpm * settings.rate.quarterNotes).roundToLong().coerceAtLeast(1L)

    /** Time a Note sounds within one step, in milliseconds. */
    fun gateMillis(settings: ArpSettings): Long =
        (stepMillis(settings) * settings.gatePercent / 100.0).roundToLong().coerceAtLeast(1L)

    /**
     * The held Notes spread over [octaves], ascending and without duplicates. Notes above MIDI 127 are
     * skipped; the original Notes are always valid, so the result is only empty for empty input.
     */
    fun pool(notes: List<Int>, octaves: Int): List<Int> =
        expanded(notes, octaves).sorted()

    /** The Notes in the order the deterministic [pattern] plays them. */
    fun sequence(notes: List<Int>, pattern: ArpPattern, octaves: Int): List<Int> {
        val pool = pool(notes, octaves)
        return when (pattern) {
            ArpPattern.UP, ArpPattern.RANDOM -> pool
            ArpPattern.DOWN -> pool.reversed()
            ArpPattern.UP_DOWN -> if (pool.size < 3) pool else pool + pool.subList(1, pool.size - 1).reversed()
            ArpPattern.AS_PLAYED -> expanded(notes, octaves)
        }
    }

    /** A random Note from [pool] that is not [previous] when there is another choice. */
    fun randomNote(pool: List<Int>, previous: Int?, random: Random): Int {
        if (pool.size <= 1) return pool.first()
        var note: Int
        do {
            note = pool[random.nextInt(pool.size)]
        } while (note == previous)
        return note
    }

    private fun expanded(notes: List<Int>, octaves: Int): List<Int> =
        (0 until octaves.coerceIn(ArpSettings.OCTAVE_RANGE))
            .flatMap { octave -> notes.distinct().map { it + 12 * octave } }
            .filter { it in 0..127 }
            .distinct()
}
