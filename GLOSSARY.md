# Faderling

A tablet app that turns the screen into pages of controls that send MIDI Control Change messages over the network.

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
One screen of Controls; the app has a Faders Page and a Knobs & Buttons Page.
_Avoid_: Tab, screen, view

### Connection

**Session**:
A live RTP-MIDI connection between the tablet and a receiver on the network, through which Control changes are sent.
_Avoid_: Connection, link, pairing
