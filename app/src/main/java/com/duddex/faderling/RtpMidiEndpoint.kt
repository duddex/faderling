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
import java.util.Random

/**
 * Listening side of RTP-MIDI (AppleMIDI, RFC 6295): accepts Session invitations from receivers,
 * answers clock sync and sends MIDI to every connected receiver. No recovery journal is sent.
 */
class RtpMidiEndpoint(context: Context, private val serviceName: String) {

    /** Name actually registered via Bonjour; Android appends " (2)" etc. when [serviceName] is taken. */
    @Volatile
    private var advertisedName = serviceName

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

    fun sendControlChange(channel: Int, cc: Int, value: Int) =
        sendPacket { seq, ts -> AppleMidi.controlChangePacket(seq, ts, ssrc, channel, cc, value) }

    fun sendNoteOn(channel: Int, note: Int, velocity: Int) =
        sendPacket { seq, ts -> AppleMidi.noteOnPacket(seq, ts, ssrc, channel, note, velocity) }

    fun sendNoteOff(channel: Int, note: Int) =
        sendPacket { seq, ts -> AppleMidi.noteOffPacket(seq, ts, ssrc, channel, note) }

    private fun sendPacket(build: (sequence: Int, timestamp: Int) -> ByteArray) {
        handler?.post {
            if (peers.isEmpty()) return@post
            val packet = build(sequence++ and 0xFFFF, now().toInt())
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
        when (AppleMidi.command(bytes)) {
            AppleMidi.CMD_INVITATION -> AppleMidi.parseSession(bytes)?.let { onInvitation(it, from, isData) }
            AppleMidi.CMD_CLOCK_SYNC -> if (isData) AppleMidi.parseClockSync(bytes)?.let { onClockSync(it, from) }
            AppleMidi.CMD_BYE -> AppleMidi.parseSession(bytes)?.let { onBye(it) }
        }
    }

    private fun onInvitation(invitation: AppleMidi.SessionMessage, from: InetSocketAddress, isData: Boolean) {
        val token = invitation.token
        val socket = if (isData) dataSocket else controlSocket

        if (invitation.version != AppleMidi.PROTOCOL_VERSION) {
            send(socket, sessionPacket(AppleMidi.CMD_REJECT, token), from)
            return
        }

        if (isData) {
            val peer = peers[invitation.ssrc]
            if (peer == null) {
                send(socket, sessionPacket(AppleMidi.CMD_REJECT, token), from)
                return
            }
            peer.data = from
            peer.lastSeen = SystemClock.elapsedRealtime()
            send(socket, sessionPacket(AppleMidi.CMD_ACCEPT, token, includeName = true), from)
            notifySessions()
        } else {
            val name = invitation.name.ifEmpty { from.address.hostAddress ?: "?" }
            peers[invitation.ssrc] = Peer(invitation.ssrc, name, token, from)
            send(socket, sessionPacket(AppleMidi.CMD_ACCEPT, token, includeName = true), from)
        }
    }

    private fun onClockSync(sync: AppleMidi.ClockSync, from: InetSocketAddress) {
        peers[sync.ssrc]?.lastSeen = SystemClock.elapsedRealtime()
        if (sync.count != 0) return
        send(dataSocket, AppleMidi.clockSyncReply(ssrc, sync.timestamp1, now()), from)
    }

    private fun onBye(bye: AppleMidi.SessionMessage) {
        if (peers.remove(bye.ssrc) != null) notifySessions()
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
        send(controlSocket, sessionPacket(AppleMidi.CMD_BYE, peer.token), peer.control)
    }

    private fun sessionPacket(command: Int, token: Int, includeName: Boolean = false): ByteArray =
        AppleMidi.sessionPacket(command, token, ssrc, if (includeName) advertisedName else null)

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
                advertisedName = info.serviceName ?: serviceName
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
        const val PEER_TIMEOUT_MS = 60_000L
        const val TIMEOUT_CHECK_MS = 5_000L
    }
}
