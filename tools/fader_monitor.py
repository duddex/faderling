"""Fader monitor: shows every Control Change arriving on a MIDI input port as a fader.

Pick the rtpMIDI session port (e.g. "X390") to see what Faderling sends through rtpMIDI.
Windows only (uses the system MIDI API via ctypes); no extra packages needed.

Usage:
  python tools/fader_monitor.py            # choose the input port in the window
  python tools/fader_monitor.py X390       # open the first port whose name contains "X390"
  python tools/fader_monitor.py --list     # list MIDI input ports
"""
import ctypes
import queue
import sys
import tkinter as tk
from ctypes import wintypes

winmm = ctypes.WinDLL("winmm")

CALLBACK_FUNCTION = 0x30000
MIM_DATA = 0x3C3
MIDI_IN_PROC = ctypes.WINFUNCTYPE(None, wintypes.HANDLE, wintypes.UINT, ctypes.c_size_t, ctypes.c_size_t, ctypes.c_size_t)


class MIDIINCAPSW(ctypes.Structure):
    _fields_ = [
        ("wMid", wintypes.WORD),
        ("wPid", wintypes.WORD),
        ("vDriverVersion", wintypes.UINT),
        ("szPname", wintypes.WCHAR * 32),
        ("dwSupport", wintypes.DWORD),
    ]


winmm.midiInOpen.argtypes = [ctypes.POINTER(wintypes.HANDLE), wintypes.UINT, ctypes.c_void_p, ctypes.c_size_t, wintypes.DWORD]
winmm.midiInGetDevCapsW.argtypes = [ctypes.c_size_t, ctypes.POINTER(MIDIINCAPSW), wintypes.UINT]
for fn in (winmm.midiInStart, winmm.midiInStop, winmm.midiInReset, winmm.midiInClose):
    fn.argtypes = [wintypes.HANDLE]


def input_ports():
    names = []
    for i in range(winmm.midiInGetNumDevs()):
        caps = MIDIINCAPSW()
        winmm.midiInGetDevCapsW(i, ctypes.byref(caps), ctypes.sizeof(caps))
        names.append(caps.szPname)
    return names


class MidiInput:
    """One open MIDI input port; puts (channel, cc, value) for every Control Change into a queue."""

    def __init__(self, index, events):
        self.events = events
        self.handle = wintypes.HANDLE()
        self.callback = MIDI_IN_PROC(self.on_message)
        result = winmm.midiInOpen(ctypes.byref(self.handle), index, ctypes.cast(self.callback, ctypes.c_void_p), 0, CALLBACK_FUNCTION)
        if result != 0:
            raise OSError(f"midiInOpen failed with error {result} (port in use by another app?)")
        winmm.midiInStart(self.handle)

    def on_message(self, handle, msg, instance, param1, param2):
        if msg != MIM_DATA:
            return
        status, data1, data2 = param1 & 0xFF, (param1 >> 8) & 0x7F, (param1 >> 16) & 0x7F
        if status & 0xF0 == 0xB0:
            self.events.put(((status & 0x0F) + 1, data1, data2))

    def close(self):
        winmm.midiInStop(self.handle)
        winmm.midiInReset(self.handle)
        winmm.midiInClose(self.handle)


class MonitorWindow:
    BG, TRACK, FILL, TEXT, DIM = "#16181d", "#2a2e36", "#ffa726", "#eceff4", "#b0b6c2"

    def __init__(self, root, initial_port):
        self.root = root
        self.events = queue.Queue()
        self.values = {}
        self.midi_in = None
        self.ports = input_ports()

        root.title("Faderling – Fader Monitor")
        root.configure(bg=self.BG)
        root.geometry("900x420")

        bar = tk.Frame(root, bg=self.BG)
        bar.pack(fill="x", padx=8, pady=4)
        tk.Label(bar, text="MIDI input:", bg=self.BG, fg=self.DIM).pack(side="left")
        self.port_var = tk.StringVar(value="")
        menu = tk.OptionMenu(bar, self.port_var, *(self.ports or ["(no MIDI inputs)"]), command=self.open_port)
        menu.config(bg=self.TRACK, fg=self.TEXT, activebackground=self.TRACK, highlightthickness=0, relief="flat")
        menu.pack(side="left", padx=6)
        tk.Button(bar, text="Clear", command=self.clear, bg=self.TRACK, fg=self.TEXT, relief="flat").pack(side="left")
        self.status = tk.Label(bar, text="", bg=self.BG, fg=self.DIM, anchor="w")
        self.status.pack(side="left", padx=8)

        self.canvas = tk.Canvas(root, bg=self.BG, highlightthickness=0)
        self.canvas.pack(fill="both", expand=True, padx=8, pady=8)
        self.canvas.bind("<Configure>", lambda e: self.draw())
        root.protocol("WM_DELETE_WINDOW", self.close)

        match = next((p for p in self.ports if initial_port and initial_port.lower() in p.lower()), None)
        if match:
            self.open_port(match)
        else:
            self.status.config(text="Select the rtpMIDI session port" if self.ports else "No MIDI input ports found")
        self.poll()

    def open_port(self, name):
        if name not in self.ports:
            return
        if self.midi_in:
            self.midi_in.close()
            self.midi_in = None
        self.port_var.set(name)
        try:
            self.midi_in = MidiInput(self.ports.index(name), self.events)
            self.status.config(text=f"Listening on {name}")
        except OSError as e:
            self.status.config(text=str(e))

    def clear(self):
        self.values.clear()
        self.draw()

    def poll(self):
        changed = False
        try:
            while True:
                channel, cc, value = self.events.get_nowait()
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
        if self.midi_in:
            self.midi_in.close()
        self.root.destroy()


def main():
    if "--list" in sys.argv:
        for i, name in enumerate(input_ports()):
            print(f"{i}: {name}")
        return
    root = tk.Tk()
    MonitorWindow(root, sys.argv[1] if len(sys.argv) > 1 else None)
    root.mainloop()


if __name__ == "__main__":
    main()
