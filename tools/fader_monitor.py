"""Fader monitor: connects to Faderling over RTP-MIDI and shows every received CC as a fader.

Works alongside rtpMIDI/a DAW (Faderling accepts several sessions at once). Standard library only.

Usage: python tools/fader_monitor.py <tablet-ip> [port=5004]
"""
import queue
import random
import socket
import struct
import sys
import threading
import time
import tkinter as tk

NAME = b"Fader Monitor\0"
CLOCK_SYNC_INTERVAL = 10
RETRY_INTERVAL = 2


class RtpMidiClient(threading.Thread):
    """Minimal RTP-MIDI initiator: invites, keeps the session alive with clock sync, reports CC."""

    def __init__(self, host, port, events):
        super().__init__(daemon=True)
        self.host, self.port, self.events = host, port, events
        self.ssrc = random.getrandbits(32)
        self.token = 0
        self.running = True
        self.ctrl = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.ctrl.bind(("", 0))
        self.data = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.data.bind(("", self.ctrl.getsockname()[1] + 1))

    def session_packet(self, command):
        return struct.pack(">HHIII", 0xFFFF, command, 2, self.token, self.ssrc)

    def invite(self, sock, port):
        sock.settimeout(RETRY_INTERVAL)
        sock.sendto(self.session_packet(0x494E) + NAME, (self.host, port))
        while True:
            reply, _ = sock.recvfrom(1024)
            if reply[:2] == b"\xff\xff" and struct.unpack(">I", reply[8:12])[0] == self.token:
                if reply[2:4] != b"OK":
                    raise ConnectionRefusedError(reply[2:4].decode(errors="replace"))
                return reply[16:].split(b"\0")[0].decode(errors="replace")

    def clock_sync(self):
        now = int(time.monotonic() * 10000)
        self.data.sendto(struct.pack(">HHIB3xQQQ", 0xFFFF, 0x434B, self.ssrc, 0, now, 0, 0),
                         (self.host, self.port + 1))

    def run(self):
        while self.running:
            self.token = random.getrandbits(32)
            try:
                name = self.invite(self.ctrl, self.port)
                self.invite(self.data, self.port + 1)
            except (socket.timeout, OSError) as e:
                self.events.put(("status", f"Waiting for {self.host}:{self.port} … ({type(e).__name__})"))
                time.sleep(RETRY_INTERVAL if not isinstance(e, socket.timeout) else 0)
                continue
            self.events.put(("status", f"Session with {name} at {self.host}"))
            self.receive_until_bye()

    def receive_until_bye(self):
        self.data.settimeout(0.5)
        self.ctrl.settimeout(0)
        next_sync = 0
        while self.running:
            if time.monotonic() >= next_sync:
                self.clock_sync()
                next_sync = time.monotonic() + CLOCK_SYNC_INTERVAL
            try:
                bye, _ = self.ctrl.recvfrom(1024)
                if bye[:4] == b"\xff\xffBY":
                    self.events.put(("status", "Tablet ended the session, reconnecting …"))
                    return
            except (BlockingIOError, socket.timeout, OSError):
                pass
            try:
                pkt, _ = self.data.recvfrom(1024)
            except (socket.timeout, OSError):
                continue
            if pkt[:4] == b"\xff\xffBY":
                self.events.put(("status", "Tablet ended the session, reconnecting …"))
                return
            if len(pkt) > 12 and pkt[0] & 0xC0 == 0x80:
                for channel, cc, value in parse_midi_list(pkt[12:]):
                    self.events.put(("cc", channel, cc, value))

    def close(self):
        self.running = False
        try:
            self.ctrl.sendto(self.session_packet(0x4259), (self.host, self.port))
        except OSError:
            pass


def parse_midi_list(payload):
    """Yields (channel, cc, value) from an RTP-MIDI command section (RFC 6295)."""
    header = payload[0]
    if header & 0x80:
        length, pos = ((header & 0x0F) << 8) | payload[1], 2
    else:
        length, pos = header & 0x0F, 1
    if header & 0x20:
        while pos < len(payload) and payload[pos] & 0x80:
            pos += 1
        pos += 1
    end, status, first = min(pos + length, len(payload)), 0, True
    while pos < end:
        if not first:
            while pos < end and payload[pos] & 0x80:
                pos += 1
            pos += 1
        first = False
        if pos < end and payload[pos] & 0x80:
            status, pos = payload[pos], pos + 1
        if status & 0xF0 == 0xB0 and pos + 1 < end:
            yield (status & 0x0F) + 1, payload[pos], payload[pos + 1]
            pos += 2
        else:
            break


class MonitorWindow:
    BG, TRACK, FILL, TEXT, DIM = "#16181d", "#2a2e36", "#ffa726", "#eceff4", "#b0b6c2"

    def __init__(self, root, client, events):
        self.root, self.client, self.events = root, client, events
        self.values = {}
        root.title("Faderling – Fader Monitor")
        root.configure(bg=self.BG)
        root.geometry("900x420")
        self.status = tk.Label(root, text="Connecting …", bg=self.BG, fg=self.DIM, anchor="w")
        self.status.pack(fill="x", padx=8, pady=4)
        self.canvas = tk.Canvas(root, bg=self.BG, highlightthickness=0)
        self.canvas.pack(fill="both", expand=True, padx=8, pady=8)
        self.canvas.bind("<Configure>", lambda e: self.draw())
        root.protocol("WM_DELETE_WINDOW", self.close)
        self.poll()

    def poll(self):
        changed = False
        try:
            while True:
                event = self.events.get_nowait()
                if event[0] == "status":
                    self.status.config(text=event[1])
                else:
                    _, channel, cc, value = event
                    self.values[(channel, cc)] = value
                    changed = True
        except queue.Empty:
            pass
        if changed:
            self.draw()
        self.root.after(15, self.poll)

    def draw(self):
        c = self.canvas
        c.delete("all")
        w, h = c.winfo_width(), c.winfo_height()
        if not self.values:
            c.create_text(w / 2, h / 2, text="Move a fader on the tablet", fill=self.DIM, font=("Segoe UI", 14))
            return
        keys = sorted(self.values)
        col = w / max(len(keys), 10)
        top, bottom = 30, h - 40
        for i, key in enumerate(keys):
            value = self.values[key]
            cx, half = col * i + col / 2, min(col * 0.3, 30)
            c.create_rectangle(cx - half, top, cx + half, bottom, fill=self.TRACK, outline="")
            y = bottom - (bottom - top) * value / 127
            c.create_rectangle(cx - half, y, cx + half, bottom, fill=self.FILL, outline="")
            c.create_text(cx, top - 14, text=str(value), fill=self.TEXT, font=("Segoe UI", 12, "bold"))
            c.create_text(cx, bottom + 12, text=f"CC {key[1]}", fill=self.TEXT, font=("Segoe UI", 10))
            c.create_text(cx, bottom + 28, text=f"ch {key[0]}", fill=self.DIM, font=("Segoe UI", 9))

    def close(self):
        self.client.close()
        self.root.destroy()


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    host = sys.argv[1]
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 5004
    events = queue.Queue()
    client = RtpMidiClient(host, port, events)
    root = tk.Tk()
    MonitorWindow(root, client, events)
    client.start()
    root.mainloop()


if __name__ == "__main__":
    main()
