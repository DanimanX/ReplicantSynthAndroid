package com.danimanx.replicantsynth.audio

/**
 * Thin JNI surface. Every call is cheap and non-blocking; the audio callback
 * owns the DSP state and the UI thread exchanges atomics and lock-free queues.
 */
object NativeEngine {
    init {
        System.loadLibrary("replicantsynth_engine")
    }

    external fun nativeCreate(): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeVersion(): String

    external fun nativeStartAudio(handle: Long): Boolean
    external fun nativeStopAudio(handle: Long)
    external fun nativeIsAudioRunning(handle: Long): Boolean
    external fun nativeRenderSilent(handle: Long, frames: Int)
    external fun nativeGetAudioStats(handle: Long, floats: FloatArray, ints: IntArray)

    external fun nativeSetPlaying(handle: Long, playing: Boolean)
    external fun nativeIsPlaying(handle: Long): Boolean
    external fun nativeSetTempo(handle: Long, bpm: Float)
    external fun nativeSetQuantize(handle: Long, steps: Int)
    external fun nativeSetSwing(handle: Long, swing: Float)
    external fun nativeSetMasterGain(handle: Long, gain: Float)

    external fun nativeConfigureClip(
        handle: Long,
        pad: Int,
        kind: Int,
        root: Int,
        length: Int,
        gain: Float,
        pan: Float,
        fxType: Int,
        fxEnabled: Boolean,
        sampleLoop: Boolean,
        mute: Boolean,
        solo: Boolean,
        params: FloatArray,
    )

    external fun nativeUpdateParams(
        handle: Long,
        pad: Int,
        params: FloatArray,
        gain: Float,
        pan: Float,
        mute: Boolean,
        solo: Boolean,
        fxEnabled: Boolean,
    )

    external fun nativeSetStep(
        handle: Long,
        pad: Int,
        step: Int,
        gate: Boolean,
        note: Int,
        velocity: Int,
        accent: Boolean,
        slide: Boolean,
    )

    external fun nativeSetDrumStep(
        handle: Long,
        pad: Int,
        voice: Int,
        step: Int,
        on: Boolean,
        velocity: Int,
    )

    external fun nativeSetMidiNote(
        handle: Long,
        pad: Int,
        on: Boolean,
        note: Int,
        start: Int,
        length: Int,
        velocity: Int,
    )

    external fun nativeClearMidiNotes(handle: Long, pad: Int)
    external fun nativePreviewNote(
        handle: Long,
        pad: Int,
        on: Boolean,
        note: Int,
        velocity: Int,
    )

    external fun nativeEnsureFx(handle: Long, pad: Int)
    external fun nativeClearClip(handle: Long, pad: Int)

    external fun nativeLaunchPad(handle: Long, pad: Int)
    external fun nativeStopPad(handle: Long, pad: Int)
    external fun nativeLaunchScene(handle: Long, row: Int)
    external fun nativeStopScene(handle: Long, row: Int)
    external fun nativeStopAll(handle: Long)
    external fun nativePanic(handle: Long)

    external fun nativeLoadSample(
        handle: Long,
        pad: Int,
        data: FloatArray,
        frames: Int,
        channels: Int,
        sampleRate: Int,
    )

    external fun nativeClearSample(handle: Long, pad: Int)

    external fun nativeGetPadStates(handle: Long, out: IntArray)
    external fun nativeGetPadMeters(handle: Long, out: FloatArray)
    external fun nativeGetMasterMeter(handle: Long): Float
    external fun nativeGetScope(handle: Long, out: FloatArray)
    external fun nativeGetSampleRate(handle: Long): Int

    external fun nativeStartMasterRecording(handle: Long, path: String): Boolean
    external fun nativeStartPadRecording(handle: Long, path: String, pad: Int): Boolean
    external fun nativeStopRecording(handle: Long)
    external fun nativeIsRecording(handle: Long): Boolean
    external fun nativeGetRecordingFrames(handle: Long): Long
    external fun nativeGetPadRecordingFrames(handle: Long): Long
    external fun nativeSetOfflineRendering(handle: Long, offline: Boolean)
}
