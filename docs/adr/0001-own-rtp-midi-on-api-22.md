# Own RTP-MIDI implementation and plain Views for API 22

The target tablet (Fire 7, 2017) runs Android 5.1 (API 22), which has no Android MIDI API (added in API 23), and current Jetpack Compose and AndroidX releases require API 23 or higher. We therefore implement the RTP-MIDI session protocol ourselves on top of UDP sockets and Android's NSD (Bonjour), and build the UI with plain Android Views without AndroidX.

## Consequences

- Raising minSdk to 23+ later would allow AndroidX/Compose, but the RTP-MIDI implementation would still be needed: Android's MIDI API has no network transport.
- No RTP-MIDI recovery journal is sent; instead each Control's final value is re-sent shortly after release to mitigate lost UDP packets.
