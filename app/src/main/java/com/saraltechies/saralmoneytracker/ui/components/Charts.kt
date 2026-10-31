package com.saraltechies.saralmoneytracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Slice(val color: Color, val fraction: Float)

/** Donut chart with a label in the middle. */
@Composable
fun DonutChart(
    slices: List<Slice>,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    description: String = "",
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600))
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.14f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            val gap = if (slices.size > 1) 1.5f else 0f
            for (s in slices) {
                val sweep = 360f * s.fraction * progress.value
                if (sweep > gap) drawArc(s.color, start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = size * 0.18f)) {
            Text(centerTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(centerValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/**
 * Bar chart for a trend. [labels] are shown under a few bars only (first, middle, last) so they never crowd;
 * the bar at [highlight] is drawn in full colour, the rest lighter.
 */
@Composable
fun BarChart(
    values: List<Long>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    highlight: Int = -1,
    maxLabel: String = "",
    description: String = "",
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(500))
    }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Column(modifier.semantics { contentDescription = description }) {
        if (maxLabel.isNotEmpty()) {
            Text(maxLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 4.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = (v.toFloat() / max) * size.height * progress.value
                val x = i * slot + (slot - barWidth) / 2
                val c = if (highlight < 0 || i == highlight) color else color.copy(alpha = 0.45f)
                drawRoundRect(c, Offset(x, size.height - h), Size(barWidth, h), CornerRadius(barWidth / 4, barWidth / 4))
            }
        }
        if (labels.isNotEmpty()) {
            // First, middle and last only, so labels never crowd on a 31-day month.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, labels.size / 2, labels.size - 1).distinct().forEach { i ->
                    Text(labels[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
