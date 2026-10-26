package com.danimanx.replicantsynth.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danimanx.replicantsynth.model.AcidParam
import com.danimanx.replicantsynth.model.CHORD_MODES
import com.danimanx.replicantsynth.model.Clip
import com.danimanx.replicantsynth.model.ClipKind
import com.danimanx.replicantsynth.model.ClipLimits
import com.danimanx.replicantsynth.model.DISTORTION_TYPES
import com.danimanx.replicantsynth.model.DRUM_VOICE_NAMES
import com.danimanx.replicantsynth.model.DrumParam
import com.danimanx.replicantsynth.model.FILTER_MODES
import com.danimanx.replicantsynth.model.FxParam
import com.danimanx.replicantsynth.model.SampleParam
import com.danimanx.replicantsynth.model.Step
import com.danimanx.replicantsynth.model.WAVETABLE_BANKS
import com.danimanx.replicantsynth.model.WaveParam
import com.danimanx.replicantsynth.model.fxById
import com.danimanx.replicantsynth.model.quantizeLabel
import com.danimanx.replicantsynth.ui.MainViewModel
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

private val TABS = listOf("SOUND", "FX", "PATTERN", "MIX")

@Composable
fun InspectorPanel(vm: MainViewModel, modifier: Modifier = Modifier) {
    val clip = vm.selectedClip
    Column(
        modifier
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(Panel)
            .border(1.dp, PanelLine.copy(alpha = 0.7f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
    ) {
        InspectorHeader(vm, clip)
        KindRow(vm, clip)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TABS.forEachIndexed { index, label ->
                NeonChip(
                    label = label,
                    selected = vm.selectedTab == index,
                    color = if (index == 1) Magenta else Cyan,
                    onClick = { vm.selectTab(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(
            Modifier.padding(top = 6.dp),
            color = PanelLine.copy(alpha = 0.7f),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            when (vm.selectedTab) {
                0 -> SoundTab(vm, clip)
                1 -> FxTab(vm, clip)
                2 -> PatternTab(vm, clip)
                else -> MixTab(vm, clip)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun InspectorHeader(vm: MainViewModel, clip: Clip) {
    val playing = vm.padRuntime[clip.index].value.isPlaying
    val queued = vm.padRuntime[clip.index].value.isQueued
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoLabel(
                    "PAD ${(clip.index + 1).toString().padStart(2, '0')}",
                    color = kindColor(clip.kind.ordinal),
                    size = 9,
                )
                Spacer(Modifier.width(6.dp))
                MonoLabel(
                    kindLabel(clip.kind),
                    color = TextDim,
                    size = 8,
                )
                if (clip.fxType != 0) {
                    Spacer(Modifier.width(6.dp))
                    MonoLabel(fxById(clip.fxType).code, color = Magenta, size = 8)
                }
            }
            Text(
                text = clip.displayName.ifBlank { "SELECT AN ENGINE" },
                color = TextBright,
                fontFamily = Mono,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        LedButton(
            label = when {
                clip.kind == ClipKind.EMPTY -> "EMPTY"
                queued -> "QUEUED"
                playing -> "STOP"
                else -> "LAUNCH"
            },
            active = playing || queued,
            color = if (queued) Amber else if (playing) Lime else Cyan,
            enabled = clip.kind != ClipKind.EMPTY,
            onClick = { vm.togglePad(clip.index) },
        )
    }
}

@Composable
private fun KindRow(vm: MainViewModel, clip: Clip) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindChip(vm, clip, ClipKind.ACID, "ACID 303", Amber)
        KindChip(vm, clip, ClipKind.WAVE, "WAVE", Cyan)
        KindChip(vm, clip, ClipKind.DRUMS, "DRUMS 909", Magenta)
        KindChip(vm, clip, ClipKind.SAMPLE, "SAMPLE", Lime)
        NeonChip(
            label = "CLEAR",
            selected = false,
            color = Red,
            onClick = { vm.changeKind(clip.index, ClipKind.EMPTY) },
        )
    }
}

@Composable
private fun KindChip(vm: MainViewModel, clip: Clip, kind: ClipKind, label: String, color: Color) {
    NeonChip(
        label = label,
        selected = clip.kind == kind,
        color = color,
        onClick = { if (clip.kind != kind) vm.changeKind(clip.index, kind) },
    )
}

private fun kindLabel(kind: ClipKind): String = when (kind) {
    ClipKind.EMPTY -> "NO ENGINE"
    ClipKind.ACID -> "REPLICANT303"
    ClipKind.WAVE -> "REPLICANTWAVE"
    ClipKind.DRUMS -> "REPLICANT909"
    ClipKind.SAMPLE -> "AUDIO CLIP"
}

// ---------------------------------------------------------------------------
// SOUND
// ---------------------------------------------------------------------------

@Composable
private fun SoundTab(vm: MainViewModel, clip: Clip) {
    when (clip.kind) {
        ClipKind.EMPTY -> {
            Text(
                "Pick an engine above, or load a WAV / FLAC / MP3 into this pad.",
                color = TextMid,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            ImportButton(vm, clip, full = true)
        }
        ClipKind.ACID -> {
            PresetBar(vm, clip, Amber)
            AcidSound(vm, clip)
        }
        ClipKind.WAVE -> {
            PresetBar(vm, clip, Cyan)
            WaveSound(vm, clip)
        }
        ClipKind.DRUMS -> {
            PresetBar(vm, clip, Magenta)
            DrumSound(vm, clip)
        }
        ClipKind.SAMPLE -> SampleSound(vm, clip)
    }
}

@Composable
internal fun PresetBar(vm: MainViewModel, clip: Clip, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel("FACTORY PRESETS", color = TextDim, size = 8)
        Spacer(Modifier.weight(1f))
        MonoLabel("${vm.presetCount(clip.kind)} AVAILABLE", color = TextDim, size = 8)
        LedButton("BROWSE", active = false, color = color) { vm.browsePresets() }
    }
}

@Composable
private fun AcidSound(vm: MainViewModel, clip: Clip) {
    SectionTitle("OSCILLATOR")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        NeonChip("SAW", clip.param(AcidParam.WAVE) < 0.5f, Amber) {
            vm.setParam(clip, AcidParam.WAVE, 0f)
        }
        NeonChip("SQUARE", clip.param(AcidParam.WAVE) >= 0.5f, Amber) {
            vm.setParam(clip, AcidParam.WAVE, 1f)
        }
        Spacer(Modifier.weight(1f))
        MonoLabel("DIODE LADDER", color = TextDim, size = 8)
    }
    SectionTitle("FILTER / ENVELOPE")
    KnobGrid(
        color = Amber,
        specs = listOf(
            KnobSpec("CUTOFF", clip.param(AcidParam.CUTOFF), 40f, 4200f, hz(clip.param(AcidParam.CUTOFF)), 420f) {
                vm.setParam(clip, AcidParam.CUTOFF, it)
            },
            KnobSpec("RESONANCE", clip.param(AcidParam.RESONANCE), 0f, 1f, pct(clip.param(AcidParam.RESONANCE)), 0.72f) {
                vm.setParam(clip, AcidParam.RESONANCE, it)
            },
            KnobSpec("ENV MOD", clip.param(AcidParam.ENV_MOD), 0f, 1f, pct(clip.param(AcidParam.ENV_MOD)), 0.68f) {
                vm.setParam(clip, AcidParam.ENV_MOD, it)
            },
            KnobSpec("DECAY", clip.param(AcidParam.DECAY), 0.2f, 2.5f, secs(clip.param(AcidParam.DECAY)), 0.55f) {
                vm.setParam(clip, AcidParam.DECAY, it)
            },
            KnobSpec("ACCENT", clip.param(AcidParam.ACCENT), 0f, 1f, pct(clip.param(AcidParam.ACCENT)), 0.82f) {
                vm.setParam(clip, AcidParam.ACCENT, it)
            },
            KnobSpec("VOLUME", clip.param(AcidParam.VOLUME), 0f, 1.2f, pct(clip.param(AcidParam.VOLUME)), 0.9f) {
                vm.setParam(clip, AcidParam.VOLUME, it)
            },
        ),
    )
    SectionTitle("DISTORTION")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        DISTORTION_TYPES.forEachIndexed { index, name ->
            NeonChip(
                label = name,
                selected = clip.param(AcidParam.DIST_TYPE).toInt() == index,
                color = Magenta,
            ) { vm.setParam(clip, AcidParam.DIST_TYPE, index.toFloat()) }
        }
    }
    Spacer(Modifier.height(6.dp))
    KnobGrid(
        color = Magenta,
        specs = listOf(
            KnobSpec("DRIVE", clip.param(AcidParam.DIST_DRIVE), 0f, 1f, pct(clip.param(AcidParam.DIST_DRIVE)), 0.55f) {
                vm.setParam(clip, AcidParam.DIST_DRIVE, it)
            },
            KnobSpec("TONE", clip.param(AcidParam.DIST_TONE), 0f, 1f, pct(clip.param(AcidParam.DIST_TONE)), 0.62f) {
                vm.setParam(clip, AcidParam.DIST_TONE, it)
            },
            KnobSpec("MIX", clip.param(AcidParam.DIST_MIX), 0f, 1f, pct(clip.param(AcidParam.DIST_MIX)), 0.55f) {
                vm.setParam(clip, AcidParam.DIST_MIX, it)
            },
        ),
    )
}

@Composable
private fun WaveSound(vm: MainViewModel, clip: Clip) {
    SectionTitle("WAVETABLE BANK")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WAVETABLE_BANKS.forEachIndexed { index, name ->
            NeonChip(
                label = name,
                selected = clip.param(WaveParam.BANK).toInt() == index,
                color = Cyan,
            ) { vm.setParam(clip, WaveParam.BANK, index.toFloat()) }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FILTER_MODES.forEachIndexed { index, name ->
            NeonChip(name, clip.param(WaveParam.FILTER_MODE).toInt() == index, Cyan) {
                vm.setParam(clip, WaveParam.FILTER_MODE, index.toFloat())
            }
        }
        Spacer(Modifier.width(6.dp))
        CHORD_MODES.forEachIndexed { index, name ->
            NeonChip(name, clip.param(WaveParam.CHORD).toInt() == index, Magenta) {
                vm.setParam(clip, WaveParam.CHORD, index.toFloat())
            }
        }
    }
    SectionTitle("OSCILLATOR / UNISON")
    KnobGrid(
        color = Cyan,
        specs = listOf(
            KnobSpec("POSITION", clip.param(WaveParam.POSITION), 0f, 1f, pct(clip.param(WaveParam.POSITION)), 0.3f) {
                vm.setParam(clip, WaveParam.POSITION, it)
            },
            KnobSpec("UNISON", clip.param(WaveParam.UNISON), 1f, 8f, "${clip.param(WaveParam.UNISON).toInt()}", 3f) {
                vm.setParam(clip, WaveParam.UNISON, it.coerceAtLeast(1f).toInt().toFloat())
            },
            KnobSpec("DETUNE", clip.param(WaveParam.DETUNE), 0f, 60f, "${clip.param(WaveParam.DETUNE).toInt()} ct", 16f) {
                vm.setParam(clip, WaveParam.DETUNE, it)
            },
            KnobSpec("SPREAD", clip.param(WaveParam.SPREAD), 0f, 1f, pct(clip.param(WaveParam.SPREAD)), 0.5f) {
                vm.setParam(clip, WaveParam.SPREAD, it)
            },
            KnobSpec("SUB", clip.param(WaveParam.SUB), 0f, 1f, pct(clip.param(WaveParam.SUB)), 0.2f) {
                vm.setParam(clip, WaveParam.SUB, it)
            },
            KnobSpec("NOISE", clip.param(WaveParam.NOISE), 0f, 1f, pct(clip.param(WaveParam.NOISE)), 0f) {
                vm.setParam(clip, WaveParam.NOISE, it)
            },
            KnobSpec("LEVEL", clip.param(WaveParam.LEVEL), 0f, 1.2f, pct(clip.param(WaveParam.LEVEL)), 0.8f) {
                vm.setParam(clip, WaveParam.LEVEL, it)
            },
            KnobSpec("DRIVE", clip.param(WaveParam.DRIVE), 1f, 5f, "%.1fx".format(clip.param(WaveParam.DRIVE)), 1.2f) {
                vm.setParam(clip, WaveParam.DRIVE, it)
            },
        ),
    )
    SectionTitle("ZDF FILTER / ENVELOPE")
    KnobGrid(
        color = Cyan,
        specs = listOf(
            KnobSpec("CUTOFF", clip.param(WaveParam.CUTOFF), 60f, 12000f, hz(clip.param(WaveParam.CUTOFF)), 2200f) {
                vm.setParam(clip, WaveParam.CUTOFF, it)
            },
            KnobSpec("RESONANCE", clip.param(WaveParam.RESONANCE), 0.5f, 14f, "%.1f".format(clip.param(WaveParam.RESONANCE)), 0.7f) {
                vm.setParam(clip, WaveParam.RESONANCE, it)
            },
            KnobSpec("ATTACK", clip.param(WaveParam.ATTACK), 0.001f, 4f, secs(clip.param(WaveParam.ATTACK)), 0.01f) {
                vm.setParam(clip, WaveParam.ATTACK, it)
            },
            KnobSpec("DECAY", clip.param(WaveParam.DECAY), 0.005f, 4f, secs(clip.param(WaveParam.DECAY)), 0.4f) {
                vm.setParam(clip, WaveParam.DECAY, it)
            },
            KnobSpec("SUSTAIN", clip.param(WaveParam.SUSTAIN), 0f, 1f, pct(clip.param(WaveParam.SUSTAIN)), 0.6f) {
                vm.setParam(clip, WaveParam.SUSTAIN, it)
            },
            KnobSpec("RELEASE", clip.param(WaveParam.RELEASE), 0.005f, 4f, secs(clip.param(WaveParam.RELEASE)), 0.5f) {
                vm.setParam(clip, WaveParam.RELEASE, it)
            },
        ),
    )
}

@Composable
private fun DrumSound(vm: MainViewModel, clip: Clip) {
    SectionTitle("909 KIT")
    MonoLabel(
        DRUM_VOICE_NAMES.joinToString("  "),
        color = TextDim,
        size = 8,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    KnobGrid(
        color = Magenta,
        specs = listOf(
            KnobSpec("TUNE", clip.param(DrumParam.TUNE), 0f, 1f, pct(clip.param(DrumParam.TUNE)), 0.5f) {
                vm.setParam(clip, DrumParam.TUNE, it)
            },
            KnobSpec("DECAY", clip.param(DrumParam.DECAY), 0f, 1f, pct(clip.param(DrumParam.DECAY)), 0.5f) {
                vm.setParam(clip, DrumParam.DECAY, it)
            },
            KnobSpec("SNIPPY", clip.param(DrumParam.SNAPPY), 0f, 1f, pct(clip.param(DrumParam.SNAPPY)), 0.5f) {
                vm.setParam(clip, DrumParam.SNAPPY, it)
            },
            KnobSpec("TONE", clip.param(DrumParam.TONE), 0f, 1f, pct(clip.param(DrumParam.TONE)), 0.55f) {
                vm.setParam(clip, DrumParam.TONE, it)
            },
            KnobSpec("ACCENT", clip.param(DrumParam.ACCENT), 0f, 1f, pct(clip.param(DrumParam.ACCENT)), 0.7f) {
                vm.setParam(clip, DrumParam.ACCENT, it)
            },
            KnobSpec("LEVEL", clip.param(DrumParam.LEVEL), 0f, 1.2f, pct(clip.param(DrumParam.LEVEL)), 0.9f) {
                vm.setParam(clip, DrumParam.LEVEL, it)
            },
            KnobSpec("CUTOFF", clip.param(DrumParam.CUTOFF), 400f, 18000f, hz(clip.param(DrumParam.CUTOFF)), 18000f) {
                vm.setParam(clip, DrumParam.CUTOFF, it)
            },
            KnobSpec("RESONANCE", clip.param(DrumParam.RESONANCE), 0.5f, 8f, "%.1f".format(clip.param(DrumParam.RESONANCE)), 0.6f) {
                vm.setParam(clip, DrumParam.RESONANCE, it)
            },
        ),
    )
}

@Composable
private fun SampleSound(vm: MainViewModel, clip: Clip) {
    SectionTitle("AUDIO CLIP")
    Text(
        text = clip.sampleName ?: "No audio loaded",
        color = if (clip.sampleMissing) Red else TextBright,
        fontFamily = Mono,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    MonoLabel(
        if (clip.sampleMissing) "MISSING FILE · IMPORT AGAIN" else "WAV / FLAC / MP3 · LOOPED CLIP",
        color = if (clip.sampleMissing) Red else TextDim,
        size = 8,
        modifier = Modifier.padding(top = 2.dp),
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ImportButton(vm, clip, full = false)
        NeonChip("LOOP", clip.sampleLoop, Lime) {
            vm.setSampleLoop(clip, !clip.sampleLoop)
        }
        NeonChip("CLEAR", false, Red) { vm.clearSample(clip) }
    }
    SectionTitle("PLAYBACK")
    KnobGrid(
        color = Lime,
        specs = listOf(
            KnobSpec("GAIN", clip.param(SampleParam.GAIN), 0f, 1.5f, pct(clip.param(SampleParam.GAIN)), 0.9f) {
                vm.setParam(clip, SampleParam.GAIN, it)
            },
            KnobSpec("PITCH", clip.param(SampleParam.PITCH), -24f, 24f, "${clip.param(SampleParam.PITCH).toInt()} st", 0f) {
                vm.setParam(clip, SampleParam.PITCH, it)
            },
        ),
    )
    Spacer(Modifier.height(6.dp))
    MonoLabel(
        "Launched on the global grid. Loop wraps the sample; pitch uses resampling.",
        color = TextDim,
        size = 8,
    )
}

@Composable
private fun ImportButton(vm: MainViewModel, clip: Clip, full: Boolean) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.importSample(clip.index, uri)
    }
    LedButton(
        label = if (vm.sampleBusy) "DECODING…" else "IMPORT AUDIO",
        active = false,
        color = Lime,
        enabled = !vm.sampleBusy,
        onClick = { launcher.launch(arrayOf("audio/*")) },
        modifier = if (full) Modifier.fillMaxWidth() else Modifier,
    )
}

// ---------------------------------------------------------------------------
// FX
// ---------------------------------------------------------------------------

@Composable
private fun FxTab(vm: MainViewModel, clip: Clip) {
    val info = fxById(clip.fxType)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LedButton(
            label = if (clip.fxEnabled) "FX ON" else "BYPASS",
            active = clip.fxEnabled,
            color = Magenta,
            onClick = { vm.toggleFx(clip) },
        )
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(PanelHi)
                .border(1.dp, PanelLine, RoundedCornerShape(8.dp))
                .combinedClickable { vm.showFxPicker = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoLabel(info.code, color = Magenta, size = 10)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = info.name,
                    color = TextBright,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                MonoLabel("CHANGE", color = TextDim, size = 8)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    MonoLabel(
        "REPLICANTFX · 48 SLOT SP-404MKII MFX CATALOG",
        color = TextDim,
        size = 8,
    )
    if (clip.fxType == 0 || !clip.fxEnabled) {
        Text(
            "Select an effect and enable FX to process this pad.",
            color = TextMid,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    SectionTitle("PARAMETERS", color = Magenta)
    KnobGrid(
        color = Magenta,
        specs = List(6) { index ->
            KnobSpec(
                label = info.labels[index],
                value = clip.param(FxParam.BASE + index),
                min = 0f,
                max = 1f,
                text = pct(clip.param(FxParam.BASE + index)),
                default = info.defaults[index],
            ) { vm.setParam(clip, FxParam.BASE + index, it) }
        },
    )
    Spacer(Modifier.height(6.dp))
    MonoLabel(
        "TIP  Long-press a pad to jump to its pattern. FX is saved with the clip.",
        color = TextDim,
        size = 8,
    )
}

// ---------------------------------------------------------------------------
// PATTERN
// ---------------------------------------------------------------------------

@Composable
internal fun PatternTab(vm: MainViewModel, clip: Clip) {
    if (clip.kind == ClipKind.EMPTY) {
        Text("Assign an engine to edit a clip.", color = TextMid, fontSize = 12.sp)
        return
    }
    if (clip.kind == ClipKind.SAMPLE) {
        Text(
            "Audio clips run free when launched; use the SOUND tab for loop, gain and pitch.",
            color = TextMid,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        ImportButton(vm, clip, full = true)
        return
    }
    if (clip.kind == ClipKind.WAVE) {
        WaveMidiEditor(vm, clip)
        return
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeonChip("8", clip.length == 8, Cyan) { vm.setLength(clip, 8) }
        NeonChip("16", clip.length == 16, Cyan) { vm.setLength(clip, 16) }
        Spacer(Modifier.weight(1f))
        NeonChip("RANDOM", false, Amber) { vm.randomizePattern(clip) }
        NeonChip("CLEAR", false, Red) { vm.clearPattern(clip) }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel("ROOT", color = TextDim, size = 8)
        NeonChip("-12", false, Cyan) { vm.transpose(clip, -12) }
        NeonChip("-1", false, Cyan) { vm.transpose(clip, -1) }
        MonoLabel(noteName(clip.root), color = Amber, size = 10)
        NeonChip("+1", false, Cyan) { vm.transpose(clip, 1) }
        NeonChip("+12", false, Cyan) { vm.transpose(clip, 12) }
        Spacer(Modifier.weight(1f))
        MonoLabel("LAUNCH · ${quantizeLabel(vm.project.quantize)}", color = TextDim, size = 8)
    }

    if (clip.kind == ClipKind.DRUMS) {
        SectionTitle("909 STEP SEQUENCER", color = Magenta)
        DrumGrid(vm, clip)
        Spacer(Modifier.height(6.dp))
        MonoLabel(
            "Tap a step to toggle · long-press to cycle velocity 70 / 100 / 120",
            color = TextDim,
            size = 8,
        )
    } else {
        SectionTitle("16-STEP CLIP", color = Amber)
        var selected by remember(clip.index) { mutableIntStateOf(0) }
        StepGrid(
            vm = vm,
            clip = clip,
            selected = selected,
            onSelect = { selected = it },
        )
        Spacer(Modifier.height(8.dp))
        val step = clip.steps.getOrElse(selected) { Step() }
        PanelSurface(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoLabel("STEP ${selected + 1}", color = Amber, size = 9)
                    MonoLabel(noteName(step.note), color = TextBright, size = 11)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NeonChip("-12", false, Cyan) { vm.setStepNote(clip, selected, step.note - 12) }
                        NeonChip("-1", false, Cyan) { vm.setStepNote(clip, selected, step.note - 1) }
                        NeonChip("+1", false, Cyan) { vm.setStepNote(clip, selected, step.note + 1) }
                        NeonChip("+12", false, Cyan) { vm.setStepNote(clip, selected, step.note + 12) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    NeonChip("GATE", step.gate, Lime) { vm.toggleGate(clip, selected) }
                    NeonChip("ACCENT", step.accent, Amber) { vm.toggleAccent(clip, selected) }
                    NeonChip("SLIDE", step.slide, Cyan) { vm.toggleSlide(clip, selected) }
                    Spacer(Modifier.weight(1f))
                    MonoLabel("VEL ${step.velocity}", color = TextDim, size = 8)
                }
                SliderRow(
                    label = "VELOCITY",
                    valueText = "${step.velocity}",
                    value = step.velocity.toFloat(),
                    min = 1f,
                    max = 127f,
                    color = Amber,
                ) {
                    vm.setStep(clip, selected, step.copy(velocity = it.toInt().coerceIn(1, 127)))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun StepGrid(
    vm: MainViewModel,
    clip: Clip,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (index in 0 until ClipLimits.STEPS) {
            val step = clip.steps[index]
            val isSelected = index == selected
            val accentColor = kindColor(clip.kind.ordinal)
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (step.gate) accentColor.copy(alpha = 0.32f) else PanelHi,
                    )
                    .border(
                        if (isSelected) 1.4.dp else 1.dp,
                        if (isSelected) TextBright else PanelLine,
                        RoundedCornerShape(5.dp),
                    )
                    .combinedClickable(
                        onClick = {
                            onSelect(index)
                            vm.toggleGate(clip, index)
                        },
                        onLongClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${index + 1}",
                        color = if (step.gate) TextBright else TextDim,
                        fontSize = 8.sp,
                        fontFamily = Mono,
                    )
                    if (step.gate) {
                        Text(
                            text = noteName(step.note),
                            color = accentColor,
                            fontSize = 7.sp,
                            fontFamily = Mono,
                            maxLines = 1,
                        )
                    }
                }
                if (step.accent) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(0.55f)
                            .height(2.dp)
                            .background(Amber),
                    )
                }
                if (step.slide) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.55f)
                            .height(2.dp)
                            .background(Cyan),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DrumGrid(vm: MainViewModel, clip: Clip) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (voice in 0 until ClipLimits.DRUM_VOICES) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = DRUM_VOICE_NAMES[voice],
                    color = Magenta,
                    fontSize = 8.sp,
                    fontFamily = Mono,
                    modifier = Modifier.width(22.dp),
                )
                for (step in 0 until ClipLimits.STEPS) {
                    val on = clip.drumOn(voice, step)
                    val velocity = clip.drumVelocity(voice, step)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(15.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (on) {
                                    if (velocity >= 110) Amber.copy(alpha = 0.75f)
                                    else Magenta.copy(alpha = 0.48f)
                                } else {
                                    PanelHi
                                },
                            )
                            .border(1.dp, PanelLine.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .combinedClickable(
                                onClick = { vm.toggleDrumStep(clip, voice, step) },
                                onLongClick = {
                                    if (on) {
                                        val next = when {
                                            velocity < 85 -> 100
                                            velocity < 110 -> 120
                                            else -> 70
                                        }
                                        vm.setDrumVelocity(clip, voice, step, next)
                                    } else {
                                        vm.toggleDrumStep(clip, voice, step)
                                    }
                                },
                            ),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MIX
// ---------------------------------------------------------------------------

@Composable
private fun MixTab(vm: MainViewModel, clip: Clip) {
    SectionTitle("CHANNEL")
    KnobGrid(
        color = Cyan,
        specs = listOf(
            KnobSpec("GAIN", clip.gain, 0f, 1.5f, pct(clip.gain), 0.8f) { vm.setGain(clip, it) },
            KnobSpec("PAN", clip.pan, -1f, 1f, panText(clip.pan), 0f) { vm.setPan(clip, it) },
        ),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NeonChip("MUTE", clip.mute, Red) { vm.toggleMute(clip) }
        NeonChip("SOLO", clip.solo, Amber) { vm.toggleSolo(clip) }
        Spacer(Modifier.weight(1f))
        MonoLabel(if (clip.mute) "SILENT" else "LIVE", color = if (clip.mute) Red else Lime, size = 8)
    }
    SectionTitle("CAPTURE")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LedButton(
            label = if (vm.stemRecordingPad == clip.index) "STOP STEM" else "RECORD STEM",
            active = vm.stemRecordingPad == clip.index,
            color = Red,
            onClick = {
                if (vm.stemRecordingPad == clip.index) vm.stopRecordingNow()
                else vm.startStemRecording(clip.index)
            },
        )
        LedButton(
            label = "TAKES",
            active = vm.recordings.isNotEmpty(),
            color = Amber,
            onClick = { vm.showRecordings = true },
        )
    }
    Spacer(Modifier.height(6.dp))
    if (vm.lastRecordingPath != null) {
        LedButton(
            label = "LOAD LAST TAKE INTO PAD",
            active = false,
            color = Lime,
            onClick = { vm.loadLastTakeIntoPad(clip.index) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
    }
    MonoLabel(
        "Stem capture records this pad post-FX to a 24-bit WAV in the app's Recordings folder.",
        color = TextDim,
        size = 8,
    )

    SectionTitle("MASTER")
    SliderRow(
        label = "MASTER GAIN",
        valueText = pct(vm.project.masterGain),
        value = vm.project.masterGain,
        min = 0f,
        max = 1.5f,
        color = Cyan,
    ) { vm.setMasterGain(it) }
    MeterBar(vm.masterMeter, color = Cyan, height = 4.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LedButton("STOP ALL", false, Amber, onClick = { vm.stopAll() }, modifier = Modifier.weight(1f))
        LedButton("PANIC", false, Red, onClick = { vm.stopTransport() }, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    MonoLabel(
        "BPM ${vm.project.bpm.toInt()} · SWING ${pct(vm.project.swing)} · LAUNCH ${quantizeLabel(vm.project.quantize)}",
        color = TextDim,
        size = 8,
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

data class KnobSpec(
    val label: String,
    val value: Float,
    val min: Float,
    val max: Float,
    val text: String,
    val default: Float?,
    val onChange: (Float) -> Unit,
)

@Composable
private fun KnobGrid(color: Color, specs: List<KnobSpec>, columns: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        specs.chunked(columns).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                row.forEach { spec ->
                    OutlinedKnob(spec, color)
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.width(70.dp))
                }
            }
        }
    }
}

@Composable
private fun OutlinedKnob(spec: KnobSpec, color: Color) {
    Knob(
        label = spec.label,
        value = spec.value,
        min = spec.min,
        max = spec.max,
        onChange = spec.onChange,
        color = color,
        valueText = spec.text,
        defaultValue = spec.default,
    )
}

fun hz(value: Float): String = when {
    value >= 10000f -> "%.1fk".format(value / 1000f)
    value >= 1000f -> "%.2fk".format(value / 1000f)
    else -> value.toInt().toString()
}

fun pct(value: Float): String = "${(value * 100f).toInt()}%"

fun secs(value: Float): String = if (value < 1f) {
    "${(value * 1000f).toInt()} ms"
} else {
    "%.2f s".format(value)
}

fun panText(value: Float): String = when {
    value < -0.02f -> "L${(-value * 100f).toInt()}"
    value > 0.02f -> "R${(value * 100f).toInt()}"
    else -> "C"
}

fun noteName(note: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val n = note.coerceIn(0, 127)
    return "${names[n % 12]}${n / 12 - 1}"
}
