package com.duddex.faderling

/** Name, CCs for X and Y, and MIDI channel of one XY Pad. */
data class XyAssignment(val name: String, val ccX: Int, val ccY: Int, val channel: Int)

object XyPads {
    const val COUNT = 2

    /** Same glide speed as the Faders: the full range 0–127 takes this long. */
    const val GLIDE_FULL_TRAVEL_MS = 250f

    /** Position shown before the XY Pad is touched for the first time; nothing is sent until then. */
    const val START_VALUE = 64f

    /** Defaults use the undefined CCs 85–88: XY 1 = 85/86, XY 2 = 87/88, channel 1. */
    fun default(index: Int): XyAssignment =
        XyAssignment(name = "XY ${index + 1}", ccX = 85 + 2 * index, ccY = 86 + 2 * index, channel = 1)

    /** Moves [position] towards [target] by at most [maxStep]; used per axis for the glide. */
    fun step(position: Float, target: Float, maxStep: Float): Float = when {
        target > position -> minOf(target, position + maxStep)
        target < position -> maxOf(target, position - maxStep)
        else -> position
    }
}
