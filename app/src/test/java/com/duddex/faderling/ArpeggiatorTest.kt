package com.duddex.faderling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ArpeggiatorTest {

    @Test
    fun stepMillisFollowsTempoAndRate() {
        assertEquals(125L, Arpeggiator.stepMillis(ArpSettings(bpm = 120, rate = ArpRate.SIXTEENTH)))
        assertEquals(250L, Arpeggiator.stepMillis(ArpSettings(bpm = 120, rate = ArpRate.EIGHTH)))
        assertEquals(167L, Arpeggiator.stepMillis(ArpSettings(bpm = 120, rate = ArpRate.EIGHTH_TRIPLET)))
        assertEquals(63L, Arpeggiator.stepMillis(ArpSettings(bpm = 120, rate = ArpRate.THIRTY_SECOND)))
        assertEquals(63L, Arpeggiator.stepMillis(ArpSettings(bpm = 240, rate = ArpRate.SIXTEENTH)))
    }

    @Test
    fun gateMillisIsAPercentageOfTheStep() {
        assertEquals(63L, Arpeggiator.gateMillis(ArpSettings(bpm = 120, rate = ArpRate.SIXTEENTH, gatePercent = 50)))
        assertEquals(13L, Arpeggiator.gateMillis(ArpSettings(bpm = 120, rate = ArpRate.SIXTEENTH, gatePercent = 10)))
        assertEquals(113L, Arpeggiator.gateMillis(ArpSettings(bpm = 120, rate = ArpRate.SIXTEENTH, gatePercent = 90)))
    }

    @Test
    fun poolSortsAndExpandsTheOctaves() {
        assertEquals(listOf(48, 52, 55), Arpeggiator.pool(listOf(55, 48, 52), 1))
        assertEquals(listOf(48, 52, 55, 60, 64, 67), Arpeggiator.pool(listOf(55, 48, 52), 2))
    }

    @Test
    fun deterministicPatterns() {
        val notes = listOf(48, 52, 55)
        assertEquals(listOf(48, 52, 55), Arpeggiator.sequence(notes, ArpPattern.UP, 1))
        assertEquals(listOf(55, 52, 48), Arpeggiator.sequence(notes, ArpPattern.DOWN, 1))
        assertEquals(listOf(48, 52, 55, 52), Arpeggiator.sequence(notes, ArpPattern.UP_DOWN, 1))
        assertEquals(listOf(52, 48, 55), Arpeggiator.sequence(listOf(52, 48, 55), ArpPattern.AS_PLAYED, 1))
    }

    @Test
    fun upDownDoesNotRepeatTheTurningNotes() {
        assertEquals(
            listOf(48, 52, 55, 60, 64, 67, 64, 60, 55, 52),
            Arpeggiator.sequence(listOf(48, 52, 55), ArpPattern.UP_DOWN, 2),
        )
    }

    @Test
    fun asPlayedKeepsTheOrderOverTheOctaves() {
        assertEquals(
            listOf(55, 48, 52, 67, 60, 64),
            Arpeggiator.sequence(listOf(55, 48, 52), ArpPattern.AS_PLAYED, 2),
        )
    }

    @Test
    fun notesAbove127AreSkipped() {
        assertEquals(listOf(120, 125), Arpeggiator.pool(listOf(120, 125), 2))
    }

    @Test
    fun singleNoteAndSingleOctaveRepeat() {
        assertEquals(listOf(48), Arpeggiator.sequence(listOf(48), ArpPattern.UP_DOWN, 1))
        assertEquals(listOf(48), Arpeggiator.sequence(listOf(48), ArpPattern.UP, 1))
    }

    @Test
    fun randomNoteAvoidsAnImmediateRepeat() {
        val pool = listOf(48, 52, 55)
        val random = Random(7)
        var previous: Int? = null
        repeat(100) {
            val note = Arpeggiator.randomNote(pool, previous, random)
            assertTrue(note in pool)
            if (previous != null) assertNotEquals(previous, note)
            previous = note
        }
    }
}
