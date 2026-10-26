package com.danimanx.replicantsynth.midi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun encoderIsTwosComplement() {
        assertEquals(1, Apc64Codec.encoderDelta(1))
        assertEquals(63, Apc64Codec.encoderDelta(63))
        assertEquals(-1, Apc64Codec.encoderDelta(127))
        assertEquals(-64, Apc64Codec.encoderDelta(64))
        assertEquals(0f, Apc64Codec.touchStripValue(-8192), 0.0001f)
        assertEquals(1f, Apc64Codec.touchStripValue(16383), 0.0001f)
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
