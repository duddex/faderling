package com.duddex.faderling

/** Name and note of one Pad. */
data class PadAssignment(val name: String, val note: Int)

/** Settings shared by all Pads: MIDI channel (1–16) and the fixed velocity of every Note On (1–127). */
data class PadSettings(val channel: Int = 10, val velocity: Int = 100)

object Pads {
    const val COUNT = 16
    const val COLUMNS = 4
    const val ROWS = 4
    const val DEFAULT_FIRST_NOTE = 36

    /** Default like an MPC or Ableton Drum Rack: notes 36–51, named after the note (C1, C#1, …). */
    fun default(index: Int): PadAssignment {
        val note = DEFAULT_FIRST_NOTE + index
        return PadAssignment(name = Keyboard.noteName(note), note = note)
    }

    /** Pad index at a grid position; row 0 is the top row, Pad 0 is bottom left, counting left to right, bottom to top. */
    fun indexAt(row: Int, column: Int): Int = (ROWS - 1 - row) * COLUMNS + column

    /** Widest a stretched Pad may become relative to its height. */
    const val MAX_STRETCH = 1.6f

    /**
     * Width and height of one Pad cell in a [width] × [height] area. Normally square; with [stretch] the cells
     * use the extra width, up to [MAX_STRETCH] times their height.
     */
    fun cellSize(width: Float, height: Float, stretch: Boolean): Pair<Float, Float> {
        val cellHeight = minOf(height / ROWS, width / COLUMNS)
        val cellWidth = if (stretch) minOf(width / COLUMNS, cellHeight * MAX_STRETCH) else cellHeight
        return cellWidth to cellHeight
    }
}
