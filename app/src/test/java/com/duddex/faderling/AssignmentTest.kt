package com.duddex.faderling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentTest {

    private val defaults = ControlKind.entries.flatMap { kind -> (0 until kind.count).map { Assignment.default(kind, it) } }

    /** CCs left undefined by the MIDI 1.0 specification. */
    private val undefinedCcs = setOf(3, 9, 14, 15) + (20..31) + (85..90) + (102..119)

    @Test
    fun `there are 10 faders, 8 knobs and 8 buttons`() {
        assertEquals(10, ControlKind.FADER.count)
        assertEquals(8, ControlKind.KNOB.count)
        assertEquals(8, ControlKind.BUTTON.count)
    }

    @Test
    fun `default CCs are unique across all Controls`() {
        assertEquals(defaults.size, defaults.map { it.cc to it.channel }.toSet().size)
    }

    @Test
    fun `default CCs only use undefined controller numbers`() {
        defaults.forEach { assertTrue("CC ${it.cc} is defined in the MIDI spec", it.cc in undefinedCcs) }
    }

    @Test
    fun `defaults are on channel 1, Momentary, and named after their CC`() {
        defaults.forEach {
            assertEquals(1, it.channel)
            assertEquals(ButtonMode.MOMENTARY, it.mode)
            assertEquals("CC ${it.cc}", it.name)
            assertTrue(it.name.length <= Assignment.MAX_NAME_LENGTH)
        }
    }

    @Test
    fun `default ranges per Control kind`() {
        assertEquals((20..29).toList(), (0 until 10).map { Assignment.default(ControlKind.FADER, it).cc })
        assertEquals((102..109).toList(), (0 until 8).map { Assignment.default(ControlKind.KNOB, it).cc })
        assertEquals((110..117).toList(), (0 until 8).map { Assignment.default(ControlKind.BUTTON, it).cc })
    }
}
