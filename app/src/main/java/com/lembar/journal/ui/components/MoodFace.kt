package com.lembar.journal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lembar.journal.domain.Mood
import com.lembar.journal.ui.theme.Lembar
import com.lembar.journal.ui.theme.MoodPalette

/**
 * Hand-drawn style mood face (pastel circle, thin ink outline), matching the
 * reference design. A null mood draws a plain empty dot.
 */
@Composable
fun MoodFace(mood: Mood?, size: Dp, modifier: Modifier = Modifier, emptyColor: Color = Lembar.colors.emptyDot) {
    Canvas(modifier.size(size)) {
        if (mood == null) {
            drawCircle(emptyColor, radius = this.size.minDimension * 0.46f)
        } else {
            drawMoodFace(mood)
        }
    }
}

fun DrawScope.drawMoodFace(mood: Mood, origin: Offset = Offset.Zero, side: Float = size.minDimension) {
    val ink = MoodPalette.faceInk
    val stroke = side * 0.045f
    fun p(x: Float, y: Float) = Offset(origin.x + x * side, origin.y + y * side)
    fun rect(l: Float, t: Float, r: Float, b: Float) = p(l, t) to Size((r - l) * side, (b - t) * side)

    val center = p(0.5f, 0.5f)
    drawCircle(MoodPalette.color(mood), radius = side * 0.46f, center = center)
    drawCircle(ink, radius = side * 0.46f, center = center, style = Stroke(stroke * 0.8f))

    val line = Stroke(stroke, cap = StrokeCap.Round)
    fun dotEyes() {
        drawCircle(ink, side * 0.042f, p(0.37f, 0.43f))
        drawCircle(ink, side * 0.042f, p(0.63f, 0.43f))
    }
    fun arc(l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float) {
        val (tl, sz) = rect(l, t, r, b)
        drawArc(ink, start, sweep, false, tl, sz, style = line)
    }

    when (mood) {
        Mood.Great -> {
            // ^ ^ eyes and an open grin
            arc(0.29f, 0.38f, 0.44f, 0.52f, 200f, 140f)
            arc(0.56f, 0.38f, 0.71f, 0.52f, 200f, 140f)
            val (tl, sz) = rect(0.32f, 0.44f, 0.68f, 0.76f)
            drawArc(ink, 0f, 180f, true, tl, sz)
        }
        Mood.Good -> {
            dotEyes()
            arc(0.34f, 0.44f, 0.66f, 0.70f, 25f, 130f)
        }
        Mood.Okay -> {
            dotEyes()
            drawLine(ink, p(0.38f, 0.63f), p(0.62f, 0.63f), stroke, StrokeCap.Round)
        }
        Mood.Down -> {
            dotEyes()
            arc(0.35f, 0.60f, 0.65f, 0.82f, 205f, 130f)
        }
        Mood.Heavy -> {
            // closed, drooping eyes, a frown and a single tear
            arc(0.29f, 0.36f, 0.44f, 0.48f, 20f, 140f)
            arc(0.56f, 0.36f, 0.71f, 0.48f, 20f, 140f)
            arc(0.36f, 0.62f, 0.64f, 0.82f, 205f, 130f)
            val tear = Path().apply {
                moveTo(p(0.33f, 0.50f).x, p(0.33f, 0.50f).y)
                quadraticTo(p(0.27f, 0.60f).x, p(0.27f, 0.60f).y, p(0.33f, 0.62f).x, p(0.33f, 0.62f).y)
                quadraticTo(p(0.39f, 0.60f).x, p(0.39f, 0.60f).y, p(0.33f, 0.50f).x, p(0.33f, 0.50f).y)
                close()
            }
            drawPath(tear, Color(0xFF8DB0E6))
            drawPath(tear, ink, style = Stroke(stroke * 0.6f))
        }
    }
}

@Composable
fun MoodFaceSmall(mood: Mood?, modifier: Modifier = Modifier) = MoodFace(mood, 22.dp, modifier)
