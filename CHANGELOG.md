# Changelog

## 0.0.4 — 2026-10-27

### APC64 touch strips and Step Sequencer
- The eight touch strips now light their LED bars. Each value is mirrored back as
  a pitch bend on the strip's channel, with the bar style on CC 104–111 and the
  bar colour on CC 112–119, so the LED follows the finger and any other change
  (knob, encoder, preset). Echoed frames from a merged USB port are ignored.
- Strip assignment is adaptive: strip 1 is the master, strip 8 the pad gain, and
  strips 2–7 are the six continuous fields of the focused editor page — the six
  engine knobs on the new **SOUND** page, the six macros on **FX**, the step
  fields on **STEPS**, the roll fields on **PIANO**. Pages with fewer fields
  leave the spare strips dark.
- Touching a strip (notes 82–89) focuses its field and labels it on the OLED; the
  editor draws the same eight bars with their parameter names.
- New **SOUND** menu page per engine, reachable from the cursor cluster and the
  encoder.
- The APC64's **STEP SEQ** view now feeds the selected clip: its pads arrive on
  the Notes cable and become step edits — an isolated press toggles the cursor
  step, a run of notes advances a 16th clock seeded on that cursor, and a clip the
  app is playing takes the incoming notes on the engine phase. 909 maps pitch to voices from a configurable drum
  base note; 303 and Wave take the pitch as the note.
- The step grids show a live playhead and the incoming step, so the pattern is
  visible in real time on screen while the unit runs.

### APC64 cursor-cluster menus
- Pressing the same session pad twice inside 400 ms opens its clip editor, so
  the menus are reachable without touching the screen (a single press still
  launches/stops the clip).
- The four-way cursor cluster (notes 94–97) now walks the clip editor from the
  hardware: up/down change menu, left/right change field, shift jumps to the
  first/last field or to the next menu.
- The encoder edits the focused field and the encoder press (note 90) activates
  it — launch the clip, load a factory preset, randomise or clear a pattern,
  toggle gate/accent/slide/hit, or open the FX browser. Outside the editor the
  encoder still nudges the BPM and the cursors move the 8 × 8 pad selection.
- Editor pages per engine: CLIP (launch, length, root, gain, pan, mute, solo,
  loop and pitch), STEPS (303/909 step, note/voice, gate/hit, accent, slide,
  velocity), PIANO (Wave cursor, pitch row, note length, velocity, octave, chord),
  FX (insert, 48-slot list, six macros) and PRESET (factory banks, applied only on
  the encoder press).
- The focused page, field and value are mirrored in the editor dialog and on the
  APC64 OLED; the cursor LEDs light amber (up/down) and blue (left/right) while a
  clip editor is open.
- The pattern step and drum voice cursors are now shared between the touch UI and
  the controller, so both always edit the same step.
- The editor pages list the new SOUND page in the cursor ring, and the header
  version chip now reads the real build version instead of a hardcoded string.

### Verification
- 36,112 host checks, 0 failures.
- 69 JVM tests (editor menus, adaptive strips, strip LED frames, Step Seq clock,
  pad-mode/firmware/port mapping, cursor and strip notes), 0 failures.
- Emulator run: clip editors for 303, 909 and Wave, page ring, strip assignment
  on CLIP/SOUND/STEPS/FX, live playhead and the new MIDI-sheet sections.
- Not verified on hardware: strip LED brightness, strip touch sensors and Step
  Seq note capture. The MIDI frames come from the APC64 protocol spec verified on
  firmware 1.2.1.6.

## 0.0.3 — 2026-10-27

### APC64 Note/Chord routing
- The app now reads the APC64's pad-mode report instead of exposing an app-side
  **APC64 KEYS** switch. Session pads launch clips; the hardware's own **NOTE**
  and **CHORD** modes write into the Wave piano roll.
- Opens the **DAW** and **Notes** USB MIDI cables separately, resolving them by
  name with cable-number fallbacks. Note/Chord pads no longer fall through a
  merged or mis-ordered port into scene/clip launches.
- Chord Mode's simultaneous note-ons share one piano-roll step through the
  existing 60 ms group window.
- Pad and scene LEDs stop updating while the unit owns the grid in Note, Chord,
  Step, Project or Custom mode; returning to Session forces a full redraw.
- Held keys are released on mode changes and unplug, preventing stuck preview
  voices.
- The identity reply is parsed and the MIDI sheet shows the connected firmware;
  firmware older than the validated **1.2.1.6** baseline is flagged.
- Documented the firmware/Bitwig pad-mode map: 0 Session, 2 Note, 3 Chord, 6 Step,
  8 Project, 9 Custom.

### Fixes
- Note/Chord pads are no longer mistaken for scenes, transport or track-select
  when their pitches overlap control note numbers on the Notes cable.
- The Note/Chord input path stays out of session launching even if Android
  merges the USB MIDI cables.

### Verification
- 36,112 host checks, 0 failures.
- 43 JVM tests (including pad-mode reports 0/2/3/6/9, identity firmware,
  port roles and chord grouping), 0 failures.
- Debug APK builds for arm64-v8a and x86_64.

## 0.0.2 — 2026-10-26

### Replicant909
- Rebalanced the voice output stages and replaced the `tanh` on the summed kit
  with a linear bus (0.75 trim) plus a transparent peak limiter. The old bus
  compressed every transient (crest factor ≈ 1.7); a normal pattern now keeps a
  crest factor above 3 and eleven simultaneous voices stay under full scale.
- The master guard is now `softLimit(0.9)` instead of `tanh`, so a single clip
  is never pre-distorted by the safety stage.

### ReplicantWave
- Wave clips now use real MIDI notes: start step, length, pitch and velocity,
  up to 96 notes per clip, 8-voice polyphony and per-key release.
- New Canvas piano roll in the PATTERN tab and in the clip editor. Tap an empty
  cell to add a note, tap a note to delete it.
- `noteOff` releases only the voices owned by that key, so overlapping notes and
  chord stacks no longer choke each other.

### Presets
- Replicant303: the full 8 × 64 deterministic factory bank (512 lines), with the
  original banks, names, scales, archetypes and patterns.
- Replicant909: the 8 classic patterns (`Classic House` … `Synthwave 909`).
- ReplicantWave: all 44 factory sounds from the desktop bank, mapped to the
  Android oscillator, filter, ADSR, sub/noise and piano roll.
- Searchable preset browser; loading a preset keeps the pad FX insert, macros
  and mixer settings.

### APC64
- New Notes/Chord input mode: pad-range notes write into the Wave piano roll at
  the cursor, with selectable length, velocity and ±2 octave transpose.
- Simultaneous chord notes are grouped onto one step; the cursor advances only
  when a new key group starts (60 ms window).
- The clip `CHORD` selector stacks fifth / minor / major / octave intervals.

### UI
- Double-tap a session clip to open its editor: piano roll (Wave), step
  sequencer (303) or kit grid (909).
- Factory preset entry points in SOUND, PATTERN and the editor.

### Fixes
- Fixed a heap out-of-bounds read in the wavetable builder: mip level 0 asked
  for 192 harmonics while the amplitude/phase tables hold indices 0–191.
- Project JSON v2 stores MIDI notes; 16-step notes round-trip correctly.
- Note audition on a stopped clip now reaches the mixer instead of being
  skipped as inactive.

### Verification
- 36,112 host checks (including 909 headroom/crest, MIDI sustain/release,
  per-key release and stopped-clip audition), 0 failures.
- 38 JVM tests (including the 512-slot 303 parity check and all factory banks),
  0 failures.

## 0.0.1 — 2026-10-20
- First Android release: 64-pad clip launcher, 303 / Wave / 909 / sample
  engines, 48 ReplicantFX slots, APC64 profile and 24-bit capture.
