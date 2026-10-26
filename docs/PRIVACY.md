# Privacy

ReplicantSynth is an offline instrument.

- **No network access.** The app declares no `INTERNET` permission and makes no
  network requests. There is no analytics, telemetry, ads or account.
- **Local files only.** Projects, samples and recordings are stored in the
  app's private storage (`filesDir`). Imported audio is copied into
  `filesDir/samples`; recordings go to `filesDir/Recordings`. Nothing is
  uploaded.
- **Audio input.** The engine opens an output stream only. It does not request
  microphone access.
- **USB MIDI.** The app uses Android's `MidiManager` to talk to a connected USB
  MIDI controller. It reads pad/encoder messages and writes LEDs/SysEx to that
  device. No MIDI data leaves the device.
- **Notifications.** A foreground-service notification keeps the audio engine
  alive while the screen is off. The notification can be disabled in Android
  settings; the service may then be subject to background limits.
- **Sharing.** Recordings leave the app only when you explicitly tap SHARE and
  choose a target app. The share uses a `FileProvider` URI scoped to the single
  WAV file.

Removing the app removes its projects, samples and recordings from app storage.
