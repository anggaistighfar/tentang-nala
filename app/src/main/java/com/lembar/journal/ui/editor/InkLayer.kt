package com.lembar.journal.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Create
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lembar.journal.domain.InkPoint
import com.lembar.journal.domain.InkStroke
import com.lembar.journal.ui.components.EraserIcon
import com.lembar.journal.ui.components.IconAction
import com.lembar.journal.ui.theme.Lembar
import kotlin.math.max

enum class PenTool { Pen, Marker, Eraser }

data class PenSettings(val tool: PenTool = PenTool.Pen, val color: Int = PenColors.first(), val size: Int = 1)

val PenColors: List<Int> = listOf(
    0xFF2F2E2C, 0xFFFF7A1A, 0xFF4A78C2, 0xFF4F9A5B, 0xFF8C6BC8, 0xFFC2453D,
).map { it.toInt() }

/** Pen widths as a fraction of page width (≈1.5, 3 and 5 dp on a 390 dp page). */
private val penWidths = listOf(0.004f, 0.0075f, 0.013f)

/** Ink on dark theme would be invisible if the user picked the default near-black. */
fun inkColor(argb: Int, dark: Boolean): Color {
    val c = Color(argb)
    return if (dark && argb == PenColors.first()) Color(0xFFEDEDEA) else c
}

fun DrawScope.drawStrokes(strokes: List<InkStroke>, width: Float, dark: Boolean) {
    strokes.forEach { drawInkStroke(it.points, inkColor(it.color, dark), it.marker, width) }
}

private fun DrawScope.drawInkStroke(points: List<InkPoint>, color: Color, marker: Boolean, scale: Float) {
    if (points.isEmpty()) return
    val c = if (marker) color.copy(alpha = 0.35f) else color
    if (points.size == 1) {
        drawCircle(c, points[0].w * scale / 2, Offset(points[0].x * scale, points[0].y * scale))
        return
    }
    if (marker) {
        val path = Path().apply {
            moveTo(points[0].x * scale, points[0].y * scale)
            for (i in 1 until points.size) lineTo(points[i].x * scale, points[i].y * scale)
        }
        drawPath(path, c, style = Stroke(points[0].w * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        return
    }
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        drawLine(c, Offset(a.x * scale, a.y * scale), Offset(b.x * scale, b.y * scale), (a.w + b.w) / 2f * scale, StrokeCap.Round)
    }
}

/**
 * Transparent layer over the page that turns S Pen strokes into ink.
 * - S Pen draws; a finger is left alone so the page still scrolls (unless [fingerDraws]).
 * - Holding the S Pen side button, or using the eraser tool, erases whole strokes.
 * - Pressure changes the line width; hovering shows where the pen will land.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun InkCanvas(
    strokes: List<InkStroke>,
    editable: Boolean,
    pen: PenSettings,
    fingerDraws: Boolean,
    onStroke: (InkStroke) -> Unit,
    onErase: (List<InkStroke>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = Lembar.colors.isDark
    val current = remember { mutableStateListOf<InkPoint>() }
    var hover by remember { mutableStateOf<Offset?>(null) }
    val penState by rememberUpdatedState(pen)
    val strokesState by rememberUpdatedState(strokes)
    val fingerState by rememberUpdatedState(fingerDraws)

    val input = if (!editable) Modifier else Modifier
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    val ch = e.changes.firstOrNull() ?: continue
                    val pen = ch.type == PointerType.Stylus || ch.type == PointerType.Eraser
                    hover = if (!pen || ch.pressed || e.type == PointerEventType.Exit) null else ch.position
                }
            }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val isPen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                // Finger on the page: leave it unconsumed so the page scrolls.
                if (!isPen && !fingerState) return@awaitEachGesture

                val w = size.width.toFloat().coerceAtLeast(1f)
                val p = penState
                val buttons = currentEvent.buttons
                val erasing = p.tool == PenTool.Eraser || down.type == PointerType.Eraser ||
                    (isPen && (buttons.isPrimaryPressed || buttons.isSecondaryPressed))
                val marker = p.tool == PenTool.Marker
                val base = penWidths[p.size] * (if (marker) 3.2f else 1f)
                val eraseR = 0.035f
                fun width(pressure: Float) = if (!isPen || marker) base else base * (0.35f + pressure.coerceIn(0f, 1f))
                val erased = mutableListOf<InkStroke>()
                fun eraseAt(o: Offset) {
                    val x = o.x / w
                    val y = o.y / w
                    strokesState.filter { s -> s !in erased && s.points.any { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) < eraseR * eraseR } }
                        .forEach { erased += it }
                }
                fun add(o: Offset, pressure: Float) {
                    if (erasing) eraseAt(o) else current.add(InkPoint(o.x / w, o.y / w, width(pressure)))
                }

                current.clear()
                add(down.position, down.pressure)
                down.consume()
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    ch.historical.forEach { add(it.position, ch.pressure) }
                    add(ch.position, ch.pressure)
                    ch.consume()
                    if (!ch.pressed) break
                }
                if (erasing) {
                    if (erased.isNotEmpty()) onErase(erased)
                } else if (current.isNotEmpty()) {
                    onStroke(InkStroke(current.toList(), p.color, marker))
                }
                current.clear()
            }
        }

    Canvas(modifier.then(input)) {
        val w = size.width
        drawStrokes(strokes, w, dark)
        if (current.isNotEmpty()) {
            val stroke = InkStroke(current.toList(), penState.color, penState.tool == PenTool.Marker)
            drawStrokes(listOf(stroke), w, dark)
        }
        hover?.let { h ->
            val r = if (penState.tool == PenTool.Eraser) 0.035f * w else penWidths[penState.size] * w * 0.6f
            drawCircle(Color(0x882F2E2C), max(r, 3f), h, style = Stroke(1.5f))
        }
    }
}

/** Pen toolbar shown while "Mode pena" is on. */
@Composable
fun PenBar(
    pen: PenSettings,
    onChange: (PenSettings) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Lembar.colors
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PenToolButton(Icons.Outlined.Create, "Pena", pen.tool == PenTool.Pen) { onChange(pen.copy(tool = PenTool.Pen)) }
        PenToolButton(Icons.Outlined.Brush, "Stabilo", pen.tool == PenTool.Marker) { onChange(pen.copy(tool = PenTool.Marker)) }
        PenToolButton(EraserIcon, "Penghapus", pen.tool == PenTool.Eraser) { onChange(pen.copy(tool = PenTool.Eraser)) }
        Spacer(Modifier.width(6.dp))
        PenColors.forEach { argb ->
            val active = argb == pen.color && pen.tool != PenTool.Eraser
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(inkColor(argb, c.isDark))
                    .border(2.dp, if (active) c.ink else Color.Transparent, CircleShape)
                    .clickable { onChange(pen.copy(color = argb, tool = if (pen.tool == PenTool.Eraser) PenTool.Pen else pen.tool)) },
            )
        }
        Spacer(Modifier.width(6.dp))
        listOf(6.dp, 9.dp, 13.dp).forEachIndexed { i, s ->
            Box(Modifier.size(34.dp).clip(CircleShape).clickable { onChange(pen.copy(size = i)) }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(s).clip(CircleShape).background(if (i == pen.size) c.ink else c.inkFaint))
            }
        }
        Spacer(Modifier.width(6.dp))
        IconAction(Icons.AutoMirrored.Rounded.Undo, "Urungkan", onUndo, tint = if (canUndo) c.ink else c.inkFaint, size = 40.dp)
        IconAction(Icons.AutoMirrored.Rounded.Redo, "Ulangi", onRedo, tint = if (canRedo) c.ink else c.inkFaint, size = 40.dp)
    }
}

@Composable
private fun PenToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val c = Lembar.colors
    IconAction(
        icon, label, onClick,
        size = 40.dp,
        tint = if (active) c.onPrimary else c.ink,
        background = if (active) c.primary else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
    )
}

@Suppress("unused")
private fun Color.argb() = toArgb()
