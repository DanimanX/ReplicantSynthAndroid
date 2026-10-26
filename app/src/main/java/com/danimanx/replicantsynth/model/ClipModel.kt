package com.danimanx.replicantsynth.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Native kind ordinals must match rep::ClipKind in ClipEngine.h. */
enum class ClipKind(val label: String, val short: String) {
    EMPTY("EMPTY", "--"),
    ACID("ACID 303", "303"),
    WAVE("WAVE", "WAVE"),
    DRUMS("DRUMS 909", "909"),
    SAMPLE("SAMPLE", "SMP");

    companion object {
        fun fromOrdinal(value: Int): ClipKind = entries.getOrElse(value) { EMPTY }
    }
}

data class Step(
    val gate: Boolean = false,
    val note: Int = 60,
    val velocity: Int = 100,
    val accent: Boolean = false,
    val slide: Boolean = false,
)

object AcidParam {
    const val WAVE = 0
    const val CUTOFF = 1
    const val RESONANCE = 2
    const val ENV_MOD = 3
    const val DECAY = 4
    const val ACCENT = 5
    const val DIST_TYPE = 6
    const val DIST_DRIVE = 7
    const val DIST_MIX = 8
    const val DIST_TONE = 9
    const val VOLUME = 10
}

object WaveParam {
    const val BANK = 0
    const val POSITION = 1
    const val UNISON = 2
    const val DETUNE = 3
    const val SPREAD = 4
    const val CUTOFF = 5
    const val RESONANCE = 6
    const val DRIVE = 7
    const val ATTACK = 8
    const val DECAY = 9
    const val SUSTAIN = 10
    const val RELEASE = 11
    const val SUB = 12
    const val NOISE = 13
    const val LEVEL = 14
    const val FILTER_MODE = 15
    const val CHORD = 16
}

object DrumParam {
    const val TUNE = 0
    const val DECAY = 1
    const val SNAPPY = 2
    const val TONE = 3
    const val ACCENT = 4
    const val LEVEL = 5
    const val CUTOFF = 6
    const val RESONANCE = 7
}

object SampleParam {
    const val GAIN = 0
    const val PITCH = 1
}

object FxParam {
    const val BASE = 24
    const val ENABLED = 30
}

object ClipLimits {
    const val PADS = 64
    const val STEPS = 16
    const val DRUM_VOICES = 11
    const val PARAMS = 32
    const val COLS = 8
    const val ROWS = 8
}

val WAVETABLE_BANKS = listOf(
    "ANALOG MORPH", "CYBER NEURO", "DIGITAL FM", "VOCALOID",
    "CYBER ACID", "DYSTOPIAN STEEL", "PWM HYPER", "SUB ZERO",
)

val FILTER_MODES = listOf("LP 24", "LP 12", "BP 12", "HP 12")
val CHORD_MODES = listOf("SINGLE", "FIFTH", "MINOR", "MAJOR", "OCTAVE")
val DISTORTION_TYPES = listOf("POCO RAT", "TUBE", "HARD CLIP", "FUZZ", "WAVEFOLD")
val DRUM_VOICE_NAMES = listOf("BD", "SD", "LT", "MT", "HT", "RS", "CP", "CH", "OH", "CC", "RC")

val QUANTIZE_OPTIONS = listOf(0, 1, 4, 16, 32, 64)
fun quantizeLabel(steps: Int): String = when (steps) {
    0 -> "OFF"
    1 -> "1/16"
    4 -> "1/4"
    16 -> "1 BAR"
    32 -> "2 BARS"
    64 -> "4 BARS"
    else -> "$steps"
}

fun nextQuantize(current: Int): Int {
    val index = QUANTIZE_OPTIONS.indexOf(current)
    return QUANTIZE_OPTIONS[(if (index < 0) 3 else index + 1) % QUANTIZE_OPTIONS.size]
}

/**
 * One clip slot. Compose state is used so the 64-pad surface and the inspector
 * stay in sync without copying the whole project on every knob movement.
 */
class Clip(val index: Int) {
    var kind by mutableStateOf(ClipKind.EMPTY)
    var name by mutableStateOf("")
    var root by mutableIntStateOf(60)
    var length by mutableIntStateOf(ClipLimits.STEPS)
    var gain by mutableFloatStateOf(0.8f)
    var pan by mutableFloatStateOf(0f)
    var mute by mutableStateOf(false)
    var solo by mutableStateOf(false)
    var steps by mutableStateOf(List(ClipLimits.STEPS) { Step() })
    var drumMask by mutableStateOf(IntArray(ClipLimits.DRUM_VOICES))
    var drumVel by mutableStateOf(IntArray(ClipLimits.DRUM_VOICES * ClipLimits.STEPS) { 100 })
    var sampleLoop by mutableStateOf(true)
    var sampleName by mutableStateOf<String?>(null)
    var samplePath by mutableStateOf<String?>(null)
    var fxType by mutableIntStateOf(0)
    var fxEnabled by mutableStateOf(false)
    var params by mutableStateOf(FloatArray(ClipLimits.PARAMS))
    var sampleMissing by mutableStateOf(false)

    val row: Int get() = index / ClipLimits.COLS
    val col: Int get() = index % ClipLimits.COLS
    val displayName: String get() = name.ifBlank { defaultName(kind, index) }

    fun param(slot: Int): Float = params.getOrElse(slot) { 0f }

    fun setParam(slot: Int, value: Float) {
        if (slot !in 0 until ClipLimits.PARAMS) return
        val copy = params.copyOf()
        copy[slot] = value
        params = copy
    }

    fun updateSteps(newSteps: List<Step>) {
        steps = newSteps.take(ClipLimits.STEPS).let {
            if (it.size == ClipLimits.STEPS) it else it + List(ClipLimits.STEPS - it.size) { Step() }
        }
    }

    fun setStep(index: Int, step: Step) {
        if (index !in 0 until ClipLimits.STEPS) return
        updateSteps(steps.toMutableList().also { it[index] = step })
    }

    fun drumOn(voice: Int, step: Int): Boolean {
        if (voice !in 0 until ClipLimits.DRUM_VOICES || step !in 0 until ClipLimits.STEPS) return false
        return (drumMask[voice] shr step) and 1 == 1
    }

    fun setDrumStep(voice: Int, step: Int, on: Boolean) {
        if (voice !in 0 until ClipLimits.DRUM_VOICES || step !in 0 until ClipLimits.STEPS) return
        val masks = drumMask.copyOf()
        masks[voice] = if (on) masks[voice] or (1 shl step) else masks[voice] and (1 shl step).inv()
        drumMask = masks
    }

    fun drumVelocity(voice: Int, step: Int): Int {
        val at = voice * ClipLimits.STEPS + step
        return drumVel.getOrElse(at) { 100 }
    }

    fun setDrumVelocity(voice: Int, step: Int, velocity: Int) {
        val at = voice * ClipLimits.STEPS + step
        if (at !in drumVel.indices) return
        val copy = drumVel.copyOf()
        copy[at] = velocity.coerceIn(1, 127)
        drumVel = copy
    }

    fun applyFxDefaults(type: Int) {
        fxType = type.coerceIn(0, FX_CATALOG.lastIndex)
        val info = fxById(fxType)
        val copy = params.copyOf()
        for (i in 0 until 6) copy[FxParam.BASE + i] = info.defaults[i]
        copy[FxParam.ENABLED] = if (fxType == 0) 0f else 1f
        params = copy
        fxEnabled = fxType != 0
    }

    fun resetParams() {
        params = FloatArray(ClipLimits.PARAMS)
    }
}

fun defaultName(kind: ClipKind, index: Int): String {
    val number = (index + 1).toString().padStart(2, '0')
    return when (kind) {
        ClipKind.EMPTY -> ""
        ClipKind.ACID -> "ACID $number"
        ClipKind.WAVE -> "WAVE $number"
        ClipKind.DRUMS -> "909 $number"
        ClipKind.SAMPLE -> "SMP $number"
    }
}

fun applyKindDefaults(clip: Clip, kind: ClipKind) {
    val previousDefault = defaultName(clip.kind, clip.index)
    val previousFx = clip.fxType
    val previousFxEnabled = clip.fxEnabled
    clip.kind = kind
    clip.length = ClipLimits.STEPS
    clip.mute = false
    clip.solo = false
    clip.sampleMissing = false
    if (clip.name.isBlank() || clip.name == previousDefault) {
        clip.name = defaultName(kind, clip.index)
    }
    when (kind) {
        ClipKind.EMPTY -> {
            clip.resetParams()
            clip.root = 60
            clip.steps = List(ClipLimits.STEPS) { Step() }
            clip.drumMask = IntArray(ClipLimits.DRUM_VOICES)
            clip.drumVel = IntArray(ClipLimits.DRUM_VOICES * ClipLimits.STEPS) { 100 }
            clip.sampleName = null
            clip.samplePath = null
            clip.fxType = 0
            clip.fxEnabled = false
        }
        ClipKind.ACID -> {
            clip.root = 36
            clip.resetParams()
            val p = clip.params.copyOf()
            p[AcidParam.WAVE] = 0f
            p[AcidParam.CUTOFF] = 420f
            p[AcidParam.RESONANCE] = 0.72f
            p[AcidParam.ENV_MOD] = 0.68f
            p[AcidParam.DECAY] = 0.55f
            p[AcidParam.ACCENT] = 0.82f
            p[AcidParam.DIST_TYPE] = 0f
            p[AcidParam.DIST_DRIVE] = 0.55f
            p[AcidParam.DIST_MIX] = 0.55f
            p[AcidParam.DIST_TONE] = 0.62f
            p[AcidParam.VOLUME] = 0.9f
            clip.params = p
            val notes = intArrayOf(0, 0, 12, 0, 7, 12)
            val gates = intArrayOf(0, 3, 6, 8, 11, 14)
            val steps = MutableList(ClipLimits.STEPS) { Step() }
            gates.forEachIndexed { i, step ->
                val accent = i == 0 || i == 3
                steps[step] = Step(
                    gate = true,
                    note = (clip.root + notes[i]).coerceIn(0, 127),
                    velocity = if (accent) 118 else 92,
                    accent = accent,
                    slide = i == 5,
                )
            }
            clip.steps = steps
            clip.name = "ACID ${(clip.index + 1).toString().padStart(2, '0')}"
        }
        ClipKind.WAVE -> {
            clip.root = 48
            clip.resetParams()
            val p = clip.params.copyOf()
            p[WaveParam.BANK] = 0f
            p[WaveParam.POSITION] = 0.3f
            p[WaveParam.UNISON] = 3f
            p[WaveParam.DETUNE] = 16f
            p[WaveParam.SPREAD] = 0.5f
            p[WaveParam.CUTOFF] = 2200f
            p[WaveParam.RESONANCE] = 0.7f
            p[WaveParam.DRIVE] = 1.2f
            p[WaveParam.ATTACK] = 0.01f
            p[WaveParam.DECAY] = 0.4f
            p[WaveParam.SUSTAIN] = 0.6f
            p[WaveParam.RELEASE] = 0.5f
            p[WaveParam.SUB] = 0.2f
            p[WaveParam.NOISE] = 0f
            p[WaveParam.LEVEL] = 0.8f
            p[WaveParam.FILTER_MODE] = 0f
            p[WaveParam.CHORD] = 2f
            clip.params = p
            val notes = intArrayOf(0, 3, 7, 12)
            val gates = intArrayOf(0, 4, 8, 12)
            val steps = MutableList(ClipLimits.STEPS) { Step() }
            gates.forEachIndexed { i, step ->
                steps[step] = Step(
                    gate = true,
                    note = (clip.root + notes[i]).coerceIn(0, 127),
                    velocity = 100,
                )
            }
            clip.steps = steps
            clip.name = "WAVE ${(clip.index + 1).toString().padStart(2, '0')}"
        }
        ClipKind.DRUMS -> {
            clip.root = 36
            clip.resetParams()
            val p = clip.params.copyOf()
            p[DrumParam.TUNE] = 0.5f
            p[DrumParam.DECAY] = 0.5f
            p[DrumParam.SNAPPY] = 0.5f
            p[DrumParam.TONE] = 0.5f
            p[DrumParam.ACCENT] = 0.7f
            p[DrumParam.LEVEL] = 0.9f
            p[DrumParam.CUTOFF] = 18000f
            p[DrumParam.RESONANCE] = 0.6f
            clip.params = p
            val masks = IntArray(ClipLimits.DRUM_VOICES)
            val velocities = IntArray(ClipLimits.DRUM_VOICES * ClipLimits.STEPS) { 100 }
            for (step in intArrayOf(0, 4, 8, 12)) {
                masks[0] = masks[0] or (1 shl step)
                velocities[step] = 120
            }
            for (step in intArrayOf(4, 12)) {
                masks[1] = masks[1] or (1 shl step)
                velocities[ClipLimits.STEPS + step] = 112
            }
            for (step in 0 until 16 step 2) {
                masks[7] = masks[7] or (1 shl step)
                velocities[7 * ClipLimits.STEPS + step] = if (step % 4 == 0) 92 else 70
            }
            masks[8] = masks[8] or (1 shl 14)
            velocities[8 * ClipLimits.STEPS + 14] = 84
            masks[6] = masks[6] or (1 shl 11)
            velocities[6 * ClipLimits.STEPS + 11] = 96
            clip.drumMask = masks
            clip.drumVel = velocities
            clip.steps = List(ClipLimits.STEPS) { Step() }
            clip.name = "909 ${(clip.index + 1).toString().padStart(2, '0')}"
        }
        ClipKind.SAMPLE -> {
            clip.root = 60
            clip.resetParams()
            val p = clip.params.copyOf()
            p[SampleParam.GAIN] = 1f
            p[SampleParam.PITCH] = 0f
            clip.params = p
            clip.steps = List(ClipLimits.STEPS) { Step() }
            if (clip.sampleName == null) clip.sampleName = "NO SAMPLE"
            if (clip.name.isBlank()) clip.name = "SMP ${(clip.index + 1).toString().padStart(2, '0')}"
        }
    }
    if (kind != ClipKind.EMPTY && previousFx != 0) {
        clip.applyFxDefaults(previousFx)
        clip.fxEnabled = previousFxEnabled
    }
}

class ProjectState {
    var name by mutableStateOf("NEON GRID")
    var bpm by mutableFloatStateOf(120f)
    var quantize by mutableIntStateOf(16)
    var swing by mutableFloatStateOf(0f)
    var masterGain by mutableFloatStateOf(0.85f)
    val clips: List<Clip> = List(ClipLimits.PADS) { Clip(it) }

    fun hasAnyClip(): Boolean = clips.any { it.kind != ClipKind.EMPTY }
}
