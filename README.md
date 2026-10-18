# Faderling

An Android app that turns a tablet into pages of MIDI controls – 10 faders, 8 knobs and 8 buttons – sending MIDI Control Change (CC) messages over Wi-Fi via RTP-MIDI (AppleMIDI). Your computer sees the tablet as a regular MIDI input, e.g. in Ableton Live.

## Features

- Two Pages, switched with the tabs in the top bar:
  - **Faders**: 10 vertical faders that glide to the touched position instead of jumping
  - **Knobs & Buttons**: two rows of 4 knobs (drag up/down, relative, no jumps) above a row of 8 square buttons
- Buttons are **Momentary** (on while held) or **Latch** (each press flips on/off); they send 127 for on, 0 for off
- 7-bit CC (0–127), send-only, landscape
- Every Control re-sends its final value shortly after release, to make up for lost Wi-Fi packets
- Per-Control Assignment (name, CC number, MIDI channel, and mode for buttons), edited on the settings screen (gear icon); duplicate CC/channel combinations are flagged
- Default CCs: faders 20–29, knobs 102–109, buttons 110–117, all on channel 1
- Advertises itself as **Faderling** via Bonjour; the receiver starts the Session
- Session status shown on the main screen
- Runs on Android 5.1 (API 22) and newer

## Build and install

Requires the Android SDK (`ANDROID_HOME` or `local.properties` with `sdk.dir`) and a JDK 17+.

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Connect on Windows

1. Install [rtpMIDI](https://www.tobias-erichsen.de/software/rtpmidi.html) by Tobias Erichsen.
2. Start Faderling on the tablet (same Wi-Fi network as the PC).
3. In rtpMIDI, create a session under *My Sessions* (**+**) and enable it.
4. Select **Faderling** in the *Directory* list and click **Connect**. The tablet shows `Session: <PC name>`.
5. In your DAW, enable the rtpMIDI session port as a MIDI input.

On macOS, use *Audio MIDI Setup → MIDI Network Setup* instead of rtpMIDI.

When the app is closed or restarted the Session ends; click **Connect** in rtpMIDI again.

## Tools

Python 3, standard library only:

- `tools/fader_monitor.py` – shows every CC arriving on a MIDI input port (e.g. the rtpMIDI session port) as a fader. Windows only.
  ```powershell
  python tools\fader_monitor.py --list
  python tools\fader_monitor.py "My Session"
  ```
- `tools/rtpmidi_probe.py` – connects to the tablet directly as an RTP-MIDI initiator and prints received CC messages; useful without rtpMIDI.
  ```powershell
  python tools\rtpmidi_probe.py <tablet-ip>
  ```

## Documentation

- [GLOSSARY.md](GLOSSARY.md) – domain language (Control, Fader, Knob, Button, Page, Assignment, Session)
- [docs/adr](docs/adr) – architecture decisions

## License

[MIT](LICENSE)
