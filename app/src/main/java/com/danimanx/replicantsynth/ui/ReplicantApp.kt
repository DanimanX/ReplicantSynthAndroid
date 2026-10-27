package com.danimanx.replicantsynth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danimanx.replicantsynth.BuildConfig
import com.danimanx.replicantsynth.audio.AudioStats
import com.danimanx.replicantsynth.midi.Apc64PadMode
import com.danimanx.replicantsynth.midi.Apc64State
import com.danimanx.replicantsynth.model.ClipKind
import com.danimanx.replicantsynth.model.quantizeLabel
import com.danimanx.replicantsynth.ui.components.ClipEditorDialog
import com.danimanx.replicantsynth.ui.components.FxPickerDialog
import com.danimanx.replicantsynth.ui.components.InspectorPanel
import com.danimanx.replicantsynth.ui.components.LedButton
import com.danimanx.replicantsynth.ui.components.MeterBar
import com.danimanx.replicantsynth.ui.components.MidiSheet
import com.danimanx.replicantsynth.ui.components.MonoLabel
import com.danimanx.replicantsynth.ui.components.PadDeck
import com.danimanx.replicantsynth.ui.components.PresetBrowser
import com.danimanx.replicantsynth.ui.components.ProjectMenuDialog
import com.danimanx.replicantsynth.ui.components.RecordingsSheet
import com.danimanx.replicantsynth.ui.components.TempoDialog
import com.danimanx.replicantsynth.ui.theme.Amber
import com.danimanx.replicantsynth.ui.theme.Cyan
import com.danimanx.replicantsynth.ui.theme.Ink
import com.danimanx.replicantsynth.ui.theme.InkDeep
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

@Composable
fun ReplicantApp(vm: MainViewModel) {
    val audio by vm.audioStats.collectAsState()
    val midi by vm.midiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val message = vm.message

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.consumeMessage()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF07111A), Ink, InkDeep),
                    ),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Cyan.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.05f),
                        radius = size.width * 0.65f,
                    ),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.9f, size.height * 0.05f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Magenta.copy(alpha = 0.06f), Color.Transparent),
                        center = Offset(size.width * 0.05f, size.height * 0.95f),
                        radius = size.width * 0.6f,
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.05f, size.height * 0.95f),
                )
                val step = size.width / 18f
                var x = 0f
                while (x < size.width) {
                    drawLine(PanelLine.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
            }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 760.dp
            val inspectorHeight = if (maxHeight >= 820.dp) 368.dp else 300.dp
            Column(Modifier.fillMaxSize()) {
                Header(
                    vm = vm,
                    audio = audio,
                    midi = midi,
                    wide = wide,
                )
                if (vm.isRecording) RecordingBanner(vm)
                if (vm.sampleBusy) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Lime.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.width(80.dp).height(2.dp),
                            color = Lime,
                            trackColor = PanelLine,
                        )
                        Spacer(Modifier.width(8.dp))
                        MonoLabel("DECODING AUDIO · WAV / FLAC / MP3", color = Lime, size = 8)
                    }
                }
                if (wide) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        PadDeck(vm, Modifier.weight(1f).fillMaxHeight().padding(8.dp))
                        InspectorPanel(
                            vm,
                            Modifier
                                .width(410.dp)
                                .fillMaxHeight()
                                .padding(end = 8.dp, bottom = 8.dp),
                        )
                    }
                } else {
                    PadDeck(
                        vm,
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                    InspectorPanel(
                        vm,
                        Modifier
                            .fillMaxWidth()
                            .height(inspectorHeight),
                    )
                }
                StatusBar(vm, audio, midi)
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = PanelHi,
                contentColor = TextBright,
                actionColor = Cyan,
                dismissActionContentColor = TextMid,
            )
        }

        if (vm.loading) LoadingOverlay(vm.loadingMessage)
        if (vm.showFxPicker) FxPickerDialog(vm)
        if (vm.showMidi) MidiSheet(vm)
        if (vm.showRecordings) RecordingsSheet(vm)
        if (vm.showProjectMenu) ProjectMenuDialog(vm)
        if (vm.showTempo) TempoDialog(vm)
        if (vm.editorPad != null) ClipEditorDialog(vm)
        if (vm.showPresets) PresetBrowser(vm)
    }
}

@Composable
private fun Header(vm: MainViewModel, audio: AudioStats, midi: Apc64State, wide: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        if (wide) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Logo(compact = false)
                Spacer(Modifier.width(18.dp))
                TransportCluster(vm, compact = false)
                Spacer(Modifier.width(12.dp))
                TempoChip(vm)
                Spacer(Modifier.width(6.dp))
                QuantizeChip(vm)
                Spacer(Modifier.weight(1f))
                AudioBadge(audio)
                Spacer(Modifier.width(6.dp))
                MidiChip(vm, midi)
                Spacer(Modifier.width(6.dp))
                MenuButton(vm)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Logo(compact = true)
                Spacer(Modifier.weight(1f))
                MidiChip(vm, midi)
                Spacer(Modifier.width(5.dp))
                MenuButton(vm)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                TransportCluster(vm, compact = true)
                Spacer(Modifier.width(2.dp))
                TempoChip(vm)
                QuantizeChip(vm)
                Spacer(Modifier.weight(1f))
                AudioBadge(audio)
            }
        }
        MeterBar(
            value = vm.masterMeter,
            color = if (vm.masterMeter > 0.98f) Red else Cyan,
            height = 2.dp,
        )
    }
}

@Composable
private fun Logo(compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "REPLICANT",
                    color = Cyan,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 16.sp else 22.sp,
                    letterSpacing = if (compact) 1.sp else 2.sp,
                )
                Text(
                    "SYNTH",
                    color = Magenta,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 16.sp else 22.sp,
                    letterSpacing = if (compact) 1.sp else 2.sp,
                )
            }
            if (!compact) {
                MonoLabel(
                    "64-PAD CLIP SYSTEM · 303 / WAVE / 909 / FX",
                    color = TextDim,
                    size = 8,
                )
            }
        }
        if (!compact) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .border(1.dp, Cyan.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                MonoLabel("v${BuildConfig.VERSION_NAME}", color = Cyan, size = 8)
            }
        }
    }
}

@Composable
private fun TransportCluster(vm: MainViewModel, compact: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TransportButton(
            icon = Icons.Filled.PlayArrow,
            label = "PLAY",
            active = vm.isPlaying,
            color = Lime,
            compact = compact,
        ) { vm.toggleTransport() }
        TransportButton(
            icon = Icons.Filled.Stop,
            label = "STOP",
            active = !vm.isPlaying,
            color = Amber,
            compact = compact,
        ) { vm.stopTransport() }
        TransportButton(
            icon = Icons.Filled.FiberManualRecord,
            label = if (vm.isRecording) formatTime(vm.recordingSeconds) else "REC",
            active = vm.isRecording,
            color = Red,
            compact = compact,
        ) { vm.toggleRecording() }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    color: Color,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(if (compact) 42.dp else 50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) color.copy(alpha = 0.16f) else Panel)
            .border(1.dp, if (active) color.copy(alpha = 0.8f) else PanelLine, RoundedCornerShape(8.dp))
            .semantics(mergeDescendants = true) { contentDescription = "TRANSPORT $label" }
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) color else TextMid,
            modifier = Modifier.size(if (compact) 17.dp else 20.dp),
        )
        MonoLabel(label, color = if (active) color else TextDim, size = 7)
    }
}

@Composable
private fun TempoChip(vm: MainViewModel) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Panel)
            .border(1.dp, PanelLine, RoundedCornerShape(8.dp))
            .clickable { vm.showTempo = true }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "${vm.project.bpm.toInt()}",
                color = Cyan,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            MonoLabel("BPM", color = TextDim, size = 7)
        }
    }
}

@Composable
private fun QuantizeChip(vm: MainViewModel) {
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Panel)
            .border(1.dp, PanelLine, RoundedCornerShape(8.dp))
            .clickable { vm.cycleQuantize() }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = quantizeLabel(vm.project.quantize),
            color = Lime,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
        )
        MonoLabel("LAUNCH", color = TextDim, size = 7)
    }
}

@Composable
private fun AudioBadge(audio: AudioStats) {
    val exclusive = audio.sharingLabel == "EXCLUSIVE"
    val color = when {
        audio.audioRunning && exclusive && audio.latencyMs < 20f -> Lime
        audio.audioRunning -> Amber
        else -> TextDim
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Panel)
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = when {
                !audio.audioRunning -> "SILENT"
                exclusive -> "${audio.latencyMs.toInt()} ms"
                else -> "SHARED"
            },
            color = color,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
        MonoLabel(
            if (audio.audioRunning) {
                "${audio.apiLabel} ${audio.sampleRate / 1000}k"
            } else {
                "CLOCK ${audio.sampleRate / 1000}k"
            },
            color = TextDim,
            size = 7,
        )
    }
}

@Composable
private fun MidiChip(vm: MainViewModel, midi: Apc64State) {
    val connected = midi.connected
    val accent = when {
        !connected -> TextDim
        midi.padMode == Apc64PadMode.CHORD -> Magenta
        else -> Lime
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (connected) accent.copy(alpha = 0.12f) else Panel)
            .border(
                1.dp,
                if (connected) accent.copy(alpha = 0.6f) else PanelLine,
                RoundedCornerShape(8.dp),
            )
            .semantics(mergeDescendants = true) { contentDescription = "MIDI DEVICE" }
            .clickable { vm.showMidi = true }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Usb,
            contentDescription = "MIDI",
            tint = if (connected) accent else TextDim,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        MonoLabel(
            if (connected) "APC64 ${midi.padMode.label}" else "MIDI",
            color = if (connected) accent else TextMid,
            size = 8,
        )
    }
}

@Composable
private fun MenuButton(vm: MainViewModel) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Panel)
            .border(1.dp, PanelLine, RoundedCornerShape(8.dp))
            .semantics(mergeDescendants = true) { contentDescription = "PROJECT MENU" }
            .clickable { vm.showProjectMenu = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Menu, contentDescription = "Project", tint = TextMid)
    }
}

@Composable
private fun RecordingBanner(vm: MainViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Red.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Red),
        )
        Spacer(Modifier.width(7.dp))
        MonoLabel(
            "REC ${formatTime(vm.recordingSeconds)} · ${vm.audioStats.value.sampleRate / 1000} kHz · 24-bit · " +
                (if (vm.stemRecordingPad != null) "PAD STEM" else "MASTER"),
            color = Red,
            size = 9,
        )
        Spacer(Modifier.weight(1f))
        LedButton(
            "STOP",
            true,
            Red,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "STOP RECORDING"
            },
        ) { vm.stopRecordingNow() }
    }
}

@Composable
private fun StatusBar(vm: MainViewModel, audio: AudioStats, midi: Apc64State) {
    val clips = vm.project.clips.count { it.kind != ClipKind.EMPTY }
    Row(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(InkDeep.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel("$clips CLIPS · 8 SCENES · 64 PADS", color = TextDim, size = 7)
        Spacer(Modifier.weight(1f))
        MonoLabel(
            if (midi.connected) "APC64 ${midi.padMode.label}" else "MIDI SCAN",
            color = if (midi.connected) Lime else TextDim,
            size = 7,
        )
        Spacer(Modifier.width(10.dp))
        MonoLabel(
            if (audio.audioRunning) "CPU ${(audio.cpuLoad * 100).toInt()}%" else "OFFLINE CLOCK",
            color = if (audio.xRuns > 0) Amber else TextDim,
            size = 7,
        )
        Spacer(Modifier.width(10.dp))
        MonoLabel("24-BIT CAPTURE", color = TextDim, size = 7)
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "REPLICANT",
                color = Cyan,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                letterSpacing = 3.sp,
            )
            Text(
                "SYNTH",
                color = Magenta,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp)
            Spacer(Modifier.height(14.dp))
            MonoLabel(message, color = TextMid, size = 9)
            Spacer(Modifier.height(4.dp))
            MonoLabel("303 · WAVE · 909 · FX · APC64", color = TextDim, size = 8)
        }
    }
}

fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
