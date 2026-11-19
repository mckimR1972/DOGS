package com.teamdogs.dogs.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.teamdogs.dogs.ui.theme.DogsTheme

enum class RailState { DONE, LATE, UPCOMING }
data class RailPoint(val minutes: Int, val state: RailState)

/** "HH:mm" → minutes since midnight, or null when blank/malformed. */
fun parseMinutes(time: String): Int? {
    val parts = time.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}

/** A merged capsule is done only when every task in it is done; otherwise the open work must stay visible. */
fun mergedRailState(states: List<RailState>): RailState = when {
    states.isNotEmpty() && states.all { it == RailState.DONE } -> RailState.DONE
    states.any { it == RailState.LATE } -> RailState.LATE
    else -> RailState.UPCOMING
}

private const val START = 6 * 60
private const val END = 24 * 60

/**
 * The signature element: the day from 06 to 24h. Done tasks are filled, late ones ringed in amber,
 * upcoming ones outlined, and a vertical marker shows now. Ticks closer than the tick size merge into
 * a capsule with a count so they never overlap.
 */
@Composable
fun DayRail(points: List<RailPoint>, nowMinutes: Int, modifier: Modifier = Modifier) {
    val c = DogsTheme.colors
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = scheme.onSurface)
    val done = points.count { it.state == RailState.DONE }
    val late = points.count { it.state == RailState.LATE }
    val summary = "오늘 일정 ${points.size}개 중 ${done}개 완료" + if (late > 0) ", ${late}개 지남" else ""
    Column(modifier.fillMaxWidth().semantics { contentDescription = summary }) {
        Canvas(Modifier.fillMaxWidth().height(46.dp)) {
            val w = size.width
            val y = 28.dp.toPx()
            val tick = 18.dp.toPx()
            fun x(min: Int) = ((min.coerceIn(START, END) - START).toFloat() / (END - START)) * (w - tick) + tick / 2
            drawLine(scheme.outlineVariant, Offset(0f, y), Offset(w, y), 2.dp.toPx())
            val nowX = x(nowMinutes)
            if (nowMinutes > START) drawLine(scheme.onSurface, Offset(0f, y), Offset(nowX, y), 2.dp.toPx())
            // Group points whose ticks would overlap.
            val sorted = points.sortedBy { it.minutes }
            val groups = mutableListOf<MutableList<RailPoint>>()
            sorted.forEach { p ->
                val last = groups.lastOrNull()
                if (last != null && x(p.minutes) - x(last.last().minutes) < tick) last.add(p) else groups.add(mutableListOf(p))
            }
            groups.forEach { g ->
                val left = x(g.first().minutes) - tick / 2
                val right = x(g.last().minutes) + tick / 2
                val state = mergedRailState(g.map { it.state })
                val topLeft = Offset(left, y - tick / 2)
                val sz = Size(right - left, tick)
                val r = CornerRadius(tick / 2)
                when (state) {
                    RailState.DONE -> drawRoundRect(c.done, topLeft, sz, r)
                    RailState.LATE -> { drawRoundRect(c.attentionContainer, topLeft, sz, r)
                        drawRoundRect(c.attention, topLeft, sz, r, style = Stroke(2.dp.toPx())) }
                    RailState.UPCOMING -> { drawRoundRect(scheme.background, topLeft, sz, r)
                        drawRoundRect(c.controlOutline, topLeft, sz, r, style = Stroke(2.dp.toPx())) }
                }
                if (state == RailState.DONE && g.size == 1) {
                    val s = 3.5.dp.toPx(); val cx = left + tick / 2
                    drawLine(scheme.onPrimary, Offset(cx - s, y), Offset(cx - s / 3, y + s * .7f), 2.dp.toPx())
                    drawLine(scheme.onPrimary, Offset(cx - s / 3, y + s * .7f), Offset(cx + s, y - s * .6f), 2.dp.toPx())
                } else if (g.size > 1) {
                    val label = measurer.measure(g.size.toString(), labelStyle.copy(
                        color = if (state == RailState.DONE) scheme.onPrimary else scheme.onSurface))
                    drawText(label, topLeft = Offset((left + right) / 2 - label.size.width / 2, y - label.size.height / 2))
                }
            }
            if (nowMinutes in START..END) {
                drawLine(scheme.onSurface, Offset(nowX, y - 22.dp.toPx()), Offset(nowX, y + 16.dp.toPx()), 2.dp.toPx())
                val now = measurer.measure("지금", labelStyle)
                val lx = (nowX + 6.dp.toPx()).coerceAtMost(w - now.size.width.toFloat())
                drawText(now, topLeft = Offset(lx, 0f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("6시", "12시", "18시", "24시").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = c.textTertiary)
            }
        }
    }
}

/** Static rail used as a brand motif on login, setup and empty states. */
@Composable
fun RailMotif(modifier: Modifier = Modifier, filled: Int = 0) {
    val c = DogsTheme.colors
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier.height(40.dp)) {
        val w = size.width; val y = size.height / 2; val r = 7.dp.toPx()
        val xs = listOf(.1f, .3f, .52f, .72f, .9f).map { it * w }
        drawLine(scheme.outlineVariant, Offset(0f, y), Offset(w, y), 2.dp.toPx())
        if (filled > 0) drawLine(scheme.onSurface, Offset(0f, y), Offset(xs[(filled - 1).coerceAtMost(4)] + r * 2, y), 2.dp.toPx())
        xs.forEachIndexed { i, x ->
            when {
                i < filled -> drawCircle(c.done, r, Offset(x, y))
                i == filled && filled > 0 -> { drawCircle(c.attentionContainer, r, Offset(x, y)); drawCircle(c.attention, r, Offset(x, y), style = Stroke(2.dp.toPx())) }
                else -> { drawCircle(scheme.background, r, Offset(x, y)); drawCircle(c.controlOutline, r, Offset(x, y), style = Stroke(2.dp.toPx())) }
            }
        }
    }
}
