package com.lembar.journal.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lembar.journal.ui.components.EraserIcon
import com.lembar.journal.ui.components.IconAction
import com.lembar.journal.ui.components.PillButton
import com.lembar.journal.ui.theme.Lembar
import com.lembar.journal.ui.theme.PaperColors
import kotlin.math.max

private data class InkPoint(val x: Float, val y: Float, val width: Float)

private class InkStroke(val points: List<InkPoint>, val color: Color, val marker: Boolean)

private enum class Tool { Pen, Marker, Eraser }

private val inkColors = listOf(
    Color(0xFF2F2E2C), Color(0xFFFF7A1A), Color(0xFF4A78C2), Color(0xFF4F9A5B), Color(0xFF8C6BC8), Color(0xFFC2453D),
)
private val penSizes = listOf(2.dp, 4.dp, 7.dp)

/**
 * Full-screen drawing sheet tuned for the S Pen:
 *  - pressure changes line width,
 *  - holding the S Pen side button while drawing erases,
 *  - once the pen is detected, finger/palm touches are ignored (if "Hanya S Pen" is on),
 *  - hovering the pen shows a size preview.
 * The paper is always light so a sketch looks the same in both themes.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SketchPad(stylusOnlyDefault: Boolean, onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val redo = remember { mutableStateListOf<InkStroke>() }
    val current = remember { mutableStateListOf<InkPoint>() }
    var tool by remember { mutableStateOf(Tool.Pen) }
    var color by remember { mutableStateOf(inkColors.first()) }
    var sizeIndex by remember { mutableStateOf(1) }
    var stylusOnly by remember { mutableStateOf(stylusOnlyDefault) }
    var stylusSeen by remember { mutableStateOf(false) }
    var currentErasing by remember { mutableStateOf(false) }
    var hover by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var confirmClose by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val toolState by rememberUpdatedState(tool)
    val colorState by rememberUpdatedState(color)
    val baseWidthPx by rememberUpdatedState(with(density) { penSizes[sizeIndex].toPx() })
    val stylusOnlyState by rememberUpdatedState(stylusOnly)
    val eraseRadius = with(density) { 14.dp.toPx() }

    fun eraseAt(p: Offset) {
        val hit = strokes.filter { s -> s.points.any { (it.x - p.x) * (it.x - p.x) + (it.y - p.y) * (it.y - p.y) < eraseRadius * eraseRadius } }
        if (hit.isNotEmpty()) strokes.removeAll(hit)
    }

    fun close() {
        if (strokes.isEmpty()) onDismiss() else confirmClose = true
    }

    // Drawn as an overlay in the activity window (not a Dialog) so system-bar
    // insets apply and the toolbar never slides under the navigation bar.
    val c = Lembar.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        BackHandler { close() }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.Rounded.Close, "Tutup", { close() })
                IconAction(
                    Icons.AutoMirrored.Rounded.Undo, "Urungkan",
                    { strokes.removeLastOrNull()?.let { redo.add(it) } },
                    tint = if (strokes.isEmpty()) c.inkFaint else c.ink,
                )
                IconAction(
                    Icons.AutoMirrored.Rounded.Redo, "Ulangi",
                    { redo.removeLastOrNull()?.let { strokes.add(it) } },
                    tint = if (redo.isEmpty()) c.inkFaint else c.ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (stylusOnly) "Hanya S Pen" else "Jari & S Pen",
                    style = Lembar.type.caption.copy(color = c.ink),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, c.border, RoundedCornerShape(8.dp))
                        .clickable { stylusOnly = !stylusOnly }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
                Spacer(Modifier.width(8.dp))
                PillButton("Simpan", enabled = strokes.isNotEmpty(), onClick = {
                    if (canvasSize.width > 0) onSave(renderBitmap(strokes, canvasSize))
                })
                Spacer(Modifier.width(8.dp))
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PaperColors.paper)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        // Hover preview: the S Pen reports position before it touches the glass.
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull() ?: continue
                                hover = when {
                                    ch.type != PointerType.Stylus && ch.type != PointerType.Eraser -> null
                                    e.type == PointerEventType.Exit -> null
                                    ch.pressed -> null
                                    else -> ch.position
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val isPen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                            if (isPen) stylusSeen = true
                            if (!isPen && stylusOnlyState && stylusSeen) return@awaitEachGesture

                            val buttons = currentEvent.buttons
                            val erasing = toolState == Tool.Eraser ||
                                down.type == PointerType.Eraser ||
                                (isPen && (buttons.isPrimaryPressed || buttons.isSecondaryPressed))
                            currentErasing = erasing
                            val marker = toolState == Tool.Marker
                            val base = if (marker) baseWidthPx * 3.2f else baseWidthPx

                            fun widthFor(pressure: Float) =
                                if (!isPen || marker) base else base * (0.35f + 1.0f * pressure.coerceIn(0f, 1f))

                            redo.clear()
                            current.clear()
                            if (erasing) eraseAt(down.position) else current.add(InkPoint(down.position.x, down.position.y, widthFor(down.pressure)))
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                // Historical samples keep fast S Pen strokes smooth.
                                change.historical.forEach { h ->
                                    if (erasing) eraseAt(h.position) else current.add(InkPoint(h.position.x, h.position.y, widthFor(change.pressure)))
                                }
                                if (erasing) eraseAt(change.position) else current.add(InkPoint(change.position.x, change.position.y, widthFor(change.pressure)))
                                change.consume()
                                if (!change.pressed) break
                            }
                            if (!erasing && current.isNotEmpty()) {
                                strokes.add(InkStroke(current.toList(), if (marker) colorState.copy(alpha = 0.35f) else colorState, marker))
                            }
                            current.clear()
                            currentErasing = false
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    strokes.forEach { drawInk(it.points, it.color, it.marker) }
                    if (current.isNotEmpty()) {
                        val marker = tool == Tool.Marker
                        drawInk(current, if (marker) color.copy(alpha = 0.35f) else color, marker)
                    }
                    hover?.let { h ->
                        val r = if (tool == Tool.Eraser) eraseRadius else baseWidthPx * (if (tool == Tool.Marker) 1.6f else 0.6f)
                        drawCircle(Color(0x552F2E2C), max(r, 3f), h, style = Stroke(1.5f))
                    }
                }
                if (strokes.isEmpty() && current.isEmpty()) {
                    Text(
                        "Gambar atau tulis dengan S Pen.\nTahan tombol S Pen untuk menghapus.",
                        style = Lembar.type.bodySmall.copy(color = PaperColors.inkFaint),
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolButton(Icons.Outlined.Create, "Pena", tool == Tool.Pen) { tool = Tool.Pen }
                ToolButton(Icons.Outlined.Brush, "Stabilo", tool == Tool.Marker) { tool = Tool.Marker }
                ToolButton(EraserIcon, "Penghapus", tool == Tool.Eraser) { tool = Tool.Eraser }
                Spacer(Modifier.weight(1f))
                penSizes.forEachIndexed { i, s ->
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).clickable { sizeIndex = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(s + 4.dp).clip(CircleShape)
                                .background(if (i == sizeIndex) c.ink else c.inkFaint),
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                inkColors.forEach { ink ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ink)
                            .border(3.dp, if (ink == color && tool != Tool.Eraser) c.background else Color.Transparent, CircleShape)
                            .border(1.5.dp, if (ink == color && tool != Tool.Eraser) c.ink else Color.Transparent, CircleShape)
                            .clickable { color = ink; if (tool == Tool.Eraser) tool = Tool.Pen },
                    )
                }
            }
        }

        if (confirmClose) {
            AlertDialog(
                onDismissRequest = { confirmClose = false },
                title = { Text("Buang sketsa?") },
                text = { Text("Gambar ini belum disimpan ke jurnal.") },
                confirmButton = { TextButton(onClick = { confirmClose = false; onDismiss() }) { Text("Buang") } },
                dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Lanjut menggambar") } },
            )
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val c = Lembar.colors
    IconAction(
        icon, label, onClick,
        size = 40.dp,
        tint = if (active) c.onPrimary else c.ink,
        background = if (active) c.primary else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
    )
}

private fun DrawScope.drawInk(points: List<InkPoint>, color: Color, marker: Boolean) {
    if (points.size == 1) {
        drawCircle(color, points[0].width / 2, Offset(points[0].x, points[0].y))
        return
    }
    if (marker) {
        // One path so overlapping translucent segments don't darken at the joints.
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        drawPath(path, color, style = Stroke(points[0].width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        return
    }
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        drawLine(color, Offset(a.x, a.y), Offset(b.x, b.y), (a.width + b.width) / 2f, StrokeCap.Round)
    }
}

private fun renderBitmap(strokes: List<InkStroke>, size: IntSize): Bitmap {
    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(PaperColors.paper.toArgb())
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    val fill = android.graphics.Paint().apply { isAntiAlias = true }
    strokes.forEach { s ->
        paint.color = s.color.toArgb()
        fill.color = s.color.toArgb()
        val pts = s.points
        if (pts.size == 1) {
            canvas.drawCircle(pts[0].x, pts[0].y, pts[0].width / 2, fill)
        } else if (s.marker) {
            val path = android.graphics.Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            }
            paint.strokeWidth = pts[0].width
            canvas.drawPath(path, paint)
        } else {
            for (i in 1 until pts.size) {
                paint.strokeWidth = (pts[i - 1].width + pts[i].width) / 2f
                canvas.drawLine(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y, paint)
            }
        }
    }
    return bmp
}
