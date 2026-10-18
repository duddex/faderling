# Faderling

A tablet app that turns the screen into pages of controls and a keyboard that send MIDI messages over the network.

## Language

### Controls

**Control**:
Any on-screen element that sends a single MIDI CC: a Fader, a Knob or a Button.
_Avoid_: Widget, element

**Fader**:
A vertical Control whose position is sent as a CC value.
_Avoid_: Slider

**Knob**:
A rotary Control whose position is sent as a CC value, turned by dragging vertically.
_Avoid_: Encoder, pot, dial

**Button**:
A Control that is either on or off and sends a CC value for each state.
_Avoid_: Pad, switch, trigger

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
One screen of the app; there is a Faders Page, a Knobs & Buttons Page and a Keyboard Page.
_Avoid_: Tab, screen, view

### Keyboard

**Keyboard**:
A piano-style set of 25 Keys spanning two octaves that sends MIDI notes; it is not a Control.
_Avoid_: Piano, keys

**Key**:
One white or black key of the Keyboard; pressing it sends Note On, releasing it sends Note Off.
_Avoid_: Note, pad

### Connection

**Session**:
A live RTP-MIDI connection between the tablet and a receiver on the network, through which MIDI messages are sent.
_Avoid_: Connection, link, pairing
