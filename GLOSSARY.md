# Faderling

A tablet app that turns the screen into pages of controls, a keyboard, drum pads, LFOs and XY Pads that send MIDI messages over the network.

## Language

### Controls

**Control**:
Any on-screen element that sends a single MIDI CC: a Fader or a Button.
_Avoid_: Widget, element

**Fader**:
A vertical Control whose position is sent as a CC value.
_Avoid_: Slider

**Fader Max**:
The value that a touch on, or a slide along, a Fader's value row sets it to: 0–127, default 100. Dragging the Fader itself still reaches 127. The Faders settings call it *Max*.
_Avoid_: Limit, cap, ceiling

**Button**:
A Control that is either on or off and sends a CC value for each state.
_Avoid_: Switch, trigger

**Momentary**:
Button mode in which the Button is on only while it is held.
_Avoid_: Push, trigger

**Latch**:
Button mode in which each press flips the Button between on and off.
_Avoid_: Toggle

**Assignment**:
The settings of one Control: its name, CC number and MIDI channel (and, for a Button, its mode).
_Avoid_: Mapping, binding, config

### Layout

**Page**:
One screen of the app; there is a Faders Page, a Buttons Page, a Keyboard Page, a Pads Page, an LFOs Page and an XY Page.
_Avoid_: Tab, screen, view

### Keyboard

**Keyboard**:
A piano-style set of Keys starting on a C that sends MIDI notes: two octaves (25 Keys), or one and a half octaves (18 Keys, C to F) on low screens such as phones. It is not a Control.
_Avoid_: Piano, keys

**Key**:
One white or black key of the Keyboard; pressing it sends Note On, releasing it sends Note Off.
_Avoid_: Note

### Pads

**Pad**:
One of 16 drum pads in an MPC-style 4×4 grid; each touch sends Note On for its note, lifting the last finger sends Note Off. It is not a Control.
_Avoid_: Drum, trigger, button

**Pad Assignment**:
The settings of one Pad: its name and note. Channel and velocity are shared by all Pads.
_Avoid_: Pad mapping, drum map

### LFOs

**LFO**:
One of 8 low-frequency oscillators that moves a CC value continuously up and down along a sine between its Min and Max, taking its Speed for one full cycle. It is not a Control; it may target the same CC as a Control on purpose.
_Avoid_: Modulator, automation, oscillator

**Speed**:
The time an LFO takes for one full cycle (up and down), in seconds.
_Avoid_: Rate, frequency

### XY Pads

**XY Pad**:
A square touch area that sends two CC values at once: X from left (0) to right (127) and Y from bottom (0) to top (127). There are two on the XY Page. It is not a Control and not a Pad; always write "XY Pad" in full.
_Avoid_: Pad (alone), touchpad, Kaoss pad, trackpad

**XY Assignment**:
The settings of one XY Pad: its name, the CC for X, the CC for Y and the MIDI channel. It may use the same CCs as Controls or LFOs on purpose.
_Avoid_: XY mapping

### Connection

**Session**:
A live RTP-MIDI connection between the tablet and a receiver on the network, through which MIDI messages are sent.
_Avoid_: Connection, link, pairing
