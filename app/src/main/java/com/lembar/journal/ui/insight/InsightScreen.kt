package com.lembar.journal.ui.insight

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lembar.journal.domain.Concern
import com.lembar.journal.domain.InsightEngine
import com.lembar.journal.domain.InsightReport
import com.lembar.journal.domain.Mood
import com.lembar.journal.domain.Recommendation
import com.lembar.journal.ui.Fmt
import com.lembar.journal.ui.LocalAuthenticator
import com.lembar.journal.ui.components.MoodFace
import com.lembar.journal.ui.components.PrimaryButton
import com.lembar.journal.ui.components.Segmented
import com.lembar.journal.ui.components.SecondaryButton
import com.lembar.journal.ui.components.SurfaceCard
import com.lembar.journal.ui.components.Tag
import com.lembar.journal.ui.components.TopBar
import com.lembar.journal.ui.icon
import com.lembar.journal.ui.lembarViewModel
import com.lembar.journal.ui.theme.Lembar
import com.lembar.journal.ui.theme.MoodPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightScreen(onBack: () -> Unit, onPrompt: (String) -> Unit) {
    val vm = lembarViewModel { c, _ -> InsightViewModel(c) }
    val report by vm.report.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val includesLocked by vm.includesLocked.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val auth = LocalAuthenticator.current
    val context = LocalContext.current
    val c = Lembar.colors
    val t = Lembar.type

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        TopBar("Analisis Nala", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text("Apa kata jurnalmu", style = t.display)
            Spacer(Modifier.height(6.dp))
            Text(
                "Dibaca dengan pendekatan psikologi (CBT, aktivasi perilaku, welas asih diri). Semua dihitung di HP ini.",
                style = t.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Segmented(vm.periods.map { "$it hari" }, vm.periods.indexOf(period), { vm.setPeriod(vm.periods[it]) })
            Spacer(Modifier.height(18.dp))

            val r = report
            if (r == null) {
                Spacer(Modifier.height(40.dp))
                return@Column
            }

            if (r.concern == Concern.Urgent) {
                SupportCard()
                Spacer(Modifier.height(18.dp))
            }

            if (!r.enoughData) {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text("Belum cukup data", style = t.title)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Analisis butuh mood dari minimal ${InsightEngine.MIN_MOOD_DAYS} hari dalam ${r.days} hari terakhir. Sekarang baru ${r.moodDays} hari.",
                            style = t.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Recommendations(r.recommendations, onPrompt)
                Disclaimer()
                return@Column
            }

            Overview(r)
            Spacer(Modifier.height(14.dp))
            MoodLine(r)

            if (r.concern == Concern.ReachOut) {
                Spacer(Modifier.height(14.dp))
                Callout(
                    "Perhatikan dirimu",
                    "Mood-mu rendah di ${r.lowDaysLast14} dari ${r.moodDaysLast14} hari tercatat dalam dua minggu terakhir" +
                        (if (r.lowStreak >= 3) ", dengan ${r.lowStreak} hari berturut-turut." else ".") +
                        " Ini bukan diagnosis, tapi pola seperti ini layak dibicarakan dengan psikolog.",
                )
            } else if (r.concern == Concern.Watch) {
                Spacer(Modifier.height(14.dp))
                Callout("Sedikit lebih lelah dari biasanya", "Beberapa tanda menunjukkan periode ini lebih berat. Pelan-pelan, dan pilih satu saran di bawah yang paling mungkin kamu lakukan.")
            }

            Spacer(Modifier.height(24.dp))
            Section("Hasil analisis")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    r.summary.forEach { line ->
                        Row {
                            Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(c.accent))
                            Spacer(Modifier.width(10.dp))
                            Text(line, style = t.body)
                        }
                    }
                }
            }

            if (r.emotions.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Section("Emosimu", "${r.positiveEmotions} positif · ${r.negativeEmotions} negatif")
                EmotionBalance(r.positiveEmotions, r.negativeEmotions)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.emotions.take(10).forEach { (label, n) -> Tag("$label · $n") }
                }
            }

            if (r.themes.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Section("Apa yang memengaruhi mood-mu", "Rata-rata mood saat topik ini muncul di tulisanmu")
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        r.themes.take(6).forEach { th ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MoodFace(Mood.of(th.avgMood.roundToInt()), 26.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(th.theme.label.replaceFirstChar { it.uppercase() }, style = t.label)
                                    Text("${th.entries} catatan · mood ${"%.1f".format(th.avgMood)}", style = t.caption)
                                }
                                val d = th.diff
                                Text(
                                    (if (d >= 0) "+" else "") + "%.1f".format(d),
                                    style = t.label.copy(color = if (d >= 0.4) Color(0xFF4F9A5B) else if (d <= -0.4) c.danger else c.inkMuted),
                                )
                            }
                        }
                    }
                }
            }

            if (r.distortions.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Section("Pola pikir yang perlu dicermati", "Terdeteksi dari kata-kata di tulisanmu")
                r.distortions.forEach { d ->
                    SurfaceCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(d.distortion.name, style = t.title, modifier = Modifier.weight(1f))
                                Text("${d.count}×", style = t.caption)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(d.distortion.explain, style = t.bodySmall)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "“${d.example}”  ·  ${Fmt.short(d.date)}",
                                style = t.bodySmall.copy(color = c.ink),
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.surfaceAlt).padding(10.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Coba tanyakan: ${d.distortion.reframe}", style = t.body)
                        }
                    }
                }
            }

            if (r.creative.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Section("Karya kreatifmu", "Ide, cerita, pantun, dan puisi dalam ${r.days} hari terakhir")
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        r.creative.forEach { st ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
                                    Icon(st.type.icon(), null, tint = c.ink, modifier = Modifier.size(19.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text("${st.count} ${st.type.noun}", style = t.label, modifier = Modifier.weight(1f))
                                if (st.avgScore != null) Text("rata-rata nilai ${st.avgScore}", style = t.caption)
                            }
                        }
                        Text(
                            "Menulis kreatif adalah cara sehat menyalurkan perasaan. Buka tiap karya untuk melihat penilaian lengkapnya.",
                            style = t.caption,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Recommendations(r.recommendations, onPrompt)

            Spacer(Modifier.height(24.dp))
            Section("Untuk psikolog atau konselor")
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "Kalau kamu sedang atau ingin konsultasi, bagikan ringkasan angka mood, emosi, dan pola ini. Isi tulisan jurnalmu tidak ikut dibagikan.",
                        style = t.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Bagikan ringkasan", onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Ringkasan jurnal mood")
                            .putExtra(Intent.EXTRA_TEXT, InsightEngine.clinicianSummary(r, name))
                        context.startActivity(Intent.createChooser(send, "Bagikan ringkasan"))
                    }, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Share)
                }
            }

            if (r.lockedSkipped > 0 && !includesLocked) {
                Spacer(Modifier.height(14.dp))
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, null, tint = c.ink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${r.lockedSkipped} jurnal terkunci belum dibaca", style = t.label)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Mood dan emosinya sudah dihitung. Buka kunci untuk ikut menganalisis isi tulisannya (hanya selama halaman ini terbuka).", style = t.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton("Sertakan jurnal terkunci", onClick = {
                            auth.authenticate("Sertakan jurnal terkunci", "Untuk analisis di HP ini", onSuccess = { vm.includeLocked() })
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Disclaimer()
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null) {
    Text(title, style = Lembar.type.headline.copy(fontSize = Lembar.type.title.fontSize * 1.2f))
    if (subtitle != null) Text(subtitle, style = Lembar.type.bodySmall)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Overview(r: InsightReport) {
    val c = Lembar.colors
    val t = Lembar.type
    val avg = r.avgMood ?: return
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoodFace(Mood.of(avg.roundToInt()), 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Mood rata-rata", style = t.caption)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.1f".format(avg), style = t.display)
                    Text(" / 5", style = t.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(InsightEngine.moodWord(avg).replaceFirstChar { it.uppercase() }, style = t.label)
            }
            r.trend?.let { tr ->
                val (icon, color, label) = when {
                    tr >= 0.3 -> Triple(Icons.AutoMirrored.Outlined.TrendingUp, Color(0xFF4F9A5B), "+%.1f".format(tr))
                    tr <= -0.3 -> Triple(Icons.AutoMirrored.Outlined.TrendingDown, c.danger, "%.1f".format(tr))
                    else -> Triple(Icons.AutoMirrored.Outlined.TrendingFlat, c.inkMuted, "stabil")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
                    Text(label, style = t.label.copy(color = color))
                    Text("vs ${r.days} hari lalu", style = t.caption)
                }
            }
        }
    }
}

/** Daily mood as a line with mood-coloured dots. */
@Composable
private fun MoodLine(r: InsightReport) {
    val c = Lembar.colors
    if (r.dailyMood.size < 2) return
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column {
            Text("Mood per hari", style = Lembar.type.caption)
            Spacer(Modifier.height(10.dp))
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val total = r.days.toFloat()
                val pad = 8.dp.toPx()
                fun x(d: java.time.LocalDate) = pad + (size.width - 2 * pad) * (java.time.temporal.ChronoUnit.DAYS.between(r.from, d) / (total - 1).coerceAtLeast(1f))
                fun y(m: Double) = pad + (size.height - 2 * pad) * (1f - ((m - 1) / 4f).toFloat())
                for (level in 1..5) {
                    drawLine(c.border, Offset(0f, y(level.toDouble())), Offset(size.width, y(level.toDouble())), 1f)
                }
                val pts = r.dailyMood.entries.map { Offset(x(it.key), y(it.value)) to it.value }
                for (i in 1 until pts.size) drawLine(c.inkFaint, pts[i - 1].first, pts[i].first, 2.dp.toPx(), StrokeCap.Round)
                pts.forEach { (p, m) ->
                    val mood = Mood.of(m.roundToInt()) ?: Mood.Okay
                    drawCircle(MoodPalette.color(mood), 5.dp.toPx(), p)
                    drawCircle(MoodPalette.faceInk, 5.dp.toPx(), p, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(Fmt.short(r.from), style = Lembar.type.caption, modifier = Modifier.weight(1f))
                Text(Fmt.short(r.to), style = Lembar.type.caption)
            }
        }
    }
}

@Composable
private fun EmotionBalance(pos: Int, neg: Int) {
    val total = (pos + neg).coerceAtLeast(1)
    val c = Lembar.colors
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surfaceAlt)) {
        if (pos > 0) Box(Modifier.weight(pos.toFloat() / total).fillMaxSize().background(MoodPalette.color(Mood.Okay)))
        if (neg > 0) Box(Modifier.weight(neg.toFloat() / total).fillMaxSize().background(MoodPalette.color(Mood.Heavy)))
    }
}

@Composable
private fun Callout(title: String, body: String) {
    val c = Lembar.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceAlt)
            .padding(16.dp),
    ) {
        Icon(Icons.Outlined.Psychology, null, tint = c.ink, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = Lembar.type.label)
            Spacer(Modifier.height(4.dp))
            Text(body, style = Lembar.type.bodySmall.copy(color = c.ink))
        }
    }
}

@Composable
private fun Recommendations(list: List<Recommendation>, onPrompt: (String) -> Unit) {
    if (list.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Section("Yang bisa kamu lakukan", "Pilih satu yang paling mungkin dilakukan minggu ini")
    list.forEachIndexed { i, rec -> RecommendationCard(rec, i == 0, onPrompt) }
}

@Composable
fun RecommendationCard(rec: Recommendation, startOpen: Boolean, onPrompt: (String) -> Unit) {
    val c = Lembar.colors
    val t = Lembar.type
    var open by rememberSaveable(rec.id) { mutableStateOf(startOpen) }
    SurfaceCard(Modifier.fillMaxWidth().padding(bottom = 10.dp).animateContentSize(), onClick = { open = !open }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rec.title, style = t.title, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = c.inkMuted)
            }
            Spacer(Modifier.height(4.dp))
            Text(rec.why, style = t.bodySmall, maxLines = if (open) Int.MAX_VALUE else 2)
            if (open) {
                Spacer(Modifier.height(12.dp))
                rec.steps.forEachIndexed { i, s ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = t.caption.copy(color = c.ink, fontWeight = FontWeight.Bold))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(s, style = t.body)
                    }
                }
                if (rec.prompt != null) {
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Coba tulis sekarang", { onPrompt(rec.prompt) }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Disclaimer() {
    Spacer(Modifier.height(22.dp))
    Text(
        "Analisis ini otomatis, berdasarkan mood, emosi, dan kata-kata di jurnalmu. Ini bukan diagnosis dan bukan pengganti psikolog. Kalau perasaanmu terasa berat atau berlangsung lama, bicaralah dengan tenaga profesional.",
        style = Lembar.type.caption,
    )
    Spacer(Modifier.height(32.dp))
}

/** Entry point card for the home and stats screens. */
@Composable
fun InsightEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Lembar.colors
    SurfaceCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Psychology, null, tint = c.accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Analisis Nala", style = Lembar.type.label)
                Text("Pola mood, pemicu, dan saran yang bisa kamu lakukan", style = Lembar.type.caption)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkMuted)
        }
    }
}
