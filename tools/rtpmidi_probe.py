"""Acts as an RTP-MIDI initiator (like rtpMIDI on a PC): invites Faderling, syncs the clock once
and prints the CC messages it receives.

Usage: python tools/rtpmidi_probe.py <tablet-ip> [port=5004] [seconds=8]
"""
import socket, struct, sys, time, random

host = sys.argv[1]
port = int(sys.argv[2]) if len(sys.argv) > 2 else 5004
seconds = float(sys.argv[3]) if len(sys.argv) > 3 else 8

ssrc = random.getrandbits(32)
token = random.getrandbits(32)
name = b"TestPC\0"

ctrl = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); ctrl.bind(("", 0))
data = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); data.bind(("", ctrl.getsockname()[1] + 1))
for s in (ctrl, data): s.settimeout(2)

def invite(sock, p):
    sock.sendto(struct.pack(">HHIII", 0xFFFF, 0x494E, 2, token, ssrc) + name, (host, p))
    reply, _ = sock.recvfrom(1024)
    cmd, ver, tok, peer = struct.unpack(">2xHIII", reply[:16])
    print(f"port {p}: {reply[2:4].decode()} version={ver} token_ok={tok == token} name={reply[16:].split(b'\0')[0].decode()}")
    return peer

invite(ctrl, port)
invite(data, port + 1)

t1 = int(time.monotonic() * 10000)
data.sendto(struct.pack(">HHIB3xQQQ", 0xFFFF, 0x434B, ssrc, 0, t1, 0, 0), (host, port + 1))
reply, _ = data.recvfrom(1024)
_, _, peer, count, r1, r2, _ = struct.unpack(">HHIB3xQQQ", reply)
print(f"CK reply count={count} ts1_echo_ok={r1 == t1} ts2={r2}")

print("listening for MIDI...")
data.settimeout(0.5)
end = time.time() + seconds
while time.time() < end:
    try:
        pkt, _ = data.recvfrom(1024)
    except socket.timeout:
        continue
    if pkt[0] & 0xC0 == 0x80 and len(pkt) >= 16:
        seq, ts = struct.unpack(">HI", pkt[2:8])
        status, cc, val = pkt[13], pkt[14], pkt[15]
        print(f"RTP seq={seq} hdr=0x{pkt[12]:02x} ch={(status & 0x0F) + 1} cc={cc} value={val}")

ctrl.sendto(struct.pack(">HHIII", 0xFFFF, 0x4259, 2, token, ssrc), (host, port))
print("sent BY")
