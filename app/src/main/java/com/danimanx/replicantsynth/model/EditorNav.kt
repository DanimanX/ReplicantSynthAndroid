package com.danimanx.replicantsynth.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Hardware navigation model for the clip editor.
 *
 * The APC64 cursor cluster walks the editor without a mouse: up/down change the
 * menu page, left/right walk the fields of that page, the encoder edits the
 * focused field and pressing the encoder activates it. This file holds the
 * pure part — the page/field layout per clip kind and the cursor arithmetic —
 * so it can be unit-tested without an Android runtime. The ViewModel applies
 * the values through its existing clip mutators.
 */
enum class NavKey {
    // CLIP page
    LAUNCH, LENGTH, ROOT, GAIN, PAN, MUTE, SOLO,

    // SAMPLE extras
    LOOP, PITCH,

    // SOUND page
    SYNTH,

    // STEP / ROLL pages
    STEP, NOTE, VOICE, HIT, GATE, ACCENT, SLIDE, VELOCITY,
    ROLL_NOTE, MIDI_LENGTH, TOGGLE_NOTE, CLEAR_NOTES,
    OCTAVE, CHORD, RANDOM, CLEAR,

    // FX page
    FX_ON, FX_SLOT, MACRO, OPEN_FX,

    // PRESET page
    PRESET, LOAD_PRESET,
}

/** One focusable control inside an editor page. */
sealed interface NavField {
    val key: NavKey
    val label: String

    /** Extra index for repeated keys: the six FX macros. */
    val slot: Int

    data class Number(
        override val key: NavKey,
        override val label: String,
        val min: Float,
        val max: Float,
        val step: Float,
        override val slot: Int = 0,
    ) : NavField

    data class Toggle(
        override val key: NavKey,
        override val label: String,
        override val slot: Int = 0,
    ) : NavField

    data class Choice(
        override val key: NavKey,
        override val label: String,
        val options: List<String>,
        override val slot: Int = 0,
    ) : NavField

    data class Action(
        override val key: NavKey,
        override val label: String,
        override val slot: Int = 0,
    ) : NavField
}

data class NavPage(val title: String, val fields: List<NavField>)

/**
 * Cursor state that lives outside the clip but is still needed to render and
 * edit a field: which step is focused, which pitch row the piano roll targets,
 * which preset the browser is pointing at.
 */
data class NavCursors(
    val step: Int = 0,
    val voice: Int = 0,
    val rollNote: Int = 60,
    val rollLength: Int = 1,
    val rollVelocity: Int = 100,
    val octave: Int = 0,
    val presetIndex: Int = 0,
)

/** Page / field cursor. All mutations go through the list of live pages. */
class EditorNav {
    var page by mutableIntStateOf(0)
        private set
    var item by mutableIntStateOf(0)
        private set

    fun reset() {
        page = 0
        item = 0
    }

    /** Keep the cursor inside the current page list after a kind change. */
    fun clamp(pages: List<NavPage>) {
        if (pages.isEmpty()) {
            reset()
            return
        }
        if (page !in pages.indices) page = page.coerceIn(0, pages.lastIndex)
        val last = pages[page].fields.lastIndex
        item = if (last < 0) 0 else item.coerceIn(0, last)
    }

    /** Pages form a ring: down from the last menu wraps to the first. */
    fun movePage(delta: Int, pages: List<NavPage>) {
        if (pages.isEmpty()) {
            reset()
            return
        }
        page = (page + delta).mod(pages.size)
        val last = pages[page].fields.lastIndex
        item = if (last < 0) 0 else item.coerceIn(0, last)
    }

    /** Fields clamp: you can feel the top and bottom of a menu. */
    fun moveItem(delta: Int, pages: List<NavPage>) {
        val fields = pages.getOrNull(page)?.fields ?: return
        if (fields.isEmpty()) return
        item = (item + delta).coerceIn(0, fields.lastIndex)
    }

    fun selectPage(index: Int, pages: List<NavPage>) {
        if (pages.isEmpty()) {
            reset()
            return
        }
        page = index.coerceIn(0, pages.lastIndex)
        val last = pages[page].fields.lastIndex
        item = if (last < 0) 0 else item.coerceIn(0, last)
    }

    fun selectItem(index: Int, pages: List<NavPage>) {
        val fields = pages.getOrNull(page)?.fields ?: return
        if (fields.isEmpty()) return
        item = index.coerceIn(0, fields.lastIndex)
    }

    fun field(pages: List<NavPage>): NavField? =
        pages.getOrNull(page)?.fields?.getOrNull(item)

    fun pageTitle(pages: List<NavPage>): String = pages.getOrNull(page)?.title.orEmpty()

    fun pageFields(pages: List<NavPage>): List<NavField> =
        pages.getOrNull(page)?.fields.orEmpty()
}

// ---------------------------------------------------------------------------
// Sound knobs
// ---------------------------------------------------------------------------

/** One knob of the SOUND page: an engine parameter exposed to menus and strips. */
data class SynthKnob(
    val param: Int,
    val label: String,
    val min: Float,
    val max: Float,
)

/**
 * The six primary knobs per engine. These are the first row of the SOUND tab and
 * the six adaptive touch strips, so the hardware and the screen edit the same
 * parameters in the same order.
 */
fun synthKnobs(kind: ClipKind): List<SynthKnob> = when (kind) {
    ClipKind.ACID -> listOf(
        SynthKnob(AcidParam.CUTOFF, "CUTOFF", 40f, 4200f),
        SynthKnob(AcidParam.RESONANCE, "RESO", 0f, 1f),
        SynthKnob(AcidParam.ENV_MOD, "ENV MOD", 0f, 1f),
        SynthKnob(AcidParam.DECAY, "DECAY", 0.2f, 2.5f),
        SynthKnob(AcidParam.ACCENT, "ACCENT", 0f, 1f),
        SynthKnob(AcidParam.VOLUME, "VOLUME", 0f, 1.2f),
    )
    ClipKind.WAVE -> listOf(
        SynthKnob(WaveParam.CUTOFF, "CUTOFF", 60f, 12000f),
        SynthKnob(WaveParam.RESONANCE, "RESO", 0.5f, 14f),
        SynthKnob(WaveParam.POSITION, "POS", 0f, 1f),
        SynthKnob(WaveParam.DETUNE, "DETUNE", 0f, 60f),
        SynthKnob(WaveParam.SPREAD, "SPREAD", 0f, 1f),
        SynthKnob(WaveParam.LEVEL, "LEVEL", 0f, 1.2f),
    )
    ClipKind.DRUMS -> listOf(
        SynthKnob(DrumParam.TUNE, "TUNE", 0f, 1f),
        SynthKnob(DrumParam.DECAY, "DECAY", 0f, 1f),
        SynthKnob(DrumParam.SNAPPY, "SNIPPY", 0f, 1f),
        SynthKnob(DrumParam.TONE, "TONE", 0f, 1f),
        SynthKnob(DrumParam.ACCENT, "ACCENT", 0f, 1f),
        SynthKnob(DrumParam.LEVEL, "LEVEL", 0f, 1.2f),
    )
    ClipKind.SAMPLE -> listOf(
        SynthKnob(SampleParam.GAIN, "GAIN", 0f, 1.5f),
        SynthKnob(SampleParam.PITCH, "PITCH", -24f, 24f),
    )
    ClipKind.EMPTY -> emptyList()
}

// ---------------------------------------------------------------------------
// Page layout
// ---------------------------------------------------------------------------

private val MIDI_LENGTH_LABELS = listOf("1/16", "1/8", "1/4", "1/2")
private val MIDI_LENGTH_STEPS = listOf(1, 2, 4, 8)

private val FX_SLOT_NAMES: List<String> by lazy {
    FX_CATALOG.map { "${it.code} ${it.name}" }
}

private val ACID_PRESET_NAMES: List<String> by lazy {
    AcidPresets.all.map { it.displayName }
}

private val WAVE_PRESET_NAMES: List<String> by lazy {
    WAVE_PRESETS.map { "${it.category.uppercase()} · ${it.name}" }
}

private val DRUM_PRESET_NAMES: List<String> by lazy {
    DrumPresets.all.map { "${it.category.uppercase()} · ${it.name}" }
}

/** Preset names for a clip kind, in the same order as [presetAt]. */
fun presetNamesFor(kind: ClipKind): List<String> = when (kind) {
    ClipKind.ACID -> ACID_PRESET_NAMES
    ClipKind.WAVE -> WAVE_PRESET_NAMES
    ClipKind.DRUMS -> DRUM_PRESET_NAMES
    else -> emptyList()
}

/** The preset behind an index produced by [presetNamesFor]. */
fun presetAt(kind: ClipKind, index: Int): Any? = when (kind) {
    ClipKind.ACID -> AcidPresets.all.getOrNull(index)
    ClipKind.WAVE -> WAVE_PRESETS.getOrNull(index)
    ClipKind.DRUMS -> DrumPresets.all.getOrNull(index)
    else -> null
}

/**
 * The menus the APC64 walks for this clip. Empty clips have no pages: there is
 * nothing to edit until an engine is assigned.
 */
fun editorPages(clip: Clip): List<NavPage> {
    if (clip.kind == ClipKind.EMPTY) return emptyList()
    val pages = mutableListOf(NavPage("CLIP", clipFields(clip)))
    soundPage(clip)?.let { pages += it }
    patternPage(clip)?.let { pages += it }
    pages += NavPage("FX", fxFields())
    if (presetNamesFor(clip.kind).isNotEmpty()) {
        pages += NavPage(
            "PRESET",
            listOf(
                NavField.Choice(NavKey.PRESET, "PRESET", presetNamesFor(clip.kind)),
                NavField.Action(NavKey.LOAD_PRESET, "LOAD"),
            ),
        )
    }
    return pages
}

private fun clipFields(clip: Clip): List<NavField> = buildList {
    add(NavField.Action(NavKey.LAUNCH, "LAUNCH"))
    add(NavField.Number(NavKey.LENGTH, "LENGTH", 1f, ClipLimits.STEPS.toFloat(), 1f))
    add(NavField.Number(NavKey.ROOT, "ROOT", 0f, 127f, 1f))
    add(NavField.Number(NavKey.GAIN, "GAIN", 0f, 1.5f, 0.05f))
    add(NavField.Number(NavKey.PAN, "PAN", -1f, 1f, 0.1f))
    add(NavField.Toggle(NavKey.MUTE, "MUTE"))
    add(NavField.Toggle(NavKey.SOLO, "SOLO"))
    if (clip.kind == ClipKind.SAMPLE) {
        add(NavField.Toggle(NavKey.LOOP, "LOOP"))
        add(NavField.Number(NavKey.PITCH, "PITCH", -24f, 24f, 1f))
    }
}

private fun soundPage(clip: Clip): NavPage? {
    val knobs = synthKnobs(clip.kind)
    if (knobs.isEmpty()) return null
    return NavPage(
        "SOUND",
        knobs.mapIndexed { index, knob ->
            NavField.Number(
                key = NavKey.SYNTH,
                label = knob.label,
                min = knob.min,
                max = knob.max,
                step = (knob.max - knob.min) / 40f,
                slot = index,
            )
        },
    )
}

private fun patternPage(clip: Clip): NavPage? = when (clip.kind) {
    ClipKind.ACID -> NavPage(
        "STEPS",
        listOf(
            NavField.Number(NavKey.STEP, "STEP", 1f, ClipLimits.STEPS.toFloat(), 1f),
            NavField.Number(NavKey.NOTE, "NOTE", 0f, 127f, 1f),
            NavField.Toggle(NavKey.GATE, "GATE"),
            NavField.Toggle(NavKey.ACCENT, "ACCENT"),
            NavField.Toggle(NavKey.SLIDE, "SLIDE"),
            NavField.Number(NavKey.VELOCITY, "VELOCITY", 1f, 127f, 5f),
            NavField.Action(NavKey.RANDOM, "RANDOM"),
            NavField.Action(NavKey.CLEAR, "CLEAR"),
        ),
    )
    ClipKind.DRUMS -> NavPage(
        "STEPS",
        listOf(
            NavField.Number(NavKey.STEP, "STEP", 1f, ClipLimits.STEPS.toFloat(), 1f),
            NavField.Number(NavKey.VOICE, "VOICE", 0f, (ClipLimits.DRUM_VOICES - 1).toFloat(), 1f),
            NavField.Toggle(NavKey.HIT, "HIT"),
            NavField.Number(NavKey.VELOCITY, "VELOCITY", 1f, 127f, 5f),
            NavField.Action(NavKey.RANDOM, "RANDOM"),
            NavField.Action(NavKey.CLEAR, "CLEAR"),
        ),
    )
    ClipKind.WAVE -> NavPage(
        "PIANO",
        listOf(
            NavField.Number(NavKey.STEP, "STEP", 1f, ClipLimits.STEPS.toFloat(), 1f),
            NavField.Number(NavKey.ROLL_NOTE, "NOTE", 0f, 127f, 1f),
            NavField.Action(NavKey.TOGGLE_NOTE, "TOGGLE"),
            NavField.Choice(NavKey.MIDI_LENGTH, "LENGTH", MIDI_LENGTH_LABELS),
            NavField.Number(NavKey.VELOCITY, "VELOCITY", 1f, 127f, 5f),
            NavField.Number(NavKey.OCTAVE, "OCTAVE", -2f, 2f, 1f),
            NavField.Choice(NavKey.CHORD, "CHORD", CHORD_MODES),
            NavField.Action(NavKey.CLEAR_NOTES, "CLEAR"),
        ),
    )
    else -> null
}

private fun fxFields(): List<NavField> = buildList {
    add(NavField.Toggle(NavKey.FX_ON, "FX ON"))
    add(NavField.Choice(NavKey.FX_SLOT, "FX SLOT", FX_SLOT_NAMES))
    for (index in 0 until 6) {
        add(NavField.Number(NavKey.MACRO, "MACRO ${index + 1}", 0f, 1f, 0.05f, slot = index))
    }
    add(NavField.Action(NavKey.OPEN_FX, "BROWSE"))
}

// ---------------------------------------------------------------------------
// Values
// ---------------------------------------------------------------------------

/** Current numeric value of a [NavField.Number]. Steps are reported 1-based. */
fun navNumberValue(clip: Clip, field: NavField.Number, cursors: NavCursors): Float =
    when (field.key) {
        NavKey.LENGTH -> clip.length.toFloat()
        NavKey.ROOT -> clip.root.toFloat()
        NavKey.GAIN -> clip.gain
        NavKey.PAN -> clip.pan
        NavKey.PITCH -> clip.param(SampleParam.PITCH)
        NavKey.STEP -> (cursors.step + 1).toFloat()
        NavKey.NOTE -> clip.steps.getOrElse(cursors.step) { Step() }.note.toFloat()
        NavKey.VOICE -> cursors.voice.toFloat()
        NavKey.VELOCITY -> when (clip.kind) {
            ClipKind.DRUMS -> clip.drumVelocity(cursors.voice, cursors.step).toFloat()
            ClipKind.WAVE -> cursors.rollVelocity.toFloat()
            else -> clip.steps.getOrElse(cursors.step) { Step() }.velocity.toFloat()
        }
        NavKey.ROLL_NOTE -> cursors.rollNote.toFloat()
        NavKey.OCTAVE -> cursors.octave.toFloat()
        NavKey.MACRO -> clip.param(FxParam.BASE + field.slot)
        NavKey.SYNTH -> synthKnobs(clip.kind)
            .getOrNull(field.slot)
            ?.let { clip.param(it.param) } ?: 0f
        else -> 0f
    }

/** Index of the selected option for a [NavField.Choice]. */
fun navChoiceIndex(clip: Clip, field: NavField.Choice, cursors: NavCursors): Int =
    when (field.key) {
        NavKey.FX_SLOT -> FX_CATALOG.indexOfFirst { it.id == clip.fxType }.coerceAtLeast(0)
        NavKey.PRESET -> cursors.presetIndex.coerceIn(0, (field.options.size - 1).coerceAtLeast(0))
        NavKey.MIDI_LENGTH -> MIDI_LENGTH_STEPS.indexOf(cursors.rollLength).coerceAtLeast(0)
        NavKey.CHORD -> clip.param(WaveParam.CHORD).toInt()
            .coerceIn(0, (field.options.size - 1).coerceAtLeast(0))
        else -> 0
    }

/** MIDI length (in 16th steps) for a piano-roll length option. */
fun midiLengthSteps(index: Int): Int = MIDI_LENGTH_STEPS.getOrElse(index) { 1 }

/** One-line value shown on screen and on the OLED. */
fun navValueText(clip: Clip, field: NavField, cursors: NavCursors): String = when (field) {
    is NavField.Number -> when (field.key) {
        NavKey.ROOT, NavKey.NOTE, NavKey.ROLL_NOTE ->
            navNoteName(navNumberValue(clip, field, cursors).toInt())
        NavKey.GAIN, NavKey.MACRO ->
            "${(navNumberValue(clip, field, cursors) * 100f).toInt()}%"
        NavKey.PAN -> {
            val pan = navNumberValue(clip, field, cursors)
            when {
                pan < -0.02f -> "L${(-pan * 100f).toInt()}"
                pan > 0.02f -> "R${(pan * 100f).toInt()}"
                else -> "C"
            }
        }
        NavKey.PITCH -> "${navNumberValue(clip, field, cursors).toInt()} st"
        NavKey.SYNTH -> {
            val knob = synthKnobs(clip.kind).getOrNull(field.slot)
            val value = navNumberValue(clip, field, cursors)
            when {
                knob == null -> "${value.toInt()}"
                knob.param == AcidParam.CUTOFF || knob.param == WaveParam.CUTOFF ->
                    navHertz(value)
                knob.param == SampleParam.PITCH -> "${value.toInt()} st"
                else -> "${(value * 100f).toInt()}%"
            }
        }
        NavKey.STEP, NavKey.LENGTH ->
            "${navNumberValue(clip, field, cursors).toInt()}/${ClipLimits.STEPS}"
        NavKey.VOICE ->
            DRUM_VOICE_NAMES.getOrElse(navNumberValue(clip, field, cursors).toInt()) { "--" }
        NavKey.OCTAVE -> {
            val octave = navNumberValue(clip, field, cursors).toInt()
            if (octave > 0) "+$octave" else "$octave"
        }
        else -> "${navNumberValue(clip, field, cursors).toInt()}"
    }
    is NavField.Toggle -> navToggleState(clip, field, cursors)
    is NavField.Choice ->
        field.options.getOrNull(navChoiceIndex(clip, field, cursors)).orEmpty()
    is NavField.Action -> "PRESS"
}

private fun navToggleState(clip: Clip, field: NavField.Toggle, cursors: NavCursors): String =
    when (field.key) {
        NavKey.MUTE -> clip.mute
        NavKey.SOLO -> clip.solo
        NavKey.LOOP -> clip.sampleLoop
        NavKey.GATE -> clip.steps.getOrElse(cursors.step) { Step() }.gate
        NavKey.ACCENT -> clip.steps.getOrElse(cursors.step) { Step() }.accent
        NavKey.SLIDE -> clip.steps.getOrElse(cursors.step) { Step() }.slide
        NavKey.HIT -> clip.drumOn(cursors.voice, cursors.step)
        NavKey.FX_ON -> clip.fxEnabled
        else -> false
    }.let { if (it) "ON" else "OFF" }

private fun navHertz(value: Float): String = when {
    value >= 10000f -> "%.1fk".format(value / 1000f)
    value >= 1000f -> "%.2fk".format(value / 1000f)
    else -> value.toInt().toString()
}

// ---------------------------------------------------------------------------
// Touch strips
// ---------------------------------------------------------------------------

/** LED colour family of a strip slot; the MIDI layer maps it to the palette. */
enum class StripTone { AMBER, CYAN, GREEN, MAGENTA, WHITE }

/** What a strip edits. [NavNumber] is a field of the focused page. */
sealed interface StripTarget {
    data object MasterGain : StripTarget
    data object PadGain : StripTarget

    /** [index] is the field's position in the page, for focusing it on touch. */
    data class NavNumber(val field: NavField.Number, val index: Int) : StripTarget

    data object Idle : StripTarget
}

/**
 * One of the eight touch strips. [value] is normalised 0..1 because that is what
 * the bar display and the incoming pitch bend use.
 */
data class StripSlot(
    val label: String,
    val value: Float,
    val tone: StripTone = StripTone.WHITE,
    val bipolar: Boolean = false,
    val target: StripTarget = StripTarget.Idle,
)

/** Master and pad gain are fixed; the six middle strips follow the focused menu. */
const val STRIP_SLOTS = 8
const val STRIP_MASTER = 0
const val STRIP_PAD_GAIN = 7
const val STRIP_ADAPTIVE_COUNT = 6
const val STRIP_GAIN_MAX = 1.5f

private fun toneFor(key: NavKey): StripTone = when (key) {
    NavKey.SYNTH -> StripTone.AMBER
    NavKey.MACRO -> StripTone.MAGENTA
    NavKey.PAN -> StripTone.CYAN
    else -> StripTone.GREEN
}

/**
 * Adaptive strip layout: strip 1 is always the master gain, strip 8 the pad
 * gain, and strips 2–7 are the first six continuous fields of the focused page
 * — the six synth knobs on SOUND, the six macros on FX, and so on. Pages with
 * fewer fields leave the remaining strips idle instead of guessing.
 */
fun stripLayout(
    clip: Clip,
    masterGain: Float,
    pages: List<NavPage>,
    pageIndex: Int,
    cursors: NavCursors,
): List<StripSlot> {
    val slots = MutableList(STRIP_SLOTS) { StripSlot("", 0f, target = StripTarget.Idle) }
    slots[STRIP_MASTER] = StripSlot(
        label = "MASTER",
        value = (masterGain / STRIP_GAIN_MAX).coerceIn(0f, 1f),
        tone = StripTone.WHITE,
        target = StripTarget.MasterGain,
    )
    slots[STRIP_PAD_GAIN] = StripSlot(
        label = "PAD",
        value = (clip.gain / STRIP_GAIN_MAX).coerceIn(0f, 1f),
        tone = StripTone.CYAN,
        target = StripTarget.PadGain,
    )
    val fields = pages.getOrNull(pageIndex)?.fields.orEmpty()
    fields.forEachIndexed { fieldIndex, field ->
        if (field !is NavField.Number) return@forEachIndexed
        val mapped = fields.take(fieldIndex).count { it is NavField.Number }
        if (mapped >= STRIP_ADAPTIVE_COUNT) return@forEachIndexed
        val span = (field.max - field.min).takeIf { it > 0f } ?: 1f
        slots[STRIP_MASTER + 1 + mapped] = StripSlot(
            label = field.label,
            value = ((navNumberValue(clip, field, cursors) - field.min) / span)
                .coerceIn(0f, 1f),
            tone = toneFor(field.key),
            bipolar = field.key == NavKey.PAN,
            target = StripTarget.NavNumber(field, fieldIndex),
        )
    }
    return slots
}

/** Map a normalised strip position back to the field's own range. */
fun stripToFieldValue(field: NavField.Number, normalized: Float): Float {
    val span = (field.max - field.min).takeIf { it > 0f } ?: 1f
    return (field.min + normalized.coerceIn(0f, 1f) * span)
        .coerceIn(field.min, field.max)
}

/** `C#3`-style label; the model layer must not depend on the UI package. */
fun navNoteName(note: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val value = note.coerceIn(0, 127)
    return "${names[value % 12]}${value / 12 - 1}"
}
