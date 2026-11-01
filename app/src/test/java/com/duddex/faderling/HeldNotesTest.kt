package com.duddex.faderling

import org.junit.Assert.assertEquals
import org.junit.Test

class HeldNotesTest {

    @Test
    fun holdKeepsNotesAfterTheirKeysAreReleased() {
        val held = HeldNotes()
        held.setHold(true)
        held.press(0, 48)
        held.press(2, 52)
        held.release(0)
        held.release(2)

        assertEquals(listOf(48, 52), held.notes())
        assertEquals(setOf(48, 52), held.latchedNotes())
    }

    @Test
    fun removingOneKeyKeepsTheNoteWhileHoldIsOn() {
        val held = HeldNotes()
        held.setHold(true)
        held.press(0, 48)
        held.press(2, 52)
        held.release(2)

        assertEquals(listOf(48, 52), held.notes())
    }

    @Test
    fun newChordAfterAllKeysAreReleasedReplacesTheLatch() {
        val held = HeldNotes()
        held.setHold(true)
        held.press(0, 48)
        held.press(2, 52)
        held.press(4, 55)
        held.release(0)
        held.release(2)
        held.release(4)

        held.press(5, 57)

        assertEquals(listOf(57), held.notes())
    }

    @Test
    fun notesAddWhileAnotherKeyIsStillDown() {
        val held = HeldNotes()
        held.setHold(true)
        held.press(0, 48)
        held.press(2, 52)
        held.release(2)
        held.press(4, 55)

        assertEquals(listOf(48, 52, 55), held.notes())
    }

    @Test
    fun turningHoldOffReleasesLatchedNotesButKeepsPressedKeys() {
        val held = HeldNotes()
        held.setHold(true)
        held.press(0, 48)
        held.press(2, 52)
        held.release(2)

        held.setHold(false)

        assertEquals(listOf(48), held.notes())
        assertEquals(emptySet<Int>(), held.latchedNotes())
        held.release(0)
        assertEquals(emptyList<Int>(), held.notes())
    }

    @Test
    fun turningHoldOnLatchesNotesThatAreAlreadyDown() {
        val held = HeldNotes()
        held.press(0, 48)
        held.setHold(true)
        held.release(0)

        assertEquals(listOf(48), held.notes())
    }

    @Test
    fun withoutHoldAReleasedNoteIsGone() {
        val held = HeldNotes()
        held.press(0, 48)
        held.release(0)

        assertEquals(emptyList<Int>(), held.notes())
    }
}
