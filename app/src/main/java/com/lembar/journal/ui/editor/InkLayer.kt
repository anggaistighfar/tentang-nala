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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lembar.journal.domain.InkPoint
import com.lembar.journal.domain.InkStroke
import com.lembar.journal.ui.components.EraserIcon
import com.lembar.journal.ui.components.IconAction
import com.lembar.journal.ui.theme.Lembar
import kotlin.math.hypot
import kotlin.math.max

enum class PenTool { Pen, Marker, Eraser, Hand }

data class PenSettings(val tool: PenTool = PenTool.Pen, val color: Int = PenColors.first(), val size: Int = 1)

val PenColors: List<Int> = listOf(
    0xFF2F2E2C, 0xFFFF7A1A, 0xFF4A78C2, 0xFF4F9A5B, 0xFF8C6BC8, 0xFFC2453D,
).map { it.toInt() }

/** Pen widths as a fraction of page width (≈1.5, 3 and 5 dp on a 390 dp page). */
private val penWidths = listOf(0.004f, 0.0075f, 0.013f)
private const val ERASE_RADIUS = 0.035f
/** Points closer than this (fraction of page width, ≈1.5 px on a 1440 px page) are dropped. */
private const val MIN_STEP = 0.001f

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
        // One smoothed path so overlapping translucent segments don't darken.
        val path = Path().apply {
            moveTo(points[0].x * scale, points[0].y * scale)
            for (i in 1 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                quadraticTo(a.x * scale, a.y * scale, (a.x + b.x) / 2 * scale, (a.y + b.y) / 2 * scale)
            }
            lineTo(points.last().x * scale, points.last().y * scale)
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

/** The stroke being drawn right now, kept apart so only this layer redraws while the pen moves. */
@Stable
class InkSession {
    val live = mutableStateListOf<InkPoint>()
    var liveColor by mutableStateOf(PenColors.first())
    var liveMarker by mutableStateOf(false)
    var hover by mutableStateOf<Offset?>(null)
    var hoverTool by mutableStateOf(PenTool.Pen)
    var hoverSize by mutableStateOf(1)
}

@Composable
fun rememberInkSession() = remember { InkSession() }

/**
 * Draws ink. Finished strokes sit in their own graphics layer, so adding points
 * to the live stroke doesn't replay every earlier stroke (smooth on long pages).
 */
@Composable
fun InkCanvas(strokes: List<InkStroke>, session: InkSession?, modifier: Modifier = Modifier) {
    val dark = Lembar.colors.isDark
    Box(modifier) {
        Canvas(Modifier.matchParentSize().graphicsLayer()) { drawStrokes(strokes, size.width, dark) }
        if (session != null) {
            Canvas(Modifier.matchParentSize().graphicsLayer()) {
                val w = size.width
                if (session.live.isNotEmpty()) {
                    drawInkStroke(session.live.toList(), inkColor(session.liveColor, dark), session.liveMarker, w)
                }
                session.hover?.let { h ->
                    val r = when (session.hoverTool) {
                        PenTool.Eraser -> ERASE_RADIUS * w
                        PenTool.Hand -> 0f
                        else -> penWidths[session.hoverSize] * w * 0.6f
                    }
                    if (r > 0f) drawCircle(Color(0x882F2E2C), max(r, 3f), h, style = Stroke(1.5f))
                }
            }
        }
    }
}

private fun PointerInputChange.isPen() = type == PointerType.Stylus || type == PointerType.Eraser

/**
 * Put on the page's container (an ancestor of the text field). It sees pointer
 * events in the Initial pass, before the text field and the scroll container:
 * S Pen events are taken for ink (so they never scroll the page or turn into
 * typed text), while finger events pass through untouched for typing and scrolling.
 *
 * - [capturePen] false lets the S Pen through (handwriting-to-text mode).
 * - [fingerDraws] lets a finger draw too (only while the pen toolbar is open).
 * - Tool [PenTool.Hand] or [onPan] turns S Pen drags into scrolling.
 * - Holding the S Pen side button, or its eraser end, erases whole strokes.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.inkInput(
    session: InkSession,
    capturePen: Boolean,
    fingerDraws: Boolean,
    pen: () -> PenSettings,
    strokes: () -> List<InkStroke>,
    onStart: () -> Unit,
    onStroke: (InkStroke) -> Unit,
    onErase: (List<InkStroke>) -> Unit,
    onPan: (Float) -> Unit,
): Modifier = this
    .pointerInput(capturePen) {
        // Hover preview: the S Pen reports its position before touching the glass.
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val ch = e.changes.firstOrNull() ?: continue
                session.hover = if (!capturePen || !ch.isPen() || ch.pressed || e.type == PointerEventType.Exit) null else ch.position
                val p = pen()
                session.hoverTool = p.tool
                session.hoverSize = p.size
            }
        }
    }
    .pointerInput(capturePen, fingerDraws) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val isPen = down.isPen()
            if (isPen && !capturePen) return@awaitEachGesture
            if (!isPen && !fingerDraws) return@awaitEachGesture

            val w = size.width.toFloat().coerceAtLeast(1f)
            val p = pen()
            down.consume()

            if (p.tool == PenTool.Hand) {
                var last = down.position.y
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    onPan(ch.position.y - last)
                    last = ch.position.y
                    ch.consume()
                    if (!ch.pressed) break
                }
                return@awaitEachGesture
            }

            onStart()
            val buttons = currentEvent.buttons
            val erasing = p.tool == PenTool.Eraser || down.type == PointerType.Eraser ||
                (isPen && (buttons.isPrimaryPressed || buttons.isSecondaryPressed))
            val marker = p.tool == PenTool.Marker
            val base = penWidths[p.size] * (if (marker) 3.2f else 1f)
            fun width(pressure: Float) = if (!isPen || marker) base else base * (0.35f + pressure.coerceIn(0f, 1f))

            val erased = LinkedHashSet<InkStroke>()
            fun eraseAt(o: Offset) {
                val x = o.x / w
                val y = o.y / w
                val r2 = ERASE_RADIUS * ERASE_RADIUS
                for (s in strokes()) {
                    if (s in erased) continue
                    if (s.points.any { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) < r2 }) erased += s
                }
            }
            var lastX = Float.NaN
            var lastY = Float.NaN
            fun add(o: Offset, pressure: Float, force: Boolean = false) {
                if (erasing) { eraseAt(o); return }
                val x = o.x / w
                val y = o.y / w
                if (!force && !lastX.isNaN() && hypot(x - lastX, y - lastY) < MIN_STEP) return
                lastX = x; lastY = y
                session.live.add(InkPoint(x, y, width(pressure)))
            }

            session.live.clear()
            session.liveColor = p.color
            session.liveMarker = marker
            add(down.position, down.pressure, force = true)
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                // Historical samples keep fast S Pen strokes smooth (it reports up to 240 Hz).
                ch.historical.forEach { add(it.position, ch.pressure) }
                add(ch.position, ch.pressure, force = !ch.pressed)
                ch.consume()
                if (!ch.pressed) break
            }
            if (erasing) {
                if (erased.isNotEmpty()) onErase(erased.toList())
            } else if (session.live.isNotEmpty()) {
                onStroke(InkStroke(session.live.toList(), p.color, marker))
            }
            session.live.clear()
        }
    }

/** Pen toolbar: tools, colours, sizes, undo/redo, and what the S Pen does on the page. */
@Composable
fun PenBar(
    pen: PenSettings,
    onChange: (PenSettings) -> Unit,
    penInk: Boolean,
    onPenInk: (Boolean) -> Unit,
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
        // Ink vs. handwriting-to-text.
        Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, c.border, RoundedCornerShape(10.dp))) {
            listOf(true to "Tinta", false to "Jadi teks").forEach { (v, label) ->
                val active = penInk == v
                Text(
                    label,
                    style = Lembar.type.caption.copy(color = if (active) c.onPrimary else c.ink, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .background(if (active) c.primary else Color.Transparent)
                        .clickable { onPenInk(v) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        PenToolButton(Icons.Outlined.Create, "Pena", pen.tool == PenTool.Pen) { onChange(pen.copy(tool = PenTool.Pen)) }
        PenToolButton(Icons.Outlined.Brush, "Stabilo", pen.tool == PenTool.Marker) { onChange(pen.copy(tool = PenTool.Marker)) }
        PenToolButton(EraserIcon, "Penghapus", pen.tool == PenTool.Eraser) { onChange(pen.copy(tool = PenTool.Eraser)) }
        PenToolButton(Icons.Outlined.PanTool, "Geser halaman", pen.tool == PenTool.Hand) { onChange(pen.copy(tool = PenTool.Hand)) }
        Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(c.border))
        PenColors.forEach { argb ->
            val active = argb == pen.color && (pen.tool == PenTool.Pen || pen.tool == PenTool.Marker)
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(inkColor(argb, c.isDark))
                    .border(2.dp, if (active) c.ink else Color.Transparent, CircleShape)
                    .clickable { onChange(pen.copy(color = argb, tool = if (pen.tool == PenTool.Marker) PenTool.Marker else PenTool.Pen)) },
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
