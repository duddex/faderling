# Changelog

All notable changes to Faderling are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- Faderling runs fullscreen: status and navigation bar are hidden while a Page is shown, for more room for the Controls (e.g. 73 px more height on a Fire 7). Swipe from the screen edge to show them for a moment; the settings screen keeps them.

### Fixed

- Colours are no longer darkened on phones whose dark mode also darkens apps (e.g. Xiaomi MIUI); the orange highlights and white fader caps looked brown and grey.

## [1.1.0] – 2026-10-24

### Added

- **X/Y** Page with 2 XY Pads, each sending two CCs at once (X left→right, Y bottom→top; defaults CC 85/86 and 87/88). The dot glides to the touched spot like a fader and stays where you let go; both XY Pads can be played at the same time. Name, CCs and channel are set in the new X/Y settings tab.

### Changed

- A long Session status in the top bar is shortened with "…" instead of pushing the settings button aside.
- README: one section per Page with its screenshot and description.

## [1.0.0] – 2026-10-20

First release.

### Added

- **Faders** Page: 10 vertical faders (CC 20–29) that glide to the touched position instead of jumping.
- **Buttons** Page: 8 big buttons (CC 110–117), each Momentary or Latch; Latch buttons show an LED.
- **Keyboard** Page: 25 keys (two octaves, C2–C4) with octave shift, chords and glissando; one octave on phones.
- **Pads** Page: 16 MPC-style drum pads (notes 36–51 on channel 10), laid out like an Ableton Drum Rack, with retrigger for two-finger rolls.
- **LFOs** Page: 8 sine LFOs (CC 102–109) with Min, Max and Speed (0.2–120 s) set directly on the Page.
- Settings with one tab per Page: name, CC/note, channel, Button mode, velocity; duplicate CC/channel warnings across Pages.
- RTP-MIDI (AppleMIDI) over Wi-Fi with Bonjour discovery; the tablet appears as "Faderling" plus its model name, e.g. in rtpMIDI on Windows or Audio MIDI Setup on macOS.
- Every Control re-sends its final value and every Note Off is sent twice, to make up for lost Wi-Fi packets.
- Runs on Android 5.1 (API 22) and newer, on tablets and phones; compact layout for low screens.
- Tools: `fader_monitor.py`, `rtpmidi_probe.py` and `latency_probe.py`; a VCV Rack demo patch.

[1.1.0]: https://github.com/duddex/faderling/releases/tag/v1.1.0
[1.0.0]: https://github.com/duddex/faderling/releases/tag/v1.0.0
