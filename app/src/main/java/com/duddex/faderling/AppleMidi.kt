package com.duddex.faderling

import java.nio.ByteBuffer

/** Packet formats of AppleMIDI session control and RTP-MIDI (RFC 6295), free of Android dependencies. */
object AppleMidi {
    const val PROTOCOL_VERSION = 2
    const val CMD_INVITATION = 0x494E
    const val CMD_ACCEPT = 0x4F4B
    const val CMD_REJECT = 0x4E4F
    const val CMD_BYE = 0x4259
    const val CMD_CLOCK_SYNC = 0x434B

    class SessionMessage(val command: Int, val version: Int, val token: Int, val ssrc: Int, val name: String)

    class ClockSync(val ssrc: Int, val count: Int, val timestamp1: Long)

    /** Bonjour name of this device, e.g. "Faderling TB305FU"; DNS-SD limits instance names to 63 bytes. */
    fun serviceName(appName: String, model: String?): String {
        val name = listOf(appName, model?.trim().orEmpty()).filter { it.isNotEmpty() }.joinToString(" ")
        var result = name
        while (result.toByteArray(Charsets.UTF_8).size > 63) result = result.dropLast(1)
        return result.trimEnd()
    }

    /** Returns the AppleMIDI command of a session-control packet, or null for anything else. */
    fun command(bytes: ByteArray): Int? {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xFF.toByte()) return null
        return ByteBuffer.wrap(bytes, 2, 2).short.toInt() and 0xFFFF
    }

    /** Parses IN, OK, NO and BY packets. */
    fun parseSession(bytes: ByteArray): SessionMessage? {
        if (bytes.size < 16) return null
        val command = command(bytes) ?: return null
        val buf = ByteBuffer.wrap(bytes, 4, 12)
        val name = String(bytes, 16, bytes.size - 16, Charsets.UTF_8).substringBefore('\u0000')
        return SessionMessage(command, version = buf.int, token = buf.int, ssrc = buf.int, name = name)
    }

    fun sessionPacket(command: Int, token: Int, ssrc: Int, name: String? = null): ByteArray {
        val nameBytes = name?.let { it.toByteArray(Charsets.UTF_8) + 0.toByte() } ?: ByteArray(0)
        return ByteBuffer.allocate(16 + nameBytes.size)
            .putShort(0xFFFF.toShort())
            .putShort(command.toShort())
            .putInt(PROTOCOL_VERSION)
            .putInt(token)
            .putInt(ssrc)
            .put(nameBytes)
            .array()
    }

    fun parseClockSync(bytes: ByteArray): ClockSync? {
        if (bytes.size < 36 || command(bytes) != CMD_CLOCK_SYNC) return null
        val buf = ByteBuffer.wrap(bytes)
        buf.position(4)
        val ssrc = buf.int
        val count = buf.get().toInt()
        buf.position(12)
        return ClockSync(ssrc, count, buf.long)
    }

    /** Answer (count 1) to a clock sync started by the receiver (count 0). */
    fun clockSyncReply(ssrc: Int, timestamp1: Long, now: Long): ByteArray =
        ByteBuffer.allocate(36)
            .putShort(0xFFFF.toShort())
            .putShort(CMD_CLOCK_SYNC.toShort())
            .putInt(ssrc)
            .put(1)
            .put(ByteArray(3))
            .putLong(timestamp1)
            .putLong(now)
            .putLong(0)
            .array()

    /** RTP-MIDI packet carrying one Control Change, without recovery journal. [channel] is 1–16. */
    fun controlChangePacket(sequence: Int, timestamp: Int, ssrc: Int, channel: Int, cc: Int, value: Int): ByteArray =
        ByteBuffer.allocate(16)
            .put(0x80.toByte())
            .put(0x61)
            .putShort(sequence.toShort())
            .putInt(timestamp)
            .putInt(ssrc)
            .put(0x03)
            .put((0xB0 or ((channel - 1) and 0x0F)).toByte())
            .put((cc and 0x7F).toByte())
            .put((value and 0x7F).toByte())
            .array()
}
