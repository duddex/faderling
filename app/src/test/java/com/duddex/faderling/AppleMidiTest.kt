package com.duddex.faderling

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class AppleMidiTest {

    /** An invitation as sent by a receiver such as rtpMIDI. */
    private fun invitation(version: Int = 2, token: Int = 0x01020304, ssrc: Int = 0x0A0B0C0D, name: String = "X390"): ByteArray =
        ByteBuffer.allocate(16 + name.length + 1)
            .putShort(0xFFFF.toShort()).put('I'.code.toByte()).put('N'.code.toByte())
            .putInt(version).putInt(token).putInt(ssrc)
            .put(name.toByteArray()).put(0)
            .array()

    @Test
    fun `service name combines app name and device model`() {
        assertEquals("Faderling TB305FU", AppleMidi.serviceName("Faderling", "TB305FU"))
        assertEquals("Faderling KFAUWI", AppleMidi.serviceName("Faderling", " KFAUWI "))
        assertEquals("Faderling", AppleMidi.serviceName("Faderling", null))
        assertEquals("Faderling", AppleMidi.serviceName("Faderling", ""))
    }

    @Test
    fun `service name fits the 63 byte DNS-SD limit`() {
        val name = AppleMidi.serviceName("Faderling", "Ä".repeat(60))
        assertEquals(true, name.toByteArray(Charsets.UTF_8).size <= 63)
        assertEquals(true, name.startsWith("Faderling Ä"))
    }

    @Test
    fun `parses an invitation`() {
        val message = AppleMidi.parseSession(invitation())!!
        assertEquals(AppleMidi.CMD_INVITATION, message.command)
        assertEquals(2, message.version)
        assertEquals(0x01020304, message.token)
        assertEquals(0x0A0B0C0D, message.ssrc)
        assertEquals("X390", message.name)
    }

    @Test
    fun `parses a bye without name`() {
        val bye = AppleMidi.sessionPacket(AppleMidi.CMD_BYE, token = 7, ssrc = 99)
        val message = AppleMidi.parseSession(bye)!!
        assertEquals(AppleMidi.CMD_BYE, message.command)
        assertEquals(99, message.ssrc)
        assertEquals("", message.name)
    }

    @Test
    fun `ignores packets that are not AppleMIDI session control`() {
        assertNull(AppleMidi.command(byteArrayOf(0x80.toByte(), 0x61, 0, 0)))
        assertNull(AppleMidi.command(byteArrayOf(0xFF.toByte())))
        assertNull(AppleMidi.parseSession(invitation().copyOf(12)))
    }

    @Test
    fun `accept echoes the token and carries the name zero-terminated`() {
        val accept = AppleMidi.sessionPacket(AppleMidi.CMD_ACCEPT, token = 0x01020304, ssrc = 0x11223344, name = "Faderling")
        val expectedHeader = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 'O'.code.toByte(), 'K'.code.toByte(),
            0, 0, 0, 2,
            1, 2, 3, 4,
            0x11, 0x22, 0x33, 0x44,
        )
        assertArrayEquals(expectedHeader, accept.copyOf(16))
        assertArrayEquals("Faderling".toByteArray() + 0.toByte(), accept.copyOfRange(16, accept.size))
    }

    @Test
    fun `clock sync reply copies timestamp 1 and adds our time`() {
        val request = ByteBuffer.allocate(36)
            .putShort(0xFFFF.toShort()).put('C'.code.toByte()).put('K'.code.toByte())
            .putInt(0x0A0B0C0D).put(0).put(ByteArray(3))
            .putLong(123_456_789L).putLong(0).putLong(0)
            .array()

        val sync = AppleMidi.parseClockSync(request)!!
        assertEquals(0x0A0B0C0D, sync.ssrc)
        assertEquals(0, sync.count)
        assertEquals(123_456_789L, sync.timestamp1)

        val reply = ByteBuffer.wrap(AppleMidi.clockSyncReply(ssrc = 42, timestamp1 = sync.timestamp1, now = 555L))
        assertEquals(AppleMidi.CMD_CLOCK_SYNC, AppleMidi.command(reply.array()))
        assertEquals(42, reply.getInt(4))
        assertEquals(1, reply.get(8).toInt())
        assertEquals(123_456_789L, reply.getLong(12))
        assertEquals(555L, reply.getLong(20))
    }

    @Test
    fun `control change packet matches RFC 6295 layout`() {
        val packet = AppleMidi.controlChangePacket(sequence = 0x1234, timestamp = 0x0A0B0C0D, ssrc = 0x11223344, channel = 3, cc = 74, value = 100)
        val expected = byteArrayOf(
            0x80.toByte(), 0x61, 0x12, 0x34,
            0x0A, 0x0B, 0x0C, 0x0D,
            0x11, 0x22, 0x33, 0x44,
            0x03, 0xB2.toByte(), 74, 100,
        )
        assertArrayEquals(expected, packet)
    }

    @Test
    fun `note on and note off packets`() {
        val on = AppleMidi.noteOnPacket(sequence = 1, timestamp = 2, ssrc = 3, channel = 1, note = 60, velocity = 100)
        assertArrayEquals(byteArrayOf(0x03, 0x90.toByte(), 60, 100), on.copyOfRange(12, 16))

        val off = AppleMidi.noteOffPacket(sequence = 1, timestamp = 2, ssrc = 3, channel = 10, note = 72)
        assertArrayEquals(byteArrayOf(0x03, 0x89.toByte(), 72, 0), off.copyOfRange(12, 16))
    }

    @Test
    fun `control change keeps channel and data bytes in MIDI range`() {
        val packet = AppleMidi.controlChangePacket(sequence = 0, timestamp = 0, ssrc = 0, channel = 16, cc = 200, value = 255)
        assertEquals(0xBF.toByte(), packet[13])
        assertEquals(200 and 0x7F, packet[14].toInt())
        assertEquals(0x7F, packet[15].toInt())
    }
}
