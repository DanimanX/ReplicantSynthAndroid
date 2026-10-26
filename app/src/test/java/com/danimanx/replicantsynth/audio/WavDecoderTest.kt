package com.danimanx.replicantsynth.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class WavDecoderTest {

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    private fun le32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    private fun wav(
        channels: Int,
        sampleRate: Int,
        bits: Int,
        format: Int,
        data: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.write(le32(36 + data.size))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(le32(16))
        out.write(le16(format))
        out.write(le16(channels))
        out.write(le32(sampleRate))
        out.write(le32(sampleRate * channels * bits / 8))
        out.write(le16(channels * bits / 8))
        out.write(le16(bits))
        out.write("data".toByteArray())
        out.write(le32(data.size))
        out.write(data)
        return out.toByteArray()
    }

    @Test
    fun decodes16BitStereo() {
        val data = ByteArrayOutputStream()
        for (v in intArrayOf(0, 16384, -16384, 32767)) {
            data.write(le16(v and 0xFFFF))
            data.write(le16(v and 0xFFFF))
        }
        val decoded = WavDecoder.decode(wav(2, 48000, 16, 1, data.toByteArray()))
        assertEquals(48000, decoded.sampleRate)
        assertEquals(4, decoded.frames)
        assertEquals(2, decoded.channels)
        assertEquals(0f, decoded.samples[0], 0.0001f)
        assertEquals(0.5f, decoded.samples[2], 0.001f)
        assertEquals(-0.5f, decoded.samples[4], 0.001f)
        assertTrue(decoded.samples[6] > 0.99f)
    }

    @Test
    fun decodes24BitMonoAndDuplicatesToStereo() {
        val data = ByteArrayOutputStream()
        for (v in intArrayOf(0, 2097152, -2097152)) {
            data.write(v and 0xFF)
            data.write((v shr 8) and 0xFF)
            data.write((v shr 16) and 0xFF)
        }
        val decoded = WavDecoder.decode(wav(1, 44100, 24, 1, data.toByteArray()))
        assertEquals(44100, decoded.sampleRate)
        assertEquals(3, decoded.frames)
        assertEquals(2, decoded.channels)
        assertEquals(1, decoded.sourceChannels)
        assertEquals(0.25f, decoded.samples[2], 0.002f)
        assertEquals(decoded.samples[2], decoded.samples[3], 0.0001f)
    }

    @Test
    fun decodesFloat32() {
        val data = ByteArrayOutputStream()
        val value = 0.25f
        val bits = java.lang.Float.floatToIntBits(value)
        data.write(le32(bits))
        data.write(le32(bits))
        val decoded = WavDecoder.decode(wav(2, 48000, 32, 3, data.toByteArray()))
        assertEquals(1, decoded.frames)
        assertEquals(value, decoded.samples[0], 0.00001f)
    }
}
