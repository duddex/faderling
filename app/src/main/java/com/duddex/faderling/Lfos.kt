package com.duddex.faderling

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Name, CC number and MIDI channel an LFO sends to. */
data class LfoAssignment(val name: String, val cc: Int, val channel: Int)

/** Range and Speed of an LFO, set directly on the LFOs Page. Min may be greater than Max (inverted sweep). */
data class LfoRange(val min: Int = 0, val max: Int = 127, val speedSeconds: Float = Lfos.DEFAULT_SPEED)

object Lfos {
    const val COUNT = 8
    const val MIN_SPEED = 0.2f
    const val MAX_SPEED = 120f
    const val DEFAULT_SPEED = 4f

    /** At most 50 CC messages per second per LFO. */
    const val MIN_SEND_INTERVAL_MS = 20L

    /** Defaults target the Knobs' CCs (102–109) on channel 1. */
    fun default(index: Int): LfoAssignment =
        LfoAssignment(name = "LFO ${index + 1}", cc = ControlKind.KNOB.defaultFirstCc + index, channel = 1)

    /** CC value at [phase] (0 = start, 1 = one full cycle): a sine starting at Min, reaching Max halfway. */
    fun value(range: LfoRange, phase: Double): Int {
        val wave = (1 - cos(2 * PI * phase)) / 2
        return (range.min + (range.max - range.min) * wave).roundToInt().coerceIn(0, 127)
    }

    /** Speed for a drag position 0..1; logarithmic so fast LFOs get fine steps and slow ones coarse steps. */
    fun speedAt(fraction: Float): Float =
        MIN_SPEED * (MAX_SPEED / MIN_SPEED).pow(fraction.coerceIn(0f, 1f))

    /** Inverse of [speedAt]. */
    fun fractionOf(speed: Float): Float =
        (ln(speed.coerceIn(MIN_SPEED, MAX_SPEED) / MIN_SPEED) / ln(MAX_SPEED / MIN_SPEED))

    /** Speed as shown on the Page, e.g. "0.25 s", "4.0 s", "75 s". */
    fun speedLabel(speed: Float): String = when {
        speed < 1f -> "%.2f s".format(java.util.Locale.ROOT, speed)
        speed < 10f -> "%.1f s".format(java.util.Locale.ROOT, speed)
        else -> "${speed.roundToInt()} s"
    }
}
