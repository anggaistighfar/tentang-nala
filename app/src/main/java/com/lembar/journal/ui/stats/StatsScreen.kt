package com.lembar.journal.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lembar.journal.data.JournalEntry
import com.lembar.journal.domain.Mood
import com.lembar.journal.domain.Progress
import com.lembar.journal.ui.Fmt
import com.lembar.journal.ui.components.IconAction
import com.lembar.journal.ui.components.MoodFace
import com.lembar.journal.ui.components.Segmented
import com.lembar.journal.ui.components.SurfaceCard
import com.lembar.journal.ui.components.Tag
import com.lembar.journal.ui.components.XpCard
import com.lembar.journal.ui.components.drawMoodFace
import com.lembar.journal.ui.components.moodOf
import com.lembar.journal.ui.theme.Lembar
import com.lembar.journal.ui.theme.MoodPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class Range(val label: String) { Week("Minggu"), Month("Bulan"), Year("Tahun") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(entries: List<JournalEntry>, progress: Progress, today: LocalDate, contentPadding: PaddingValues) {
    val c = Lembar.colors
    val t = Lembar.type
    var range by rememberSaveable { mutableIntStateOf(Range.Month.ordinal) }
    var weekStart by rememberSaveable { mutableStateOf(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var year by rememberSaveable { mutableIntStateOf(today.year) }

    val ym = YearMonth.parse(month)
    val (from, to) = when (Range.entries[range]) {
        Range.Week -> LocalDate.ofEpochDay(weekStart).let { it to it.plusDays(6) }
        Range.Month -> ym.atDay(1) to ym.atEndOfMonth()
        Range.Year -> LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
    }
    val inRange = remember(entries, from, to) { entries.filter { !it.date.isBefore(from) && !it.date.isAfter(to) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("Mood", style = t.display)
            Spacer(Modifier.height(16.dp))
            Segmented(Range.entries.map { it.label }, range, { range = it })
            Spacer(Modifier.height(14.dp))
        }

        when (Range.entries[range]) {
            Range.Week -> Stepper(
                "${LocalDate.ofEpochDay(weekStart).dayOfMonth} ${Fmt.monthShort(LocalDate.ofEpochDay(weekStart).monthValue)} – ${to.dayOfMonth} ${Fmt.monthShort(to.monthValue)}",
                onPrev = { weekStart -= 7 },
                onNext = { weekStart += 7 },
                canNext = LocalDate.ofEpochDay(weekStart).plusDays(7) <= today,
            )
            Range.Month -> MonthChips(ym, YearMonth.from(today)) { month = it.toString() }
            Range.Year -> Stepper("$year", { year-- }, { year++ }, canNext = year < today.year)
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(22.dp))
            Text("Rata-rata Mood", style = t.headline.copy(fontSize = t.title.fontSize * 1.2f))
            Text(
                when (Range.entries[range]) {
                    Range.Year -> "Satu titik untuk setiap hari"
                    else -> "Mood rata-rata per hari"
                },
                style = t.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            SurfaceCard(Modifier.fillMaxWidth()) {
                when (Range.entries[range]) {
                    Range.Week -> WeekFaces(entries, LocalDate.ofEpochDay(weekStart), today)
                    Range.Month -> MonthCalendar(entries, ym, today)
                    Range.Year -> YearPixels(entries, year, today)
                }
            }

            Spacer(Modifier.height(26.dp))
            Text("Distribusi Mood", style = t.headline.copy(fontSize = t.title.fontSize * 1.2f))
            Text("Mood yang paling sering kamu rasakan", style = t.bodySmall)
            Spacer(Modifier.height(12.dp))
            SurfaceCard(Modifier.fillMaxWidth()) { Distribution(inRange) }

            val emotions = remember(inRange) {
                inRange.flatMap { it.emotions }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8)
            }
            if (emotions.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                Text("Emosi yang sering muncul", style = t.headline.copy(fontSize = t.title.fontSize * 1.2f))
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    emotions.forEach { (label, n) -> Tag("$label · $n") }
                }
            }

            Insight(inRange)

            Spacer(Modifier.height(26.dp))
            Text("Perjalananmu", style = t.headline.copy(fontSize = t.title.fontSize * 1.2f))
            Spacer(Modifier.height(12.dp))
            XpCard(progress.level, progress.currentStreak, onClick = {})
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("${entries.size}", "catatan", Modifier.weight(1f))
                Stat("${progress.journaledDays}", "hari menulis", Modifier.weight(1f))
                Stat("${progress.longestStreak}", "rekor beruntun", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Total ${progress.level.totalXp} XP", style = t.caption)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    SurfaceCard(modifier) {
        Column {
            Text(value, style = Lembar.type.headline)
            Text(label, style = Lembar.type.caption)
        }
    }
}

@Composable
private fun Stepper(label: String, onPrev: () -> Unit, onNext: () -> Unit, canNext: Boolean) {
    val c = Lembar.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Sebelumnya", onPrev)
        Text(label, style = Lembar.type.label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconAction(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Berikutnya", { if (canNext) onNext() }, tint = if (canNext) c.ink else c.inkFaint)
    }
}

@Composable
private fun MonthChips(selected: YearMonth, current: YearMonth, onSelect: (YearMonth) -> Unit) {
    val c = Lembar.colors
    val months = remember(current) { (23 downTo 0).map { current.minusMonths(it.toLong()) } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (months.indexOf(selected) - 2).coerceAtLeast(0))
    LaunchedEffect(Unit) { listState.scrollToItem((months.indexOf(selected) - 2).coerceAtLeast(0)) }
    LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(months) { i, m ->
            val active = m == selected
            val showYear = i == 0 || m.monthValue == 1
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) c.ink else Color.Transparent)
                    .border(1.dp, if (active) c.ink else c.border, RoundedCornerShape(8.dp))
                    .clickable { onSelect(m) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    if (showYear) "${Fmt.month(m.monthValue)} ${m.year}" else Fmt.month(m.monthValue),
                    style = Lembar.type.label,
                    color = if (active) c.background else c.ink,
                )
            }
        }
    }
}

@Composable
private fun DayHeader() {
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach {
            Text(
                Fmt.dayShort(it), style = Lembar.type.caption, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WeekFaces(entries: List<JournalEntry>, monday: LocalDate, today: LocalDate) {
    Column {
        DayHeader()
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val d = monday.plusDays(i.toLong())
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    MoodFace(entries.moodOf(d), 34.dp, emptyColor = Lembar.colors.emptyDot.copy(alpha = if (d.isAfter(today)) 0.4f else 1f))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val d = monday.plusDays(i.toLong())
                Text(
                    "${d.dayOfMonth}", style = Lembar.type.caption, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MonthCalendar(entries: List<JournalEntry>, ym: YearMonth, today: LocalDate) {
    val lead = ym.atDay(1).dayOfWeek.value - 1
    val days = ym.lengthOfMonth()
    val rows = (lead + days + 6) / 7
    Column {
        DayHeader()
        Spacer(Modifier.height(8.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                for (col in 0 until 7) {
                    val dayNum = r * 7 + col - lead + 1
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..days) {
                            val d = ym.atDay(dayNum)
                            MoodFace(entries.moodOf(d), 32.dp, emptyColor = Lembar.colors.emptyDot.copy(alpha = if (d.isAfter(today)) 0.4f else 1f))
                        }
                    }
                }
            }
        }
    }
}

/** "Year in pixels": twelve mini months, one coloured dot per day. */
@Composable
private fun YearPixels(entries: List<JournalEntry>, year: Int, today: LocalDate) {
    val c = Lembar.colors
    val moods = remember(entries, year) {
        entries.filter { it.date.year == year }.groupBy { it.date }
            .mapValues { (_, l) -> l.mapNotNull { it.mood?.value }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.let(Mood::of) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in 0 until 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (col in 0 until 3) {
                    val m = row * 3 + col + 1
                    val ym = YearMonth.of(year, m)
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.monthShort(m), style = Lembar.type.caption)
                        Spacer(Modifier.height(4.dp))
                        Canvas(Modifier.fillMaxWidth().aspectRatio(7f / 6f)) {
                            val cell = size.width / 7f
                            val lead = ym.atDay(1).dayOfWeek.value - 1
                            for (d in 1..ym.lengthOfMonth()) {
                                val idx = lead + d - 1
                                val date = ym.atDay(d)
                                val mood = moods[date]
                                val color = when {
                                    mood != null -> MoodPalette.color(mood)
                                    date.isAfter(today) -> c.emptyDot.copy(alpha = 0.35f)
                                    else -> c.emptyDot
                                }
                                drawCircle(color, cell * 0.38f, Offset(cell * (idx % 7) + cell / 2, cell * (idx / 7) + cell / 2))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Half-donut like the reference, with a small face on each segment. */
@Composable
private fun Distribution(list: List<JournalEntry>) {
    val c = Lembar.colors
    val t = Lembar.type
    val counts = remember(list) { Mood.entries.associateWith { m -> list.count { it.mood == m } } }
    val total = counts.values.sum()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (total == 0) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("Belum ada mood di rentang ini.", style = t.bodySmall)
            }
            return@Column
        }
        Canvas(Modifier.fillMaxWidth().aspectRatio(2f).padding(horizontal = 8.dp)) {
            val thickness = size.width * 0.17f
            val radius = size.width / 2 - thickness / 2
            val center = Offset(size.width / 2, size.height)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val box = Size(radius * 2, radius * 2)
            var start = 180f
            // Most frequent moods drawn from the left, as in the reference.
            Mood.entries.reversed().forEach { m ->
                val n = counts[m] ?: 0
                if (n == 0) return@forEach
                val sweep = 180f * n / total
                drawArc(MoodPalette.color(m), start, sweep, false, topLeft, box, style = Stroke(thickness))
                if (sweep > 14f) {
                    val mid = Math.toRadians((start + sweep / 2).toDouble())
                    val side = thickness * 0.62f
                    val p = Offset(center.x + radius * cos(mid).toFloat(), center.y + radius * sin(mid).toFloat())
                    drawMoodFace(m, Offset(p.x - side / 2, p.y - side / 2), side)
                }
                start += sweep
            }
        }
        Spacer(Modifier.height(16.dp))
        Mood.entries.reversed().forEach { m ->
            val n = counts[m] ?: 0
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                MoodFace(m, 20.dp)
                Spacer(Modifier.width(10.dp))
                Text(m.label, style = t.label, modifier = Modifier.weight(1f))
                Text("$n", style = t.label)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
                    Text("${(100f * n / total).roundToInt()}%", style = t.caption)
                }
            }
        }
    }
}

/** A gentle observation once there's enough data (5+ days with a mood). */
@Composable
private fun Insight(list: List<JournalEntry>) {
    val withMood = list.filter { it.mood != null }
    if (withMood.map { it.date }.distinct().size < 5) return
    val avg = withMood.map { it.mood!!.value }.average()
    val byDay = withMood.groupBy { it.date.dayOfWeek }.mapValues { (_, l) -> l.map { it.mood!!.value }.average() }
    val best = byDay.maxByOrNull { it.value }?.key
    val avgMood = Mood.of(avg.roundToInt())
    Spacer(Modifier.height(26.dp))
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoodFace(avgMood, 40.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Rata-rata: ${avgMood?.label ?: "-"}", style = Lembar.type.label)
                if (best != null && byDay.size >= 3) {
                    Text("Hari ${Fmt.day(best)} cenderung jadi harimu yang paling cerah.", style = Lembar.type.bodySmall)
                }
            }
        }
    }
}
