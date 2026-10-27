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
A full refresh is sent every 1.2 s to recover from lost packets; only changes
are sent in between.

In Note, Chord, Step, Project and Custom modes the unit owns the pad grid and
its LEDs. The app stops sending pad/scene LED frames and restores the full
session grid when the unit reports Session again (the unit clears its own grid
on every mode change).

## Controls

| Control | Message | App action |
| --- | --- | --- |
| Pads (Session) | Notes 0–63 on the DAW cable | Launch/stop; selects the pad |
| Pads (Note/Chord) | Notes on the Notes cable | Write keys into the Wave piano roll |
| Scene 1–8 | Notes 112–119 | Launch scene |
| Play / Stop | 91 / 93 | Transport |
| Record | 92 | Toggle master capture |
| Clip Stop | 111 | Stop all clips |
| Mute / Solo | 109 / 110 | Selected pad mute/solo |
| Shift | 120 | Reserved |
| Encoder | CC 90 | BPM ± (two's-complement relative) |
| Touch strip 1 | Pitch bend ch 1 | Master gain |
| Touch strip 8 | Pitch bend ch 8 | Selected pad gain |
| Touch strips 2–7 | Pitch bend ch 2–7 | Selected pad FX macros 1–6 |
| OLED | SysEx 0x10 / 0x1A | ReplicantSynth, BPM, quantize |

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

## Verification status

The codec, mode report, identity firmware, port-role mapping, palette and
handshake bytes are unit-tested (`Apc64CodecTest`); the parser is tested with
fragmented/running-status streams (`MidiStreamParserTest`); and chord grouping
is covered by `NoteEntryTest`. A physical APC64 was not attached to the CI
runner, so device-specific firmware quirks should be reported with the debug
log line shown in the MIDI sheet.
