# Changelog

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
