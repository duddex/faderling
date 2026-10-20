package com.duddex.faderling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LfosTest {

    @Test
    fun `8 LFOs default to CC 102 to 109 on channel 1`() {
        val defaults = (0 until Lfos.COUNT).map(Lfos::default)
        assertEquals((102..109).toList(), defaults.map { it.cc })
        assertTrue(defaults.all { it.channel == 1 })
        assertEquals("LFO 1", defaults.first().name)
        assertEquals(LfoRange(0, 127, 4f), LfoRange())
    }

    @Test
    fun `sine starts at Min, reaches Max halfway and returns`() {
        val range = LfoRange(min = 10, max = 110)
        assertEquals(10, Lfos.value(range, 0.0))
        assertEquals(60, Lfos.value(range, 0.25))
        assertEquals(110, Lfos.value(range, 0.5))
        assertEquals(60, Lfos.value(range, 0.75))
        assertEquals(10, Lfos.value(range, 1.0))
    }

    @Test
    fun `Min above Max runs inverted`() {
        val range = LfoRange(min = 100, max = 20)
        assertEquals(100, Lfos.value(range, 0.0))
        assertEquals(20, Lfos.value(range, 0.5))
    }

    @Test
    fun `speed drag is logarithmic from 0_2 s to 120 s`() {
        assertEquals(0.2f, Lfos.speedAt(0f), 0.001f)
        assertEquals(120f, Lfos.speedAt(1f), 0.01f)
        assertEquals(120f, Lfos.speedAt(2f), 0.01f)
        assertEquals(4f, Lfos.speedAt(Lfos.fractionOf(4f)), 0.001f)
        assertTrue(Lfos.speedAt(0.5f) < 10f)
    }

    @Test
    fun `speed labels`() {
        assertEquals("0.25 s", Lfos.speedLabel(0.25f))
        assertEquals("4.0 s", Lfos.speedLabel(4f))
        assertEquals("75 s", Lfos.speedLabel(75.2f))
    }

    @Test
    fun `at most 50 messages per second`() {
        assertEquals(50L, 1000L / Lfos.MIN_SEND_INTERVAL_MS)
    }
}
