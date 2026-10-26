package com.danimanx.replicantsynth.audio

/** Decoded audio ready for the native sample player. Always interleaved stereo. */
data class DecodedAudio(
    val samples: FloatArray,
    val frames: Int,
    val channels: Int,
    val sampleRate: Int,
    val sourceChannels: Int = channels,
) {
    val durationSeconds: Double get() = if (sampleRate > 0) frames.toDouble() / sampleRate else 0.0

    companion object {
        fun toStereo(interleaved: FloatArray, frames: Int, channels: Int): FloatArray {
            val out = FloatArray(frames * 2)
            if (channels <= 0) return out
            for (i in 0 until frames) {
                val base = i * channels
                when {
                    channels == 1 -> {
                        val v = interleaved.getOrElse(base) { 0f }
                        out[i * 2] = v
                        out[i * 2 + 1] = v
                    }
                    else -> {
                        out[i * 2] = interleaved.getOrElse(base) { 0f }
                        out[i * 2 + 1] = interleaved.getOrElse(base + 1) { 0f }
                    }
                }
            }
            return out
        }
    }
}
