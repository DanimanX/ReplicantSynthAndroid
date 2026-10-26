package com.danimanx.replicantsynth.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/**
 * Decodes an imported audio file into interleaved stereo floats.
 * WAV is parsed directly; FLAC, MP3 and other compressed formats go through
 * MediaExtractor + MediaCodec. The native sample player handles the source
 * sample rate and pitch, so no resampling happens here.
 */
object AudioDecoder {

    fun decode(file: File, maxSeconds: Int = 300): DecodedAudio {
        require(file.isFile) { "missing audio file: ${file.name}" }
        val extension = file.extension.lowercase()
        if (extension == "wav" || extension == "wave") {
            return WavDecoder.decode(file.readBytes(), maxSeconds)
        }
        return decodeCompressed(file, maxSeconds)
    }

    private fun decodeCompressed(file: File, maxSeconds: Int): DecodedAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            require(trackIndex >= 0 && format != null) { "no audio track in ${file.name}" }
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("missing MIME")
            val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            var outputRate = sourceRate
            var outputChannels = sourceChannels
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            val maxSamples = sourceRate * maxSeconds * sourceChannels
            val sink = FloatSink(maxSamples.coerceAtLeast(4096))

            var guard = 0
            while (!outputEos && guard < 2_000_000) {
                guard++
                if (!inputEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        val size = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inIndex, 0, size, extractor.sampleTime.coerceAtLeast(0), 0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        outputRate = outFormat.getIntOr(MediaFormat.KEY_SAMPLE_RATE, sourceRate)
                        outputChannels = outFormat.getIntOr(
                            MediaFormat.KEY_CHANNEL_COUNT, sourceChannels,
                        )
                        pcmEncoding = outFormat.getIntOr(
                            MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT,
                        )
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            when (pcmEncoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> {
                                    val floats = buffer.asFloatBuffer()
                                    while (floats.hasRemaining() && sink.size < maxSamples) {
                                        sink.add(floats.get())
                                    }
                                }
                                AudioFormat.ENCODING_PCM_8BIT -> {
                                    while (buffer.hasRemaining() && sink.size < maxSamples) {
                                        sink.add(((buffer.get().toInt() and 0xFF) - 128) / 128f)
                                    }
                                }
                                AudioFormat.ENCODING_PCM_32BIT -> {
                                    val ints = buffer.asIntBuffer()
                                    while (ints.hasRemaining() && sink.size < maxSamples) {
                                        sink.add(ints.get() / 2147483648f)
                                    }
                                }
                                else -> {
                                    val shorts = buffer.asShortBuffer()
                                    while (shorts.hasRemaining() && sink.size < maxSamples) {
                                        sink.add(shorts.get() / 32768f)
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEos = true
                        }
                    }
                }
            }

            val interleaved = sink.toArray()
            val channels = outputChannels.coerceAtLeast(1)
            val frames = interleaved.size / channels
            require(frames > 0) { "decoded no audio from ${file.name}" }
            val stereo = DecodedAudio.toStereo(interleaved, frames, channels)
            return DecodedAudio(
                samples = stereo,
                frames = frames,
                channels = 2,
                sampleRate = outputRate,
                sourceChannels = channels,
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.getIntOr(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback

    private class FloatSink(capacity: Int) {
        private var data = FloatArray(capacity.coerceAtLeast(1024))
        var size = 0
            private set

        fun add(value: Float) {
            if (size >= data.size) {
                val grown = FloatArray(maxOf(data.size * 2, 1024))
                System.arraycopy(data, 0, grown, 0, data.size)
                data = grown
            }
            data[size++] = value
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}
