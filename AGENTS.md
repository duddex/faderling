# Notes for coding agents

Faderling is an Android app that turns tablets and phones into a touch MIDI controller sending MIDI over Wi-Fi (RTP-MIDI/AppleMIDI). Start with these files:

- [README.md](README.md) – features, build, connecting, tools
- [GLOSSARY.md](GLOSSARY.md) – domain language; use these terms in code, docs and conversation
- [docs/adr](docs/adr) – architecture decisions
- [CHANGELOG.md](CHANGELOG.md) – released versions
- Open work: GitHub issues (`gh issue list -R duddex/faderling`)

## Constraints

- **minSdk 22** (Amazon Fire 7, Android 5.1) and targetSdk 36. Because of that: plain Android Views, **no AndroidX, no Compose**, own RTP-MIDI implementation (ADR 0001).
- All five Pages (Faders, Buttons, Keyboard, Pads, LFOs) must work on tablets **and** phones. Below 450 dp available height the compact layout is used (`ScreenLayout`).
- Faderling only runs while visible: no background service. The Session and the LFOs stop when the app leaves the screen.
- Send-only: the app never receives MIDI from the DAW.

## Code

- Kotlin in `app/src/main/java/com/duddex/faderling/`; one custom View per Control type (`FaderView`, `ButtonView`, `KeyboardView`, `PadGridView`, `LfoStripView`), state and settings in plain Kotlin objects (`Keyboard`, `Pads`, `Lfos`, `AppleMidi`).
- Keep logic that can be tested without Android in those plain objects and cover it with JUnit tests in `app/src/test/`.
- Settings are stored in SharedPreferences via `AssignmentStore`; keep existing keys compatible.
- Match the existing style; comments only where behaviour is not obvious.

## Build and check

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

- Lint must not report new findings (`OldTargetApi` and `GradleDependency` are known, see the issue about API 37).
- Debug builds install as **Faderling Debug** (`com.duddex.faderling.debug`) next to the release app.
- CI (GitHub Actions) builds, tests and lints every push; `main` is protected and needs the CI check for pull requests.

## Testing on devices

- Devices are connected via wireless ADB; find them with `adb devices -l` / `adb mdns services` (IPs and IDs change – do not hard-code them in files).
- `tools/rtpmidi_probe.py <device-ip>` connects as an RTP-MIDI receiver and prints every CC and note – use it to verify what a tap sends. `tools/latency_probe.py` measures network latency.
- `adb shell input` cannot do multi-touch; such cases are listed in an issue for manual testing.
- Before changing app settings on a device for a test (e.g. via `run-as … shared_prefs`), back them up and restore them afterwards – the devices hold the owner's real settings.
- Installing or restarting the app ends the owner's rtpMIDI session; mention it.

## README screenshots

- Taken on the Fire 7 (1024×600) with a probe session named "Studio PC" (copy `tools/rtpmidi_probe.py`, change the name). Rotate the raw `screencap` to landscape (the direction depends on how the tablet is held), crop the system status bar (top 25 px) and navigation bar (bottom 48 px).
- The status line must only show "Session: Studio PC" – never the owner's computer name. If the owner's rtpMIDI session is connected, copy the clean status area from an existing screenshot.
- Check every image before committing.

## Documentation

- Update `GLOSSARY.md` when a term is introduced or changed (format in `.opencode/skills/domain-modeling/`); record an ADR only for decisions that are hard to reverse, surprising and a real trade-off.
- Keep README, screenshots and CHANGELOG in sync with features.
- Keep personal data out of the repo: no computer names, IP addresses or owner names in docs or screenshots.

## Working with the owner

- For new features, first ask the open design questions as a numbered list, each with a recommended answer ("yes" accepts it); wait for answers before building.
- Show proposed README or other prose text for review before committing it.
- Commit after each larger work package with a descriptive message, push, and check that CI passes.
- Report honestly what was tested and what was not.

## Releases

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts`, add a `CHANGELOG.md` entry.
2. `.\gradlew.bat assembleRelease` – signed with the key from `~/.android/faderling-release.properties` (not in the repo; ask the owner for it). Without the file the APK is unsigned.
3. Test the release APK on the devices. Installing over a debug-signed `com.duddex.faderling` requires uninstalling it first, which deletes its settings – back them up and ask first.
4. Tag `vX.Y.Z`, push the tag, create a GitHub Release with `faderling-X.Y.Z.apk` attached.
