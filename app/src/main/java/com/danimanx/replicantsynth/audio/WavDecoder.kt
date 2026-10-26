package com.danimanx.replicantsynth.audio

/**
 * Self-contained RIFF/WAVE reader for the formats a musician actually drops on
 * a phone: PCM 8/16/24/32-bit, IEEE float 32-bit and WAVE_FORMAT_EXTENSIBLE.
 * Android's MediaExtractor is unreliable for raw PCM, so WAV never goes through
 * MediaCodec.
 */
object WavDecoder {

    fun decode(bytes: ByteArray, maxSeconds: Int = 300): DecodedAudio {
        require(bytes.size >= 44) { "not a WAV file" }
        require(ascii(bytes, 0, 4) == "RIFF" && ascii(bytes, 8, 4) == "WAVE") {
            "not a RIFF/WAVE file"
        }

        var fmtOffset = -1
        var fmtSize = 0
        var dataOffset = -1
        var dataSize = 0
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = ascii(bytes, offset, 4)
            val size = le32(bytes, offset + 4)
            val body = offset + 8
            if (size < 0 || body + size > bytes.size) break
            when (id) {
                "fmt " -> {
                    fmtOffset = body
                    fmtSize = size
                }
                "data" -> {
                    dataOffset = body
                    dataSize = size
                }
            }
            if (fmtOffset >= 0 && dataOffset >= 0) break
            offset = body + size + (size and 1)
        }
        require(fmtOffset >= 0) { "WAV has no fmt chunk" }
        require(dataOffset >= 0) { "WAV has no data chunk" }
        require(fmtSize >= 16) { "WAV fmt chunk too short" }

        var format = le16(bytes, fmtOffset)
        val channels = le16(bytes, fmtOffset + 2)
        val sampleRate = le32(bytes, fmtOffset + 4)
        val blockAlign = le16(bytes, fmtOffset + 12)
        val bits = le16(bytes, fmtOffset + 14)
        if (format == 0xFFFE && fmtSize >= 40) {
            // WAVE_FORMAT_EXTENSIBLE: the real tag is the first two bytes of the
            // sub-format GUID.
            format = le16(bytes, fmtOffset + 24)
        }
        require(channels in 1..8) { "unsupported channel count $channels" }
        require(sampleRate in 8000..192000) { "unsupported sample rate $sampleRate" }
        require(bits == 8 || bits == 16 || bits == 24 || bits == 32) {
            "unsupported bit depth $bits"
        }
        require(blockAlign >= channels * (bits / 8)) { "invalid WAV block alignment" }
        require(format == 1 || format == 3) { "unsupported WAV format tag $format" }

        val bytesPerSample = bits / 8
        val availableFrames = minOf(dataSize / blockAlign, bytes.size / blockAlign)
        val maxFrames = minOf(availableFrames, sampleRate * maxSeconds)
        require(maxFrames > 0) { "WAV has no audio frames" }

        val stereo = FloatArray(maxFrames * 2)
        for (frame in 0 until maxFrames) {
            val frameOffset = dataOffset + frame * blockAlign
            val first = readSample(bytes, frameOffset, format, bits)
            if (channels == 1) {
                stereo[frame * 2] = first
                stereo[frame * 2 + 1] = first
            } else {
                stereo[frame * 2] = first
                stereo[frame * 2 + 1] = readSample(bytes, frameOffset + bytesPerSample, format, bits)
            }
        }
        return DecodedAudio(
            samples = stereo,
            frames = maxFrames,
            channels = 2,
            sampleRate = sampleRate,
            sourceChannels = channels,
        )
    }

    private fun readSample(bytes: ByteArray, offset: Int, format: Int, bits: Int): Float {
        return if (format == 3) {
            if (bits != 32) 0f else Float.fromBits(le32(bytes, offset))
        } else {
            when (bits) {
                8 -> ((bytes[offset].toInt() and 0xFF) - 128) / 128f
                16 -> le16Signed(bytes, offset) / 32768f
                24 -> le24Signed(bytes, offset) / 8388608f
                else -> le32Signed(bytes, offset) / 2147483648f
            }
        }
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.US_ASCII)

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun le16Signed(bytes: ByteArray, offset: Int): Int {
        val v = le16(bytes, offset)
        return if (v and 0x8000 != 0) v - 0x10000 else v
    }

    private fun le24Signed(bytes: ByteArray, offset: Int): Int {
        val v = (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16)
        return if (v and 0x800000 != 0) v - 0x1000000 else v
    }

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun le32Signed(bytes: ByteArray, offset: Int): Long =
        le32(bytes, offset).toLong()
}
