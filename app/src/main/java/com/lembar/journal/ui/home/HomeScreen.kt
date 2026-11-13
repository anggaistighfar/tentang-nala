package com.lembar.journal.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lembar.journal.data.JournalEntry
import com.lembar.journal.domain.Progress
import com.lembar.journal.domain.Reflections
import com.lembar.journal.ui.Fmt
import com.lembar.journal.ui.components.EntryCard
import com.lembar.journal.ui.components.PrimaryButton
import com.lembar.journal.ui.components.ReflectionCard
import com.lembar.journal.ui.components.SectionHeader
import com.lembar.journal.ui.components.SurfaceCard
import com.lembar.journal.ui.components.WeekStrip
import com.lembar.journal.ui.components.XpCard
import com.lembar.journal.ui.insight.InsightEntryCard
import com.lembar.journal.ui.theme.Lembar
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(
    name: String,
    today: LocalDate,
    entries: List<JournalEntry>,
    progress: Progress,
    contentPadding: PaddingValues,
    onCheckIn: (LocalDate) -> Unit,
    onOpen: (Long) -> Unit,
    onReflection: (String) -> Unit,
    onProgress: () -> Unit,
    onInsight: () -> Unit,
) {
    val c = Lembar.colors
    val t = Lembar.type
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedDay).let { if (it.isAfter(today)) today else it }
    val dayEntries = remember(entries, selected) { entries.filter { it.date == selected } }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hari ini, ${today.dayOfMonth} ${Fmt.month(today.monthValue)}", style = t.bodySmall, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(c.surfaceAlt).clickable(onClick = onProgress),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(name.trim().firstOrNull()?.uppercase() ?: "L", style = t.label)
                    }
                }
                Spacer(Modifier.height(14.dp))
                val first = name.trim().substringBefore(' ').ifEmpty { null }
                Text(
                    if (first != null) "Hai $first,\napa yang kamu pikirkan?" else "${Fmt.greeting(LocalTime.now().hour)},\napa yang kamu pikirkan?",
                    style = t.display,
                )
                Spacer(Modifier.height(20.dp))
            }
        }
        item {
            WeekStrip(today, selected, entries, onSelect = { selectedDay = it.toEpochDay() })
            Spacer(Modifier.height(20.dp))
        }

        if (dayEntries.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (selected == today) "Bagaimana perasaanmu?" else "Belum ada catatan untuk ${Fmt.relativeDay(selected, today).lowercase()}",
                        style = t.headline,
                        textAlign = TextAlign.Center,
                    )
                    if (selected != today) {
                        Spacer(Modifier.height(6.dp))
                        Text("Kamu tetap bisa menulisnya sekarang. XP hanya dihitung untuk hari ini dan kemarin.", style = t.bodySmall, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("check in", { onCheckIn(selected) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else {
            item {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
                    SectionHeader(Fmt.relativeDay(selected, today), "${dayEntries.size} catatan", Modifier.weight(1f))
                    Text(
                        "+ tambah",
                        style = t.label.copy(color = c.inkMuted),
                        modifier = Modifier.clip(CircleShape).clickable { onCheckIn(selected) }.padding(8.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            items(dayEntries, key = { it.id }) { e ->
                EntryCard(e, onClick = { onOpen(e.id) }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
            }
            item { Spacer(Modifier.height(18.dp)) }
        }

        item {
            XpCard(progress.level, progress.currentStreak, onProgress, Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))
            InsightEntryCard(onInsight, Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(28.dp))
        }

        item {
            SectionHeader("Untukmu", "Pancingan menulis untuk hari-hari tertentu", Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Reflections.all) { r -> ReflectionCard(r, onClick = { onReflection(r.prompt) }) }
            }
            Spacer(Modifier.height(24.dp))
        }

        val recent = entries.filter { it.date != selected }.take(3)
        if (recent.isNotEmpty()) {
            item {
                SectionHeader("Sebelumnya", null, Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(10.dp))
            }
            items(recent, key = { "r${it.id}" }) { e ->
                EntryCard(e, onClick = { onOpen(e.id) }, showDate = true, modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
            }
        } else if (entries.isEmpty()) {
            item {
                SurfaceCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                    Column {
                        Text("Lembar pertamamu", style = t.title)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Cukup satu kalimat atau satu foto. Semua tersimpan di HP ini saja, tidak dikirim ke mana pun.",
                            style = t.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
