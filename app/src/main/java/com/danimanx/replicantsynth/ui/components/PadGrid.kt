package com.danimanx.replicantsynth.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danimanx.replicantsynth.model.ClipKind
import com.danimanx.replicantsynth.model.ClipLimits
import com.danimanx.replicantsynth.ui.MainViewModel
import com.danimanx.replicantsynth.ui.PadRuntime
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

fun kindColor(kind: Int): Color = when (kind) {
    1 -> Amber
    2 -> Cyan
    3 -> Magenta
    4 -> Lime
    else -> TextDim
}

@Composable
fun PadDeck(vm: MainViewModel, modifier: Modifier = Modifier) {
    val blink by rememberInfiniteTransition(label = "apc-blink").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "blink",
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val railWidth = if (maxWidth < 520.dp) 28.dp else 38.dp
        val side = minOf(maxWidth - railWidth - 10.dp, maxHeight - 4.dp).coerceAtLeast(140.dp)
        Row(
            Modifier
                .width(side + railWidth + 6.dp)
                .height(side),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            SceneRail(vm, railWidth, Modifier.fillMaxHeight(), blink)
            PadGrid(vm, Modifier.weight(1f).fillMaxHeight(), blink)
        }
    }
}

@Composable
private fun SceneRail(
    vm: MainViewModel,
    width: Dp,
    modifier: Modifier,
    blink: Float,
) {
    Column(
        modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (row in 0 until 8) {
            var playing = false
            var queued = false
            for (col in 0 until 8) {
                val runtime = vm.padRuntime[row * 8 + col].value
                if (runtime.isPlaying) playing = true
                if (runtime.isQueued) queued = true
            }
            val color = when {
                playing -> Lime
                queued -> Amber
                else -> TextDim
            }
            val active = playing || queued
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) color.copy(alpha = 0.12f) else Panel)
                    .border(
                        1.dp,
                        if (active) color.copy(alpha = if (queued && !playing) blink else 0.7f)
                        else PanelLine.copy(alpha = 0.55f),
                        RoundedCornerShape(6.dp),
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = "SCENE ${row + 1}"
                    }
                    .combinedClickable(
                        onClick = { vm.launchScene(row) },
                        onLongClick = { vm.stopScene(row) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(if (width < 34.dp) 3.dp else 4.dp)
                            .clip(CircleShape)
                            .background(if (active) color else PanelLine),
                    )
                    Text(
                        text = "${row + 1}",
                        color = if (active) color else TextDim,
                        fontSize = if (width < 34.dp) 8.sp else 10.sp,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PadGrid(vm: MainViewModel, modifier: Modifier, blink: Float) {
    val project = vm.project
    BoxWithConstraints(modifier) {
        val compact = maxWidth / 8 < 52.dp
        Column(
            Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (row in 0 until 8) {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (col in 0 until 8) {
                        val pad = row * 8 + col
                        val runtime = vm.padRuntime[pad].value
                        val clip = project.clips[pad]
                        PadCell(
                            vm = vm,
                            pad = pad,
                            runtime = runtime,
                            clipKind = clip.kind,
                            clipName = clip.displayName,
                            fxType = clip.fxType,
                            selected = vm.selectedPad == pad,
                            compact = compact,
                            blink = blink,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PadCell(
    vm: MainViewModel,
    pad: Int,
    runtime: PadRuntime,
    clipKind: ClipKind,
    clipName: String,
    fxType: Int,
    selected: Boolean,
    compact: Boolean,
    blink: Float,
    modifier: Modifier,
) {
    val color = kindColor(runtime.kind)
    val playing = runtime.state == 2
    val queuedStart = runtime.state == 1
    val queuedStop = runtime.state == 3
    val empty = runtime.kind == 0
    val meter = runtime.meter.coerceIn(0f, 1.25f) / 1.25f

    val borderColor = when {
        selected -> TextBright
        queuedStart -> Amber.copy(alpha = blink)
        queuedStop -> Red.copy(alpha = blink)
        playing -> color
        empty -> PanelLine.copy(alpha = 0.45f)
        else -> color.copy(alpha = 0.42f)
    }
    val borderWidth = if (selected || playing) 1.6.dp else 1.dp
    val background = when {
        empty -> Brush.verticalGradient(listOf(Panel, Panel))
        playing -> Brush.verticalGradient(
            listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0.10f), Panel),
        )
        else -> Brush.verticalGradient(
            listOf(color.copy(alpha = 0.13f), Panel),
        )
    }
    val shape = RoundedCornerShape(if (compact) 5.dp else 8.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = "PAD ${(pad + 1).toString().padStart(2, '0')} " +
                    (if (empty) "EMPTY" else clipKind.short)
            }
            .combinedClickable(
                onClick = { vm.togglePad(pad) },
                onLongClick = {
                    vm.selectPad(pad, 2)
                },
                onDoubleClick = { vm.openEditor(pad) },
            )
            .padding(if (compact) 3.dp else 5.dp),
    ) {
        if (playing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(color.copy(alpha = 0.18f), Color.Transparent),
                        ),
                    ),
            )
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = (pad + 1).toString().padStart(2, '0'),
                    color = if (empty) TextDim.copy(alpha = 0.55f) else TextMid,
                    fontSize = if (compact) 7.sp else 8.sp,
                    fontFamily = Mono,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (runtime.mute) MonoLabel("M", color = Red, size = 7)
                    if (runtime.solo) MonoLabel("S", color = Amber, size = 7)
                    if (fxType != 0) {
                        Spacer(Modifier.width(2.dp))
                        Box(
                            Modifier
                                .size(if (compact) 3.dp else 4.dp)
                                .clip(CircleShape)
                                .background(Magenta),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = if (empty) "+" else clipKind.short,
                    color = if (empty) TextDim.copy(alpha = 0.5f) else
                        if (playing) TextBright else color,
                    fontSize = when {
                        compact -> 8.sp
                        empty -> 12.sp
                        else -> 11.sp
                    },
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            if (!compact) {
                Text(
                    text = if (empty) "" else clipName,
                    color = if (playing) TextBright.copy(alpha = 0.85f) else TextDim,
                    fontSize = 7.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(PanelLine.copy(alpha = 0.55f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(meter)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (playing) color else color.copy(alpha = 0.6f)),
                )
            }
        }
        // Step phase as a thin LED line on top.
        if (playing) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .height(2.dp)
                    .fillMaxWidth((runtime.phase.coerceIn(0, 15) + 1) / 16f)
                    .background(TextBright.copy(alpha = 0.55f)),
            )
        }
    }
}
