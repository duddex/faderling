package com.duddex.faderling

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardTest {

    @Test
    fun `two octaves from C to C have 15 white and 10 black keys`() {
        val black = (0 until Keyboard.KEY_COUNT).count(Keyboard::isBlack)
        assertEquals(25, Keyboard.KEY_COUNT)
        assertEquals(10, black)
        assertEquals(false, Keyboard.isBlack(0))
        assertEquals(true, Keyboard.isBlack(1))
        assertEquals(false, Keyboard.isBlack(24))
    }

    @Test
    fun `note names follow Ableton where middle C is C3`() {
        assertEquals("C3", Keyboard.noteName(60))
        assertEquals("C2", Keyboard.noteName(48))
        assertEquals("C4", Keyboard.noteName(72))
        assertEquals("C-2", Keyboard.noteName(0))
        assertEquals("G8", Keyboard.noteName(127))
        assertEquals("F#3", Keyboard.noteName(66))
    }

    @Test
    fun `default range is C2 to C4`() {
        assertEquals(48, Keyboard.DEFAULT_LOWEST_NOTE)
    }

    @Test
    fun `octave shift keeps all keys within MIDI 0 to 127`() {
        assertEquals(60, Keyboard.shift(48, 1))
        assertEquals(36, Keyboard.shift(48, -1))
        assertEquals(0, Keyboard.shift(0, -1))
        assertEquals(96, Keyboard.shift(96, 1))
        assertEquals(true, Keyboard.MAX_LOWEST_NOTE + Keyboard.KEY_COUNT - 1 <= 127)
    }

    @Test
    fun `one-octave compact keyboard starts at middle C and can go one octave higher`() {
        assertEquals(13, Keyboard.COMPACT_KEY_COUNT)
        assertEquals("C3", Keyboard.noteName(Keyboard.COMPACT_DEFAULT_LOWEST_NOTE))
        assertEquals(96, Keyboard.maxLowestNote(Keyboard.KEY_COUNT))
        assertEquals(108, Keyboard.maxLowestNote(Keyboard.COMPACT_KEY_COUNT))
        assertEquals(108, Keyboard.shift(108, 1, Keyboard.COMPACT_KEY_COUNT))
        assertEquals(true, 108 + Keyboard.COMPACT_KEY_COUNT - 1 <= 127)
    }
}
