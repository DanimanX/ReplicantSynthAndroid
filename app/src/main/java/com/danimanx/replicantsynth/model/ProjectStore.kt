package com.danimanx.replicantsynth.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Project persistence. The engine itself is stateless between launches: this
 * JSON document is the single source of truth for the 64 slots, and the
 * ViewModel replays it into the native clip engine on load.
 */
object ProjectStore {
    const val FILE_NAME = "project.json"
    private const val VERSION = 2

    fun save(context: Context, project: ProjectState): Boolean = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.writeText(encode(project, context.filesDir))
        if (file.exists() && !file.delete()) error("cannot replace project")
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        true
    }.getOrDefault(false)

    fun load(context: Context): ProjectState? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        decode(file.readText(), context.filesDir)
    }.getOrNull()

    fun encode(project: ProjectState, filesDir: File? = null): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("name", project.name)
        root.put("bpm", project.bpm.toDouble())
        root.put("quantize", project.quantize)
        root.put("swing", project.swing.toDouble())
        root.put("masterGain", project.masterGain.toDouble())

        val clips = JSONArray()
        project.clips.forEach { clip ->
            val obj = JSONObject()
            obj.put("kind", clip.kind.name)
            obj.put("name", clip.name)
            obj.put("root", clip.root)
            obj.put("length", clip.length)
            obj.put("gain", clip.gain.toDouble())
            obj.put("pan", clip.pan.toDouble())
            obj.put("mute", clip.mute)
            obj.put("solo", clip.solo)
            obj.put("sampleLoop", clip.sampleLoop)
            obj.put("sampleName", clip.sampleName ?: JSONObject.NULL)
            obj.put("samplePath", relativeSamplePath(clip.samplePath, filesDir))
            obj.put("fxType", clip.fxType)
            obj.put("fxEnabled", clip.fxEnabled)

            val steps = JSONArray()
            clip.steps.take(ClipLimits.STEPS).forEach { step ->
                steps.put(
                    (if (step.gate) 1 else 0) or
                        (if (step.accent) 2 else 0) or
                        (if (step.slide) 4 else 0) or
                        ((step.note and 0x7F) shl 8) or
                        ((step.velocity and 0x7F) shl 16),
                )
            }
            obj.put("steps", steps)

            val midi = JSONArray()
            clip.midiNotes.take(ClipLimits.MAX_MIDI_NOTES).forEach { note ->
                midi.put(
                    (note.note and 0x7F) or
                        ((note.start and 0x0F) shl 8) or
                        (((note.length - 1).coerceIn(0, 15)) shl 12) or
                        ((note.velocity and 0x7F) shl 16),
                )
            }
            obj.put("midiNotes", midi)

            val masks = JSONArray()
            clip.drumMask.take(ClipLimits.DRUM_VOICES).forEach { masks.put(it) }
            obj.put("drumMask", masks)

            val velocities = JSONArray()
            clip.drumVel.take(ClipLimits.DRUM_VOICES * ClipLimits.STEPS).forEach {
                velocities.put(it)
            }
            obj.put("drumVel", velocities)

            val params = JSONArray()
            clip.params.take(ClipLimits.PARAMS).forEach { params.put(it.toDouble()) }
            obj.put("params", params)

            clips.put(obj)
        }
        root.put("clips", clips)
        return root.toString(2)
    }

    fun decode(json: String, filesDir: File? = null): ProjectState {
        val root = JSONObject(json)
        val project = ProjectState()
        project.name = root.optString("name", project.name)
        project.bpm = root.optDouble("bpm", project.bpm.toDouble()).toFloat()
            .coerceIn(30f, 300f)
        project.quantize = nearestQuantize(root.optInt("quantize", project.quantize))
        project.swing = root.optDouble("swing", project.swing.toDouble()).toFloat()
            .coerceIn(0f, 0.66f)
        project.masterGain = root.optDouble("masterGain", project.masterGain.toDouble()).toFloat()
            .coerceIn(0f, 1.5f)

        val clips = root.optJSONArray("clips") ?: return project
        for (index in 0 until minOf(clips.length(), ClipLimits.PADS)) {
            val obj = clips.optJSONObject(index) ?: continue
            val clip = project.clips[index]
            clip.kind = runCatching {
                ClipKind.valueOf(obj.optString("kind", ClipKind.EMPTY.name))
            }.getOrDefault(ClipKind.EMPTY)
            clip.name = obj.optString("name", defaultName(clip.kind, index))
            clip.root = obj.optInt("root", clip.root).coerceIn(0, 127)
            clip.length = obj.optInt("length", ClipLimits.STEPS).coerceIn(1, ClipLimits.STEPS)
            clip.gain = obj.optDouble("gain", clip.gain.toDouble()).toFloat().coerceIn(0f, 1.5f)
            clip.pan = obj.optDouble("pan", clip.pan.toDouble()).toFloat().coerceIn(-1f, 1f)
            clip.mute = obj.optBoolean("mute", false)
            clip.solo = obj.optBoolean("solo", false)
            clip.sampleLoop = obj.optBoolean("sampleLoop", true)
            clip.sampleName = if (obj.isNull("sampleName")) null else obj.optString("sampleName")
            val rawPath = if (obj.isNull("samplePath")) null else obj.optString("samplePath")
            clip.samplePath = resolveSamplePath(rawPath, filesDir)
            clip.sampleMissing = clip.samplePath != null && !File(clip.samplePath!!).exists()
            clip.fxType = obj.optInt("fxType", 0).coerceIn(0, FX_CATALOG.lastIndex)
            clip.fxEnabled = obj.optBoolean("fxEnabled", clip.fxType != 0)

            val steps = obj.optJSONArray("steps")
            if (steps != null) {
                clip.steps = List(ClipLimits.STEPS) { i ->
                    val packed = steps.optInt(i, 0)
                    Step(
                        gate = (packed and 1) != 0,
                        accent = (packed and 2) != 0,
                        slide = (packed and 4) != 0,
                        note = (packed shr 8) and 0x7F,
                        velocity = ((packed shr 16) and 0x7F).coerceAtLeast(1),
                    )
                }
            }

            val midi = obj.optJSONArray("midiNotes")
            if (midi != null) {
                clip.midiNotes = buildList {
                    for (i in 0 until minOf(midi.length(), ClipLimits.MAX_MIDI_NOTES)) {
                        val packed = midi.optInt(i, 0)
                        val note = packed and 0x7F
                        val start = (packed shr 8) and 0x0F
                        val length = ((packed shr 12) and 0x0F) + 1
                        val velocity = ((packed shr 16) and 0x7F).coerceAtLeast(1)
                        add(MidiNote(note, start, length, velocity))
                    }
                }
            }

            val masks = obj.optJSONArray("drumMask")
            if (masks != null) {
                val values = IntArray(ClipLimits.DRUM_VOICES)
                for (v in values.indices) values[v] = masks.optInt(v, 0)
                clip.drumMask = values
            }
            val velocities = obj.optJSONArray("drumVel")
            if (velocities != null) {
                val values = IntArray(ClipLimits.DRUM_VOICES * ClipLimits.STEPS) { 100 }
                for (i in values.indices) values[i] = velocities.optInt(i, 100).coerceIn(1, 127)
                clip.drumVel = values
            }
            val params = obj.optJSONArray("params")
            if (params != null) {
                val values = FloatArray(ClipLimits.PARAMS)
                for (i in values.indices) values[i] = params.optDouble(i, 0.0).toFloat()
                clip.params = values
            }
        }
        return project
    }

    private fun nearestQuantize(value: Int): Int =
        QUANTIZE_OPTIONS.minByOrNull { kotlin.math.abs(it - value) } ?: 16

    private fun relativeSamplePath(path: String?, filesDir: File?): Any {
        if (path == null) return JSONObject.NULL
        if (filesDir == null) return path
        val samples = File(filesDir, "samples")
        val file = File(path)
        return if (file.parentFile?.absolutePath == samples.absolutePath) {
            "samples/${file.name}"
        } else {
            path
        }
    }

    private fun resolveSamplePath(path: String?, filesDir: File?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("samples/") && filesDir != null) {
            return File(filesDir, path).absolutePath
        }
        val file = File(path)
        if (file.exists() || filesDir == null) return path
        val fallback = File(File(filesDir, "samples"), file.name)
        return if (fallback.exists()) fallback.absolutePath else path
    }
}
