# Faderling

A tablet app that turns the screen into a bank of faders that send MIDI Control Change messages over the network.

## Language

**Fader**:
One on-screen vertical control whose position is sent as a single MIDI CC value.
_Avoid_: Slider, knob, control

**Assignment**:
The settings of one Fader: its name, CC number and MIDI channel.
_Avoid_: Mapping, binding, config

**Session**:
A live RTP-MIDI connection between the tablet and a receiver on the network, through which Fader moves are sent.
_Avoid_: Connection, link, pairing
