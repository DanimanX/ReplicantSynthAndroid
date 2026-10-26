package com.danimanx.replicantsynth.model

/**
 * The factory demo: six scenes that exercise the 303, the 909, the wavetable
 * engine and an imported sample, each with a ReplicantFX insert. It is written
 * on first launch so the grid is playable (and screenshots are meaningful)
 * before the user imports anything.
 */
object DemoProject {

    fun build(samplePath: String?): ProjectState {
        val project = ProjectState()
        project.name = "NEON GRID"
        project.bpm = 126f
        project.quantize = 16
        project.masterGain = 0.85f
        project.swing = 0f

        acid(project, 0, 36, 1, "ACID BASS", -0.05f)
        drums(project, 1, 3, "909 KIT", 0f, busy = false)
        wave(project, 2, 48, 0, 0, 15, 2100f, 0f, "NEON PAD")

        acid(project, 8, 41, 5, "ACID LEAD", 0.05f)
        drums(project, 9, 12, "909 RESO", -0.1f, busy = true)
        wave(project, 10, 53, 4, 2, 18, 2600f, -0.2f, "ACID ARP")

        sample(project, 16, 10, -0.15f, samplePath, "NEON VOX")
        acid(project, 17, 36, 34, "FILTER BASS", 0f)
        drums(project, 18, 31, "VINYL 909", 0.1f, busy = true)

        wave(project, 24, 55, 2, 3, 6, 3200f, 0.2f, "FM LEAD")
        acid(project, 25, 43, 8, "DIST ACID", -0.2f)
        drums(project, 26, 2, "DELAY KIT", 0.2f, busy = true)

        wave(project, 32, 48, 3, 4, 44, 1700f, -0.25f, "VOX PAD")
        acid(project, 33, 36, 33, "ZAN-ZOU", 0.2f)

        acid(project, 40, 48, 13, "LOOP ACID", 0f)
        drums(project, 41, 8, "FULL KIT", -0.1f, busy = true)
        wave(project, 42, 60, 7, 3, 14, 3600f, 0.25f, "SUB LEAD")
        sample(project, 43, 47, 0.15f, samplePath, "VOX CLOUD")

        return project
    }

    private fun base(clip: Clip, kind: ClipKind, name: String, gain: Float, pan: Float) {
        applyKindDefaults(clip, kind)
        clip.name = name
        clip.gain = gain
        clip.pan = pan
    }

    private fun acid(project: ProjectState, index: Int, root: Int, fx: Int, name: String,
                     pan: Float) {
        val clip = project.clips[index]
        base(clip, ClipKind.ACID, name, 0.78f, pan)
        clip.root = root
        val notes = intArrayOf(0, 0, 12, 3, 7, 10, 12, 7)
        val gates = intArrayOf(0, 3, 6, 8, 10, 11, 14, 15)
        val steps = MutableList(ClipLimits.STEPS) { Step() }
        gates.forEachIndexed { i, step ->
            val accent = i == 0 || i == 3 || i == 5
            steps[step] = Step(
                gate = true,
                note = (root + notes[i]).coerceIn(0, 127),
                velocity = if (accent) 118 else 94,
                accent = accent,
                slide = i == 6,
            )
        }
        clip.steps = steps
        clip.applyFxDefaults(fx)
    }

    private fun wave(project: ProjectState, index: Int, root: Int, bank: Int, chord: Int,
                     fx: Int, cutoff: Float, pan: Float, name: String) {
        val clip = project.clips[index]
        base(clip, ClipKind.WAVE, name, 0.62f, pan)
        clip.root = root
        val p = clip.params.copyOf()
        p[WaveParam.BANK] = bank.toFloat()
        p[WaveParam.POSITION] = 0.3f
        p[WaveParam.UNISON] = 4f
        p[WaveParam.DETUNE] = 16f
        p[WaveParam.SPREAD] = 0.55f
        p[WaveParam.CUTOFF] = cutoff
        p[WaveParam.RESONANCE] = 0.7f
        p[WaveParam.DRIVE] = 1.3f
        p[WaveParam.ATTACK] = 0.02f
        p[WaveParam.DECAY] = 0.5f
        p[WaveParam.SUSTAIN] = 0.65f
        p[WaveParam.RELEASE] = 0.55f
        p[WaveParam.SUB] = 0.2f
        p[WaveParam.NOISE] = 0.02f
        p[WaveParam.LEVEL] = 0.72f
        p[WaveParam.FILTER_MODE] = 0f
        p[WaveParam.CHORD] = chord.toFloat()
        clip.params = p
        val steps = MutableList(ClipLimits.STEPS) { Step() }
        for (step in intArrayOf(0, 3, 6, 10, 14)) {
            steps[step] = Step(gate = true, note = (root + (step % 7)).coerceIn(0, 127), velocity = 100)
        }
        clip.steps = steps
        clip.applyFxDefaults(fx)
    }

    private fun drums(project: ProjectState, index: Int, fx: Int, name: String, pan: Float,
                      busy: Boolean) {
        val clip = project.clips[index]
        base(clip, ClipKind.DRUMS, name, 0.85f, pan)
        val p = clip.params.copyOf()
        p[DrumParam.TUNE] = 0.52f
        p[DrumParam.DECAY] = 0.5f
        p[DrumParam.SNAPPY] = 0.52f
        p[DrumParam.TONE] = 0.55f
        p[DrumParam.ACCENT] = 0.7f
        p[DrumParam.LEVEL] = 0.9f
        p[DrumParam.CUTOFF] = 18000f
        p[DrumParam.RESONANCE] = 0.6f
        clip.params = p

        val masks = IntArray(ClipLimits.DRUM_VOICES)
        val velocities = IntArray(ClipLimits.DRUM_VOICES * ClipLimits.STEPS) { 100 }
        for (step in intArrayOf(0, 4, 8, 12)) {
            masks[0] = masks[0] or (1 shl step)
            velocities[step] = 122
        }
        for (step in intArrayOf(4, 12)) {
            masks[1] = masks[1] or (1 shl step)
            velocities[ClipLimits.STEPS + step] = 112
        }
        for (step in 0 until 16 step 2) {
            masks[7] = masks[7] or (1 shl step)
            velocities[7 * ClipLimits.STEPS + step] = if (step % 4 == 0) 92 else 70
        }
        if (busy) {
            for (step in intArrayOf(6, 14)) {
                masks[8] = masks[8] or (1 shl step)
                velocities[8 * ClipLimits.STEPS + step] = 88
            }
            masks[6] = masks[6] or (1 shl 11)
            velocities[6 * ClipLimits.STEPS + 11] = 96
            masks[9] = masks[9] or (1 shl 0)
            velocities[9 * ClipLimits.STEPS] = 90
        }
        clip.drumMask = masks
        clip.drumVel = velocities
        clip.applyFxDefaults(fx)
    }

    private fun sample(project: ProjectState, index: Int, fx: Int, pan: Float,
                       samplePath: String?, name: String) {
        val clip = project.clips[index]
        base(clip, ClipKind.SAMPLE, name, 0.75f, pan)
        val p = clip.params.copyOf()
        p[SampleParam.GAIN] = 0.8f
        p[SampleParam.PITCH] = 0f
        clip.params = p
        clip.samplePath = samplePath
        clip.sampleName = if (samplePath != null) "NEON VOX" else "NO SAMPLE"
        clip.sampleMissing = samplePath == null
        clip.sampleLoop = true
        clip.applyFxDefaults(fx)
    }
}
