package com.danimanx.replicantsynth.midi

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
