# APC64 / USB MIDI

## Connection

1. Use a USB-C OTG adapter (or a powered hub) and connect the APC64.
2. Open the **MIDI** chip in the header. The app lists USB MIDI devices,
   auto-connects the first name containing `APC64`, and opens both the device's
   MIDI input (to send LEDs/SysEx) and output (to receive pads).
3. Android's MIDI service grants access without a runtime USB permission dialog
   on standard devices. If the list is empty, tap **REFRESH** after re-seating
   the cable.

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

## Pad map

- Ableton/DAW mode: notes **0–63**, note 0 is the **bottom-left** pad.
- `BASE 0 · DAW` is the default. `BASE 36 · CUSTOM` covers a custom project or
  FreeAPC64 firmware that maps the grid to C1–D#6.
- `FLIP PAD ROWS` converts the bottom-left hardware origin to the app's
  top-left row-major grid. Both settings are persisted.

The on-screen grid and the APC64 always refer to the same pad number; changing
the base/flip only changes the USB note translation.

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

## Controls

| Control | Message | App action |
| --- | --- | --- |
| Pads | Notes 0–63 | Launch/stop; selects the pad |
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
- **Some LEDs stay stale**: LED feedback is throttled to changes plus a 1.2 s
  refresh; wait one refresh or press **LED TEST**.
- **No device listed**: Android's `MidiManager` is unavailable on a few builds;
  check `adb shell dumpsys media.midi` or use a USB MIDI class-compliant port.
- **Touch strips do nothing**: they send pitch bend on channels 1–8 in DAW
  mode; the app maps those channels. In a custom project they may be configured
  as CC instead.

## Verification status

The codec, mapping, palette and handshake bytes are unit-tested
(`Apc64CodecTest`) and the parser is tested with fragmented/running-status
streams (`MidiStreamParserTest`). A physical APC64 was not attached to the CI
runner, so device-specific firmware quirks should be reported with the
debug log line shown in the MIDI sheet.
