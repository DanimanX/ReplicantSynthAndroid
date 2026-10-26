package com.danimanx.replicantsynth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.danimanx.replicantsynth.ui.theme.Ink
import com.danimanx.replicantsynth.ui.theme.Mono
import com.danimanx.replicantsynth.ui.theme.Panel
import com.danimanx.replicantsynth.ui.theme.PanelHi
import com.danimanx.replicantsynth.ui.theme.PanelLine
import com.danimanx.replicantsynth.ui.theme.TextBright
import com.danimanx.replicantsynth.ui.theme.TextDim
import com.danimanx.replicantsynth.ui.theme.TextMid
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MonoLabel(
    text: String,
    color: Color = TextMid,
    size: Int = 10,
    weight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        color = color,
        fontSize = size.sp,
        fontFamily = Mono,
        fontWeight = weight,
        letterSpacing = 0.8.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        modifier = modifier,
    )
}

@Composable
fun NeonChip(
    label: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) color.copy(alpha = 0.20f * alpha) else Panel)
            .border(
                1.dp,
                if (selected) color.copy(alpha = 0.85f * alpha) else PanelLine,
                RoundedCornerShape(7.dp),
            )
            .combinedClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        MonoLabel(
            label,
            color = if (selected) color else TextMid,
            size = 9,
            weight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
fun LedButton(
    label: String,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) color.copy(alpha = 0.18f) else Panel)
            .border(1.dp, if (active) color else PanelLine, RoundedCornerShape(8.dp))
            .combinedClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (active) color else TextDim.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.width(6.dp))
            MonoLabel(label, color = if (active) color else TextMid, size = 9)
        }
    }
}

/**
 * Rotary control: vertical drag, 270-degree arc, double tap to return to the
 * default. `valueText` is provided by the caller so units stay musical.
 */
@Composable
fun Knob(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color,
    valueText: String,
    defaultValue: Float? = null,
    size: Dp = 58.dp,
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val span = (max - min).takeIf { it > 0f } ?: 1f
    val fraction = ((value - min) / span).coerceIn(0f, 1f)

    Column(
        modifier = modifier.width(size + 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    var start = 0f
                    var total = 0f
                    detectDragGestures(
                        onDragStart = {
                            start = current
                            total = 0f
                        },
                        onDrag = { changeEvent, drag ->
                            changeEvent.consume()
                            total += drag.y
                            val next = (start - total / 200f * span).coerceIn(min, max)
                            change(next)
                        },
                    )
                }
                .pointerInput(defaultValue) {
                    detectTapGestures(
                        onDoubleTap = { defaultValue?.let { change(it) } },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size)) {
                val stroke = 3.dp.toPx()
                val inset = stroke * 2.2f
                val arcSize = Size(this.size.width - inset, this.size.height - inset)
                val topLeft = Offset(inset / 2f, inset / 2f)
                drawArc(
                    color = PanelLine,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val radius = this.size.minDimension / 2f - inset * 0.75f
                val angle = Math.toRadians((135f + 270f * fraction).toDouble())
                val centre = Offset(this.size.width / 2f, this.size.height / 2f)
                val tip = Offset(
                    centre.x + (radius * cos(angle)).toFloat(),
                    centre.y + (radius * sin(angle)).toFloat(),
                )
                drawLine(
                    color = color,
                    start = centre,
                    end = tip,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = Ink,
                    radius = radius * 0.48f,
                    center = centre,
                )
                drawCircle(
                    color = color.copy(alpha = 0.18f),
                    radius = radius * 0.42f,
                    center = centre,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MonoLabel(valueText, color = color, size = 9, weight = FontWeight.Bold)
            }
        }
        MonoLabel(
            label,
            color = TextDim,
            size = 8,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun MeterBar(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(PanelLine.copy(alpha = 0.55f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(height))
                .background(
                    Brush.horizontalGradient(
                        listOf(color.copy(alpha = 0.6f), color),
                    ),
                ),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, color: Color = TextMid) {
    Row(
        modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        MonoLabel(text, color = color, size = 9)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(PanelLine.copy(alpha = 0.7f)),
        )
    }
}

@Composable
fun PanelSurface(
    modifier: Modifier = Modifier,
    color: Color = Panel,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .border(1.dp, PanelLine.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        content()
    }
}

@Composable
fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    color: Color,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoLabel(label, color = TextDim, size = 9)
            MonoLabel(valueText, color = color, size = 9)
        }
        Spacer(Modifier.height(3.dp))
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = PanelLine,
            ),
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
    }
}
