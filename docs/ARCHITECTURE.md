# Architecture

ReplicantSynth is a single-process Android application with a strict split
between UI state, control messages, and the real-time audio domain.

## Layers

```
┌─────────────────────────────────────────────────────────────┐
│ Jetpack Compose                                              │
│  MainViewModel · PadDeck · Inspector · Sheets                │
│  Compose state, project JSON, SAF import, MIDI events        │
└───────────────▲──────────────────────────────┬──────────────┘
                │ atomics / snapshots          │ JNI commands
┌───────────────┴──────────────────────────────▼──────────────┐
│ JNI bridge (jni_bridge.cpp)                                  │
│  MPSC command queue · atomic telemetry · sample publication  │
└───────────────▲──────────────────────────────┬──────────────┘
                │ audio callback               │ writer thread
┌───────────────┴──────────────────────────────▼──────────────┐
│ ClipEngine (C++20)                                           │
│  transport · 64 clips · 4 engines · 48 FX · 2 recorders      │
└───────────────▲─────────────────────────────────────────────┘
                │ float stereo, 48 kHz
┌───────────────┴─────────────────────────────────────────────┐
│ Oboe → AAudio exclusive → AAudio shared → OpenSL ES          │
└─────────────────────────────────────────────────────────────┘
```

## Threading

**UI thread.** Owns `ProjectState` (Compose snapshot state), the selected pad,
dialogs, SAF import and recording file names. It never reads or writes DSP
memory directly.

**MIDI binder thread.** Android's `MidiReceiver.onSend` feeds an incremental
parser; parsed pad/scene/transport events are posted to the UI thread.

**Audio callback.** Owns all DSP state: voices, envelopes, filters, FX delay
lines, clip phases, transport counters and meters. It performs no allocation,
no locking, no file I/O and no JNI upcalls.

**Command queue.** Clip configuration, parameter changes, steps and launch
requests are copied into a fixed `Command` ring (16,384 entries). Multiple
producers (UI and MIDI) are serialised by a small mutex on push; the audio
thread pops without locking. A full queue drops the newest command rather than
blocking the callback.

**Telemetry.** Pad states (kind, state, phase, FX type, mute/solo), pad meters,
master meter and scope are atomics written by the audio thread and polled at
20 Hz.

**Samples.** The UI decodes a file to interleaved floats, constructs an
immutable `SampleData`, publishes it with an atomic pointer and keeps the
`shared_ptr` alive for the engine lifetime. The audio thread only reads the
pointer. A sample can be replaced without freeing memory under the callback.

**Capture.** Two `Recorder` instances (master and one stem) each own a
single-producer ring and a writer thread. The audio thread pushes stereo
frames; the writer converts to 24-bit PCM and streams to a WAV file. Offline
rendering uses a blocking push so a fast host renderer cannot outrun the disk.

## Clip engine

### Grid

64 pads are independent clip slots, mapped row-major from the top-left. Each
slot stores its engine kind, root note, 16-step pattern, drum masks/velocities,
synth parameters, FX type and six FX macros, mixer gain/pan/mute/solo.

### Timing

- 16th-note grid derived from BPM: `samplesPerStep = sampleRate * 60 / bpm / 4`.
- Swing shortens even steps and lengthens odd steps by ±swing/2.
- Launch quantization is OFF, 1/16, 1/4, 1 bar (16 steps), 2 bars or 4 bars.
  A launch schedules `pendingStart` at the next boundary; a second press
  schedules `pendingStop`. The UI sees `QueuedStart` / `QueuedStop`.
- Scenes launch every pad in a row and stop clips outside that row, matching a
  clip-launcher scene. Long-pressing a scene rail button stops the row.
- Transport stop is a hard stop; per-clip stop leaves a 0.75 s FX/release tail.

### Engines

| Kind | Implementation | Notes |
| --- | --- | --- |
| 303 | `core/Acid303.*` + `core/acid/*` | Port of the Replicant303 C++ voice: polyBLEP oscillator, diode ladder, capacitor slide, accent, five distortions. |
| Wave | `core/Wavetable.*` | 8 banks × 16 frames × 7 mip levels, shared read-only tables, 6-voice poly per pad, up to 8 unison per voice, ZDF SVF, sub/noise, ADSR. |
| 909 | `core/DrumKit909.*` + `core/drums909/*` | Replicant909 voice models; 11 voices per clip, 6-bit PCM cymbal ROM generated procedurally, CH→OH choke. |
| Sample | `ClipEngine::renderChunk` | Stereo float sample, linear interpolation, source-rate ratio, pitch, loop or one-shot. |

### FX

`core/Effects.*` ports the ReplicantFX `Sp404BusProcessor` match arms for all
48 SP-404MKII slots: Filter+Drive, Isolator, Crusher, Phaser, Chorus/Flanger/
JUNO, drives, seven delay variants, reverbs, resonator, DJFX looper family,
tremolo/slicer, wah/super filter, EQ, vinyl/cassette, ring/pitch/sub family and
compressor. Parameters are smoothed, and each clip owns an independent delay
line and comb bank.

Memory is lazy: an `Effect` (two 72,000-sample delay lines) is allocated only
when a pad selects a non-zero FX slot.

### Mixing and metering

Post-FX pad signal → gain/balance → mute/solo → master sum → master gain →
`tanh` guard. Per-pad peak and master peak are published once per block.

## UI

- `PadDeck` uses `BoxWithConstraints` to fit a square 8 × 8 grid next to an
  8-button scene rail. Pad cells show number, engine, FX dot, mute/solo,
  meter and step phase; queued pads blink.
- `InspectorPanel` has SOUND / FX / PATTERN / MIX pages. Knobs are custom
  Canvas controls with vertical drag, 270° arcs and double-tap reset.
- The phone layout stacks the grid over a 334 dp inspector; widths above
  760 dp place the inspector beside the grid.
- Sheets: FX browser (48 effects with search), MIDI/APC64, recordings,
  project menu and transport.

## Persistence

`ProjectStore` writes `filesDir/project.json` atomically (`.tmp` + rename).
Sample paths are stored relative to `filesDir/samples` when possible; decode
happens on load, so a project restores after a process restart. Recordings are
24-bit WAVs in `filesDir/Recordings`.

## Tests

- `host/engine_tests.cpp` compiles the real engine without Android/Oboe and
  runs 36,103 assertions: table ranges, every engine, all 48 FX with defaults,
  quantized start, scene transitions, sample playback, capture header/frames,
  44.1 kHz re-prepare and peak bounds.
- JVM tests cover WAV parsing, project JSON, APC64 mapping/LED palette/SysEx,
  MIDI running status and fragmented SysEx.
- Emulator screenshots are captured by `tools/capture_screenshots.py`; the
  emulator is a UI/behaviour check, not a latency measurement.
