# APC64 / USB MIDI

## Connection

1. Use a USB-C OTG adapter (or a powered hub) and connect the APC64.
2. Open the **MIDI** chip in the header. The app lists USB MIDI devices,
   auto-connects the first name containing `APC64`, and opens the **DAW** and
   **Notes** USB MIDI cables separately. The DAW cable sends LEDs/SysEx and
   receives session pads and side buttons; the Notes cable carries the unit's
   own Note and Chord surfaces.
3. Android's MIDI service grants access without a runtime USB permission dialog
   on standard devices. If the list is empty, tap **REFRESH** after re-seating
   the cable.

Ports are resolved by name (`DAW`, `Notes`, `Custom`) with cable-number
fallbacks (`0` = DAW, `1` = Notes, `2` = MIDI, `3` = Custom). The MIDI sheet
shows which DAW and Notes ports were opened. If the Notes port is missing,
Note/Chord mode cannot reach the piano roll; replug the USB cable.

## Handshake

The APC64 in stock firmware shows “Please connect to Ableton Live” and blanks
the pads until it receives the Akai mode SysEx. ReplicantSynth sends the same
sequence Bitwig and Ableton use:

```
F0 47 00 53 1B 00 01 00 F7   track type = note
F0 47 00 53 19 00 01 00 F7   generic/DAW mode
F0 7E 7F 06 01 F7            universal identity request
F0 47 00 53 1C 00 01 01 F7   take display ownership
F0 47 00 53 19 00 01 00 F7   generic/DAW mode (repeat)
F0 47 00 53 10 ... F7        OLED lines 1–4
```

If the unit was previously in Ableton Live mode, disconnect/reconnect after
opening ReplicantSynth so the handshake is sent to a fresh endpoint.

### Firmware validation

The identity reply is parsed and shown in the MIDI sheet:

```
F0 7E 00 06 02 47 53 00 19 <fw0 fw1 fw2 fw3> ... F7
```

Firmware **1.2.1.6** is the validated baseline. The app prints the four bytes
as `major.minor.patch.build` and marks older firmware as needing validation.
The custom FreeAPC64 firmware reports the same version because the patches do
not change the version field.

## Pad modes

The unit reports its own pad mode on the DAW cable:

```
F0 47 00 53 19 00 01 <mode> F7
```

The IDs below are from firmware 1.2.1.6 and Bitwig's APC64 extension; they are
**not** the page indexes used by Akai's project editor. A note must be routed by
the mode reported by the unit, not by an app-side switch.

| Mode | ID | Pad grid | App action |
| --- | --- | --- | --- |
| Session | 0 | host-drawn | Pads launch/stop clips |
| Overview | 1 | host-drawn | Treated like Session |
| Note | 2 | unit-owned | Notes cable → Wave piano roll |
| Chord | 3 | unit-owned | Notes cable → chord notes in the piano roll |
| Chord settings | 4 | unit-owned | Pads stay on the unit |
| Drum | 5 | unit-owned | Pads stay on the unit |
| Step | 6 | unit-owned | Pads stay on the unit |
| Step settings | 7 | unit-owned | Pads stay on the unit |
| Project | 8 | unit-owned | Pads stay on the unit |
| Custom | 9 | unit-owned | Pads stay on the unit |

When the mode is not Session/Overview the app never maps a DAW-cable note to a
clip. On Android builds that merge the USB cables, pad-range notes that arrive
on the DAW cable in Note/Chord mode are treated as keys instead of clips. The
Notes cable is always interpreted as keys, even when a pitch collides with a
scene or transport note number.

## Pad map

- Ableton/DAW mode: notes **0–63**, note 0 is the **bottom-left** pad.
- `BASE 0 · DAW` is the default. `BASE 36 · CUSTOM` covers a custom project or
  FreeAPC64 firmware that maps the grid to C1–D#6.
- `FLIP PAD ROWS` converts the bottom-left hardware origin to the app's
  top-left row-major grid. Both settings are persisted.

The on-screen grid and the APC64 always refer to the same pad number; changing
the base/flip only changes the USB note translation.

## Note and Chord mode

Select **NOTE** or **CHORD** on the APC64. The app follows the mode report and
routes the Notes cable into the Wave piano roll. There is no app-side “APC64
KEYS” switch: Session pads keep launching clips while the hardware is in
Session, and Note/Chord pads only write notes while the hardware is in a key
mode.

1. The note lands on the amber **cursor** step with the selected length
   (`1/16`, `1/8`, `1/4`, `1/2`) and the incoming velocity.
2. Chord Mode sends several note-ons for one pad. Notes within the 60 ms group
   window share one step; the cursor advances by the note length when a new key
   group starts, so playing pads in order writes a phrase.
3. Each note is auditioned through the native engine immediately. In Chord
   Mode the hardware's notes are already the chord; the clip's `CHORD` selector
   is a separate sound-design stack and can be set to `SINGLE` if you do not
   want it added on top.
4. `OCT -` / `OCT +` transpose incoming keys by ±2 octaves.
5. If the selected pad is not a Wave clip, the key is ignored and the editor
   shows a hint. Select a Wave clip to record notes.

Scene, transport, mute/solo, clip-stop and track-select buttons keep their
normal functions while Note/Chord mode is active because the unit reports them
on the DAW cable.

## Menu navigation (cursor cluster)

The four-way cursor cluster under the pad grid walks the clip editor from the
hardware. Open a clip — double-tap it on screen, or press the same pad twice
inside 400 ms on the unit — and the cursors take over the app's menu model:

| Cursor | Action |
| --- | --- |
| Up / Down | Previous / next menu page (the pages form a ring) |
| Left / Right | Previous / next field inside the current page |
| Shift + Up/Down | Jump to the first / last field of the page |
| Shift + Left/Right | Previous / next menu page (same as Up/Down) |
| Encoder twist | Edit the focused field (steps, toggles, choices) |
| Encoder press (note 90) | Activate the focused field: launch, load preset, randomise, clear, toggle |

The pages depend on the clip's engine:

- **CLIP** — launch, length, root, gain, pan, mute, solo (plus loop and pitch for
  audio clips).
- **SOUND** — the six primary engine knobs (303: cutoff, resonance, env mod,
  decay, accent, volume; Wave: cutoff, resonance, position, detune, spread,
  level; 909: tune, decay, snappy, tone, accent, level; audio: gain, pitch).
- **STEPS** (303/909) — focused step, note or drum voice, gate/hit, accent,
  slide, velocity, randomise, clear.
- **PIANO** (Wave) — focused step, pitch row, toggle note at the cursor, note
  length, velocity, APC64 octave, chord mode, clear notes.
- **FX** — insert on/bypass, the 48-slot effect list, the six macros, FX browser.
- **PRESET** — the factory bank for this engine; twist to choose, press to load
  (the preset list is only applied on the encoder press, so auditioning cannot
  overwrite a pattern by accident).

The focused page and field are mirrored in the editor dialog and on the OLED:
page name, `n. FIELD`, the live value and the `ENC=EDIT` hint. Preset and effect
names longer than the eight-character display are truncated there.

While the editor is closed the same cursors walk the 8 × 8 grid instead: left and
right move one pad, up and down move one row. The encoder keeps nudging the BPM
until a clip editor is open.

The cursor notes (94–97) and the encoder button (90) come from the Ableton Live
APC64 remote script; they are side controls, never clip slots, so they cannot
collide with the pad grid or the scene row.

## Step Sequencer mode

The APC64's **STEP SEQ** view is a sequencer inside the unit: its 8 tracks have
their own transport and play into USB MIDI, and the unit owns the pad grid and
its LEDs there. The app cannot draw on that grid, so it reads the Notes cable
instead and treats the unit's pads as step input for the clip in the editor:

| Situation | What a pad press does |
| --- | --- |
| Isolated press | Toggles the cell on the focused step, so the joystick decides where the note goes |
| A run of notes | Advances one 16th per step time and writes each note, so a sequence keeps its rhythm |
| App playing that clip | Lands on the engine phase, so the take is exact |

A press on a cell that already holds that note removes it; otherwise the note is
written with the incoming velocity.

How the incoming note becomes a cell:

- **909** — pitch maps to the drum voice from the unit's drum-pad base note
  (MIDI sheet: `36` / `40` / `48` / `60`, default 36 = C1). Voice 0 is the
  lowest pitch.
- **303** — the pitch becomes the note of that step, gate on.
- **Wave** — the pitch is inserted as a piano-roll note of the current length.
- Audio and empty clips ignore the input; the editor shows a hint.

The step index comes from the engine phase when the app is playing that clip,
so a take lands exactly on the grid. When the unit runs on its own, a stream of
notes advances a 16th clock seeded on the cursor (from the project BPM), so a
captured sequence keeps its rhythm instead of collapsing onto one step.

`STEP SEQ TO CLIP` in the MIDI sheet turns this off. The editor's step grids
show the live playhead, so the pattern being edited is visible in real time on
screen while the unit shows its own sequence.

## LED feedback

APC64 LEDs use note-on velocity as a colour palette and the MIDI channel as
brightness:

| Channel | Meaning |
| --- | --- |
| 6 | full brightness |
| 0 | half brightness |
| 10 | pulse |
| 14 | blink |

The app uses:

| Clip state | LED |
| --- | --- |
| Empty | grey, half (white if selected) |
| Loaded / stopped | amber, half (white if selected) |
| Queued start | yellow, blink |
| Playing | kind colour, full: amber 303, blue Wave, yellow 909, green sample |
| Queued stop | red, blink |

Scenes 112–119 light green when any pad in the row plays, yellow/blink when a
clip in the row is queued. Transport notes show play/stop/record state.
The cursor cluster stays dark until a clip editor is open; then up/down light
amber and left/right blue so the two axes are readable at a glance.
A full refresh is sent every 1.2 s to recover from lost packets; only changes
are sent in between.

In Note, Chord, Step, Project and Custom modes the unit owns the pad grid and
its LEDs. The app stops sending pad/scene LED frames and restores the full
session grid when the unit reports Session again (the unit clears its own grid
on every mode change).

## Controls

| Control | Message | App action |
| --- | --- | --- |
| Pads (Session) | Notes 0–63 on the DAW cable | Launch/stop; selects the pad; a second press within 400 ms opens the clip editor |
| Pads (Note/Chord) | Notes on the Notes cable | Write keys into the Wave piano roll |
| Scene 1–8 | Notes 112–119 | Launch scene |
| Play / Stop | 91 / 93 | Transport |
| Record | 92 | Toggle master capture |
| Clip Stop | 111 | Stop all clips |
| Mute / Solo | 109 / 110 | Selected pad mute/solo |
| Shift | 120 | Reserved |
| Cursor up / down / left / right | Notes 94 / 95 / 96 / 97 | Walk the clip-editor menus |
| Encoder press | Note 90 | Activate the focused menu field |
| Strip touch | Notes 82–89 | Focus that strip's field and label it on the OLED |
| Strip LEDs | Pitch bend + CC 104–111 / 112–119 | Bar position, style and colour |
| Encoder | CC 90 | BPM ± (two's-complement relative); edits the focused field while the editor is open |
| Touch strip 1 | Pitch bend ch 1 | Master gain |
| Touch strip 8 | Pitch bend ch 8 | Selected pad gain |
| Touch strips 2–7 | Pitch bend ch 2–7 | Six fields of the focused menu (synth knobs, FX macros, step fields) |
| OLED | SysEx 0x10 / 0x1A | ReplicantSynth, BPM, quantize |

## Touch strips

Each strip is an absolute 14-bit position on its own pitch-bend channel plus a
capacitive touch sensor on notes 82–89. The app answers with the messages the
hardware expects for its LED bar:

| Direction | Message |
| --- | --- |
| Strip → host, position | pitch bend, channel 1–8, 14-bit |
| Strip → host, touch | note-on 82–89 |
| Host → strip, bar position | pitch bend, channel 1–8, 14-bit |
| Host → strip, bar style | CC 104–111: 0 off, 1 bar, 3 bipolar |
| Host → strip, bar colour | CC 112–119, palette index |

The bar works as a servo: the app mirrors every value back on the strip's own
channel, so the LED follows the finger and also lights up when a value changes
from anywhere else (a knob, the encoder, a preset). Positions the app sent are
remembered and ignored if a merged USB port echoes them, so the strip never
fights the finger.

### Adaptive assignment

The assignment follows the clip-editor menus, so the strips always edit what is
on screen:

- **Strip 1** — master gain (fixed).
- **Strip 8** — selected pad gain (fixed).
- **Strips 2–7** — the six continuous fields of the focused page: the six synth
  knobs on **SOUND**, the six macros on **FX**, step / note / velocity on
  **STEPS**, cursor / note / velocity / octave on **PIANO**. Pages with fewer
  fields (CLIP has four) leave the remaining strips dark instead of guessing.

Touching a strip focuses its field, so the editor and the OLED follow the strip
you grab. With the clip editor closed the strips stay on the CLIP page.

Touch strips are absolute 14-bit values normalised to 0–1.

## OLED

Lines are written with the Akai display message. The payload is
`[lineIndex, ASCII..., 0]`, using message id `0x10` for lines 1–4 and `0x1A`
for lines 5–8. The app writes four lines (name, engine family, BPM, quantize).

## Troubleshooting

- **Pads dark / “Connect to Ableton”**: the handshake did not reach the device.
  Reconnect, then tap **REFRESH**; make sure no DAW owns the USB MIDI port.
- **Pads trigger the wrong slot**: switch BASE 0/36 or FLIP PAD ROWS. If only
  some notes arrive, the unit is in a custom project with a different note map.
- **Note/Chord pads do nothing**: check the MIDI sheet’s **NOTES** port. If it
  says `MISSING`, the Android port resolver did not find cable 1; replug the
  USB cable. Also check that the selected pad is a Wave clip.
- **Note pads launch clips**: this is the bug fixed in 0.0.3. The hardware mode
  report should put the app in NOTE or CHORD; if it still shows SESSION, press
  SESSION and then NOTE once more and include the MIDI sheet’s monitor line.
- **Firmware warning**: the identity reply is older than 1.2.1.6. Update the
  APC64 firmware or verify the pad-mode IDs against your build before relying on
  automatic Note/Chord routing.
- **Some LEDs stay stale**: LED feedback is throttled to changes plus a 1.2 s
  refresh; wait one refresh or press **LED TEST**.
- **No device listed**: Android's `MidiManager` is unavailable on a few builds;
  check `adb shell dumpsys media.midi` or use a USB MIDI class-compliant port.
- **Touch strips do nothing**: they send pitch bend on channels 1–8 in DAW
  mode; the app maps those channels. In a custom project they may be configured
  as CC instead.
- **Cursors do nothing**: the cluster only walks menus while a clip editor is
  open; with the editor closed it moves the pad selection. Check the MIDI
  sheet's monitor line for `NOTE 94`–`NOTE 97` while pressing them, and open
  a clip with an engine assigned (empty pads have no menus).

## Verification status

The codec, mode report, identity firmware, port-role mapping, palette, handshake
bytes and cursor/encoder-button notes are unit-tested (`Apc64CodecTest`); the
parser is tested with fragmented/running-status streams
(`MidiStreamParserTest`); chord grouping is covered by `NoteEntryTest`; and the
editor menu model — page ring per engine, field clamping, value text, preset
indexing, the SOUND page and the adaptive strip layout — is covered by
`EditorNavTest`; the strip LED frame bytes and strip-touch notes by
`Apc64CodecTest`; and the Step Sequencer's 16th clock by `StepClockTest`. A physical APC64 was not attached to
the CI runner, so device-specific firmware quirks should be reported with the
debug log line shown in the MIDI sheet.
