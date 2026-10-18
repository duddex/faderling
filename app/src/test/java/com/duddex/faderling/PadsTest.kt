package com.duddex.faderling

import org.junit.Assert.assertEquals
import org.junit.Test

class PadsTest {

    @Test
    fun `16 Pads default to notes 36 to 51 named after the note`() {
        val defaults = (0 until Pads.COUNT).map(Pads::default)
        assertEquals((36..51).toList(), defaults.map { it.note })
        assertEquals("C1", defaults.first().name)
        assertEquals("D#2", defaults.last().name)
    }

    @Test
    fun `Pad 1 is bottom left and Pads count left to right, bottom to top`() {
        assertEquals(0, Pads.indexAt(row = 3, column = 0))
        assertEquals(3, Pads.indexAt(row = 3, column = 3))
        assertEquals(4, Pads.indexAt(row = 2, column = 0))
        assertEquals(12, Pads.indexAt(row = 0, column = 0))
        assertEquals(15, Pads.indexAt(row = 0, column = 3))
    }

    @Test
    fun `Pads use the General MIDI drum channel and velocity 100 by default`() {
        assertEquals(10, PadSettings().channel)
        assertEquals(100, PadSettings().velocity)
    }
}
