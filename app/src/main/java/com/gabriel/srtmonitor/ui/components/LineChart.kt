package com.gabriel.srtmonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gabriel.srtmonitor.ui.theme.Palette
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

data class ChartSeries(val name: String, val color: Color, val values: List<Float>, val fill: Boolean = false)

/** Scrolling time-series chart (newest sample at the right edge). */
@Composable
fun LineChart(
    title: String,
    series: List<ChartSeries>,
    capacity: Int,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Palette.Muted, fontSize = 10.sp)
    Column(modifier.fillMaxWidth()) {
        Text(title, color = Palette.Text, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val axisW = 40.dp.toPx()
            val plotW = size.width - axisW
            val plotH = size.height - 4.dp.toPx()
            val rawMax = series.flatMap { it.values }.maxOrNull() ?: 0f
            val yMax = niceCeil(max(rawMax * 1.15f, 1f))

            // grid + labels
            for (i in 0..4) {
                val y = plotH - plotH * i / 4f
                drawLine(Palette.Outline.copy(alpha = 0.5f), Offset(axisW, y), Offset(size.width, y), strokeWidth = 1f)
                val v = yMax * i / 4f
                val label = if (yMax >= 10f) v.toInt().toString() else String.format("%.1f", v)
                val layout = measurer.measure(label, labelStyle)
                drawText(layout, topLeft = Offset(axisW - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
            }

            val step = plotW / (capacity - 1).coerceAtLeast(1)
            series.forEach { s ->
                if (s.values.size < 2) return@forEach
                val offsetX = axisW + plotW - step * (s.values.size - 1)
                val path = Path()
                s.values.forEachIndexed { i, v ->
                    val x = offsetX + step * i
                    val y = plotH - (v / yMax).coerceIn(0f, 1f) * plotH
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (s.fill) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(offsetX + step * (s.values.size - 1), plotH)
                        lineTo(offsetX, plotH)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.35f), Color.Transparent), 0f, plotH))
                }
                drawPath(path, s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (showLegend && series.size > 1) {
            Row(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                series.forEach { s ->
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(s.color))
                        Spacer(Modifier.width(5.dp))
                        Text(s.name, color = Palette.Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun niceCeil(v: Float): Float {
    val exp = 10.0.pow(kotlin.math.floor(log10(v.toDouble())))
    val f = v / exp
    val nice = when {
        f <= 1 -> 1.0
        f <= 2 -> 2.0
        f <= 2.5 -> 2.5
        f <= 5 -> 5.0
        else -> 10.0
    }
    return ceil(nice * exp).toFloat()
}
