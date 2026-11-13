package com.lembar.journal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lembar.journal.data.Attachment
import com.lembar.journal.data.JournalEntry
import com.lembar.journal.data.MediaKind
import com.lembar.journal.domain.LevelInfo
import com.lembar.journal.domain.Mood
import com.lembar.journal.domain.Reflection
import com.lembar.journal.ui.Fmt
import com.lembar.journal.ui.theme.Lembar
import com.lembar.journal.ui.theme.MoodPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

fun JournalEntry.displayTitle(): String = when {
    locked -> "Jurnal terkunci"
    title.isNotBlank() -> title.trim()
    body.isNotBlank() -> body.trim().lineSequence().first().take(60)
    attachments.isNotEmpty() -> "Momen tanpa kata"
    else -> mood?.let { "Merasa ${it.label.lowercase()}" } ?: "Tanpa judul"
}

/** Rounded average of the moods logged that day, or null. */
fun List<JournalEntry>.moodOf(date: LocalDate): Mood? {
    val values = filter { it.date == date }.mapNotNull { it.mood?.value }
    if (values.isEmpty()) return null
    return Mood.of(values.average().roundToInt())
}

/** Journal card (design 2 list item). */
@Composable
fun EntryCard(entry: JournalEntry, onClick: () -> Unit, modifier: Modifier = Modifier, showDate: Boolean = false) {
    val c = Lembar.colors
    val t = Lembar.type
    val firstPhoto = entry.attachments.firstOrNull()
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row {
            Column(Modifier.weight(1f)) {
                if (showDate) {
                    Text(Fmt.dayMonth(entry.date), style = t.caption)
                    Spacer(Modifier.height(4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.locked) {
                        Icon(Icons.Outlined.Lock, null, tint = c.ink, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(entry.displayTitle(), style = t.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                // Without a title the body already shows as the heading, so don't repeat it.
                val preview = when {
                    entry.locked -> "Ketuk untuk membuka dengan sidik jari atau PIN."
                    entry.title.isBlank() -> ""
                    else -> entry.body.trim()
                }
                if (preview.isNotEmpty()) {
                    Text(preview, style = t.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Diperbarui ${Fmt.time(entry.updatedAt)}", style = t.caption.copy(color = c.inkFaint))
                    Spacer(Modifier.weight(1f))
                    if (entry.attachments.isNotEmpty()) {
                        Icon(Icons.Outlined.Photo, null, tint = c.inkMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${entry.attachments.size}", style = t.caption)
                        Spacer(Modifier.width(10.dp))
                    }
                    if (entry.mood != null) MoodFace(entry.mood, 20.dp)
                }
            }
            if (firstPhoto != null && !entry.locked) {
                Spacer(Modifier.width(12.dp))
                MediaImage(firstPhoto, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
            }
        }
    }
}

@Composable
fun MediaImage(attachment: Attachment, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bg = if (attachment.kind == MediaKind.Sketch) Lembar.colors.paper else Lembar.colors.surfaceAlt
    AsyncImage(
        model = attachment.file,
        contentDescription = if (attachment.kind == MediaKind.Sketch) "Sketsa" else "Foto",
        contentScale = contentScale,
        modifier = modifier.background(bg),
    )
}

/**
 * Mon–Sun week strip with mood faces (design 1). Swipe for earlier weeks.
 * Selected day gets a filled pill (design 2 orange in Night).
 */
@Composable
fun WeekStrip(
    today: LocalDate,
    selected: LocalDate,
    entries: List<JournalEntry>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = 260
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val initial = (weeks - 1 - ChronoUnit.WEEKS.between(
        selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), thisMonday,
    ).toInt()).coerceIn(0, weeks - 1)
    val pager = rememberPagerState(initialPage = initial) { weeks }
    val c = Lembar.colors
    val t = Lembar.type

    HorizontalPager(pager, modifier.fillMaxWidth()) { page ->
        val monday = thisMonday.minusWeeks((weeks - 1 - page).toLong())
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0 until 7) {
                val day = monday.plusDays(i.toLong())
                val future = day.isAfter(today)
                val isSel = day == selected
                val pillBg = when {
                    !isSel -> Color.Transparent
                    c.isDark -> c.primary
                    else -> c.surface
                }
                val labelColor = when {
                    isSel && c.isDark -> c.onPrimary
                    future -> c.inkFaint
                    else -> c.inkMuted
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(pillBg)
                        .clickable(enabled = !future) { onSelect(day) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Fmt.dayShort(day.dayOfWeek), style = t.caption.copy(color = labelColor))
                    Text(
                        "${day.dayOfMonth}",
                        style = t.label.copy(
                            color = if (isSel && c.isDark) c.onPrimary else if (future) c.inkFaint else c.ink,
                            fontWeight = if (day == today) FontWeight.Bold else FontWeight.Medium,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    val mood = remember(entries, day) { entries.moodOf(day) }
                    MoodFace(
                        mood, 30.dp,
                        emptyColor = if (isSel && c.isDark) Color.White.copy(alpha = 0.25f) else c.emptyDot.copy(alpha = if (future) 0.5f else 1f),
                    )
                }
            }
        }
    }
}

/** Level badge with ring, progress bar, and streak. */
@Composable
fun XpCard(level: LevelInfo, streak: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Lembar.colors
    val t = Lembar.type
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 4.dp.toPx()
                    drawArc(c.surfaceAlt, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                    drawArc(c.accent, -90f, 360f * level.progress, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                }
                Text("${level.level}", style = t.title.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Level ${level.level} · ${level.title}", style = t.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                ProgressBar(level.progress)
                Spacer(Modifier.height(6.dp))
                Text("${level.xpIntoLevel} / ${level.xpForNext} XP menuju level ${level.level + 1}", style = t.caption)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.LocalFireDepartment, null,
                    tint = if (streak > 0) c.accent else c.inkFaint,
                    modifier = Modifier.size(24.dp),
                )
                Text("$streak", style = t.title)
                Text("hari", style = t.caption)
            }
        }
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = Lembar.colors.accent) {
    val track = Lembar.colors.surfaceAlt
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * progress.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
    }
}

private val reflectionTones = listOf(
    Color(0xFFF9D2B4), Color(0xFFCBE7C3), Color(0xFFFBE3A0), Color(0xFFD8CBF0), Color(0xFFC5D6F3),
)

/** "Untukmu" card with a small line doodle (design 1 "For You"). */
@Composable
fun ReflectionCard(item: Reflection, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = Lembar.type
    Column(modifier.width(148.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(16.dp))
                .background(reflectionTones[item.tone % reflectionTones.size]),
            contentAlignment = Alignment.Center,
        ) {
            Doodle(item.tone, Modifier.fillMaxSize().padding(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = t.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Doodle(kind: Int, modifier: Modifier) {
    val ink = MoodPalette.faceInk
    Canvas(modifier) {
        val s = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        fun pt(x: Float, y: Float) = Offset(x * w, y * h)
        when (kind % 5) {
            0 -> { // laptop + burst
                drawRoundRect(ink, pt(0.12f, 0.22f), Size(0.58f * w, 0.42f * h), CornerRadius(6f), style = s)
                drawLine(ink, pt(0.02f, 0.72f), pt(0.80f, 0.72f), s.width, StrokeCap.Round)
                for (i in 0..2) drawLine(ink, pt(0.22f, 0.34f + i * 0.09f), pt(0.52f - i * 0.08f, 0.34f + i * 0.09f), s.width * 0.7f, StrokeCap.Round)
                val burst = Path()
                val cx = 0.80f; val cy = 0.24f
                for (i in 0 until 14) {
                    val a = Math.toRadians(i * (360.0 / 14)).toFloat()
                    val r = if (i % 2 == 0) 0.20f else 0.10f
                    val p = pt(cx + r * kotlin.math.cos(a), cy + r * kotlin.math.sin(a))
                    if (i == 0) burst.moveTo(p.x, p.y) else burst.lineTo(p.x, p.y)
                }
                burst.close()
                drawPath(burst, Color(0xFFFF7A1A), style = s)
            }
            1 -> { // heart with a plaster
                val heart = Path().apply {
                    moveTo(pt(0.5f, 0.85f).x, pt(0.5f, 0.85f).y)
                    cubicTo(pt(0.0f, 0.55f).x, pt(0.0f, 0.55f).y, pt(0.1f, 0.1f).x, pt(0.1f, 0.1f).y, pt(0.5f, 0.3f).x, pt(0.5f, 0.3f).y)
                    cubicTo(pt(0.9f, 0.1f).x, pt(0.9f, 0.1f).y, pt(1.0f, 0.55f).x, pt(1.0f, 0.55f).y, pt(0.5f, 0.85f).x, pt(0.5f, 0.85f).y)
                    close()
                }
                drawPath(heart, ink, style = s)
                drawLine(ink, pt(0.35f, 0.38f), pt(0.65f, 0.62f), s.width * 3.2f, StrokeCap.Round)
                drawLine(Color(0xFFCBE7C3), pt(0.35f, 0.38f), pt(0.65f, 0.62f), s.width * 1.8f, StrokeCap.Round)
            }
            2 -> { // three sparkles
                fun sparkle(cx: Float, cy: Float, r: Float) {
                    drawLine(ink, pt(cx, cy - r), pt(cx, cy + r), s.width, StrokeCap.Round)
                    drawLine(ink, pt(cx - r, cy), pt(cx + r, cy), s.width, StrokeCap.Round)
                }
                sparkle(0.3f, 0.35f, 0.18f)
                sparkle(0.72f, 0.28f, 0.1f)
                sparkle(0.62f, 0.7f, 0.15f)
            }
            3 -> { // envelope
                drawRoundRect(ink, pt(0.08f, 0.22f), Size(0.84f * w, 0.56f * h), CornerRadius(6f), style = s)
                val flap = Path().apply {
                    moveTo(pt(0.08f, 0.24f).x, pt(0.08f, 0.24f).y)
                    lineTo(pt(0.5f, 0.56f).x, pt(0.5f, 0.56f).y)
                    lineTo(pt(0.92f, 0.24f).x, pt(0.92f, 0.24f).y)
                }
                drawPath(flap, ink, style = s)
                drawCircle(Color(0xFFFF7A1A), 0.07f * w, pt(0.5f, 0.56f))
            }
            else -> { // moon + stars
                val moon = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(pt(0.15f, 0.15f), Size(0.6f * w, 0.6f * w)))
                }
                drawPath(moon, ink, style = s)
                drawCircle(reflectionTones[4], 0.27f * w, pt(0.58f, 0.36f))
                drawCircle(ink, 0.03f * w, pt(0.82f, 0.7f))
                drawCircle(ink, 0.02f * w, pt(0.7f, 0.86f))
            }
        }
    }
}

/** Big centered empty state with an empty mood dot, used on lists. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Lembar.colors.emptyDot))
        Spacer(Modifier.height(14.dp))
        Text(title, style = Lembar.type.title)
        Spacer(Modifier.height(4.dp))
        Text(body, style = Lembar.type.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
