package com.duddex.faderling

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.util.Random

/**
 * Listening side of RTP-MIDI (AppleMIDI, RFC 6295): accepts Session invitations from receivers,
 * answers clock sync and sends MIDI to every connected receiver. No recovery journal is sent.
 */
class RtpMidiEndpoint(context: Context, private val serviceName: String) {

    fun interface SessionsListener {
        fun onSessionsChanged(peerNames: List<String>)
    }

    private class Peer(val ssrc: Int, val name: String, val token: Int, val control: InetSocketAddress) {
        var data: InetSocketAddress? = null
        var lastSeen = SystemClock.elapsedRealtime()
    }

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ssrc = Random().nextInt()
    private val peers = LinkedHashMap<Int, Peer>()

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var controlSocket: DatagramSocket? = null
    private var dataSocket: DatagramSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var sequence = 0

    var listener: SessionsListener? = null
        set(value) {
            field = value
            value?.onSessionsChanged(sessionNames)
        }

    private var sessionNames: List<String> = emptyList()

    fun start() {
        if (thread != null) return
        val t = HandlerThread("rtp-midi").apply { start() }
        thread = t
        handler = Handler(t.looper).apply {
            post {
                if (openSockets()) {
                    receiveLoop(controlSocket!!, isData = false)
                    receiveLoop(dataSocket!!, isData = true)
                    register(controlSocket!!.localPort)
                    postDelayed(::checkTimeouts, TIMEOUT_CHECK_MS)
                }
            }
        }
    }

    fun stop() {
        val h = handler ?: return
        h.post {
            peers.values.forEach(::sendBye)
            peers.clear()
            notifySessions()
            controlSocket?.close()
            dataSocket?.close()
            controlSocket = null
            dataSocket = null
        }
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        thread?.quitSafely()
        thread = null
        handler = null
    }

    fun sendControlChange(channel: Int, cc: Int, value: Int) {
        handler?.post {
            if (peers.isEmpty()) return@post
            val packet = ByteBuffer.allocate(16)
                .put(0x80.toByte())
                .put(0x61)
                .putShort((sequence++ and 0xFFFF).toShort())
                .putInt(now().toInt())
                .putInt(ssrc)
                .put(0x03)
                .put((0xB0 or ((channel - 1) and 0x0F)).toByte())
                .put((cc and 0x7F).toByte())
                .put((value and 0x7F).toByte())
                .array()
            peers.values.forEach { p -> p.data?.let { send(dataSocket, packet, it) } }
        }
    }

    private fun openSockets(): Boolean {
        for (port in PREFERRED_PORT until PREFERRED_PORT + 20 step 2) {
            try {
                val control = DatagramSocket(port)
                try {
                    dataSocket = DatagramSocket(port + 1)
                    controlSocket = control
                    return true
                } catch (e: SocketException) {
                    control.close()
                }
            } catch (_: SocketException) {
            }
        }
        Log.e(TAG, "No free UDP port pair for RTP-MIDI")
        return false
    }

    private fun receiveLoop(socket: DatagramSocket, isData: Boolean) {
        Thread({
            val buffer = ByteArray(1024)
            while (!socket.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: Exception) {
                    break
                }
                val bytes = packet.data.copyOf(packet.length)
                val from = InetSocketAddress(packet.address, packet.port)
                handler?.post { onPacket(bytes, from, isData) }
            }
        }, if (isData) "rtp-midi-data" else "rtp-midi-control").apply { isDaemon = true }.start()
    }

    private fun onPacket(bytes: ByteArray, from: InetSocketAddress, isData: Boolean) {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xFF.toByte()) return
        val buf = ByteBuffer.wrap(bytes)
        buf.position(2)
        when (buf.short.toInt()) {
            CMD_INVITATION -> onInvitation(buf, bytes, from, isData)
            CMD_CLOCK_SYNC -> if (isData) onClockSync(buf, from)
            CMD_BYE -> onBye(buf)
        }
    }

    private fun onInvitation(buf: ByteBuffer, bytes: ByteArray, from: InetSocketAddress, isData: Boolean) {
        if (bytes.size < 16) return
        val version = buf.int
        val token = buf.int
        val peerSsrc = buf.int
        val name = String(bytes, 16, bytes.size - 16, Charsets.UTF_8).substringBefore('\u0000')
        val socket = if (isData) dataSocket else controlSocket

        if (version != PROTOCOL_VERSION) {
            send(socket, sessionPacket(CMD_REJECT, token), from)
            return
        }

        if (isData) {
            val peer = peers[peerSsrc]
            if (peer == null) {
                send(socket, sessionPacket(CMD_REJECT, token), from)
                return
            }
            peer.data = from
            peer.lastSeen = SystemClock.elapsedRealtime()
            send(socket, sessionPacket(CMD_ACCEPT, token, includeName = true), from)
            notifySessions()
        } else {
            peers[peerSsrc] = Peer(peerSsrc, name.ifEmpty { from.address.hostAddress ?: "?" }, token, from)
            send(socket, sessionPacket(CMD_ACCEPT, token, includeName = true), from)
        }
    }

    private fun onClockSync(buf: ByteBuffer, from: InetSocketAddress) {
        if (buf.remaining() < 32) return
        val peerSsrc = buf.int
        val count = buf.get().toInt()
        buf.position(buf.position() + 3)
        val ts1 = buf.long
        peers[peerSsrc]?.lastSeen = SystemClock.elapsedRealtime()
        if (count != 0) return
        val reply = ByteBuffer.allocate(36)
            .putShort(0xFFFF.toShort())
            .putShort(CMD_CLOCK_SYNC.toShort())
            .putInt(ssrc)
            .put(1)
            .put(ByteArray(3))
            .putLong(ts1)
            .putLong(now())
            .putLong(0)
            .array()
        send(dataSocket, reply, from)
    }

    private fun onBye(buf: ByteBuffer) {
        if (buf.remaining() < 12) return
        buf.int
        buf.int
        val peerSsrc = buf.int
        if (peers.remove(peerSsrc) != null) notifySessions()
    }

    private fun checkTimeouts() {
        val cutoff = SystemClock.elapsedRealtime() - PEER_TIMEOUT_MS
        val stale = peers.values.filter { it.lastSeen < cutoff }
        if (stale.isNotEmpty()) {
            stale.forEach { peers.remove(it.ssrc) }
            notifySessions()
        }
        handler?.postDelayed(::checkTimeouts, TIMEOUT_CHECK_MS)
    }

    private fun sendBye(peer: Peer) {
        send(controlSocket, sessionPacket(CMD_BYE, peer.token), peer.control)
    }

    private fun sessionPacket(command: Int, token: Int, includeName: Boolean = false): ByteArray {
        val name = if (includeName) serviceName.toByteArray(Charsets.UTF_8) + 0.toByte() else ByteArray(0)
        return ByteBuffer.allocate(16 + name.size)
            .putShort(0xFFFF.toShort())
            .putShort(command.toShort())
            .putInt(PROTOCOL_VERSION)
            .putInt(token)
            .putInt(ssrc)
            .put(name)
            .array()
    }

    private fun send(socket: DatagramSocket?, bytes: ByteArray, to: InetSocketAddress) {
        try {
            socket?.send(DatagramPacket(bytes, bytes.size, to))
        } catch (e: Exception) {
            Log.w(TAG, "Send to $to failed", e)
        }
    }

    private fun notifySessions() {
        val names = peers.values.filter { it.data != null }.map { it.name }
        mainHandler.post {
            sessionNames = names
            listener?.onSessionsChanged(names)
        }
    }

    private fun register(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = this@RtpMidiEndpoint.serviceName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Advertised as ${info.serviceName} on port $port")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Bonjour registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registration = l
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
    }

    /** Local clock in 100 µs units, as used by AppleMIDI clock sync and RTP timestamps. */
    private fun now(): Long = SystemClock.elapsedRealtimeNanos() / 100_000

    private companion object {
        const val TAG = "RtpMidi"
        const val SERVICE_TYPE = "_apple-midi._udp"
        const val PREFERRED_PORT = 5004
        const val PROTOCOL_VERSION = 2
        const val CMD_INVITATION = 0x494E
        const val CMD_ACCEPT = 0x4F4B
        const val CMD_REJECT = 0x4E4F
        const val CMD_BYE = 0x4259
        const val CMD_CLOCK_SYNC = 0x434B
        const val PEER_TIMEOUT_MS = 60_000L
        const val TIMEOUT_CHECK_MS = 5_000L
    }
}
