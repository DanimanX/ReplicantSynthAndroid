package com.danimanx.replicantsynth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danimanx.replicantsynth.model.AcidPreset
import com.danimanx.replicantsynth.model.AcidPresets
import com.danimanx.replicantsynth.model.CHORD_MODES
import com.danimanx.replicantsynth.model.Clip
import com.danimanx.replicantsynth.model.ClipKind
import com.danimanx.replicantsynth.model.ClipLimits
import com.danimanx.replicantsynth.model.DrumPreset
import com.danimanx.replicantsynth.model.DrumPresets
import com.danimanx.replicantsynth.model.WAVE_PRESETS
import com.danimanx.replicantsynth.model.WaveParam
import com.danimanx.replicantsynth.model.quantizeLabel
import com.danimanx.replicantsynth.midi.Apc64PadMode
import com.danimanx.replicantsynth.ui.MainViewModel
import com.danimanx.replicantsynth.ui.theme.Amber
import com.danimanx.replicantsynth.ui.theme.Cyan
import com.danimanx.replicantsynth.ui.theme.Lime
import com.danimanx.replicantsynth.ui.theme.Magenta
import com.danimanx.replicantsynth.ui.theme.Mono
import com.danimanx.replicantsynth.ui.theme.Panel
import com.danimanx.replicantsynth.ui.theme.PanelHi
import com.danimanx.replicantsynth.ui.theme.PanelLine
import com.danimanx.replicantsynth.ui.theme.Red
import com.danimanx.replicantsynth.ui.theme.TextBright
import com.danimanx.replicantsynth.ui.theme.TextDim
import com.danimanx.replicantsynth.ui.theme.TextMid
import java.util.Locale

/** Double-tap a pad: piano roll for Wave, step sequencer for 303, kit grid for 909. */
@Composable
fun ClipEditorDialog(vm: MainViewModel) {
    val pad = vm.editorPad ?: return
    val clip = vm.project.clips[pad]
    SheetFrame(
        title = "CLIP ${(pad + 1).toString().padStart(2, '0')} · ${engineName(clip.kind)}",
        subtitle = "DOUBLE-TAP ANY PAD TO EDIT · ${clip.displayName}",
        onDismiss = { vm.closeEditor() },
    ) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MonoLabel(clip.displayName, color = TextBright, size = 11)
                Spacer(Modifier.weight(1f))
                MonoLabel("${vm.presetCount(clip.kind)} PRESETS", color = TextDim, size = 8)
                LedButton("PRESETS", false, kindColor(clip.kind.ordinal)) { vm.browsePresets() }
            }
            HorizontalDivider(
                Modifier.padding(top = 6.dp),
                color = PanelLine.copy(alpha = 0.7f),
            )
            when (clip.kind) {
                ClipKind.EMPTY -> Text(
                    "Assign an engine in the SOUND tab, then double-tap this pad again.",
                    color = TextMid,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                ClipKind.WAVE -> WaveMidiEditor(vm, clip)
                ClipKind.ACID, ClipKind.DRUMS -> PatternTab(vm, clip)
                ClipKind.SAMPLE -> Text(
                    "Audio clips have no pattern editor. Use the SOUND tab for loop, gain and pitch.",
                    color = TextMid,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun WaveMidiEditor(vm: MainViewModel, clip: Clip) {
    val midi by vm.midiState.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("PIANO ROLL", color = Cyan)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            MonoLabel("${clip.midiNotes.size} NOTES", color = Cyan, size = 9)
            Spacer(Modifier.weight(1f))
            MonoLabel("${vm.midiLength}/16", color = TextDim, size = 8)
            listOf(1 to "1/16", 2 to "1/8", 4 to "1/4", 8 to "1/2").forEach { (steps, label) ->
                NeonChip(label, vm.midiLength == steps, Cyan) { vm.chooseMidiLength(steps) }
            }
        }
        Spacer(Modifier.height(5.dp))
        val keysOn = midi.connected && midi.padMode.keySurface
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            LedButton(
                label = if (midi.connected) "APC64 ${midi.padMode.label}" else "APC64 OFFLINE",
                active = keysOn,
                color = when (midi.padMode) {
                    Apc64PadMode.CHORD -> Magenta
                    else -> Lime
                },
                enabled = false,
                onClick = {},
            )
            NeonChip("OCT -", false, Cyan) { vm.nudgeApcOctave(vm.apcOctave - 1) }
            MonoLabel("OCT ${if (vm.apcOctave >= 0) "+" else ""}${vm.apcOctave}", color = Amber, size = 9)
            NeonChip("OCT +", false, Cyan) { vm.nudgeApcOctave(vm.apcOctave + 1) }
            Spacer(Modifier.width(4.dp))
            MonoLabel("CURSOR ${(vm.midiCursor + 1).toString().padStart(2, '0')}", color = Amber, size = 8)
            NeonChip("-", false, Cyan) { vm.moveMidiCursor(vm.midiCursor - 1) }
            NeonChip("+", false, Cyan) { vm.moveMidiCursor(vm.midiCursor + 1) }
        }
        val hint = when {
            !midi.connected ->
                "CONNECT THE APC64 — PRESS NOTE OR CHORD ON THE UNIT"
            !midi.notesPortOpen ->
                "APC64 NOTES PORT MISSING — REPLUG THE USB CABLE"
            !midi.padMode.keySurface ->
                "PRESS NOTE OR CHORD ON THE APC64 TO PLAY INTO THIS ROLL"
            else ->
                "APC64 ${midi.padMode.label} ACTIVE · PADS LAND ON THE CURSOR"
        }
        MonoLabel(
            hint,
            color = if (keysOn) Lime else Amber,
            size = 8,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        PianoRoll(vm, clip)
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel("TAP CELL = ADD · TAP NOTE = DELETE", color = TextDim, size = 8)
            MonoLabel("ROOT ${noteName(clip.root)}", color = TextDim, size = 8)
        }
        SliderRow(
            label = "NOTE VELOCITY",
            valueText = "${vm.midiVelocity}",
            value = vm.midiVelocity.toFloat(),
            min = 1f,
            max = 127f,
            color = Cyan,
        ) { vm.chooseMidiVelocity(it.toInt().coerceIn(1, 127)) }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel("CHORD", color = TextDim, size = 8)
            CHORD_MODES.forEachIndexed { index, label ->
                NeonChip(
                    label,
                    clip.param(WaveParam.CHORD).toInt() == index,
                    Magenta,
                ) { vm.setParam(clip, WaveParam.CHORD, index.toFloat()) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            NeonChip("CLEAR NOTES", false, Red) { vm.clearMidiNotes(clip) }
            NeonChip("STEPS → MIDI", false, Amber) { vm.convertStepsToMidi(clip) }
            Spacer(Modifier.weight(1f))
            MonoLabel("LAUNCH · ${quantizeLabel(vm.project.quantize)}", color = TextDim, size = 8)
        }
    }
}

@Composable
private fun PianoRoll(vm: MainViewModel, clip: Clip) {
    val rows = 25
    val low = (clip.root - 12).coerceIn(0, 127 - rows)
    val high = low + rows - 1
    val rowHeight = 13.dp
    val runtime = vm.padRuntime[clip.index].value
    val notes = clip.midiNotes

    Row(Modifier.fillMaxWidth().height(rowHeight * rows)) {
        Column(Modifier.width(33.dp).fillMaxHeight()) {
            for (note in high downTo low) {
                val isRoot = note % 12 == clip.root % 12
                Box(
                    Modifier.fillMaxWidth().height(rowHeight),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = noteName(note),
                        color = if (isRoot) Amber else TextDim,
                        fontSize = 7.sp,
                        fontFamily = Mono,
                        fontWeight = if (isRoot) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(3.dp))
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(PanelHi)
                .border(1.dp, PanelLine, RoundedCornerShape(6.dp))
                .pointerInput(clip.index, notes, low, rows) {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    detectTapGestures { offset ->
                        val step = ((offset.x / (w / ClipLimits.STEPS)).toInt())
                            .coerceIn(0, ClipLimits.STEPS - 1)
                        val row = ((offset.y / (h / rows)).toInt()).coerceIn(0, rows - 1)
                        val note = high - row
                        vm.moveMidiCursor(step)
                        val existing = notes.firstOrNull {
                            it.note == note && step >= it.start && step < it.end
                        }
                        if (existing != null) {
                            vm.removeMidiNote(clip, existing.note, existing.start)
                        } else {
                            vm.addMidiNote(
                                clip,
                                note,
                                start = step,
                                length = vm.midiLength,
                                velocity = vm.midiVelocity,
                                advance = false,
                            )
                        }
                    }
                },
        ) {
            val colW = size.width / ClipLimits.STEPS.toFloat()
            val rowH = size.height / rows.toFloat()

            for (r in 0 until rows) {
                val note = high - r
                val black = note % 12 in intArrayOf(1, 3, 6, 8, 10)
                drawRect(
                    color = if (black) Color(0xFF0A1620) else Color(0xFF0E1F2B),
                    topLeft = Offset(0f, r * rowH),
                    size = Size(size.width, rowH),
                )
                if (note % 12 == clip.root % 12) {
                    drawRect(
                        color = Amber.copy(alpha = 0.07f),
                        topLeft = Offset(0f, r * rowH),
                        size = Size(size.width, rowH),
                    )
                }
            }

            for (r in 0..rows) {
                drawLine(
                    color = PanelLine.copy(alpha = 0.25f),
                    start = Offset(0f, r * rowH),
                    end = Offset(size.width, r * rowH),
                    strokeWidth = 1f,
                )
            }
            for (step in 0..ClipLimits.STEPS) {
                drawLine(
                    color = if (step % 4 == 0) PanelLine else PanelLine.copy(alpha = 0.30f),
                    start = Offset(step * colW, 0f),
                    end = Offset(step * colW, size.height),
                    strokeWidth = if (step % 4 == 0) 1.6f else 1f,
                )
            }

            if (runtime.isPlaying) {
                drawRect(
                    color = Cyan.copy(alpha = 0.08f),
                    topLeft = Offset(runtime.phase.coerceIn(0, 15) * colW, 0f),
                    size = Size(colW, size.height),
                )
            }
            drawLine(
                color = Amber,
                start = Offset(vm.midiCursor * colW + colW * 0.5f, 0f),
                end = Offset(vm.midiCursor * colW + colW * 0.5f, size.height),
                strokeWidth = 2f,
            )

            for (note in notes) {
                val row = high - note.note
                if (row !in 0 until rows) continue
                val topLeft = Offset(note.start * colW + 1f, row * rowH + 1f)
                val size = Size(note.length * colW - 2f, rowH - 2f)
                if (size.width <= 0f || size.height <= 0f) continue
                val color = when {
                    note.velocity >= 110 -> Amber
                    note.velocity >= 90 -> Cyan
                    else -> Magenta
                }
                drawRoundRect(
                    color = color.copy(alpha = 0.78f),
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = CornerRadius(3f, 3f),
                )
                drawRoundRect(
                    color = TextBright.copy(alpha = 0.55f),
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = CornerRadius(3f, 3f),
                    style = Stroke(width = 1f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preset browser
// ---------------------------------------------------------------------------

@Composable
fun PresetBrowser(vm: MainViewModel) {
    val clip = vm.selectedClip
    val kind = clip.kind
    var query by remember { mutableStateOf("") }
    var bank by remember { mutableIntStateOf(-1) }
    val q = query.trim().lowercase(Locale.US)

    SheetFrame(
        title = "FACTORY PRESETS",
        subtitle = "${engineName(kind)} · TARGET PAD ${(clip.index + 1).toString().padStart(2, '0')}",
        onDismiss = { vm.showPresets = false },
    ) {
        Text(
            "Ported from the desktop Replicant banks. Applying a preset keeps this clip's FX insert and mixer settings.",
            color = TextDim,
            fontSize = 9.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { MonoLabel("SEARCH PRESET", color = TextDim, size = 8) },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextBright,
                fontFamily = Mono,
                fontSize = 12.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        if (kind == ClipKind.ACID) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NeonChip("ALL", bank < 0, Amber) { bank = -1 }
                AcidPresets.BANK_NAMES.forEachIndexed { index, name ->
                    NeonChip(
                        "${('A'.code + index).toChar()} · $name",
                        bank == index,
                        Amber,
                    ) { bank = index }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        when (kind) {
            ClipKind.WAVE -> {
                val filtered = remember(q) {
                    if (q.isBlank()) WAVE_PRESETS
                    else WAVE_PRESETS.filter {
                        it.name.lowercase(Locale.US).contains(q) ||
                            it.category.lowercase(Locale.US).contains(q)
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered.size, key = { filtered[it].name }) { index ->
                        val preset = filtered[index]
                        PresetRow(
                            code = preset.category.take(4).uppercase(Locale.US),
                            name = preset.name,
                            detail = "${preset.category} · ${preset.notes.size} " +
                                if (preset.notes.size == 1) "note" else "notes",
                            color = Cyan,
                        ) { vm.applyPreset(preset) }
                    }
                }
            }
            ClipKind.ACID -> {
                val all = remember { AcidPresets.all }
                val filtered = remember(q, bank) {
                    all.filter { preset ->
                        (bank < 0 || preset.bank == bank) &&
                            (q.isBlank() ||
                                preset.name.lowercase(Locale.US).contains(q) ||
                                preset.displayName.lowercase(Locale.US).contains(q))
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered.size, key = { "${filtered[it].bank}-${filtered[it].slot}" }) { index ->
                        val preset = filtered[index]
                        AcidPresetRow(preset) { vm.applyPreset(preset) }
                    }
                }
            }
            ClipKind.DRUMS -> {
                val filtered = remember(q) {
                    if (q.isBlank()) DrumPresets.all
                    else DrumPresets.all.filter {
                        it.name.lowercase(Locale.US).contains(q) ||
                            it.category.lowercase(Locale.US).contains(q)
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered.size, key = { filtered[it].name }) { index ->
                        val preset = filtered[index]
                        PresetRow(
                            code = preset.category.take(4).uppercase(Locale.US),
                            name = preset.name,
                            detail = "${preset.category} · 16-step kit",
                            color = Magenta,
                        ) { vm.applyPreset(preset) }
                    }
                }
            }
            else -> Text(
                "Select a 303, Wave or 909 pad first.",
                color = TextMid,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun AcidPresetRow(preset: AcidPreset, onApply: () -> Unit) {
    PresetRow(
        code = "${preset.bankLetter}${(preset.slot + 1).toString().padStart(2, '0')}",
        name = preset.name,
        detail = "${AcidPresets.BANK_NAMES[preset.bank]} · ${preset.length} steps",
        color = Amber,
        onApply = onApply,
    )
}

@Composable
private fun PresetRow(
    code: String,
    name: String,
    detail: String,
    color: Color,
    onApply: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PanelHi)
            .border(1.dp, PanelLine, RoundedCornerShape(8.dp))
            .clickable(onClick = onApply)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(44.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            MonoLabel(code, color = color, size = 8)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = TextBright,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MonoLabel(detail, color = TextDim, size = 7)
        }
        MonoLabel("LOAD", color = color, size = 8)
    }
}

private fun engineName(kind: ClipKind): String = when (kind) {
    ClipKind.EMPTY -> "NO ENGINE"
    ClipKind.ACID -> "REPLICANT303"
    ClipKind.WAVE -> "REPLICANTWAVE"
    ClipKind.DRUMS -> "REPLICANT909"
    ClipKind.SAMPLE -> "AUDIO CLIP"
}
