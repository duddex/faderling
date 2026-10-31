package com.duddex.faderling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaderMaxTest {

    @Test
    fun `default is 100`() {
        assertEquals(100, FaderMax.DEFAULT)
        assertTrue(FaderMax.DEFAULT in FaderMax.RANGE)
    }

    @Test
    fun `stored values are clamped to the MIDI range`() {
        assertEquals(0, FaderMax.clamp(-1))
        assertEquals(0, FaderMax.clamp(0))
        assertEquals(100, FaderMax.clamp(100))
        assertEquals(127, FaderMax.clamp(127))
        assertEquals(127, FaderMax.clamp(200))
    }
}
