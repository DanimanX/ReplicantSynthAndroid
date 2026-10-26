# Factory presets

The preset browser is available from **SOUND → BROWSE**, from the clip editor,
and it applies to the selected clip. Loading a preset replaces the engine
parameters and pattern; the pad's ReplicantFX insert, its six macros, gain,
pan, mute and solo stay exactly as they were.

## Replicant303 — 512 lines

Port of the Replicant303 preset bank: **8 banks × 64 slots**.

- Banks: `AMBER`, `SUB`, `NEON`, `ACID`, `BLADE`, `ROOFTOP`, `OFF WORLD`, `LAST`.
- Slot 1 of each bank is a named factory line (`Amber Line`, `Sub Level`, …);
  slots 2–64 use the same deterministic SplitMix32 generator, scales,
  archetypes and name tables as the desktop build.
- Each preset carries the full acid line (gate, note, accent, slide, length)
  and the voice: waveform, cutoff, resonance, env mod, decay, accent and the
  five-model distortion.
- The generator is reproduced in `AcidPresets`; a unit test compares bank 3,
  slot 17 against values dumped from the desktop engine.

The desktop preset also stores tempo, swing and tuning. Tempo is global in the
clip launcher and the clip has no tuning control, so those values are consumed
by the generator for parity but not applied.

## Replicant909 — 8 patterns

Port of `PresetBank909`: `Classic House`, `Detroit Techno`, `Acid Jack`,
`Hard Groove`, `Electro Funk`, `Industrial Rave`, `Minimal Techno`,
`Synthwave 909`.

Each preset sets the 11-voice step mask, per-step velocity (normal / accent /
flam approximated) and the total-accent amount. Voice tune/decay/snappy/tone
stay at their init values, exactly like the desktop bank.

## ReplicantWave — 44 sounds

Port of the ReplicantWave factory bank (16 core + 22 NIGHT CITY + 6 Midtempo
Cyberpunk). The Android wavetable engine has one oscillator plus sub and noise,
so the mapping is:

- `osc1_table`, position, unison, detune, spread → wavetable oscillator
- `filter_*` → cutoff, resonance, drive and filter mode
- `amp_*` → ADSR
- `sub_*`, `noise_*` → sub and noise levels
- the first bar of the desktop sequencer → piano-roll MIDI notes
- desktop osc 2, chorus, delay and reverb → the pad's ReplicantFX insert
  (choose the matching SP-404MKII slot manually)

Presets with no sequence (pads, drones) load a sustained root note so the clip
auditions immediately; play the APC64 or use the piano roll to turn them into a
part.

Source files: `model/Presets.kt` (303/909), `model/WavePresets.kt` (Wave,
generated from `ReplicatWave/src/presets/factory.rs`).
