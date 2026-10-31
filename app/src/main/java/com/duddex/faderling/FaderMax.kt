package com.duddex.faderling

/** Highest value a slide along a Fader's value row sets it to; dragging the Fader itself still reaches 127. */
object FaderMax {
    const val DEFAULT = 100
    val RANGE = 0..127

    fun clamp(value: Int): Int = value.coerceIn(RANGE)
}
