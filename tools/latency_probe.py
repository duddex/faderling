"""Measures network latency between Faderling and this PC.

1. Clock-sync round trips (AppleMIDI CK) -> RTT distribution and tablet/PC clock offset.
2. Listens for MIDI packets (play Keys, Pads or Controls on the tablet meanwhile) and computes
   send (tablet RTP timestamp) -> arrival (PC) per packet.

Only the network part is measured: touch screen -> app and the PC side (rtpMIDI, DAW, audio
buffer) are not included.

Usage: python tools/latency_probe.py <tablet-ip> [pings=200] [listen-seconds=15]
"""
import random, socket, statistics, struct, sys, time

host = sys.argv[1]
pings = int(sys.argv[2]) if len(sys.argv) > 2 else 200
listen = float(sys.argv[3]) if len(sys.argv) > 3 else 15
port = 5004
ssrc, token = random.getrandbits(32), random.getrandbits(32)

def now():  # PC clock in 100 µs units, like the tablet
    return int(time.perf_counter() * 10000)

ctrl = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); ctrl.bind(("", 0))
data = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); data.bind(("", ctrl.getsockname()[1] + 1))
for sock, p in ((ctrl, port), (data, port + 1)):
    sock.settimeout(2)
    sock.sendto(struct.pack(">HHIII", 0xFFFF, 0x494E, 2, token, ssrc) + b"Latency\0", (host, p))
    sock.recvfrom(1024)

rtts, samples, lost = [], [], 0
data.settimeout(1)
for _ in range(pings):
    t1 = now()
    data.sendto(struct.pack(">HHIB3xQQQ", 0xFFFF, 0x434B, ssrc, 0, t1, 0, 0), (host, port + 1))
    try:
        while True:
            pkt, _ = data.recvfrom(1024)
            if pkt[2:4] == b"CK" and struct.unpack(">Q", pkt[12:20])[0] == t1:
                break
    except socket.timeout:
        lost += 1
        continue
    t3 = now()
    _, _, _, _, r1, t2, _ = struct.unpack(">HHIB3xQQQ", pkt)
    rtts.append((t3 - t1) / 10)
    samples.append((t3 - t1, t2 - (t1 + t3) / 2))
    time.sleep(0.02)

def stats(values):
    v = sorted(values)
    return f"min {v[0]:.1f}  median {statistics.median(v):.1f}  p95 {v[int(len(v) * 0.95) - 1]:.1f}  max {v[-1]:.1f} ms  (n={len(v)})"

print("RTT (round trip)      :", stats(rtts), f" lost: {lost}")
print("one-way estimate (RTT/2):", stats([r / 2 for r in rtts]))
offset = min(samples)[1]  # offset from the fastest exchange is the most accurate

print("listening for MIDI ...", flush=True)
one_way = []
data.settimeout(0.5)
end = time.time() + listen
while time.time() < end:
    try:
        pkt, _ = data.recvfrom(1024)
    except socket.timeout:
        continue
    arrival = now()
    if pkt[0] & 0xC0 == 0x80 and len(pkt) >= 16:
        ts = struct.unpack(">I", pkt[4:8])[0]
        delay = ((arrival + offset) - ts) % (1 << 32)
        kind = {0x90: "on ", 0x80: "off", 0xB0: "cc "}.get(pkt[13] & 0xF0, "?  ")
        one_way.append(delay / 10)
        print(f"  {kind} {pkt[14]:3d}  send->arrival {delay / 10:6.1f} ms")

ctrl.sendto(struct.pack(">HHIII", 0xFFFF, 0x4259, 2, token, ssrc), (host, port))
if one_way:
    print("send->arrival (MIDI)  :", stats(one_way))
