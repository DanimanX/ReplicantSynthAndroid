package com.danimanx.replicantsynth.audio

import android.content.Context
import com.danimanx.replicantsynth.model.Clip
import com.danimanx.replicantsynth.model.MidiNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioStats(
    val ready: Boolean = false,
    val audioRunning: Boolean = false,
    val latencyMs: Float = 0f,
    val cpuLoad: Float = 0f,
    val sampleRate: Int = 48000,
    val framesPerBurst: Int = 0,
    val bufferFrames: Int = 0,
    val xRuns: Int = 0,
    val apiLabel: String = "OFFLINE",
    val sharingLabel: String = "SILENT",
    val error: String? = null,
) {
    val latencyLabel: String
        get() = when {
            !audioRunning -> "silent"
            sharingLabel == "EXCLUSIVE" -> "${latencyMs.toInt()} ms"
            else -> sharingLabel.lowercase()
        }
}

/**
 * Owns the native engine handle. Creation builds the wavetable banks, so it
 * always happens on a worker thread. If the device cannot open an audio stream
 * the engine still runs from a silent clock, which keeps the sequencer, meters
 * and capture usable for testing and for phones without an output route.
 */
class EngineController(context: Context) {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(AudioStats())
    val state: StateFlow<AudioStats> = _state.asStateFlow()

    @Volatile
    private var handle: Long = 0L

    @Volatile
    private var tickerRunning = false
    private var ticker: Thread? = null

    @Synchronized
    fun ensure(): Boolean {
        if (handle == 0L) {
            handle = runCatching { NativeEngine.nativeCreate() }.getOrElse {
                _state.value = _state.value.copy(error = "Engine creation failed: ${it.message}")
                0L
            }
        }
        if (handle == 0L) return false
        val running = runCatching { NativeEngine.nativeStartAudio(handle) }.getOrDefault(false)
        _state.value = _state.value.copy(ready = true, audioRunning = running, error = null)
        updateTicker()
        return running
    }

    fun isReady(): Boolean = handle != 0L

    @Synchronized
    fun destroy() {
        stopTicker()
        val local = handle
        handle = 0L
        if (local != 0L) {
            runCatching { NativeEngine.nativeStopAudio(local) }
            runCatching { NativeEngine.nativeDestroy(local) }
        }
        _state.value = AudioStats()
    }

    @Synchronized
    fun refreshStats(): AudioStats {
        val local = handle
        if (local == 0L) return _state.value
        val floats = FloatArray(4)
        val ints = IntArray(6)
        runCatching { NativeEngine.nativeGetAudioStats(local, floats, ints) }
        val running = runCatching { NativeEngine.nativeIsAudioRunning(local) }.getOrDefault(false)
        val api = when (ints[4]) {
            1 -> "AAUDIO"
            2 -> "OPENSL"
            else -> "OFFLINE"
        }
        val sharing = when (ints[5]) {
            1 -> "EXCLUSIVE"
            2 -> "SHARED"
            else -> "SILENT"
        }
        val stats = _state.value.copy(
            ready = true,
            audioRunning = running,
            latencyMs = floats[0],
            cpuLoad = floats[1],
            sampleRate = if (ints[1] > 0) ints[1] else 48000,
            framesPerBurst = ints[2],
            bufferFrames = ints[3],
            xRuns = ints[0],
            apiLabel = api,
            sharingLabel = sharing,
        )
        _state.value = stats
        updateTicker()
        return stats
    }

    private fun updateTicker() {
        val local = handle
        val shouldRun = local != 0L && !_state.value.audioRunning
        if (shouldRun && !tickerRunning) {
            tickerRunning = true
            ticker = Thread({
                while (tickerRunning && handle != 0L) {
                    runCatching { NativeEngine.nativeRenderSilent(handle, 960) }
                    try {
                        Thread.sleep(20)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }, "ReplicantSilentClock").apply {
                priority = Thread.NORM_PRIORITY
                isDaemon = true
                start()
            }
        } else if (!shouldRun) {
            stopTicker()
        }
    }

    private fun stopTicker() {
        tickerRunning = false
        val thread = ticker
        ticker = null
        thread?.interrupt()
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(600) }
        }
    }

    // Transport -----------------------------------------------------------------

    fun setPlaying(playing: Boolean) = withHandle { NativeEngine.nativeSetPlaying(it, playing) }
    fun isPlaying(): Boolean = withHandleResult(false) { NativeEngine.nativeIsPlaying(it) }
    fun setTempo(bpm: Float) = withHandle { NativeEngine.nativeSetTempo(it, bpm) }
    fun setQuantize(steps: Int) = withHandle { NativeEngine.nativeSetQuantize(it, steps) }
    fun setSwing(swing: Float) = withHandle { NativeEngine.nativeSetSwing(it, swing) }
    fun setMasterGain(gain: Float) = withHandle { NativeEngine.nativeSetMasterGain(it, gain) }

    fun launchPad(pad: Int) = withHandle { NativeEngine.nativeLaunchPad(it, pad) }
    fun stopPad(pad: Int) = withHandle { NativeEngine.nativeStopPad(it, pad) }
    fun launchScene(row: Int) = withHandle { NativeEngine.nativeLaunchScene(it, row) }
    fun stopScene(row: Int) = withHandle { NativeEngine.nativeStopScene(it, row) }
    fun stopAll() = withHandle { NativeEngine.nativeStopAll(it) }
    fun panic() = withHandle { NativeEngine.nativePanic(it) }

    // Clips ---------------------------------------------------------------------

    fun configureClip(clip: Clip) = withHandle { local ->
        if (clip.fxType != 0) NativeEngine.nativeEnsureFx(local, clip.index)
        NativeEngine.nativeConfigureClip(
            local,
            clip.index,
            clip.kind.ordinal,
            clip.root,
            clip.length,
            clip.gain,
            clip.pan,
            clip.fxType,
            clip.fxEnabled,
            clip.sampleLoop,
            clip.mute,
            clip.solo,
            clip.params,
        )
    }

    fun updateParams(clip: Clip) = withHandle { local ->
        NativeEngine.nativeUpdateParams(
            local,
            clip.index,
            clip.params,
            clip.gain,
            clip.pan,
            clip.mute,
            clip.solo,
            clip.fxEnabled,
        )
    }

    fun setStep(clip: Clip, stepIndex: Int) {
        val step = clip.steps.getOrNull(stepIndex) ?: return
        withHandle { local ->
            NativeEngine.nativeSetStep(
                local,
                clip.index,
                stepIndex,
                step.gate,
                step.note,
                step.velocity,
                step.accent,
                step.slide,
            )
        }
    }

    fun setDrumStep(clip: Clip, voice: Int, step: Int) {
        withHandle { local ->
            NativeEngine.nativeSetDrumStep(
                local,
                clip.index,
                voice,
                step,
                clip.drumOn(voice, step),
                clip.drumVelocity(voice, step),
            )
        }
    }

    fun setMidiNote(clip: Clip, note: MidiNote, on: Boolean = true) = withHandle { local ->
        NativeEngine.nativeSetMidiNote(
            local,
            clip.index,
            on,
            note.note,
            note.start,
            note.length,
            note.velocity,
        )
    }

    fun clearMidiNotes(pad: Int) = withHandle { NativeEngine.nativeClearMidiNotes(it, pad) }

    fun previewNote(pad: Int, note: Int, velocity: Int, on: Boolean) = withHandle { local ->
        NativeEngine.nativePreviewNote(local, pad, on, note, velocity)
    }

    fun clearClip(pad: Int) = withHandle { NativeEngine.nativeClearClip(it, pad) }
    fun loadSample(pad: Int, data: FloatArray, frames: Int, channels: Int, sampleRate: Int) =
        withHandle { local ->
            NativeEngine.nativeLoadSample(local, pad, data, frames, channels, sampleRate)
        }

    fun clearSample(pad: Int) = withHandle { NativeEngine.nativeClearSample(it, pad) }

    // Telemetry -----------------------------------------------------------------

    fun padStates(out: IntArray) = withHandle { NativeEngine.nativeGetPadStates(it, out) }
    fun padMeters(out: FloatArray) = withHandle { NativeEngine.nativeGetPadMeters(it, out) }
    fun masterMeter(): Float = withHandleResult(0f) { NativeEngine.nativeGetMasterMeter(it) }
    fun scope(out: FloatArray) = withHandle { NativeEngine.nativeGetScope(it, out) }
    fun sampleRate(): Int = withHandleResult(48000) { NativeEngine.nativeGetSampleRate(it) }

    // Capture -------------------------------------------------------------------

    fun startMasterRecording(path: String): Boolean =
        withHandleResult(false) { NativeEngine.nativeStartMasterRecording(it, path) }

    fun startPadRecording(path: String, pad: Int): Boolean =
        withHandleResult(false) { NativeEngine.nativeStartPadRecording(it, path, pad) }

    fun stopRecording() = withHandle { NativeEngine.nativeStopRecording(it) }
    fun isRecording(): Boolean = withHandleResult(false) { NativeEngine.nativeIsRecording(it) }
    fun recordedFrames(): Long =
        withHandleResult(0L) { NativeEngine.nativeGetRecordingFrames(it) }

    fun padRecordedFrames(): Long =
        withHandleResult(0L) { NativeEngine.nativeGetPadRecordingFrames(it) }

    private inline fun withHandle(block: (Long) -> Unit) {
        synchronized(this) {
            val local = handle
            if (local != 0L) runCatching { block(local) }
        }
    }

    private inline fun <T> withHandleResult(fallback: T, block: (Long) -> T): T {
        synchronized(this) {
            val local = handle
            return if (local == 0L) fallback
            else runCatching { block(local) }.getOrDefault(fallback)
        }
    }
}
