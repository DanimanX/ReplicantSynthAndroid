package com.danimanx.replicantsynth.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danimanx.replicantsynth.audio.AudioDecoder
import com.danimanx.replicantsynth.audio.AudioStats
import com.danimanx.replicantsynth.audio.DecodedAudio
import com.danimanx.replicantsynth.audio.EngineHolder
import com.danimanx.replicantsynth.audio.SampleLibrary
import com.danimanx.replicantsynth.midi.Apc64Controller
import com.danimanx.replicantsynth.midi.Apc64Event
import com.danimanx.replicantsynth.midi.Apc64PadMode
import com.danimanx.replicantsynth.midi.Apc64State
import com.danimanx.replicantsynth.midi.NoteGroupCursor
import com.danimanx.replicantsynth.midi.TransportAction
import com.danimanx.replicantsynth.model.Clip
import com.danimanx.replicantsynth.model.ClipKind
import com.danimanx.replicantsynth.model.ClipLimits
import com.danimanx.replicantsynth.model.AcidPreset
import com.danimanx.replicantsynth.model.AcidPresets
import com.danimanx.replicantsynth.model.DemoProject
import com.danimanx.replicantsynth.model.DrumPreset
import com.danimanx.replicantsynth.model.DrumPresets
import com.danimanx.replicantsynth.model.FxParam
import com.danimanx.replicantsynth.model.MidiNote
import com.danimanx.replicantsynth.model.WAVE_PRESETS
import com.danimanx.replicantsynth.model.WavePreset
import com.danimanx.replicantsynth.model.applyAcidPreset
import com.danimanx.replicantsynth.model.applyDrumPreset
import com.danimanx.replicantsynth.model.applyWavePreset
import com.danimanx.replicantsynth.model.ProjectState
import com.danimanx.replicantsynth.model.ProjectStore
import com.danimanx.replicantsynth.model.Step
import com.danimanx.replicantsynth.model.applyKindDefaults
import com.danimanx.replicantsynth.model.defaultName
import com.danimanx.replicantsynth.model.fxById
import com.danimanx.replicantsynth.model.nextQuantize
import com.danimanx.replicantsynth.model.quantizeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class PadRuntime(
    val kind: Int = 0,
    val state: Int = 0,
    val phase: Int = 0,
    val fxType: Int = 0,
    val meter: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
) {
    val isEmpty: Boolean get() = kind == 0
    val isPlaying: Boolean get() = state == 2
    val isQueued: Boolean get() = state == 1 || state == 3
}

data class RecordingTake(
    val file: File,
    val name: String,
    val seconds: Float,
    val isStem: Boolean,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val engine = EngineHolder.get(application)

    var project by mutableStateOf(ProjectState())
        private set

    val padRuntime = List(ClipLimits.PADS) { mutableStateOf(PadRuntime()) }
    val audioStats: StateFlow<AudioStats> = engine.state

    var selectedPad by mutableIntStateOf(0)
    var selectedTab by mutableIntStateOf(0)
    var isPlaying by mutableStateOf(false)
    var masterMeter by mutableFloatStateOf(0f)
    var scope by mutableStateOf(FloatArray(128))
    var loading by mutableStateOf(true)
    var loadingMessage by mutableStateOf("BUILDING WAVETABLES")
    var sampleBusy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var showMidi by mutableStateOf(false)
    var showProjectMenu by mutableStateOf(false)
    var showTempo by mutableStateOf(false)
    var showFxPicker by mutableStateOf(false)
    var showRecordings by mutableStateOf(false)
    var showPresets by mutableStateOf(false)
    var editorPad by mutableStateOf<Int?>(null)
    var midiCursor by mutableIntStateOf(0)
    var midiLength by mutableIntStateOf(1)
    var midiVelocity by mutableIntStateOf(100)
    var apcOctave by mutableIntStateOf(0)
    var masterRecording by mutableStateOf(false)
    var stemRecordingPad by mutableStateOf<Int?>(null)
    var recordingSeconds by mutableFloatStateOf(0f)
    var lastRecordingPath by mutableStateOf<String?>(null)
    val recordings = mutableStateListOf<RecordingTake>()

    val midi = Apc64Controller(application) { event -> handleMidi(event) }
    val midiState: StateFlow<Apc64State> = midi.state
    private val noteEntry = NoteGroupCursor(ClipLimits.STEPS)
    /** Raw APC64 key -> (pad, transposed note) so release follows a pad change. */
    private val activeApcKeys = HashMap<Int, Pair<Int, Int>>()
    private var apcKeyHintShown = false

    val selectedClip: Clip
        get() = project.clips[selectedPad.coerceIn(0, ClipLimits.PADS - 1)]

    val isRecording: Boolean
        get() = masterRecording || stemRecordingPad != null

    private var pollJob: Job? = null
    private var saveJob: Job? = null
    private var recordingStartedAt = 0L
    private val sampleCache = HashMap<String, DecodedAudio>()

    init {
        midi.start()
        refreshRecordings()
        viewModelScope.launch {
            loading = true
            loadingMessage = "BUILDING WAVETABLES"
            withContext(Dispatchers.IO) { engine.ensure() }
            val stored = withContext(Dispatchers.IO) { ProjectStore.load(app) }
            val loaded = if (stored != null) {
                stored
            } else {
                val sample = withContext(Dispatchers.IO) { SampleLibrary.ensureDemoSample(app) }
                DemoProject.build(sample?.absolutePath).also { demo ->
                    withContext(Dispatchers.IO) { ProjectStore.save(app, demo) }
                }
            }
            project = loaded
            syncTransportToEngine()
            loadingMessage = "LOADING SAMPLES"
            loadSamples(project)
            loadingMessage = "SYNCING 64 PADS"
            syncAllClips()
            selectedPad = project.clips.indexOfFirst { it.kind != ClipKind.EMPTY }
                .coerceAtLeast(0)
            loading = false
            startPolling()
            midi.sendOled(
                listOf(
                    "REPLICANT",
                    "SYNTH",
                    "${project.bpm.toInt()} BPM",
                    quantizeLabel(project.quantize),
                ),
            )
        }
    }

    fun consumeMessage() {
        message = null
    }

    // Project -------------------------------------------------------------------

    private fun syncTransportToEngine() {
        engine.setTempo(project.bpm)
        engine.setQuantize(project.quantize)
        engine.setSwing(project.swing)
        engine.setMasterGain(project.masterGain)
    }

    private fun syncAllClips() {
        for (clip in project.clips) {
            if (clip.kind == ClipKind.EMPTY) {
                engine.clearClip(clip.index)
                continue
            }
            syncClipPattern(clip)
            padRuntime[clip.index].value = PadRuntime(
                kind = clip.kind.ordinal,
                state = 0,
                fxType = clip.fxType,
                mute = clip.mute,
                solo = clip.solo,
            )
        }
    }

    /** Push a clip's config plus whichever pattern model its engine uses. */
    private fun syncClipPattern(clip: Clip) {
        engine.configureClip(clip)
        when (clip.kind) {
            ClipKind.DRUMS -> {
                for (voice in 0 until ClipLimits.DRUM_VOICES) {
                    for (step in 0 until ClipLimits.STEPS) {
                        engine.setDrumStep(clip, voice, step)
                    }
                }
            }
            ClipKind.ACID -> {
                for (step in 0 until ClipLimits.STEPS) engine.setStep(clip, step)
            }
            ClipKind.WAVE -> {
                for (step in 0 until ClipLimits.STEPS) engine.setStep(clip, step)
                engine.clearMidiNotes(clip.index)
                clip.midiNotes.forEach { engine.setMidiNote(clip, it) }
            }
            else -> Unit
        }
    }

    private suspend fun loadSamples(target: ProjectState) {
        val paths = target.clips.mapNotNull { it.samplePath }.distinct()
        for (path in paths) {
            val file = File(path)
            if (!file.isFile) {
                target.clips.filter { it.samplePath == path }.forEach {
                    it.sampleMissing = true
                    it.sampleName = it.sampleName ?: "MISSING"
                }
                continue
            }
            val decoded = sampleCache[path] ?: withContext(Dispatchers.IO) {
                runCatching { AudioDecoder.decode(file) }.getOrNull()
            }
            if (decoded == null) {
                target.clips.filter { it.samplePath == path }.forEach { it.sampleMissing = true }
                continue
            }
            sampleCache[path] = decoded
            target.clips.filter { it.samplePath == path }.forEach { clip ->
                engine.loadSample(
                    clip.index,
                    decoded.samples,
                    decoded.frames,
                    2,
                    decoded.sampleRate,
                )
                clip.sampleMissing = false
                if (clip.sampleName.isNullOrBlank()) clip.sampleName = file.nameWithoutExtension
            }
        }
        target.clips.filter { it.kind == ClipKind.SAMPLE && it.samplePath == null }.forEach {
            it.sampleMissing = true
        }
    }

    fun loadDemo() {
        viewModelScope.launch {
            loading = true
            loadingMessage = "LOADING DEMO"
            engine.setPlaying(false)
            engine.panic()
            val sample = withContext(Dispatchers.IO) { SampleLibrary.ensureDemoSample(app) }
            val demo = DemoProject.build(sample?.absolutePath)
            project = demo
            syncTransportToEngine()
            loadSamples(demo)
            syncAllClips()
            selectedPad = 0
            selectedTab = 0
            loading = false
            saveNow()
            message = "Demo loaded · 6 scenes"
        }
    }

    fun newProject() {
        viewModelScope.launch {
            loading = true
            loadingMessage = "CLEARING PADS"
            engine.setPlaying(false)
            engine.panic()
            val fresh = ProjectState()
            project = fresh
            syncTransportToEngine()
            for (clip in fresh.clips) engine.clearClip(clip.index)
            selectedPad = 0
            selectedTab = 0
            loading = false
            saveNow()
            message = "New empty project"
        }
    }

    fun renameProject(name: String) {
        project.name = name.ifBlank { "UNTITLED" }
        scheduleSave()
    }

    fun saveNow() {
        viewModelScope.launch(Dispatchers.IO) { ProjectStore.save(app, project) }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            withContext(Dispatchers.IO) { ProjectStore.save(app, project) }
        }
    }

    // Selection and transport ---------------------------------------------------

    fun selectPad(pad: Int, tab: Int = selectedTab) {
        selectedPad = pad.coerceIn(0, ClipLimits.PADS - 1)
        selectedTab = tab
        apcKeyHintShown = false
    }

    fun selectTab(tab: Int) {
        selectedTab = tab
    }

    fun togglePad(pad: Int) {
        selectedPad = pad
        val clip = project.clips[pad]
        if (clip.kind == ClipKind.EMPTY) {
            selectedTab = 0
            return
        }
        if (clip.kind == ClipKind.SAMPLE && clip.sampleMissing) {
            selectedTab = 0
            message = "Sample missing · import audio for pad ${pad + 1}"
            return
        }
        engine.launchPad(pad)
    }

    fun stopPad(pad: Int) {
        engine.stopPad(pad)
    }

    fun launchScene(row: Int) {
        engine.launchScene(row)
    }

    fun stopScene(row: Int) {
        engine.stopScene(row)
    }

    fun stopAll() {
        engine.stopAll()
    }

    fun toggleTransport() {
        val next = !isPlaying
        engine.setPlaying(next)
        isPlaying = next
    }

    fun stopTransport() {
        engine.setPlaying(false)
        isPlaying = false
    }

    fun setBpm(value: Float) {
        project.bpm = value.coerceIn(30f, 300f)
        engine.setTempo(project.bpm)
        scheduleSave()
    }

    fun nudgeBpm(delta: Float) {
        setBpm(project.bpm + delta)
    }

    fun cycleQuantize() {
        project.quantize = nextQuantize(project.quantize)
        engine.setQuantize(project.quantize)
        scheduleSave()
    }

    fun setQuantize(value: Int) {
        project.quantize = value
        engine.setQuantize(value)
        scheduleSave()
    }

    fun setSwing(value: Float) {
        project.swing = value.coerceIn(0f, 0.66f)
        engine.setSwing(project.swing)
        scheduleSave()
    }

    fun setMasterGain(value: Float) {
        project.masterGain = value.coerceIn(0f, 1.5f)
        engine.setMasterGain(project.masterGain)
        scheduleSave()
    }

    // Clip editing --------------------------------------------------------------

    fun changeKind(pad: Int, kind: ClipKind) {
        val clip = project.clips[pad]
        applyKindDefaults(clip, kind)
        if (kind == ClipKind.EMPTY) {
            engine.clearClip(pad)
        } else {
            syncClipPattern(clip)
        }
        selectedTab = 0
        scheduleSave()
    }

    fun setParam(clip: Clip, slot: Int, value: Float) {
        clip.setParam(slot, value)
        engine.updateParams(clip)
        scheduleSave()
    }

    fun setGain(clip: Clip, value: Float) {
        clip.gain = value.coerceIn(0f, 1.5f)
        engine.updateParams(clip)
        scheduleSave()
    }

    fun setPan(clip: Clip, value: Float) {
        clip.pan = value.coerceIn(-1f, 1f)
        engine.updateParams(clip)
        scheduleSave()
    }

    fun toggleMute(clip: Clip) {
        clip.mute = !clip.mute
        engine.updateParams(clip)
        scheduleSave()
    }

    fun toggleSolo(clip: Clip) {
        clip.solo = !clip.solo
        engine.updateParams(clip)
        scheduleSave()
    }

    fun setFxType(clip: Clip, type: Int) {
        clip.applyFxDefaults(type)
        engine.configureClip(clip)
        showFxPicker = false
        scheduleSave()
    }

    fun toggleFx(clip: Clip) {
        clip.fxEnabled = !clip.fxEnabled
        engine.updateParams(clip)
        scheduleSave()
    }

    fun setStep(clip: Clip, index: Int, step: Step) {
        clip.setStep(index, step)
        engine.setStep(clip, index)
        scheduleSave()
    }

    fun toggleGate(clip: Clip, index: Int) {
        val step = clip.steps[index]
        val note = if (!step.gate && step.note == 60) clip.root else step.note
        setStep(clip, index, step.copy(gate = !step.gate, note = note))
    }

    fun setStepNote(clip: Clip, index: Int, note: Int) {
        setStep(clip, index, clip.steps[index].copy(note = note.coerceIn(0, 127)))
    }

    fun toggleAccent(clip: Clip, index: Int) {
        setStep(clip, index, clip.steps[index].copy(accent = !clip.steps[index].accent))
    }

    fun toggleSlide(clip: Clip, index: Int) {
        setStep(clip, index, clip.steps[index].copy(slide = !clip.steps[index].slide))
    }

    fun toggleDrumStep(clip: Clip, voice: Int, step: Int) {
        clip.setDrumStep(voice, step, !clip.drumOn(voice, step))
        engine.setDrumStep(clip, voice, step)
        scheduleSave()
    }

    fun setDrumVelocity(clip: Clip, voice: Int, step: Int, velocity: Int) {
        clip.setDrumVelocity(voice, step, velocity)
        engine.setDrumStep(clip, voice, step)
        scheduleSave()
    }

    fun setLength(clip: Clip, length: Int) {
        clip.length = length.coerceIn(1, ClipLimits.STEPS)
        engine.configureClip(clip)
        scheduleSave()
    }

    fun setRoot(clip: Clip, root: Int) {
        clip.root = root.coerceIn(0, 127)
        scheduleSave()
    }

    fun transpose(clip: Clip, semitones: Int) {
        clip.root = (clip.root + semitones).coerceIn(0, 127)
        if (clip.kind == ClipKind.ACID || clip.kind == ClipKind.WAVE) {
            clip.updateSteps(
                clip.steps.map {
                    it.copy(note = (it.note + semitones).coerceIn(0, 127))
                },
            )
            for (step in 0 until ClipLimits.STEPS) engine.setStep(clip, step)
        }
        if (clip.kind == ClipKind.WAVE) {
            clip.midiNotes = clip.midiNotes.map {
                it.copy(note = (it.note + semitones).coerceIn(0, 127))
            }
            engine.clearMidiNotes(clip.index)
            clip.midiNotes.forEach { engine.setMidiNote(clip, it) }
        }
        scheduleSave()
    }

    fun clearPattern(clip: Clip) {
        clip.steps = List(ClipLimits.STEPS) { Step() }
        clip.midiNotes = emptyList()
        engine.clearMidiNotes(clip.index)
        clip.drumMask = IntArray(ClipLimits.DRUM_VOICES)
        for (step in 0 until ClipLimits.STEPS) engine.setStep(clip, step)
        for (voice in 0 until ClipLimits.DRUM_VOICES) {
            for (step in 0 until ClipLimits.STEPS) engine.setDrumStep(clip, voice, step)
        }
        scheduleSave()
    }

    fun randomizePattern(clip: Clip) {
        val random = Random(System.nanoTime())
        val scale = intArrayOf(0, 2, 3, 5, 7, 8, 10, 12)
        if (clip.kind == ClipKind.ACID) {
            val steps = MutableList(ClipLimits.STEPS) { Step() }
            for (i in 0 until ClipLimits.STEPS) {
                if (random.nextFloat() < 0.46f) {
                    val offset = scale[random.nextInt(scale.size)] + 12 * random.nextInt(2)
                    val accent = random.nextFloat() < 0.18f
                    steps[i] = Step(
                        gate = true,
                        note = (clip.root + offset).coerceIn(0, 127),
                        velocity = if (accent) 118 else 94,
                        accent = accent,
                        slide = random.nextFloat() < 0.14f,
                    )
                }
            }
            clip.steps = steps
            for (i in 0 until ClipLimits.STEPS) engine.setStep(clip, i)
        } else if (clip.kind == ClipKind.WAVE) {
            clip.steps = List(ClipLimits.STEPS) { Step() }
            val notes = mutableListOf<MidiNote>()
            for (i in 0 until ClipLimits.STEPS) {
                if (random.nextFloat() < 0.32f) {
                    val offset = scale[random.nextInt(scale.size)] + 12 * random.nextInt(2)
                    notes += MidiNote(
                        note = (clip.root + offset).coerceIn(0, 127),
                        start = i,
                        length = if (random.nextFloat() < 0.35f) 2 else 1,
                        velocity = if (random.nextFloat() < 0.2f) 118 else 94,
                    )
                }
            }
            clip.midiNotes = notes
            syncClipPattern(clip)
        } else if (clip.kind == ClipKind.DRUMS) {
            val densities = floatArrayOf(0.28f, 0.16f, 0.08f, 0.08f, 0.08f, 0.06f, 0.1f, 0.42f, 0.1f, 0.06f, 0.08f)
            val masks = IntArray(ClipLimits.DRUM_VOICES)
            val velocities = IntArray(ClipLimits.DRUM_VOICES * ClipLimits.STEPS) { 100 }
            for (voice in 0 until ClipLimits.DRUM_VOICES) {
                for (step in 0 until ClipLimits.STEPS) {
                    if (random.nextFloat() < densities[voice]) {
                        masks[voice] = masks[voice] or (1 shl step)
                        velocities[voice * ClipLimits.STEPS + step] =
                            if (random.nextFloat() < 0.2f) 118 else 80 + random.nextInt(28)
                    }
                }
            }
            clip.drumMask = masks
            clip.drumVel = velocities
            for (voice in 0 until ClipLimits.DRUM_VOICES) {
                for (step in 0 until ClipLimits.STEPS) engine.setDrumStep(clip, voice, step)
            }
        }
        scheduleSave()
    }

    fun setSampleLoop(clip: Clip, loop: Boolean) {
        clip.sampleLoop = loop
        engine.configureClip(clip)
        scheduleSave()
    }

    fun clearSample(clip: Clip) {
        engine.clearSample(clip.index)
        clip.samplePath = null
        clip.sampleName = null
        clip.sampleMissing = false
        scheduleSave()
    }

    // MIDI piano roll -----------------------------------------------------------

    fun openEditor(pad: Int) {
        val target = pad.coerceIn(0, ClipLimits.PADS - 1)
        selectPad(target, 2)
        midiCursor = 0
        noteEntry.reset(0)
        val selected = project.clips[target]
        if (selected.kind == ClipKind.WAVE && selected.length != ClipLimits.STEPS) {
            selected.length = ClipLimits.STEPS
            syncClipPattern(selected)
        }
        if (project.clips[target].kind == ClipKind.WAVE && project.clips[target].midiNotes.isEmpty()) {
            // Seed the roll with the legacy 16-step line the first time it opens.
            val clip = project.clips[target]
            val converted = clip.steps.mapIndexedNotNull { index, step ->
                if (step.gate) MidiNote(step.note, index, 1, step.velocity) else null
            }
            if (converted.isNotEmpty()) {
                clip.midiNotes = converted
                clip.steps = List(ClipLimits.STEPS) { Step() }
                syncClipPattern(clip)
                scheduleSave()
            }
        }
        editorPad = target
    }

    fun closeEditor() {
        editorPad = null
    }

    fun moveMidiCursor(step: Int) {
        midiCursor = step.coerceIn(0, ClipLimits.STEPS - 1)
        noteEntry.reset(midiCursor)
    }

    fun chooseMidiLength(steps: Int) {
        midiLength = steps.coerceIn(1, ClipLimits.STEPS)
    }

    fun chooseMidiVelocity(velocity: Int) {
        midiVelocity = velocity.coerceIn(1, 127)
    }

    fun addMidiNote(
        clip: Clip,
        note: Int,
        start: Int = midiCursor,
        length: Int = midiLength,
        velocity: Int = midiVelocity,
        advance: Boolean = true,
    ) {
        if (clip.kind != ClipKind.WAVE) return
        val entry = clip.addMidiNote(note, start, length, velocity)
        engine.setMidiNote(clip, entry)
        val pad = clip.index
        viewModelScope.launch {
            engine.previewNote(pad, entry.note, entry.velocity, true)
            delay(280)
            engine.previewNote(pad, entry.note, 0, false)
        }
        if (advance) midiCursor = (entry.start + entry.length).mod(ClipLimits.STEPS)
        scheduleSave()
    }

    fun removeMidiNote(clip: Clip, note: Int, start: Int) {
        if (clip.kind != ClipKind.WAVE) return
        clip.removeMidiNote(note, start)
        engine.setMidiNote(clip, MidiNote(note, start), on = false)
        scheduleSave()
    }

    fun clearMidiNotes(clip: Clip) {
        clip.clearMidiNotes()
        engine.clearMidiNotes(clip.index)
        scheduleSave()
    }

    fun convertStepsToMidi(clip: Clip) {
        if (clip.kind != ClipKind.WAVE) return
        clip.midiNotes = clip.steps.mapIndexedNotNull { index, step ->
            if (step.gate) MidiNote(step.note, index, 1, step.velocity) else null
        }
        clip.steps = List(ClipLimits.STEPS) { Step() }
        syncClipPattern(clip)
        scheduleSave()
        message = "Converted ${clip.midiNotes.size} steps to MIDI notes"
    }

    fun nudgeApcOctave(octave: Int) {
        apcOctave = octave.coerceIn(-2, 2)
    }

    // Presets -------------------------------------------------------------------

    fun browsePresets() {
        if (selectedClip.kind == ClipKind.EMPTY) {
            message = "Assign an engine first"
            return
        }
        showPresets = true
    }

    fun applyPreset(preset: Any) {
        val clip = selectedClip
        when (preset) {
            is WavePreset -> if (clip.kind == ClipKind.WAVE) clip.applyWavePreset(preset)
            is AcidPreset -> if (clip.kind == ClipKind.ACID) clip.applyAcidPreset(preset)
            is DrumPreset -> if (clip.kind == ClipKind.DRUMS) clip.applyDrumPreset(preset)
            else -> return
        }
        syncClipPattern(clip)
        showPresets = false
        scheduleSave()
        message = "${clip.name} loaded"
    }

    fun presetCount(kind: ClipKind): Int = when (kind) {
        ClipKind.ACID -> AcidPresets.COUNT
        ClipKind.WAVE -> WAVE_PRESETS.size
        ClipKind.DRUMS -> DrumPresets.all.size
        else -> 0
    }

    // Sample import -------------------------------------------------------------

    fun importSample(pad: Int, uri: Uri) {
        viewModelScope.launch {
            sampleBusy = true
            try {
                val file = withContext(Dispatchers.IO) {
                    SampleLibrary.importAudio(app, uri)
                } ?: run {
                    message = "Could not copy that file"
                    return@launch
                }
                val decoded = withContext(Dispatchers.IO) {
                    runCatching { AudioDecoder.decode(file) }.getOrNull()
                } ?: run {
                    message = "Unsupported or corrupt audio file"
                    sampleBusy = false
                    return@launch
                }
                sampleCache[file.absolutePath] = decoded
                engine.loadSample(pad, decoded.samples, decoded.frames, 2, decoded.sampleRate)
                val clip = project.clips[pad]
                val name = file.nameWithoutExtension.uppercase(Locale.US).take(18)
                applyKindDefaults(clip, ClipKind.SAMPLE)
                clip.samplePath = file.absolutePath
                clip.sampleName = name
                clip.sampleMissing = false
                clip.sampleLoop = true
                clip.name = name
                val p = clip.params.copyOf()
                p[0] = 0.9f
                p[1] = 0f
                clip.params = p
                clip.applyFxDefaults(0)
                engine.configureClip(clip)
                selectedPad = pad
                selectedTab = 0
                message = "Loaded $name · ${"%.1f".format(decoded.durationSeconds)} s"
                scheduleSave()
            } finally {
                sampleBusy = false
            }
        }
    }

    fun loadLastTakeIntoPad(pad: Int) {
        val path = lastRecordingPath ?: return
        val file = File(path)
        if (!file.isFile) {
            message = "No take available"
            return
        }
        loadTakeIntoPad(
            RecordingTake(
                file = file,
                name = file.nameWithoutExtension.uppercase(Locale.US),
                seconds = (file.length() / (48000f * 6f)),
                isStem = true,
            ),
            pad,
        )
    }

    fun loadTakeIntoPad(take: RecordingTake, pad: Int) {
        viewModelScope.launch {
            sampleBusy = true
            try {
                val decoded = withContext(Dispatchers.IO) {
                    runCatching { AudioDecoder.decode(take.file) }.getOrNull()
                } ?: run {
                    message = "Capture is not readable"
                    return@launch
                }
                val path = take.file.absolutePath
                sampleCache[path] = decoded
                engine.loadSample(pad, decoded.samples, decoded.frames, 2, decoded.sampleRate)
                val clip = project.clips[pad]
                applyKindDefaults(clip, ClipKind.SAMPLE)
                clip.samplePath = path
                clip.sampleName = take.name
                clip.sampleMissing = false
                clip.sampleLoop = true
                clip.name = take.name
                clip.applyFxDefaults(0)
                engine.configureClip(clip)
                selectedPad = pad
                selectedTab = 0
                showRecordings = false
                message = "${take.name} loaded into pad ${pad + 1}"
                scheduleSave()
            } finally {
                sampleBusy = false
            }
        }
    }

    // Capture -------------------------------------------------------------------

    private fun recordingsDir(): File = File(app.filesDir, "Recordings").apply { mkdirs() }

    private fun newTakeFile(prefix: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(recordingsDir(), "$prefix-$stamp.wav")
    }

    fun toggleRecording() {
        if (isRecording) stopRecordingNow() else startMasterRecording()
    }

    fun startMasterRecording() {
        if (isRecording) stopRecordingNow()
        val file = newTakeFile("take")
        val ok = engine.startMasterRecording(file.absolutePath)
        if (!ok) {
            message = "Could not start recording"
            return
        }
        lastRecordingPath = file.absolutePath
        masterRecording = true
        stemRecordingPad = null
        recordingStartedAt = System.currentTimeMillis()
        recordingSeconds = 0f
        message = "Recording master · 24-bit WAV"
    }

    fun startStemRecording(pad: Int) {
        if (isRecording) stopRecordingNow()
        val file = newTakeFile("stem-${(pad + 1).toString().padStart(2, '0')}")
        val ok = engine.startPadRecording(file.absolutePath, pad)
        if (!ok) {
            message = "Could not start stem capture"
            return
        }
        lastRecordingPath = file.absolutePath
        stemRecordingPad = pad
        masterRecording = false
        recordingStartedAt = System.currentTimeMillis()
        recordingSeconds = 0f
        message = "Capturing pad ${pad + 1} · 24-bit WAV"
    }

    fun stopRecordingNow() {
        if (!isRecording) return
        engine.stopRecording()
        val frames = engine.recordedFrames()
        val padFrames = engine.padRecordedFrames()
        val path = lastRecordingPath
        val wasStem = stemRecordingPad != null
        val seconds = maxOf(
            frames / 48000f,
            padFrames / 48000f,
            (System.currentTimeMillis() - recordingStartedAt) / 1000f,
        )
        masterRecording = false
        stemRecordingPad = null
        recordingSeconds = 0f
        if (path != null) {
            val file = File(path)
            if (file.exists() && file.length() > 44) {
                val take = RecordingTake(
                    file = file,
                    name = if (wasStem) "STEM ${file.name.substringAfter("stem-").substringBefore("-")}"
                    else "TAKE ${file.name.substringAfter("take-").substringBefore("-")}",
                    seconds = seconds,
                    isStem = wasStem,
                )
                recordings.removeAll { it.file.absolutePath == file.absolutePath }
                recordings.add(0, take)
                message = "Saved ${take.name} · ${"%.1f".format(seconds)} s"
            } else {
                message = "Recording did not produce audio"
            }
        }
    }

    fun refreshRecordings() {
        recordings.clear()
        val files = recordingsDir().listFiles { file -> file.extension.equals("wav", true) }
            ?: return
        files.sortedByDescending { it.lastModified() }.forEach { file ->
            val seconds = (file.length() / (48000f * 6f)).coerceAtLeast(0f)
            recordings.add(
                RecordingTake(
                    file = file,
                    name = file.nameWithoutExtension.uppercase(Locale.US),
                    seconds = seconds,
                    isStem = file.name.startsWith("stem-"),
                ),
            )
        }
    }

    // APC64 ---------------------------------------------------------------------

    private fun handleMidi(event: Apc64Event) {
        when (event) {
            is Apc64Event.Key -> {
                val pad = (editorPad ?: selectedPad).coerceIn(0, ClipLimits.PADS - 1)
                val clip = project.clips[pad]
                val note = (event.note + apcOctave * 12).coerceIn(0, 127)
                if (event.pressed) {
                    if (clip.kind != ClipKind.WAVE) {
                        if (!apcKeyHintShown) {
                            apcKeyHintShown = true
                            message = "APC64 ${midiState.value.modeLabel} mode · " +
                                "select a WAVE clip to write notes"
                        }
                        return
                    }
                    activeApcKeys[event.note] = pad to note
                    val step = noteEntry.press(SystemClock.uptimeMillis(), midiLength)
                    midiCursor = step
                    addMidiNote(
                        clip,
                        note,
                        start = step,
                        length = midiLength,
                        velocity = event.velocity,
                        advance = false,
                    )
                } else {
                    val (targetPad, playedNote) = activeApcKeys.remove(event.note)
                        ?: (pad to note)
                    engine.previewNote(targetPad, playedNote, 0, false)
                }
            }
            is Apc64Event.Mode -> onApcMode(event.mode)
            Apc64Event.AllKeysOff -> releaseApcKeys()
            is Apc64Event.Pad -> if (event.pressed) togglePad(event.pad)
            is Apc64Event.Scene -> launchScene(event.row)
            is Apc64Event.Transport -> when (event.action) {
                TransportAction.PLAY -> {
                    engine.setPlaying(true)
                    isPlaying = true
                }
                TransportAction.STOP -> stopTransport()
                TransportAction.RECORD -> toggleRecording()
            }
            is Apc64Event.Encoder -> nudgeBpm(event.delta.toFloat())
            is Apc64Event.TouchStrip -> applyTouchStrip(event.index, event.value)
            is Apc64Event.TrackSelect -> selectPad(selectedPad / 8 * 8 + event.column.coerceIn(0, 7))
            is Apc64Event.Mute -> if (event.pressed) toggleMute(selectedClip)
            is Apc64Event.Solo -> if (event.pressed) toggleSolo(selectedClip)
            Apc64Event.ClipStop -> stopAll()
            is Apc64Event.Shift -> Unit
        }
    }

    private fun onApcMode(mode: Apc64PadMode) {
        if (!mode.keySurface) releaseApcKeys()
        apcKeyHintShown = false
        message = when (mode) {
            Apc64PadMode.NOTE -> "APC64 NOTE mode · keys → piano roll"
            Apc64PadMode.CHORD -> "APC64 CHORD mode · chords → piano roll"
            Apc64PadMode.SESSION -> "APC64 SESSION mode · pads → clips"
            Apc64PadMode.OVERVIEW -> "APC64 OVERVIEW mode · pads → clips"
            else -> "APC64 ${mode.label} mode · pads stay on the unit"
        }
    }

    private fun releaseApcKeys() {
        for ((pad, note) in activeApcKeys.values) {
            engine.previewNote(pad, note, 0, false)
        }
        activeApcKeys.clear()
    }

    private fun applyTouchStrip(index: Int, value: Float) {
        when (index) {
            0 -> setMasterGain(value)
            7 -> setGain(selectedClip, value)
            in 1..6 -> setParam(selectedClip, FxParam.BASE + index - 1, value)
            else -> Unit
        }
    }

    fun testApcLeds() {
        midi.sendTestPattern()
        message = "APC64 LED test sent"
    }

    // Polling -------------------------------------------------------------------

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.Default) {
            val states = IntArray(ClipLimits.PADS)
            val meters = FloatArray(ClipLimits.PADS)
            val scopeBuffer = FloatArray(128)
            while (isActive) {
                engine.padStates(states)
                engine.padMeters(meters)
                engine.scope(scopeBuffer)
                val playing = engine.isPlaying()
                val master = engine.masterMeter()
                engine.refreshStats()
                withContext(Dispatchers.Main) {
                    for (pad in 0 until ClipLimits.PADS) {
                        val packed = states[pad]
                        padRuntime[pad].value = PadRuntime(
                            kind = packed and 0x0F,
                            state = (packed shr 4) and 0x0F,
                            phase = (packed shr 8) and 0xFF,
                            fxType = (packed shr 16) and 0xFF,
                            meter = meters[pad],
                            mute = (packed shr 24) and 1 == 1,
                            solo = (packed shr 25) and 1 == 1,
                        )
                    }
                    isPlaying = playing
                    masterMeter = master
                    scope = scopeBuffer.copyOf()
                    if (isRecording) {
                        recordingSeconds =
                            (System.currentTimeMillis() - recordingStartedAt) / 1000f
                    }
                    midi.updateLeds(states, selectedPad, playing, isRecording)
                }
                delay(50)
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        midi.stop()
        Thread { ProjectStore.save(app, project) }.start()
        super.onCleared()
    }

    fun padLabel(pad: Int): String =
        project.clips[pad].displayName.ifBlank { defaultName(project.clips[pad].kind, pad) }

    fun fxLabel(clip: Clip): String = if (clip.fxType == 0) {
        "NO FX"
    } else {
        val info = fxById(clip.fxType)
        "${info.code} · ${info.name}"
    }
}
