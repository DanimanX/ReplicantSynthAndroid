package com.danimanx.replicantsynth.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round

/**
 * Factory content for the three Replicant engines, ported from the desktop
 * projects:
 *  - Replicant303       8 banks x 64 acid lines (deterministic generator)
 *  - Replicant909       8 classic drum patterns
 *  - ReplicantWave      44 cyberpunk sounds (see WavePresets.kt)
 *
 * A preset only replaces the sound/pattern of a clip. The channel strip, the
 * ReplicantFX insert and its six macros are left untouched.
 */
data class WavePreset(
    val name: String,
    val category: String,
    val baseNote: Int,
    val params: FloatArray,
    val notes: List<MidiNote>,
)

data class AcidPreset(
    val name: String,
    val bank: Int,
    val slot: Int,
    val root: Int,
    val length: Int,
    val params: FloatArray,
    val steps: List<Step>,
) {
    val bankLetter: String get() = ('A'.code + bank).toChar().toString()
    val displayName: String
        get() = "$bankLetter${(slot + 1).toString().padStart(2, '0')} · $name"
}

data class DrumPreset(
    val name: String,
    val category: String,
    val accent: Float,
    val masks: IntArray,
    val velocities: IntArray,
)

// ---------------------------------------------------------------------------
// Replicant303 factory bank (port of Replicant303 PresetBank)
// ---------------------------------------------------------------------------

object AcidPresets {
    const val BANKS = 8
    const val PER_BANK = 64
    const val COUNT = BANKS * PER_BANK

    val BANK_NAMES = listOf(
        "AMBER", "SUB", "NEON", "ACID", "BLADE", "ROOFTOP", "OFF WORLD", "LAST",
    )

    private val FACTORY_NAMES = listOf(
        "Amber Line", "Sub Level", "Neon Run", "Acid Rain",
        "Blade Pulse", "Rooftop Chase", "Off World", "Last Replicant",
    )

    private val ADJECTIVES = listOf(
        "Amber", "Sub", "Neon", "Acid", "Blade", "Rooftop", "Off", "Last",
        "Chrome", "Static", "Velvet", "Rust", "Ghost", "Silver", "Crimson",
        "Electric", "Hollow", "Bright", "Dark", "Fever", "Glass", "Iron",
        "Lunar", "Magnetic", "Midnight", "Mirror", "Molten", "Obsidian",
        "Phantom", "Plastic", "Prism", "Quartz", "Radiant", "Reactor",
        "Rebel", "Redline", "Satin", "Scarlet", "Secret", "Shadow", "Silent",
        "Solar", "Sonic", "Spectral", "Steel", "Storm", "Subzero", "Sunken",
        "Tidal", "Toxic", "Turbo", "Twilight", "Ultraviolet", "Vacuum",
        "Vapor", "Velour", "Violet", "Voltage", "Wired", "Wraith", "Zero",
        "Zenith", "Cobalt", "Carbon",
    )

    private val NOUNS = listOf(
        "Line", "Level", "Run", "Rain", "Pulse", "Chase", "World", "Replicant",
        "Circuit", "Signal", "Engine", "Wire", "Grid", "Vault", "Machine",
        "Protocol", "Sequence", "District", "Corridor", "Frequency", "Reactor",
        "Tempo", "Voltage", "Current", "Motion", "Shadow", "Mirror", "Ladder",
        "Filter", "Oscillator", "Groove", "Pattern", "Sector", "Tunnel",
        "Chamber", "Beacon", "Antenna", "Archive", "Bishop", "Cipher", "Column",
        "Compass", "Console", "Cortex", "Cradle", "Cursor", "Delta", "Divider",
        "Driver", "Echo", "Ember", "Envelope", "Fabric", "Factory", "Frame",
        "Horizon", "Kernel", "Lantern", "Lattice", "Matrix", "Meridian",
        "Monolith", "Neuron", "Orbit",
    )

    private val SCALES = arrayOf(
        intArrayOf(0, 3, 5, 7, 10, 12, 15, 19),
        intArrayOf(0, 2, 3, 5, 7, 8, 10, 12),
        intArrayOf(0, 2, 4, 7, 9, 12, 14, 16),
        intArrayOf(0, 1, 3, 5, 6, 8, 10, 12),
        intArrayOf(0, 3, 5, 6, 7, 10, 12, 15),
        intArrayOf(0, 2, 3, 5, 7, 9, 10, 12),
        intArrayOf(0, 2, 3, 5, 7, 8, 11, 12),
        intArrayOf(0, 1, 2, 4, 6, 7, 10, 12),
    )

    private val LENGTHS = intArrayOf(16, 16, 16, 16, 16, 12, 8, 7)

    private class DefaultLine(
        val notes: IntArray,
        val accents: IntArray,
        val slides: IntArray,
        val muted: IntArray,
        val length: Int,
    )

    private val DEFAULT_LINES = arrayOf(
        DefaultLine(
            intArrayOf(36, 36, 48, 36, 39, 36, 43, 36, 36, 48, 36, 39, 36, 43, 36, 36),
            intArrayOf(1, 6, 10), intArrayOf(4, 8), intArrayOf(3, 11), 16,
        ),
        DefaultLine(
            intArrayOf(24, 24, 36, 24, 27, 24, 31, 24, 24, 36, 24, 27, 31, 24, 24, 24),
            intArrayOf(1, 5, 9, 13), intArrayOf(2, 10), intArrayOf(7, 15), 16,
        ),
        DefaultLine(
            intArrayOf(36, 48, 36, 43, 36, 48, 39, 36, 36, 43, 48, 36, 39, 36, 43, 36),
            intArrayOf(1, 3, 5, 7, 9, 11, 13, 15), intArrayOf(2, 6, 10, 14), intArrayOf(), 16,
        ),
        DefaultLine(
            intArrayOf(24, 36, 24, 31, 24, 36, 27, 24, 31, 36, 24, 27, 24, 31, 36, 24),
            intArrayOf(0, 4, 8, 12), intArrayOf(1, 5, 9, 13), intArrayOf(6, 14), 16,
        ),
        DefaultLine(
            intArrayOf(36, 36, 39, 36, 43, 36, 46, 43, 36, 36, 39, 43, 36, 36, 31, 36),
            intArrayOf(3, 7, 11, 15), intArrayOf(4, 12), intArrayOf(1, 9), 16,
        ),
        DefaultLine(
            intArrayOf(48, 36, 48, 39, 48, 43, 48, 36, 46, 36, 46, 39, 43, 39, 36, 36),
            intArrayOf(0, 2, 6, 8, 12, 14), intArrayOf(2, 6, 10, 14), intArrayOf(4, 12), 16,
        ),
        DefaultLine(
            intArrayOf(24, 27, 24, 36, 24, 31, 27, 24, 36, 24, 27, 31, 24, 27, 24, 24),
            intArrayOf(1, 5, 9, 13), intArrayOf(3, 7, 11, 15), intArrayOf(2, 10), 16,
        ),
        DefaultLine(
            intArrayOf(36, 31, 36, 43, 36, 31, 39, 36, 36, 31, 36, 43, 46, 43, 39, 36),
            intArrayOf(0, 3, 6, 9, 12, 15), intArrayOf(1, 5, 9, 13), intArrayOf(7, 14), 16,
        ),
    )

    /** SplitMix32, the same deterministic PRNG the desktop bank ships with. */
    private class FastRandom(seed: UInt) {
        private var state: UInt = if (seed != 0u) seed else 0x30303030u

        fun next(): UInt {
            state += 0x9e3779b9u
            var z = state
            z = (z xor (z shr 16)) * 0x85ebca6bu
            z = (z xor (z shr 13)) * 0xc2b2ae35u
            return z xor (z shr 16)
        }

        fun nextFloat(): Float = (next() shr 8).toFloat() * (1.0f / 16777216.0f)

        fun nextInt(low: Int, high: Int): Int {
            val span = (high - low + 1).toUInt()
            return low + (next() % span).toInt()
        }

        fun between(low: Float, high: Float): Float = low + (high - low) * nextFloat()

        fun chance(probability: Float): Boolean = nextFloat() < probability
    }

    private fun makeSeed(bank: Int, slot: Int): UInt =
        ((bank + 1).toUInt() * 0x9e3779b9u) xor
            ((slot + 1).toUInt() * 0x85ebca6bu) xor 0x52443303u

    private fun logBetween(rng: FastRandom, low: Float, high: Float): Float =
        exp(rng.between(ln(low), ln(high)))

    private fun generatedName(bank: Int, slot: Int): String {
        val adjective = (slot * 7 + bank * 11) % 64
        val noun = (slot * 17 + bank * 23) % 64
        var name = "${ADJECTIVES[adjective]} ${NOUNS[noun]}"
        if (name in FACTORY_NAMES) name += " II"
        return name
    }

    private fun defaultParams(): FloatArray = FloatArray(ClipLimits.PARAMS).also { p ->
        p[AcidParam.WAVE] = 0f
        p[AcidParam.CUTOFF] = 350f
        p[AcidParam.RESONANCE] = 0.65f
        p[AcidParam.ENV_MOD] = 0.60f
        p[AcidParam.DECAY] = 0.75f
        p[AcidParam.ACCENT] = 0.70f
        p[AcidParam.DIST_TYPE] = 0f
        p[AcidParam.DIST_DRIVE] = 0.50f
        p[AcidParam.DIST_MIX] = 0.85f
        p[AcidParam.DIST_TONE] = 0.60f
        p[AcidParam.VOLUME] = 0.90f
    }

    private fun defaultSteps(line: DefaultLine): List<Step> {
        val steps = MutableList(ClipLimits.STEPS) { Step() }
        for (i in 0 until ClipLimits.STEPS) {
            steps[i] = Step(
                gate = i !in line.muted,
                note = line.notes[i].coerceIn(24, 48),
                velocity = if (i in line.accents) 118 else 92,
                accent = i in line.accents,
                slide = i in line.slides,
            )
        }
        return steps
    }

    private fun presetFrom(
        name: String,
        bank: Int,
        slot: Int,
        length: Int,
        steps: List<Step>,
        params: FloatArray,
    ): AcidPreset {
        val root = steps.firstOrNull { it.gate }?.note ?: 36
        return AcidPreset(
            name = name,
            bank = bank,
            slot = slot,
            root = root,
            length = length.coerceIn(1, ClipLimits.STEPS),
            params = params,
            steps = steps,
        )
    }

    fun preset(bank: Int, slot: Int): AcidPreset {
        val b = bank.coerceIn(0, BANKS - 1)
        val s = slot.coerceIn(0, PER_BANK - 1)
        if (s == 0) {
            val line = DEFAULT_LINES[b]
            return presetFrom(
                FACTORY_NAMES[b], b, s, line.length, defaultSteps(line), defaultParams(),
            )
        }
        return generated(b, s)
    }

    private fun generated(bank: Int, slot: Int): AcidPreset {
        val rng = FastRandom(makeSeed(bank, slot))
        val scale = SCALES[bank]
        val root = 24 + rng.nextInt(0, 12)
        val length = LENGTHS[rng.nextInt(0, LENGTHS.size - 1)]
        val density = rng.between(0.62f, 0.94f)
        val accentChance = rng.between(0.16f, 0.38f)
        val slideChance = rng.between(0.10f, 0.32f)

        val gates = BooleanArray(ClipLimits.STEPS)
        val notes = IntArray(ClipLimits.STEPS)
        val accents = BooleanArray(ClipLimits.STEPS)
        val slides = BooleanArray(ClipLimits.STEPS)

        var previousNote = root + scale[0]
        for (i in 0 until ClipLimits.STEPS) {
            val gate = i == 0 || rng.chance(density)
            gates[i] = gate
            val degree = rng.nextInt(0, 7)
            var octave = 0
            if (rng.chance(0.16f)) octave = 12
            if (rng.chance(0.08f)) octave = -12
            var note = root + scale[degree] + octave
            if (rng.chance(0.12f)) note = previousNote
            note = note.coerceIn(24, 48)
            notes[i] = note
            previousNote = note
            accents[i] = gate && rng.chance(accentChance)
            slides[i] = gate && rng.chance(slideChance)
        }

        var gateCount = (0 until length).count { gates[it] }
        var guard = 0
        while (gateCount < 4 && guard < 64) {
            val i = rng.nextInt(0, length - 1)
            if (!gates[i]) {
                gates[i] = true
                gateCount++
            }
            guard++
        }

        val allSameNote = (1 until length).all { notes[it] == notes[0] }
        if (allSameNote) {
            notes[length / 2] = (notes[0] + scale[3]).coerceIn(24, 48)
            notes[length - 1] = (notes[0] + scale[5]).coerceIn(24, 48)
        }
        if ((0 until length).none { accents[it] }) accents[0] = true

        val p = defaultParams()
        when ((bank + slot) % 8) {
            0 -> { // Classic acid
                p[AcidParam.WAVE] = 0f
                p[AcidParam.CUTOFF] = logBetween(rng, 170f, 900f)
                p[AcidParam.RESONANCE] = rng.between(0.55f, 0.84f)
                p[AcidParam.ENV_MOD] = rng.between(0.50f, 0.82f)
                p[AcidParam.DECAY] = rng.between(0.38f, 0.90f)
                p[AcidParam.ACCENT] = rng.between(0.60f, 0.85f)
                p[AcidParam.DIST_TYPE] = 0f
                p[AcidParam.DIST_DRIVE] = rng.between(0.28f, 0.62f)
                p[AcidParam.DIST_TONE] = rng.between(0.45f, 0.75f)
                p[AcidParam.DIST_MIX] = rng.between(0.62f, 0.90f)
            }
            1 -> { // Sub pressure
                p[AcidParam.WAVE] = 0f
                p[AcidParam.CUTOFF] = logBetween(rng, 70f, 380f)
                p[AcidParam.RESONANCE] = rng.between(0.30f, 0.68f)
                p[AcidParam.ENV_MOD] = rng.between(0.30f, 0.68f)
                p[AcidParam.DECAY] = rng.between(0.65f, 1.45f)
                p[AcidParam.ACCENT] = rng.between(0.55f, 0.82f)
                p[AcidParam.DIST_TYPE] = if (rng.chance(0.70f)) 0f else 2f
                p[AcidParam.DIST_DRIVE] = rng.between(0.18f, 0.48f)
                p[AcidParam.DIST_TONE] = rng.between(0.25f, 0.55f)
                p[AcidParam.DIST_MIX] = rng.between(0.55f, 0.82f)
            }
            2 -> { // Bright / open
                p[AcidParam.WAVE] = if (rng.chance(0.75f)) 1f else 0f
                p[AcidParam.CUTOFF] = logBetween(rng, 800f, 2600f)
                p[AcidParam.RESONANCE] = rng.between(0.35f, 0.78f)
                p[AcidParam.ENV_MOD] = rng.between(0.68f, 0.95f)
                p[AcidParam.DECAY] = rng.between(0.20f, 0.52f)
                p[AcidParam.ACCENT] = rng.between(0.50f, 0.80f)
                p[AcidParam.DIST_TYPE] = if (rng.chance(0.65f)) 1f else 0f
                p[AcidParam.DIST_DRIVE] = rng.between(0.15f, 0.42f)
                p[AcidParam.DIST_TONE] = rng.between(0.62f, 0.90f)
                p[AcidParam.DIST_MIX] = rng.between(0.35f, 0.68f)
            }
            3 -> { // Screamer
                p[AcidParam.WAVE] = 0f
                p[AcidParam.CUTOFF] = logBetween(rng, 280f, 1300f)
                p[AcidParam.RESONANCE] = rng.between(0.68f, 0.94f)
                p[AcidParam.ENV_MOD] = rng.between(0.58f, 0.90f)
                p[AcidParam.DECAY] = rng.between(0.22f, 0.62f)
                p[AcidParam.ACCENT] = rng.between(0.65f, 0.92f)
                p[AcidParam.DIST_TYPE] = if (rng.chance(0.60f)) 2f else 0f
                p[AcidParam.DIST_DRIVE] = rng.between(0.58f, 0.92f)
                p[AcidParam.DIST_TONE] = rng.between(0.40f, 0.78f)
                p[AcidParam.DIST_MIX] = rng.between(0.72f, 1.0f)
            }
            4 -> { // Germanium fuzz
                p[AcidParam.WAVE] = if (rng.chance(0.70f)) 1f else 0f
                p[AcidParam.CUTOFF] = logBetween(rng, 200f, 1000f)
                p[AcidParam.RESONANCE] = rng.between(0.45f, 0.80f)
                p[AcidParam.ENV_MOD] = rng.between(0.48f, 0.86f)
                p[AcidParam.DECAY] = rng.between(0.30f, 0.85f)
                p[AcidParam.ACCENT] = rng.between(0.55f, 0.85f)
                p[AcidParam.DIST_TYPE] = 3f
                p[AcidParam.DIST_DRIVE] = rng.between(0.48f, 0.90f)
                p[AcidParam.DIST_TONE] = rng.between(0.30f, 0.68f)
                p[AcidParam.DIST_MIX] = rng.between(0.60f, 0.95f)
            }
            5 -> { // Wavefolder
                p[AcidParam.WAVE] = 0f
                p[AcidParam.CUTOFF] = logBetween(rng, 380f, 1900f)
                p[AcidParam.RESONANCE] = rng.between(0.42f, 0.82f)
                p[AcidParam.ENV_MOD] = rng.between(0.62f, 0.94f)
                p[AcidParam.DECAY] = rng.between(0.25f, 0.72f)
                p[AcidParam.ACCENT] = rng.between(0.55f, 0.88f)
                p[AcidParam.DIST_TYPE] = 4f
                p[AcidParam.DIST_DRIVE] = rng.between(0.38f, 0.86f)
                p[AcidParam.DIST_TONE] = rng.between(0.42f, 0.82f)
                p[AcidParam.DIST_MIX] = rng.between(0.50f, 0.90f)
            }
            6 -> { // Clean / dry
                p[AcidParam.WAVE] = if (rng.chance(0.55f)) 0f else 1f
                p[AcidParam.CUTOFF] = logBetween(rng, 650f, 2200f)
                p[AcidParam.RESONANCE] = rng.between(0.18f, 0.55f)
                p[AcidParam.ENV_MOD] = rng.between(0.38f, 0.78f)
                p[AcidParam.DECAY] = rng.between(0.28f, 0.72f)
                p[AcidParam.ACCENT] = rng.between(0.42f, 0.75f)
                val active = rng.chance(0.35f)
                p[AcidParam.DIST_TYPE] = rng.nextInt(0, 4).toFloat()
                p[AcidParam.DIST_DRIVE] = rng.between(0.10f, 0.38f)
                p[AcidParam.DIST_TONE] = rng.between(0.50f, 0.85f)
                val mix = rng.between(0.20f, 0.55f)
                p[AcidParam.DIST_MIX] = if (active) mix else 0f
            }
            else -> { // Hard / industrial
                p[AcidParam.WAVE] = 0f
                p[AcidParam.CUTOFF] = logBetween(rng, 140f, 720f)
                p[AcidParam.RESONANCE] = rng.between(0.60f, 0.94f)
                p[AcidParam.ENV_MOD] = rng.between(0.70f, 0.97f)
                p[AcidParam.DECAY] = rng.between(0.20f, 0.48f)
                p[AcidParam.ACCENT] = rng.between(0.72f, 0.96f)
                p[AcidParam.DIST_TYPE] = if (rng.chance(0.68f)) 0f else 2f
                p[AcidParam.DIST_DRIVE] = rng.between(0.70f, 1.0f)
                p[AcidParam.DIST_TONE] = rng.between(0.32f, 0.70f)
                p[AcidParam.DIST_MIX] = rng.between(0.78f, 1.0f)
            }
        }

        // The original also randomises tempo/swing/tuning here. Tempo is global
        // in the clip launcher and the clip has no tuning control, so those
        // draws are consumed to keep the parameter stream identical.
        rng.between(116.0f, 150.0f)
        if (!rng.chance(0.55f)) rng.between(0.04f, 0.30f)
        if (rng.chance(0.10f)) rng.chance(0.5f)

        val steps = List(ClipLimits.STEPS) { i ->
            Step(
                gate = gates[i],
                note = notes[i],
                velocity = if (accents[i]) 118 else 92,
                accent = accents[i],
                slide = slides[i],
            )
        }
        return presetFrom(generatedName(bank, slot), bank, slot, length, steps, p)
    }

    val all: List<AcidPreset> by lazy {
        buildList(COUNT) {
            for (bank in 0 until BANKS) {
                for (slot in 0 until PER_BANK) add(preset(bank, slot))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Replicant909 factory patterns (port of Replicant909 PresetBank909)
// ---------------------------------------------------------------------------

object DrumPresets {
    private class PatternBuilder {
        val masks = IntArray(ClipLimits.DRUM_VOICES)
        val velocities = IntArray(ClipLimits.DRUM_VOICES * ClipLimits.STEPS) { 100 }

        fun hit(voice: Int, step: Int, velocity: Int = 100) {
            if (voice !in 0 until ClipLimits.DRUM_VOICES) return
            if (step !in 0 until ClipLimits.STEPS) return
            masks[voice] = masks[voice] or (1 shl step)
            velocities[voice * ClipLimits.STEPS + step] = velocity
        }

        fun every(voice: Int, from: Int, to: Int, interval: Int, velocity: Int = 100) {
            var step = from
            while (step <= to) {
                hit(voice, step, velocity)
                step += interval
            }
        }
    }

    private fun pattern(
        name: String,
        category: String,
        accent: Float,
        build: PatternBuilder.() -> Unit,
    ): DrumPreset {
        val builder = PatternBuilder()
        builder.build()
        return DrumPreset(name, category, accent, builder.masks, builder.velocities)
    }

    val all: List<DrumPreset> = listOf(
        // BD=0 SD=1 LT=2 MT=3 HT=4 RS=5 CP=6 CH=7 OH=8 CC=9 RC=10
        pattern("Classic House", "House", 0.75f) {
            for (step in intArrayOf(0, 4, 8, 12)) hit(0, step, if (step % 8 == 0) 120 else 100)
            hit(6, 4, 120); hit(6, 12, 120)
            for (step in intArrayOf(2, 6, 10, 14)) hit(8, step, 118)
            for (step in 0 until 16) if (step % 4 != 2) hit(7, step, 96)
            hit(5, 3, 100); hit(5, 11, 100)
        },
        pattern("Detroit Techno", "Techno", 0.85f) {
            for (step in intArrayOf(0, 4, 8, 12)) hit(0, step, 122)
            hit(1, 4, 120); hit(1, 12, 120); hit(1, 15, 100)
            hit(2, 6, 100); hit(3, 10, 100); hit(4, 14, 100)
            every(10, 0, 15, 2, 96)
            hit(10, 2, 118); hit(10, 10, 118)
            for (step in intArrayOf(2, 6, 10, 14)) hit(8, step, 118)
        },
        pattern("Acid Jack", "Acid", 0.80f) {
            for (step in intArrayOf(0, 4, 8, 12)) hit(0, step, if (step % 8 == 0) 120 else 100)
            hit(1, 4, 120); hit(1, 10, 100); hit(1, 12, 120)
            for (step in intArrayOf(2, 7, 14)) hit(5, step, 100)
            for (step in 0 until 16) hit(7, step, if (step % 2 == 0) 100 else 118)
        },
        pattern("Hard Groove", "Techno", 0.90f) {
            every(0, 0, 15, 4, 122)
            hit(1, 4, 120); hit(1, 12, 110)
            hit(2, 3, 100); hit(2, 11, 100); hit(3, 7, 100)
            every(10, 0, 15, 1, 92)
        },
        pattern("Electro Funk", "Electro", 0.75f) {
            hit(0, 0, 122); hit(0, 6, 100); hit(0, 8, 100); hit(0, 10, 118)
            hit(6, 4, 120); hit(6, 12, 120)
            hit(5, 2, 100); hit(5, 14, 100)
            every(7, 0, 15, 2, 96)
        },
        pattern("Industrial Rave", "Industrial", 0.95f) {
            every(0, 0, 15, 4, 124)
            hit(1, 4, 120); hit(1, 12, 120); hit(1, 14, 100)
            hit(9, 0, 120); hit(9, 8, 100)
            for (step in 2 until 16 step 4) hit(8, step, 118)
        },
        pattern("Minimal Techno", "Minimal", 0.60f) {
            every(0, 0, 15, 4, 100)
            hit(5, 4, 100); hit(5, 12, 100); hit(5, 7, 100)
            for (step in intArrayOf(2, 6, 10, 14)) hit(7, step, 96)
        },
        pattern("Synthwave 909", "Synthwave", 0.70f) {
            hit(0, 0, 120); hit(0, 8, 120)
            hit(1, 4, 120); hit(1, 12, 120)
            hit(6, 4, 100); hit(6, 12, 100)
            every(7, 0, 15, 1, 92)
            hit(8, 2, 100); hit(8, 10, 100)
            hit(4, 14, 100); hit(3, 15, 100)
        },
    )
}

// ---------------------------------------------------------------------------
// Apply helpers
// ---------------------------------------------------------------------------

private fun Clip.mergePresetParams(source: FloatArray): FloatArray {
    val merged = FloatArray(ClipLimits.PARAMS)
    for (i in 0 until minOf(source.size, ClipLimits.PARAMS)) merged[i] = source[i]
    // Keep the live ReplicantFX insert and its six macros.
    for (i in FxParam.BASE until ClipLimits.PARAMS) merged[i] = params[i]
    return merged
}

fun Clip.applyWavePreset(preset: WavePreset) {
    kind = ClipKind.WAVE
    name = preset.name
    root = preset.baseNote
    length = ClipLimits.STEPS
    params = mergePresetParams(preset.params)
    steps = List(ClipLimits.STEPS) { Step() }
    midiNotes = preset.notes
    sampleMissing = false
}

fun Clip.applyAcidPreset(preset: AcidPreset) {
    kind = ClipKind.ACID
    name = preset.name
    root = preset.root
    length = preset.length
    params = mergePresetParams(preset.params)
    steps = preset.steps
    midiNotes = emptyList()
    sampleMissing = false
}

fun Clip.applyDrumPreset(preset: DrumPreset) {
    kind = ClipKind.DRUMS
    name = preset.name
    root = 36
    length = ClipLimits.STEPS
    params = mergePresetParams(FloatArray(ClipLimits.PARAMS).also { p ->
        p[DrumParam.TUNE] = 0.5f
        p[DrumParam.DECAY] = 0.5f
        p[DrumParam.SNAPPY] = 0.5f
        p[DrumParam.TONE] = 0.5f
        p[DrumParam.ACCENT] = preset.accent
        p[DrumParam.LEVEL] = 0.85f
        p[DrumParam.CUTOFF] = 18000f
        p[DrumParam.RESONANCE] = 0.6f
    })
    steps = List(ClipLimits.STEPS) { Step() }
    midiNotes = emptyList()
    drumMask = preset.masks.copyOf()
    drumVel = preset.velocities.copyOf()
    sampleMissing = false
}
