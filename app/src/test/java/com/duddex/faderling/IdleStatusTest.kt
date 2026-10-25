package com.duddex.faderling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdleStatusTest {

    @Test
    fun `name drops the leading Faderling but keeps everything else`() {
        assertEquals("KFAUWI", IdleStatus.shortName("Faderling KFAUWI"))
        assertEquals("Debug KFAUWI", IdleStatus.shortName("Faderling Debug KFAUWI"))
        assertEquals("KFAUWI (2)", IdleStatus.shortName("Faderling KFAUWI (2)"))
    }

    @Test
    fun `name without the Faderling prefix stays unchanged`() {
        assertEquals("Faderling", IdleStatus.shortName("Faderling"))
        assertEquals("FaderlingX 1", IdleStatus.shortName("FaderlingX 1"))
        assertEquals("Other", IdleStatus.shortName("Other"))
    }

    @Test
    fun `Wi-Fi address comes from the first wlan interface`() {
        val addresses = listOf("rmnet_data2" to "10.1.2.3", "wlan0" to "192.168.2.120", "wlan1" to "192.168.49.1")
        assertEquals("192.168.2.120", IdleStatus.wifiAddress(addresses))
    }

    @Test
    fun `no Wi-Fi interface means no address`() {
        assertNull(IdleStatus.wifiAddress(listOf("rmnet_data2" to "10.1.2.3")))
        assertNull(IdleStatus.wifiAddress(emptyList()))
    }

    @Test
    fun `endpoint shows the port once it is known`() {
        assertEquals("192.168.2.123:5004", IdleStatus.endpoint("192.168.2.123", 5004))
        assertEquals("192.168.2.123", IdleStatus.endpoint("192.168.2.123", null))
    }

    @Test
    fun `three steps of three seconds`() {
        assertEquals(3, IdleStatus.STEP_COUNT)
        assertEquals(3000L, IdleStatus.STEP_MS)
    }
}
