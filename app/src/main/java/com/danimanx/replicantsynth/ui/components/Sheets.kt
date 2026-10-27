package com.danimanx.replicantsynth.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.danimanx.replicantsynth.audio.SampleLibrary
import com.danimanx.replicantsynth.midi.Apc64Codec
import com.danimanx.replicantsynth.model.FX_CATALOG
import com.danimanx.replicantsynth.model.FxParam
import com.danimanx.replicantsynth.model.QUANTIZE_OPTIONS
import com.danimanx.replicantsynth.model.fxById
import com.danimanx.replicantsynth.model.quantizeLabel
import com.danimanx.replicantsynth.ui.MainViewModel
import com.danimanx.replicantsynth.ui.RecordingTake
import com.danimanx.replicantsynth.ui.theme.Amber
import com.danimanx.replicantsynth.ui.theme.Cyan
import com.danimanx.replicantsynth.ui.theme.Ink
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

@Composable
internal fun SheetFrame(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, PanelLine, RoundedCornerShape(18.dp)),
            color = Panel,
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = TextBright,
                            fontFamily = Mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp,
                        )
                        MonoLabel(subtitle, color = TextDim, size = 8)
                    }
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(PanelHi)
                            .combinedClickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMid)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun FxPickerDialog(vm: MainViewModel) {
    val clip = vm.selectedClip
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim().lowercase(Locale.US)
        if (q.isBlank()) FX_CATALOG
        else FX_CATALOG.filter {
            it.name.lowercase(Locale.US).contains(q) ||
                it.code.lowercase(Locale.US).contains(q) ||
                it.labels.any { label -> label.lowercase(Locale.US).contains(q) }
        }
    }
    SheetFrame(
        title = "REPLICANTFX",
        subtitle = "48-SLOT SP-404MKII MFX CATALOG · PAD ${(clip.index + 1).toString().padStart(2, '0')}",
        onDismiss = { vm.showFxPicker = false },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { MonoLabel("SEARCH EFFECT / CODE", color = TextDim, size = 8) },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextBright,
                fontFamily = Mono,
                fontSize = 12.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.id }) { info ->
                val selected = info.id == clip.fxType
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Magenta.copy(alpha = 0.14f) else PanelHi)
                        .border(
                            1.dp,
                            if (selected) Magenta.copy(alpha = 0.7f) else PanelLine,
                            RoundedCornerShape(8.dp),
                        )
                        .combinedClickable { vm.setFxType(clip, info.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(42.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Magenta.copy(alpha = if (selected) 0.24f else 0.10f))
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MonoLabel(info.code, color = Magenta, size = 9)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = info.name,
                            color = if (selected) TextBright else TextMid,
                            fontFamily = Mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MonoLabel(info.labels.joinToString(" · "), color = TextDim, size = 7)
                    }
                    MonoLabel(info.id.toString().padStart(2, '0'), color = TextDim, size = 8)
                }
            }
        }
    }
}

@Composable
fun MidiSheet(vm: MainViewModel) {
    val state by vm.midiState.collectAsState()
    SheetFrame(
        title = "APC64 / USB MIDI",
        subtitle = "64 PADS · 8 SCENES · TOUCH STRIPS · OLED",
        onDismiss = { vm.showMidi = false },
    ) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PanelHi)
                    .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (state.connected) Lime else Red),
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.connectedName ?: "NO CONTROLLER CONNECTED",
                        color = if (state.connected) Lime else TextMid,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    MonoLabel(
                        when {
                            !state.available -> "ANDROID MIDI SERVICE UNAVAILABLE"
                            state.connected ->
                                "FW ${state.firmware ?: "…"} · ${state.padMode.label}"
                            else -> "USB HOST MIDI READY · PROFILE APC64"
                        },
                        color = if (state.connected && !state.firmwareSupported) Amber else TextDim,
                        size = 8,
                    )
                }
                LedButton("REFRESH", false, Cyan) { vm.midi.scanDevices() }
            }

            SectionTitle("DEVICES")
            if (state.devices.isEmpty()) {
                Text(
                    "Connect an APC64 with a USB-C OTG adapter. The app sends the Akai SysEx handshake automatically.",
                    color = TextMid,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                state.devices.forEach { device ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PanelHi)
                            .border(1.dp, PanelLine, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                device.name,
                                color = TextBright,
                                fontFamily = Mono,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MonoLabel(
                                if (state.connectedId == device.id) "CONNECTED" else "AVAILABLE",
                                color = if (state.connectedId == device.id) Lime else TextDim,
                                size = 8,
                            )
                        }
                        LedButton(
                            label = if (state.connectedId == device.id) "DISCONNECT" else "CONNECT",
                            active = state.connectedId == device.id,
                            color = Cyan,
                            onClick = {
                                if (state.connectedId == device.id) vm.midi.disconnect()
                                else vm.midi.connect(device.id)
                            },
                        )
                    }
                }
            }

            SectionTitle("PAD MAPPING")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NeonChip("BASE 0 · DAW", state.padBaseNote == 0, Cyan) {
                    vm.midi.setPadBaseNote(0)
                }
                NeonChip("BASE 36 · CUSTOM", state.padBaseNote == 36, Cyan) {
                    vm.midi.setPadBaseNote(36)
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                label = "FLIP PAD ROWS",
                detail = "Note 0 = bottom-left (Ableton layout)",
                checked = state.flipRows,
            ) { vm.midi.setFlipRows(it) }
            SwitchRow(
                label = "LED FEEDBACK",
                detail = "Note-on velocity palette on channel 6",
                checked = state.ledFeedback,
            ) { vm.midi.setLedFeedback(it) }
            SectionTitle("PAD MODE")
            MonoLabel(
                when {
                    state.padMode.keySurface ->
                        "${state.padMode.label} · NOTES PORT → PIANO ROLL"
                    state.padMode.hostOwnsPads ->
                        "${state.padMode.label} · PADS → CLIPS"
                    else ->
                        "${state.padMode.label} · PADS STAY ON THE UNIT"
                },
                color = if (state.padMode.keySurface) Lime else Cyan,
                size = 9,
            )
            MonoLabel(
                "DAW ${state.dawPort ?: "—"} · NOTES ${state.notesPort ?: "MISSING"}",
                color = if (!state.connected || state.notesPortOpen) TextMid else Amber,
                size = 8,
            )
            MonoLabel(
                if (state.firmware == null) {
                    "FIRMWARE VALIDATING…"
                } else {
                    "FIRMWARE ${state.firmware} · " +
                        if (state.firmwareSupported) "MODE MAP OK"
                        else "VALIDATE ${Apc64Codec.VALIDATED_FIRMWARE}"
                },
                color = if (state.firmware != null && !state.firmwareSupported) {
                    Amber
                } else {
                    TextDim
                },
                size = 8,
            )
            Text(
                "Press NOTE or CHORD on the APC64. The app follows the hardware mode; no app switch moves the session pads into key input. In Note/Chord the unit owns the pad grid and its LEDs are restored when you return to Session.",
                color = TextMid,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            SectionTitle("TOUCH STRIPS")
            MonoLabel(
                "LED BARS FOLLOW THE VALUE · CC 104-111 / 112-119 + PITCH BEND",
                color = TextDim,
                size = 8,
            )
            MonoLabel(
                "1 MASTER · 8 PAD GAIN · 2-7 = SIX FIELDS OF THE FOCUSED MENU",
                color = Cyan,
                size = 8,
            )
            Text(
                "Open a clip and the middle strips become that page's parameters: the six " +
                    "engine knobs on SOUND, the six macros on FX, the step fields on STEPS. " +
                    "Touching a strip focuses its field on the editor and on the OLED.",
                color = TextMid,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp),
            )

            SectionTitle("STEP SEQUENCER")
            SwitchRow(
                label = "STEP SEQ TO CLIP",
                detail = "Step-view pads add/remove steps of the selected clip",
                checked = vm.stepSeqCapture,
            ) { vm.toggleStepSeqCapture(it) }
            Spacer(Modifier.height(4.dp))
            MonoLabel("DRUM PAD BASE NOTE", color = TextDim, size = 8)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(36, 40, 48, 60).forEach { base ->
                    NeonChip("$base", state.stepSeqDrumBase == base, Amber) {
                        vm.midi.setStepSeqDrumBase(base)
                    }
                }
            }
            Text(
                "The APC64 Step Sequencer is its own transport and owns its pad grid, so the " +
                    "app cannot draw on it. Its pads and notes are read from the Notes cable " +
                    "instead: stopped, a pad toggles a cell; playing, it records the unit's " +
                    "sequence into the clip with the incoming velocity, quantised to 16ths.",
                color = TextMid,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp),
            )

            SectionTitle("CONTROLS")
            Text(
                "PADS SESSION  NOTES 0–63 (CLIPS)\nPADS NOTE     NOTES PORT → PIANO ROLL\nPADS CHORD    NOTES PORT → CHORD NOTES\nSCENES        NOTES 112–119\nPLAY/STOP     91 / 93\nRECORD        92\nCLIP STOP     111\nMUTE / SOLO   109 / 110\nCURSORS       94 / 95 / 96 / 97\nENCODER       CC 90\nENC PRESS     NOTE 90\nTOUCH 1–8     PB CH 1–8\nSTRIP 1       MASTER GAIN\nSTRIP 8       PAD GAIN\nSTRIP 2–7     SIX FIELDS OF THE FOCUSED MENU\nMENUS  UP/DOWN PAGE · LEFT/RIGHT FIELD\nSTEP SEQ      NOTES → CLIP STEPS",
                color = TextMid,
                fontFamily = Mono,
                fontSize = 8.sp,
                lineHeight = 12.sp,
            )

            SectionTitle("TEST")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LedButton("LED TEST", false, Magenta) { vm.testApcLeds() }
                LedButton("OLED", false, Cyan) {
                    vm.midi.sendOled(
                        listOf(
                            "REPLICANT",
                            "SYNTH",
                            "${vm.project.bpm.toInt()} BPM",
                            quantizeLabel(vm.project.quantize),
                        ),
                    )
                }
            }

            SectionTitle("MONITOR")
            MonoLabel(
                state.lastMessage.ifBlank { "Waiting for MIDI…" },
                color = Cyan,
                size = 9,
            )
            state.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Red, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Handshake sent on connect: F0 47 00 53 1B / 19 / 1C · LED channel 6 · OLED lines 1–4.",
                color = TextDim,
                fontSize = 9.sp,
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PanelHi)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            MonoLabel(label, color = TextBright, size = 9)
            MonoLabel(detail, color = TextDim, size = 8)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cyan,
                checkedTrackColor = Cyan.copy(alpha = 0.3f),
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = PanelLine,
            ),
        )
    }
}

@Composable
fun RecordingsSheet(vm: MainViewModel) {
    val context = LocalContext.current
    SheetFrame(
        title = "RECORDINGS",
        subtitle = "24-BIT WAV · MASTER TAKES AND PAD STEMS",
        onDismiss = { vm.showRecordings = false },
    ) {
        if (vm.recordings.isEmpty()) {
            Text(
                "Hit REC in the header to capture the master, or RECORD STEM in the MIX tab to capture one pad.",
                color = TextMid,
                fontSize = 12.sp,
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(vm.recordings, key = { it.file.absolutePath }) { take ->
                    RecordingRow(
                        take = take,
                        onLoad = { vm.loadTakeIntoPad(take, vm.selectedPad) },
                        onShare = {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.files",
                                take.file,
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/wav"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(intent, "Share take"))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(take: RecordingTake, onLoad: () -> Unit, onShare: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PanelHi)
            .border(1.dp, PanelLine, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                take.name,
                color = TextBright,
                fontFamily = Mono,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MonoLabel(
                "${"%.1f".format(take.seconds)} s · ${take.file.length() / 1024} kB" +
                    if (take.isStem) " · STEM" else " · MASTER",
                color = if (take.isStem) Amber else Cyan,
                size = 8,
            )
        }
        LedButton("LOAD", false, Lime, onClick = onLoad)
        Spacer(Modifier.width(4.dp))
        LedButton("SHARE", false, Cyan, onClick = onShare)
    }
}

@Composable
fun ProjectMenuDialog(vm: MainViewModel) {
    var name by remember { mutableStateOf(vm.project.name) }
    var confirmNew by remember { mutableStateOf(false) }
    SheetFrame(
        title = "PROJECT",
        subtitle = "AUTOSAVE · ${vm.project.name}",
        onDismiss = { vm.showProjectMenu = false },
    ) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    vm.renameProject(it)
                },
                singleLine = true,
                label = { MonoLabel("PROJECT NAME", color = TextDim, size = 8) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextBright,
                    fontFamily = Mono,
                    fontSize = 13.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle("TEMPO")
            SliderRow(
                label = "BPM",
                valueText = "${vm.project.bpm.toInt()}",
                value = vm.project.bpm,
                min = 30f,
                max = 300f,
                onChange = { vm.setBpm(it) },
                color = Cyan,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(120f, 126f, 130f, 140f, 150f).forEach { bpm ->
                    NeonChip("${bpm.toInt()}", vm.project.bpm == bpm, Cyan) { vm.setBpm(bpm) }
                }
            }
            SliderRow(
                "SWING",
                pct(vm.project.swing),
                vm.project.swing,
                0f,
                0.66f,
                Amber,
            ) { vm.setSwing(it) }
            SliderRow(
                "MASTER GAIN",
                pct(vm.project.masterGain),
                vm.project.masterGain,
                0f,
                1.5f,
                Magenta,
            ) { vm.setMasterGain(it) }

            SectionTitle("LAUNCH QUANTIZE")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                QUANTIZE_OPTIONS.forEach { option ->
                    NeonChip(quantizeLabel(option), vm.project.quantize == option, Lime) {
                        vm.setQuantize(option)
                    }
                }
            }

            SectionTitle("ACTIONS")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LedButton("SAVE NOW", false, Cyan) { vm.saveNow() }
                LedButton("RECORDINGS", false, Amber) {
                    vm.showProjectMenu = false
                    vm.showRecordings = true
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LedButton("LOAD DEMO", false, Magenta) {
                    vm.showProjectMenu = false
                    vm.loadDemo()
                }
                LedButton("NEW PROJECT", false, Red) { confirmNew = true }
            }
            Spacer(Modifier.height(14.dp))
            MonoLabel("REPLICANTSYNTH 0.0.2 · MIT", color = TextDim, size = 8)
            MonoLabel(
                "Replicant303 · ReplicantWave · Replicant909 · ReplicantFX",
                color = TextDim,
                size = 7,
            )
        }
    }
    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Clear all 64 pads?", fontFamily = Mono, fontSize = 15.sp) },
            text = {
                Text(
                    "The current project is replaced by an empty grid. Recordings and imported samples stay on disk.",
                    fontSize = 12.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmNew = false
                    vm.showProjectMenu = false
                    vm.newProject()
                }) { Text("CLEAR", color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmNew = false }) { Text("CANCEL") }
            },
            containerColor = PanelHi,
        )
    }
}

@Composable
fun TempoDialog(vm: MainViewModel) {
    SheetFrame(
        title = "TRANSPORT",
        subtitle = "TEMPO · SWING · MASTER",
        onDismiss = { vm.showTempo = false },
    ) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoLabel("BPM", color = TextDim, size = 9)
                Text(
                    text = "${vm.project.bpm.toInt()}",
                    color = Cyan,
                    fontFamily = Mono,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Slider(
                value = vm.project.bpm,
                onValueChange = { vm.setBpm(it) },
                valueRange = 30f..300f,
                colors = SliderDefaults.colors(
                    thumbColor = Cyan,
                    activeTrackColor = Cyan,
                    inactiveTrackColor = PanelLine,
                ),
            )
            Spacer(Modifier.height(8.dp))
            SliderRow("SWING", pct(vm.project.swing), vm.project.swing, 0f, 0.66f, Amber) {
                vm.setSwing(it)
            }
            SliderRow(
                "MASTER GAIN",
                pct(vm.project.masterGain),
                vm.project.masterGain,
                0f,
                1.5f,
                Magenta,
            ) { vm.setMasterGain(it) }
            SectionTitle("LAUNCH QUANTIZE")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QUANTIZE_OPTIONS.forEach { option ->
                    NeonChip(quantizeLabel(option), vm.project.quantize == option, Lime) {
                        vm.setQuantize(option)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
