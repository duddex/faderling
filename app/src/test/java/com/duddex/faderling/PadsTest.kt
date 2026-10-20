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

    @Test
    fun `Pads use spare width up to 1_6 times their height`() {
        assertEquals(160f to 100f, Pads.cellSize(width = 1000f, height = 400f))
        assertEquals(125f to 100f, Pads.cellSize(width = 500f, height = 400f))
    }

    @Test
    fun `Pads stay square when the area is taller than wide`() {
        assertEquals(50f to 50f, Pads.cellSize(width = 200f, height = 400f))
    }

    @Test
    fun `compact layout only below 450 dp available height`() {
        assertEquals(true, ScreenLayout.isCompact(411))
        assertEquals(false, ScreenLayout.isCompact(527))
        assertEquals(false, ScreenLayout.isCompact(711))
    }
}
