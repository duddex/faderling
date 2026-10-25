package com.duddex.faderling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowSwipeTest {

    @Test
    fun `each strip is reached once, the start strip never again`() {
        val swipe = RowSwipe(2)
        assertEquals(emptyList<Int>(), swipe.reach(2))
        assertEquals(listOf(3), swipe.reach(3))
        assertEquals(listOf(4), swipe.reach(4))
        assertEquals(emptyList<Int>(), swipe.reach(3))
        assertEquals(listOf(1), swipe.reach(1))
    }

    @Test
    fun `strips skipped by a fast swipe are reached too`() {
        val swipe = RowSwipe(1)
        assertEquals(listOf(2, 3, 4, 5), swipe.reach(5))
        assertEquals(listOf(0), swipe.reach(0))
    }

    @Test
    fun `going back only reaches strips beyond the earlier ones`() {
        val swipe = RowSwipe(4)
        assertEquals(listOf(5, 6), swipe.reach(6))
        assertEquals(listOf(3, 2), swipe.reach(2))
    }

    @Test
    fun `finds the strip under x`() {
        val lefts = listOf(0, 100, 200)
        val rights = listOf(100, 200, 300)
        assertEquals(0, RowSwipe.indexAt(0f, lefts, rights))
        assertEquals(0, RowSwipe.indexAt(99.9f, lefts, rights))
        assertEquals(1, RowSwipe.indexAt(100f, lefts, rights))
        assertEquals(2, RowSwipe.indexAt(299f, lefts, rights))
        assertEquals(0, RowSwipe.indexAt(-1f, lefts, rights))
        assertEquals(2, RowSwipe.indexAt(300f, lefts, rights))
    }

    @Test
    fun `strips with a gap between them`() {
        val lefts = listOf(0, 110)
        val rights = listOf(100, 210)
        assertNull(RowSwipe.indexAt(105f, lefts, rights))
        assertEquals(1, RowSwipe.indexAt(110f, lefts, rights))
    }
}
