package com.lembar.journal.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.lembar.journal.data.JournalEntry
import com.lembar.journal.domain.Mood
import com.lembar.journal.ui.Fmt
import com.lembar.journal.ui.components.EmptyState
import com.lembar.journal.ui.components.EntryCard
import com.lembar.journal.ui.components.MoodFace
import com.lembar.journal.ui.theme.Lembar
import java.time.YearMonth

@Composable
fun ArchiveScreen(entries: List<JournalEntry>, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val c = Lembar.colors
    val t = Lembar.type
    var query by rememberSaveable { mutableStateOf("") }
    var moodFilter by rememberSaveable { mutableStateOf(0) }

    val filtered = remember(entries, query, moodFilter) {
        val q = query.trim().lowercase()
        entries.filter { e ->
            (moodFilter == 0 || e.mood?.value == moodFilter) &&
                (q.isEmpty() || (!e.locked && (e.title.lowercase().contains(q) || e.body.lowercase().contains(q) || e.emotions.any { it.contains(q) })))
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { YearMonth.from(it.date) } }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Text("Arsip", style = t.display)
                Spacer(Modifier.height(4.dp))
                Text("${entries.size} catatan tersimpan di HP ini", style = t.bodySmall)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Cari tulisan atau emosi", style = t.body.copy(color = c.inkFaint))
                        BasicTextField(
                            query, { query = it },
                            singleLine = true,
                            textStyle = t.body,
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(Icons.Rounded.Close, "Hapus", tint = c.inkMuted, modifier = Modifier.size(20.dp).clickable { query = "" })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Filter mood", style = t.caption)
                    Mood.entries.forEach { m ->
                        val active = moodFilter == m.value
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, if (active) c.ink else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                                .clickable { moodFilter = if (active) 0 else m.value },
                            contentAlignment = Alignment.Center,
                        ) {
                            MoodFace(m, 30.dp, Modifier.alpha(if (moodFilter == 0 || active) 1f else 0.4f))
                        }
                    }
                }
                if (query.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Jurnal terkunci tidak ikut dicari.", style = t.caption)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    if (entries.isEmpty()) "Arsip masih kosong" else "Tidak ada yang cocok",
                    if (entries.isEmpty()) "Catatan pertamamu akan muncul di sini." else "Coba kata lain atau hapus filter mood.",
                )
            }
        }

        grouped.forEach { (month, list) ->
            item(key = "h$month") {
                Text(
                    Fmt.monthYear(month),
                    style = t.headline.copy(fontSize = t.title.fontSize),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                )
            }
            items(list, key = { it.id }) { e ->
                Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                    Column(Modifier.width(44.dp).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${e.date.dayOfMonth}", style = t.title)
                        Text(Fmt.dayShort(e.date.dayOfWeek), style = t.caption)
                    }
                    Spacer(Modifier.width(8.dp))
                    EntryCard(e, onClick = { onOpen(e.id) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
