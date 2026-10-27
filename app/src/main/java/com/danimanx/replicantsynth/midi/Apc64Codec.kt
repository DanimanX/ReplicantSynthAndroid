package com.danimanx.replicantsynth.midi

/**
 * The APC64's pad-mode report, validated against firmware 1.2.1.6 and the
 * Bitwig APC64 extension. These IDs are what the unit sends back after the
 * NOTE / CHORD / STEP / PROJECT buttons on the hardware; they are *not* the
 * editor page indexes used by Akai's project editor.
 */
enum class Apc64PadMode(val id: Int, val label: String) {
    SESSION(0, "SESSION"),
    OVERVIEW(1, "OVERVIEW"),
    NOTE(2, "NOTE"),
    CHORD(3, "CHORD"),
    CHORD_SETTINGS(4, "CHORD CFG"),
    DRUM(5, "DRUM"),
    STEP(6, "STEP"),
    STEP_SETTINGS(7, "STEP CFG"),
    PROJECT(8, "PROJECT"),
    CUSTOM(9, "CUSTOM"),
    UNKNOWN(-1, "UNKNOWN");

    /** Session and Overview are host-drawn: the 64 pads are clip slots. */
    val hostOwnsPads: Boolean get() = this == SESSION || this == OVERVIEW

    /** The unit's Note and Chord surfaces send real key presses. */
    val keySurface: Boolean get() = this == NOTE || this == CHORD

    companion object {
        fun fromId(id: Int): Apc64PadMode =
            entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

/** USB MIDI cable exposed by the APC64 (DAW, Notes, MIDI, Custom). */
enum class Apc64PortRole { DAW, NOTES, CUSTOM, MIDI, UNKNOWN }

/** Where a note event belongs: clip grid, piano-roll key, side control, or nowhere. */
enum class Apc64NoteRoute { CLIP, KEY, CONTROL, IGNORE }

/**
 * APC64 protocol constants and pure mapping helpers. The layout follows the
 * Ableton Live 12 APC64 remote script and the Akai hardware SysEx manual:
 *  - 64 pads on notes 0..63, note 0 = bottom-left, LED channel 6 = full
 *  - scene launch buttons on notes 112..119
 *  - transport on 91 (play), 92 (record), 93 (stop)
 *  - encoder on CC 90, touch strips on pitch-bend channels 0..7
 *  - LED colour is a note-on velocity palette, with brightness in the channel
 */
object Apc64Codec {

    const val PAD_COUNT = 64
    const val PAD_BASE_DAW = 0
    const val PAD_BASE_CUSTOM = 36

    const val LED_FULL = 6
    const val LED_HALF = 0
    const val LED_PULSE = 10
    const val LED_BLINK = 14

    const val COLOR_OFF = 0
    const val COLOR_GREY = 1
    const val COLOR_WHITE = 3
    const val COLOR_RED = 5
    const val COLOR_AMBER = 9
    const val COLOR_YELLOW = 13
    const val COLOR_GREEN = 21
    const val COLOR_BLUE = 45

    const val NOTE_PLAY = 91
    const val NOTE_RECORD = 92
    const val NOTE_STOP = 93
    const val NOTE_CLIP_STOP = 111
    const val NOTE_MUTE = 109
    const val NOTE_SOLO = 110
    const val NOTE_SHIFT = 120
    const val NOTE_TEMPO = 72
    const val CC_ENCODER = 90
    const val SCENE_FIRST = 112
    const val SCENE_LAST = 119
    const val TRACK_SELECT_FIRST = 100
    const val TRACK_SELECT_LAST = 107

    /** Newest firmware whose pad-mode report was verified (Bitwig + live capture). */
    const val VALIDATED_FIRMWARE = "1.2.1.6"

    /** note 0 is the bottom-left pad; the UI grid is top-left row-major. */
    fun padForNote(note: Int, baseNote: Int = PAD_BASE_DAW, flipRows: Boolean = true): Int? {
        val offset = note - baseNote
        if (offset !in 0 until PAD_COUNT) return null
        val fromBottom = offset / 8
        val column = offset % 8
        val row = if (flipRows) 7 - fromBottom else fromBottom
        return row * 8 + column
    }

    fun noteForPad(pad: Int, baseNote: Int = PAD_BASE_DAW, flipRows: Boolean = true): Int {
        val row = pad / 8
        val column = pad % 8
        val fromBottom = if (flipRows) 7 - row else row
        return baseNote + fromBottom * 8 + column
    }

    fun sceneForNote(note: Int): Int? =
        if (note in SCENE_FIRST..SCENE_LAST) note - SCENE_FIRST else null

    fun ledColorFor(
        state: Int,
        kind: Int,
        selected: Boolean,
    ): Pair<Int, Int> {
        // state values mirror rep::ClipState.
        if (kind == 0) {
            return if (selected) COLOR_WHITE to LED_HALF else COLOR_GREY to LED_HALF
        }
        return when (state) {
            1 -> COLOR_YELLOW to LED_BLINK // queued start
            2 -> when (kind) {
                1 -> COLOR_AMBER to LED_FULL // acid
                2 -> COLOR_BLUE to LED_FULL // wavetable
                3 -> COLOR_YELLOW to LED_FULL // drums
                else -> COLOR_GREEN to LED_FULL // sample
            }
            3 -> COLOR_RED to LED_BLINK // queued stop
            else -> if (selected) COLOR_WHITE to LED_FULL else COLOR_AMBER to LED_HALF
        }
    }

    fun led(note: Int, color: Int, channel: Int = LED_FULL): ByteArray = byteArrayOf(
        (0x90 or (channel and 0x0F)).toByte(),
        (note and 0x7F).toByte(),
        (color and 0x7F).toByte(),
    )

    fun noteOn(note: Int, velocity: Int, channel: Int = 0): ByteArray = byteArrayOf(
        (0x90 or (channel and 0x0F)).toByte(),
        (note and 0x7F).toByte(),
        (velocity and 0x7F).toByte(),
    )

    fun noteOff(note: Int, channel: Int = 0): ByteArray = byteArrayOf(
        (0x80 or (channel and 0x0F)).toByte(),
        (note and 0x7F).toByte(),
        0,
    )

    fun cc(controller: Int, value: Int, channel: Int = 0): ByteArray = byteArrayOf(
        (0xB0 or (channel and 0x0F)).toByte(),
        (controller and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private fun sysex(payload: IntArray): ByteArray {
        val out = ByteArray(payload.size + 2)
        out[0] = 0xF0.toByte()
        for (i in payload.indices) out[i + 1] = (payload[i] and 0x7F).toByte()
        out[out.size - 1] = 0xF7.toByte()
        return out
    }

    /** F0 47 00 53 <id> <lenMsb> <lenLsb> <payload...> F7 */
    fun akaiMessage(messageId: Int, vararg values: Int): ByteArray {
        val payload = values.toList()
        val len = payload.size
        return sysex(
            intArrayOf(
                0x47, 0x00, 0x53, messageId,
                (len shr 7) and 0x7F, len and 0x7F,
            ) + payload.toIntArray(),
        )
    }

    fun modeMessage(mode: Int): ByteArray = akaiMessage(0x19, mode and 0x7F)
    fun trackTypeMessage(type: Int): ByteArray = akaiMessage(0x1B, type and 0x01)
    fun displayOwnerMessage(owner: Int): ByteArray = akaiMessage(0x1C, owner and 0x01)

    fun oledLine(lineIndex: Int, text: String): ByteArray {
        val index = lineIndex.coerceIn(0, 7)
        val offset = if (index >= 4) 0x1A else 0x10
        val line = index and 0x03
        val chars = text.take(8).map { it.code and 0x7F }
        return akaiMessage(offset, line, *chars.toIntArray(), 0)
    }

    val identityRequest: ByteArray = byteArrayOf(
        0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte(),
    )

    /**
     * Resolve an Android MIDI port to an APC64 cable. Android exposes the four
     * USB MIDI cables as separate ports; the names vary by OS/device, so fall
     * back to the cable number when the name is generic.
     */
    fun portRole(name: String, portNumber: Int): Apc64PortRole {
        val upper = name.uppercase()
        return when {
            "DAW" in upper -> Apc64PortRole.DAW
            "NOTE" in upper -> Apc64PortRole.NOTES
            "CUSTOM" in upper -> Apc64PortRole.CUSTOM
            "MIDI" in upper -> Apc64PortRole.MIDI
            portNumber == 0 -> Apc64PortRole.DAW
            portNumber == 1 -> Apc64PortRole.NOTES
            portNumber == 2 -> Apc64PortRole.MIDI
            portNumber == 3 -> Apc64PortRole.CUSTOM
            else -> Apc64PortRole.UNKNOWN
        }
    }

    /** Parse the Akai SysEx envelope, returning the message id and data payload. */
    fun parseAkaiMessage(bytes: ByteArray): Pair<Int, ByteArray>? {
        if (bytes.size < 9) return null
        if (bytes[0] != 0xF0.toByte() || bytes[1] != 0x47.toByte() ||
            bytes[2] != 0x00.toByte() || bytes[3] != 0x53.toByte() ||
            bytes.last() != 0xF7.toByte()
        ) {
            return null
        }
        val id = bytes[4].toInt() and 0x7F
        val length = ((bytes[5].toInt() and 0x7F) shl 7) or
            (bytes[6].toInt() and 0x7F)
        val start = 7
        if (start + length + 1 != bytes.size) return null
        return id to bytes.copyOfRange(start, start + length)
    }

    /**
     * Pad-mode report from the unit: `F0 47 00 53 19 00 01 <mode> F7`.
     * Firmware 1.2.1.6 reports 0 Session, 1 Overview, 2 Note, 3 Chord,
     * 5 Drum, 6 Step, 8 Project and 9 Custom.
     */
    fun padModeFromSysex(bytes: ByteArray): Apc64PadMode? {
        val (id, payload) = parseAkaiMessage(bytes) ?: return null
        if (id != 0x19 || payload.size != 1) return null
        val mode = Apc64PadMode.fromId(payload[0].toInt() and 0x7F)
        return mode.takeIf { it != Apc64PadMode.UNKNOWN }
    }

    /**
     * Universal identity reply: `F0 7E 00 06 02 47 53 00 19 <fw0 fw1 fw2 fw3> ...`.
     * The four firmware bytes are printed as `major.minor.patch.build`.
     */
    fun firmwareFromIdentity(bytes: ByteArray): String? {
        if (bytes.size < 17) return null
        val prefix = byteArrayOf(
            0xF0.toByte(), 0x7E, 0x00, 0x06, 0x02, 0x47, 0x53, 0x00, 0x19,
        )
        for (i in prefix.indices) {
            if (bytes[i] != prefix[i]) return null
        }
        val fw = IntArray(4) { bytes[9 + it].toInt() and 0xFF }
        if (fw.any { it > 99 }) return null
        return fw.joinToString(".")
    }

    /** True when the firmware is new enough for the validated pad-mode report. */
    fun firmwareSupported(version: String?): Boolean {
        if (version.isNullOrBlank()) return false
        val parts = version.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size < 4) return false
        val minimum = listOf(1, 2, 1, 6)
        for (i in 0 until 4) {
            if (parts[i] != minimum[i]) return parts[i] > minimum[i]
        }
        return true
    }

    fun isPadRangeNote(note: Int): Boolean = note in 0 until PAD_COUNT

    /**
     * Split incoming notes by cable and by the unit's reported mode.
     *
     * Notes-cable events are piano-roll keys in Note/Chord mode (and as a
     * fallback before the first mode report), even when a pitch matches a scene
     * or transport note number. Step/Project/Custom/Chord-settings/Drum notes
     * are the unit's own data and are ignored. On the DAW cable, only Session
     * and Overview map pad-range notes to clips; Note/Chord map them to keys so
     * a merged USB implementation can never launch clips from the key surface.
     */
    fun routeNote(
        mode: Apc64PadMode,
        fromNotesPort: Boolean,
        note: Int,
    ): Apc64NoteRoute {
        if (fromNotesPort) {
            // Note, Chord and the fallback (mode report not seen yet) are key
            // surfaces. Step/Project/Custom notes are the unit's own data and
            // must not be written into the piano roll.
            return when (mode) {
                Apc64PadMode.STEP,
                Apc64PadMode.STEP_SETTINGS,
                Apc64PadMode.PROJECT,
                Apc64PadMode.CUSTOM,
                Apc64PadMode.CHORD_SETTINGS,
                Apc64PadMode.DRUM,
                -> Apc64NoteRoute.IGNORE
                else -> Apc64NoteRoute.KEY
            }
        }
        if (isPadRangeNote(note)) {
            return when {
                mode.hostOwnsPads -> Apc64NoteRoute.CLIP
                mode.keySurface -> Apc64NoteRoute.KEY
                else -> Apc64NoteRoute.IGNORE
            }
        }
        return Apc64NoteRoute.CONTROL
    }

    /** The sequence Bitwig and Ableton send to unlock the 64-pad surface. */
    fun handshake(): List<ByteArray> = listOf(
        trackTypeMessage(0),
        modeMessage(0),
        identityRequest,
        displayOwnerMessage(1),
        modeMessage(0),
        oledLine(0, "REPLICANT"),
        oledLine(1, "SYNTH"),
        oledLine(2, "64 PAD"),
        oledLine(3, "CLIP GRID"),
    )

    fun encoderDelta(value: Int): Int =
        if (value < 64) value else value - 128

    fun touchStripValue(value: Int): Float =
        ((value.coerceIn(0, 16383)) / 16383f)
}
