package com.danimanx.replicantsynth.midi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Apc64CodecTest {

    @Test
    fun mapsDawNotesToTopLeftGrid() {
        assertEquals(56, Apc64Codec.padForNote(0))   // bottom-left
        assertEquals(57, Apc64Codec.padForNote(1))
        assertEquals(7, Apc64Codec.padForNote(63))   // top-right
        assertEquals(0, Apc64Codec.padForNote(56))   // top-left
        assertEquals(63, Apc64Codec.padForNote(7))   // bottom-right
        assertNull(Apc64Codec.padForNote(64))
    }

    @Test
    fun inverseMappingMatches() {
        for (pad in 0 until 64) {
            val note = Apc64Codec.noteForPad(pad)
            assertEquals(pad, Apc64Codec.padForNote(note))
        }
    }

    @Test
    fun customBaseAndFlip() {
        assertEquals(56, Apc64Codec.padForNote(36, Apc64Codec.PAD_BASE_CUSTOM))
        assertEquals(0, Apc64Codec.padForNote(36, Apc64Codec.PAD_BASE_CUSTOM, flipRows = false))
        assertEquals(36, Apc64Codec.noteForPad(0, Apc64Codec.PAD_BASE_CUSTOM, flipRows = false))
    }

    @Test
    fun scenesAndTransport() {
        assertEquals(0, Apc64Codec.sceneForNote(112))
        assertEquals(7, Apc64Codec.sceneForNote(119))
        assertNull(Apc64Codec.sceneForNote(120))
    }

    @Test
    fun ledPaletteFollowsStateAndKind() {
        assertEquals(Apc64Codec.COLOR_AMBER to Apc64Codec.LED_FULL, Apc64Codec.ledColorFor(2, 1, false))
        assertEquals(Apc64Codec.COLOR_BLUE to Apc64Codec.LED_FULL, Apc64Codec.ledColorFor(2, 2, false))
        assertEquals(Apc64Codec.COLOR_YELLOW to Apc64Codec.LED_BLINK, Apc64Codec.ledColorFor(1, 3, false))
        assertEquals(Apc64Codec.COLOR_WHITE to Apc64Codec.LED_HALF, Apc64Codec.ledColorFor(0, 0, true))
    }

    @Test
    fun handshakeUsesAkaiHeaderAndModeMessages() {
        val messages = Apc64Codec.handshake()
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0x47, 0x00, 0x53, 0x1B, 0x00, 0x01, 0x00, 0xF7.toByte()),
            messages[0],
        )
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0x47, 0x00, 0x53, 0x19, 0x00, 0x01, 0x00, 0xF7.toByte()),
            messages[1],
        )
        assertTrueContains(messages[2], byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte()))
    }

    @Test
    fun padModeReportMatchesFirmwareIds() {
        assertEquals(Apc64PadMode.SESSION, Apc64Codec.padModeFromSysex(modeReport(0)))
        assertEquals(Apc64PadMode.OVERVIEW, Apc64Codec.padModeFromSysex(modeReport(1)))
        assertEquals(Apc64PadMode.NOTE, Apc64Codec.padModeFromSysex(modeReport(2)))
        assertEquals(Apc64PadMode.CHORD, Apc64Codec.padModeFromSysex(modeReport(3)))
        assertEquals(Apc64PadMode.STEP, Apc64Codec.padModeFromSysex(modeReport(6)))
        assertEquals(Apc64PadMode.CUSTOM, Apc64Codec.padModeFromSysex(modeReport(9)))
        assertNull(Apc64Codec.padModeFromSysex(modeReport(42)))
        assertNull(Apc64Codec.padModeFromSysex(modeReport(2, length = 2)))
        assertNull(Apc64Codec.padModeFromSysex(Apc64Codec.displayOwnerMessage(1)))
    }

    @Test
    fun keySurfaceOnlyCoversNoteAndChord() {
        assertTrue(Apc64PadMode.NOTE.keySurface)
        assertTrue(Apc64PadMode.CHORD.keySurface)
        assertFalse(Apc64PadMode.SESSION.keySurface)
        assertFalse(Apc64PadMode.STEP.keySurface)
        assertFalse(Apc64PadMode.CUSTOM.keySurface)
        assertTrue(Apc64PadMode.SESSION.hostOwnsPads)
        assertTrue(Apc64PadMode.OVERVIEW.hostOwnsPads)
        assertFalse(Apc64PadMode.NOTE.hostOwnsPads)
    }

    @Test
    fun notesRouteByCableAndHardwareMode() {
        // Session pads launch clips; Notes-cable keys still work as a fallback
        // when the unit is already in a key view before its first report.
        assertEquals(Apc64NoteRoute.CLIP, Apc64Codec.routeNote(Apc64PadMode.SESSION, false, 0))
        assertEquals(Apc64NoteRoute.CLIP, Apc64Codec.routeNote(Apc64PadMode.OVERVIEW, false, 63))
        assertEquals(Apc64NoteRoute.KEY, Apc64Codec.routeNote(Apc64PadMode.SESSION, true, 36))
        // The key surfaces never launch clips on the DAW cable.
        assertEquals(Apc64NoteRoute.KEY, Apc64Codec.routeNote(Apc64PadMode.NOTE, false, 36))
        assertEquals(Apc64NoteRoute.KEY, Apc64Codec.routeNote(Apc64PadMode.CHORD, false, 60))
        assertEquals(Apc64NoteRoute.IGNORE, Apc64Codec.routeNote(Apc64PadMode.STEP, false, 60))
        assertEquals(Apc64NoteRoute.IGNORE, Apc64Codec.routeNote(Apc64PadMode.CUSTOM, false, 0))
        // Step/custom Notes-cable data is not piano-roll input.
        assertEquals(Apc64NoteRoute.IGNORE, Apc64Codec.routeNote(Apc64PadMode.STEP, true, 60))
        assertEquals(Apc64NoteRoute.IGNORE, Apc64Codec.routeNote(Apc64PadMode.CUSTOM, true, 60))
        // Side buttons are control notes on the DAW cable only when not a key.
        assertEquals(Apc64NoteRoute.CONTROL, Apc64Codec.routeNote(Apc64PadMode.NOTE, false, 91))
        assertEquals(Apc64NoteRoute.KEY, Apc64Codec.routeNote(Apc64PadMode.NOTE, true, 91))
    }

    @Test
    fun identityReplyCarriesValidatedFirmware() {
        val identity = byteArrayOf(
            0xF0.toByte(), 0x7E, 0x00, 0x06, 0x02, 0x47, 0x53, 0x00, 0x19,
            0x01, 0x02, 0x01, 0x06,
            0x00, 0x00, 0x00, 0xF7.toByte(),
        )
        assertEquals("1.2.1.6", Apc64Codec.firmwareFromIdentity(identity))
        assertTrue(Apc64Codec.firmwareSupported("1.2.1.6"))
        assertTrue(Apc64Codec.firmwareSupported("1.2.1.7"))
        assertFalse(Apc64Codec.firmwareSupported("1.2.1.5"))
        assertFalse(Apc64Codec.firmwareSupported("1.2"))
        assertFalse(Apc64Codec.firmwareSupported(null))
        assertNull(Apc64Codec.firmwareFromIdentity(Apc64Codec.identityRequest))
    }

    @Test
    fun portRolesUseNamesThenCableNumbers() {
        assertEquals(Apc64PortRole.DAW, Apc64Codec.portRole("APC64 DAW", 0))
        assertEquals(Apc64PortRole.NOTES, Apc64Codec.portRole("APC64 Notes", 1))
        assertEquals(Apc64PortRole.CUSTOM, Apc64Codec.portRole("APC64 Custom", 3))
        assertEquals(Apc64PortRole.MIDI, Apc64Codec.portRole("APC64 MIDI", 2))
        assertEquals(Apc64PortRole.DAW, Apc64Codec.portRole("", 0))
        assertEquals(Apc64PortRole.NOTES, Apc64Codec.portRole("APC64", 1))
        assertEquals(Apc64PortRole.CUSTOM, Apc64Codec.portRole("APC64", 3))
    }

    @Test
    fun encoderIsTwosComplement() {
        assertEquals(1, Apc64Codec.encoderDelta(1))
        assertEquals(63, Apc64Codec.encoderDelta(63))
        assertEquals(-1, Apc64Codec.encoderDelta(127))
        assertEquals(-64, Apc64Codec.encoderDelta(64))
        assertEquals(0f, Apc64Codec.touchStripValue(-8192), 0.0001f)
        assertEquals(1f, Apc64Codec.touchStripValue(16383), 0.0001f)
    }

    private fun modeReport(mode: Int, length: Int = 1): ByteArray {
        val payload = if (length == 1) intArrayOf(mode) else intArrayOf(mode, 0)
        return Apc64Codec.akaiMessage(0x19, *payload)
    }

    private fun assertTrueContains(haystack: ByteArray, needle: ByteArray) {
        var found = false
        for (i in 0..haystack.size - needle.size) {
            var ok = true
            for (j in needle.indices) if (haystack[i + j] != needle[j]) ok = false
            if (ok) found = true
        }
        org.junit.Assert.assertTrue("pattern not found", found)
    }
}
