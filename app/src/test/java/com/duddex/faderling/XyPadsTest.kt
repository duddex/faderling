package com.duddex.faderling

import org.junit.Assert.assertEquals
import org.junit.Test

class XyPadsTest {

    @Test
    fun `two XY Pads default to the undefined CCs 85 to 88 on channel 1`() {
        assertEquals(XyAssignment("XY 1", ccX = 85, ccY = 86, channel = 1), XyPads.default(0))
        assertEquals(XyAssignment("XY 2", ccX = 87, ccY = 88, channel = 1), XyPads.default(1))
        assertEquals(2, XyPads.COUNT)
    }

    @Test
    fun `glide step moves towards the target without overshooting`() {
        assertEquals(74f, XyPads.step(64f, 127f, 10f))
        assertEquals(54f, XyPads.step(64f, 0f, 10f))
        assertEquals(70f, XyPads.step(64f, 70f, 10f))
        assertEquals(64f, XyPads.step(64f, 64f, 10f))
    }

    @Test
    fun `glides at the same speed as the Faders`() {
        assertEquals(250f, XyPads.GLIDE_FULL_TRAVEL_MS)
    }
}
