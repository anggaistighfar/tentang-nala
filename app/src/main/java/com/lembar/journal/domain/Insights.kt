package com.lembar.journal.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What the analysis needs from one entry. [text] is empty for locked entries the user hasn't unlocked. */
data class InsightEntry(
    val date: LocalDate,
    val mood: Int,
    val emotions: List<String>,
    val text: String,
    val locked: Boolean,
    val type: EntryType = EntryType.Journal,
    val title: String = "",
    val body: String = "",
)

data class CreativeStat(val type: EntryType, val count: Int, val avgScore: Int?)

enum class Concern { None, Watch, ReachOut, Urgent }

data class ThemeFinding(val theme: Lexicon.Theme, val entries: Int, val avgMood: Double, val diff: Double)

data class DistortionFinding(val distortion: Lexicon.Distortion, val count: Int, val example: String, val date: LocalDate)

data class Recommendation(
    val id: String,
    val title: String,
    val why: String,
    val steps: List<String>,
    /** Opens a guided entry with this prompt when not null. */
    val prompt: String? = null,
)

data class InsightReport(
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    val entryCount: Int,
    val daysWritten: Int,
    val moodDays: Int,
    val avgMood: Double?,
    val prevAvgMood: Double?,
    val variability: Double?,
    val dailyMood: Map<LocalDate, Double>,
    val lowStreak: Int,
    val lowDaysLast14: Int,
    val moodDaysLast14: Int,
    val emotions: List<Pair<String, Int>>,
    val positiveEmotions: Int,
    val negativeEmotions: Int,
    val themes: List<ThemeFinding>,
    val distortions: List<DistortionFinding>,
    val bestDay: DayOfWeek?,
    val worstDay: DayOfWeek?,
    val crisisDates: List<LocalDate>,
    val creative: List<CreativeStat>,
    val textAnalyzed: Int,
    val lockedSkipped: Int,
    val concern: Concern,
    val summary: List<String>,
    val recommendations: List<Recommendation>,
) {
    val enoughData: Boolean get() = moodDays >= InsightEngine.MIN_MOOD_DAYS
    val trend: Double? get() = if (avgMood != null && prevAvgMood != null) avgMood - prevAvgMood else null
}

/** Short reflection for a single entry, shown under the entry. */
data class EntryReflection(
    val validation: String,
    val distortion: DistortionFinding?,
    val suggestion: Recommendation?,
    val crisis: Boolean,
)

/**
 * Rule-based analysis grounded in common, evidence-based approaches:
 *  - mood monitoring (trend, variability, persistence of low mood over two weeks),
 *  - affect labelling (which emotions recur),
 *  - behavioural activation (which activities go with better days),
 *  - CBT thought records (spotting common thinking traps),
 *  - self-compassion, grounding and sleep-hygiene exercises.
 *
 * It describes patterns and suggests self-help; it never diagnoses.
 */
object InsightEngine {
    const val MIN_MOOD_DAYS = 3
    private const val THEME_MIN_ENTRIES = 2
    private const val THEME_MIN_DIFF = 0.4

    private val valence: Map<String, Valence> = Emotions.all.associate { it.label to it.valence }
    private val anxious = setOf("cemas", "takut", "tertekan", "bingung")
    private val shame = setOf("bersalah", "malu", "kecewa")
    private val angry = setOf("kesal", "marah")
    private val tired = setOf("lelah", "tidak semangat", "bosan")
    private val lonely = setOf("kesepian")

    fun analyze(all: List<InsightEntry>, today: LocalDate, days: Int): InsightReport {
        val from = today.minusDays(days - 1L)
        val inRange = all.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        val prevFrom = from.minusDays(days.toLong())
        val prev = all.filter { !it.date.isBefore(prevFrom) && it.date.isBefore(from) }

        val daily = dailyMood(inRange)
        val avg = daily.values.takeIf { it.isNotEmpty() }?.average()
        val prevDaily = dailyMood(prev)
        val prevAvg = prevDaily.values.takeIf { it.size >= MIN_MOOD_DAYS }?.average()
        val sd = if (daily.size >= 4) stdDev(daily.values.toList()) else null

        // Persistence uses the last 14 days regardless of the chosen range.
        val last14 = dailyMood(all.filter { !it.date.isBefore(today.minusDays(13)) && !it.date.isAfter(today) })
        val lowDays14 = last14.values.count { it <= 2.0 }
        val lowStreak = longestLowStreak(daily)

        val emotionCounts = inRange.flatMap { it.emotions }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }
        val pos = inRange.sumOf { e -> e.emotions.count { valence[it] == Valence.Positive } }
        val neg = inRange.sumOf { e -> e.emotions.count { valence[it] == Valence.Negative } }

        // Stories and poems may be fiction: their words say little about the writer's own thinking.
        val withText = inRange.filter { it.text.isNotBlank() && (it.type == EntryType.Journal || it.type == EntryType.Idea) }
        val normalized = withText.map { it to Lexicon.normalize(it.text) }
        val themes = themeFindings(normalized, avg)
        val distortions = distortionFindings(normalized)

        val byWeekday = inRange.filter { it.mood > 0 }.groupBy { it.date.dayOfWeek }
            .filterValues { it.size >= 2 }.mapValues { (_, l) -> l.map { it.mood }.average() }
        val (best, worst) = if (byWeekday.size >= 3 && (byWeekday.values.max() - byWeekday.values.min()) >= 0.8) {
            byWeekday.maxBy { it.value }.key to byWeekday.minBy { it.value }.key
        } else null to null

        // Safety looks at the last 30 days of anything readable, not only the chosen range.
        val crisisDates = all
            .filter { !it.date.isBefore(today.minusDays(29)) && it.text.isNotBlank() && Lexicon.hasCrisis(it.text) }
            .map { it.date }.distinct().sortedDescending()

        val concern = when {
            crisisDates.isNotEmpty() -> Concern.Urgent
            (last14.size >= 10 && lowDays14 >= 10) || lowStreak >= 7 -> Concern.ReachOut
            (avg != null && daily.size >= MIN_MOOD_DAYS && avg <= 2.5) ||
                (avg != null && prevAvg != null && avg - prevAvg <= -0.8) ||
                (pos + neg >= 6 && neg.toDouble() / (pos + neg) >= 0.65) -> Concern.Watch
            else -> Concern.None
        }

        val report = InsightReport(
            from = from, to = today, days = days,
            entryCount = inRange.size,
            daysWritten = inRange.map { it.date }.distinct().size,
            moodDays = daily.size,
            avgMood = avg, prevAvgMood = prevAvg, variability = sd,
            dailyMood = daily,
            lowStreak = lowStreak, lowDaysLast14 = lowDays14, moodDaysLast14 = last14.size,
            emotions = emotionCounts,
            positiveEmotions = pos, negativeEmotions = neg,
            themes = themes, distortions = distortions,
            bestDay = best, worstDay = worst,
            crisisDates = crisisDates,
            creative = inRange.filter { it.type.creative }.groupBy { it.type }.map { (t, l) ->
                val scores = l.filter { it.body.isNotBlank() }.mapNotNull { CreativeReviewer.review(t, it.title, it.body)?.score }
                CreativeStat(t, l.size, scores.takeIf { it.isNotEmpty() }?.average()?.roundToInt())
            }.sortedBy { it.type.ordinal },
            textAnalyzed = withText.size,
            lockedSkipped = inRange.count { it.locked && it.text.isBlank() },
            concern = concern,
            summary = emptyList(),
            recommendations = emptyList(),
        )
        return report.copy(summary = summarize(report), recommendations = recommend(report))
    }

    // ---- Single entry --------------------------------------------------------------

    fun reflect(entry: InsightEntry): EntryReflection {
        val norm = Lexicon.normalize(entry.text)
        val crisis = entry.text.isNotBlank() && Lexicon.hasCrisis(entry.text)
        val distortion = if (entry.text.isBlank()) null else Lexicon.distortions.firstNotNullOfOrNull { d ->
            Lexicon.firstMatch(norm, d.phrases)?.let { (p, i) -> DistortionFinding(d, 1, Lexicon.snippet(norm, i, p), entry.date) }
        }
        val emo = entry.emotions.toSet()
        val named = entry.emotions.take(2).joinToString(" dan ")
        val validation = when {
            crisis -> "Terima kasih sudah menuliskan ini. Yang kamu rasakan terdengar sangat berat, dan kamu tidak harus menanggungnya sendirian."
            entry.mood in 1..2 && named.isNotEmpty() -> "Merasa $named itu manusiawi. Kamu sudah melakukan hal yang membantu: memberi nama pada perasaanmu."
            entry.mood in 1..2 -> "Hari yang berat. Menuliskannya sudah merupakan bentuk merawat diri."
            entry.mood == 3 && emo.any { it in tired } -> "Hari yang datar dan melelahkan juga layak dicatat. Energi yang rendah sering jadi tanda butuh jeda."
            entry.mood == 3 -> "Hari yang biasa saja. Hari seperti ini justru membantu kamu melihat polamu dengan lebih jernih."
            entry.mood >= 4 && named.isNotEmpty() -> "Senang membaca ada rasa $named hari ini. Coba ingat apa yang membuatnya terjadi, supaya bisa diulang."
            entry.mood >= 4 -> "Hari yang baik. Menyimpan momen seperti ini membantu otak lebih mudah mengingat hal positif."
            else -> "Terima kasih sudah menulis. Setiap catatan membuat polamu sedikit lebih terlihat."
        }
        val suggestion = when {
            crisis -> null
            distortion != null -> rec("reframe")
            emo.any { it in anxious } -> rec("grounding")
            emo.any { it in shame } -> rec("compassion")
            emo.any { it in angry } -> rec("anger")
            emo.any { it in lonely } -> rec("connect")
            entry.mood in 1..2 -> rec("activation")
            entry.mood >= 4 -> rec("savor")
            else -> null
        }
        return EntryReflection(validation, distortion, suggestion, crisis)
    }

    // ---- Helpers -------------------------------------------------------------------

    private fun dailyMood(list: List<InsightEntry>): Map<LocalDate, Double> =
        list.filter { it.mood > 0 }.groupBy { it.date }.mapValues { (_, l) -> l.map { it.mood }.average() }.toSortedMap()

    private fun stdDev(v: List<Double>): Double {
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
    }

    private fun longestLowStreak(daily: Map<LocalDate, Double>): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for ((d, m) in daily) {
            run = if (m <= 2.0) (if (prev != null && d == prev.plusDays(1) && run > 0) run + 1 else 1) else 0
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    private fun themeFindings(entries: List<Pair<InsightEntry, String>>, overall: Double?): List<ThemeFinding> {
        if (overall == null) return emptyList()
        return Lexicon.themes.mapNotNull { theme ->
            val hits = entries.filter { (e, n) -> e.mood > 0 && Lexicon.containsAny(n, theme.words) }
            if (hits.size < THEME_MIN_ENTRIES) return@mapNotNull null
            val avg = hits.map { it.first.mood }.average()
            ThemeFinding(theme, hits.size, avg, avg - overall)
        }.sortedByDescending { abs(it.diff) * it.entries }
    }

    private fun distortionFindings(entries: List<Pair<InsightEntry, String>>): List<DistortionFinding> =
        Lexicon.distortions.mapNotNull { d ->
            val hits = entries.mapNotNull { (e, n) -> Lexicon.firstMatch(n, d.phrases)?.let { Triple(e, n, it) } }
            if (hits.isEmpty()) return@mapNotNull null
            val (e, n, m) = hits.maxBy { it.first.date }
            DistortionFinding(d, hits.size, Lexicon.snippet(n, m.second, m.first), e.date)
        }.sortedByDescending { it.count }

    fun moodWord(avg: Double): String = when {
        avg < 1.75 -> "berat"
        avg < 2.5 -> "cenderung sedih"
        avg < 3.25 -> "biasa"
        avg < 4.25 -> "baik"
        else -> "sangat baik"
    }

    private fun summarize(r: InsightReport): List<String> {
        if (!r.enoughData) return emptyList()
        val out = mutableListOf<String>()
        val avg = r.avgMood!!
        out += "Dalam ${r.days} hari terakhir kamu menulis di ${r.daysWritten} hari, dengan mood rata-rata ${"%.1f".format(avg)} dari 5 (${moodWord(avg)})."
        r.trend?.let { t ->
            out += when {
                t >= 0.5 -> "Mood-mu naik dibanding ${r.days} hari sebelumnya (+${"%.1f".format(t)}). Ada yang sedang berjalan baik."
                t <= -0.5 -> "Mood-mu turun dibanding ${r.days} hari sebelumnya (${"%.1f".format(t)}). Perlu sedikit perhatian lebih untuk dirimu."
                else -> "Mood-mu relatif stabil dibanding ${r.days} hari sebelumnya."
            }
        }
        r.variability?.let { if (it >= 1.2) out += "Mood-mu cukup naik-turun dari hari ke hari. Mencatat pemicunya bisa membantu memahami apa yang memengaruhimu." }
        if (r.emotions.isNotEmpty()) {
            val top = r.emotions.take(3).joinToString(", ") { it.first }
            out += "Emosi yang paling sering kamu tulis: $top."
        }
        val lift = r.themes.filter { it.diff >= THEME_MIN_DIFF }.maxByOrNull { it.diff }
        val drag = r.themes.filter { it.diff <= -THEME_MIN_DIFF }.minByOrNull { it.diff }
        if (lift != null) out += "Saat kamu menulis tentang ${lift.theme.label}, mood-mu cenderung lebih baik (rata-rata ${"%.1f".format(lift.avgMood)})."
        if (drag != null) out += "Saat kamu menulis tentang ${drag.theme.label}, mood-mu cenderung lebih rendah (rata-rata ${"%.1f".format(drag.avgMood)})."
        if (r.bestDay != null && r.worstDay != null) {
            out += "Hari ${dayName(r.bestDay)} cenderung paling cerah, sedangkan hari ${dayName(r.worstDay)} paling berat."
        }
        if (r.distortions.isNotEmpty()) {
            out += "Ada beberapa pola pikir yang bisa membuat perasaan terasa lebih berat, misalnya “${r.distortions.first().distortion.name.lowercase()}”."
        }
        return out
    }

    private fun dayName(d: DayOfWeek) = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")[d.value - 1]

    private fun recommend(r: InsightReport): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        fun add(id: String, why: String? = null) {
            if (out.none { it.id == id }) out += rec(id).let { if (why != null) it.copy(why = why) else it }
        }
        val counts = r.emotions.toMap()
        fun freq(set: Set<String>) = set.sumOf { counts[it] ?: 0 }

        if (r.concern == Concern.ReachOut || r.concern == Concern.Urgent) add("professional")
        if (!r.enoughData) {
            add("keepgoing")
            return out
        }
        if (r.distortions.isNotEmpty()) add("reframe")
        if (freq(anxious) >= 2) add("grounding")
        if ((r.avgMood ?: 3.0) <= 2.8 || r.lowStreak >= 3) {
            val act = r.themes.filter { it.theme.kind == Lexicon.Kind.Activity && it.diff > 0 }.maxByOrNull { it.diff }
            add(
                "activation",
                if (act != null) "Di catatanmu, hari-hari dengan ${act.theme.label} punya mood rata-rata ${"%.1f".format(act.avgMood)}, lebih tinggi dari biasanya. Lakukan lagi, walau sebentar."
                else null,
            )
        }
        if (freq(shame) >= 2 || r.distortions.any { it.distortion.id == "label" }) add("compassion")
        val sleep = r.themes.firstOrNull { it.theme.id == "sleep" }
        if (freq(tired) >= 3 || (sleep != null && sleep.diff < 0)) add("sleep")
        if (freq(lonely) >= 2) add("connect")
        if (freq(angry) >= 3) add("anger")
        if (freq(anxious) >= 4) add("worry")
        if ((r.avgMood ?: 0.0) >= 3.5) add("savor")
        if (r.daysWritten * 2 < r.days.coerceAtMost(14)) add("keepgoing")
        if (out.isEmpty()) add("savor")
        return out.take(5)
    }

    private val catalogue: Map<String, Recommendation> = listOf(
        Recommendation(
            "professional", "Bicara dengan profesional",
            "Mood rendah yang bertahan sekitar dua minggu atau lebih adalah tanda yang baik untuk dibicarakan dengan psikolog. Ini bukan tanda lemah; justru langkah yang berani.",
            listOf(
                "Banyak Puskesmas punya layanan psikolog; tanyakan di Puskesmas terdekat atau ke dokter umum.",
                "Untuk bicara sekarang: Healing119 dari Kemenkes, telepon 119 lalu pilih ekstensi 8, atau chat di healing119.id (gratis).",
                "Bawa “Ringkasan untuk psikolog” dari halaman ini supaya lebih mudah bercerita.",
            ),
        ),
        Recommendation(
            "reframe", "Uji pikiranmu (catatan pikiran)",
            "Pikiran saat sedih atau cemas sering terdengar seperti fakta, padahal belum tentu. Menuliskannya ulang membantu melihat sisi lain.",
            listOf(
                "Tulis pikirannya persis seperti yang muncul.",
                "Tulis bukti yang mendukung dan bukti yang tidak mendukung.",
                "Tulis versi yang lebih seimbang, lalu nilai lagi perasaanmu.",
            ),
            prompt = "Pikiran yang mengganggu hari ini: … Bukti yang mendukung: … Bukti yang tidak mendukung: … Cara pandang yang lebih seimbang: …",
        ),
        Recommendation(
            "grounding", "Tenangkan badan dulu (grounding)",
            "Cemas dan tegang sering muncul di tubuh lebih dulu. Menenangkan napas memberi sinyal aman ke sistem saraf.",
            listOf(
                "Napas kotak: tarik 4 hitungan, tahan 4, buang 4, tahan 4. Ulangi 4 kali.",
                "5-4-3-2-1: sebutkan 5 hal yang kamu lihat, 4 yang bisa disentuh, 3 yang terdengar, 2 yang tercium, 1 yang terasa di lidah.",
                "Setelah lebih tenang, tulis apa yang tadi membuatmu cemas.",
            ),
            prompt = "Yang membuatku cemas sekarang adalah … Bagian yang bisa aku kendalikan: … Bagian yang di luar kendaliku: …",
        ),
        Recommendation(
            "activation", "Lakukan satu hal kecil yang berarti",
            "Saat mood turun, kita cenderung berhenti melakukan hal yang biasanya membantu, dan mood makin turun. Bergerak duluan, perasaan menyusul (behavioral activation).",
            listOf(
                "Pilih satu aktivitas 10–20 menit yang menyenangkan atau bermakna (jalan sebentar, mandi air hangat, menelepon teman).",
                "Jadwalkan jamnya hari ini atau besok.",
                "Catat mood sebelum dan sesudah, lalu bandingkan.",
            ),
            prompt = "Satu hal kecil yang akan aku lakukan besok: … Jamnya: … Mood sebelum (1–5): … Mood sesudah (1–5): …",
        ),
        Recommendation(
            "compassion", "Bicara ke diri sendiri seperti ke sahabat",
            "Rasa bersalah dan malu tumbuh dari kritik diri yang keras. Welas asih pada diri (self-compassion) terbukti menurunkan stres tanpa membuat kita malas.",
            listOf(
                "Akui: “Ini momen yang berat.”",
                "Ingat: “Semua orang pernah salah dan merasa begini.”",
                "Tulis kalimat yang akan kamu katakan ke sahabat yang mengalami hal sama, lalu tujukan ke dirimu.",
            ),
            prompt = "Kalau sahabatku mengalami apa yang aku alami hari ini, aku akan bilang ke dia: …",
        ),
        Recommendation(
            "sleep", "Rawat tidur dan energimu",
            "Lelah dan kurang tidur membuat emosi lebih mudah goyah. Perbaikan kecil pada kebiasaan tidur sering berdampak besar pada mood.",
            listOf(
                "Usahakan jam bangun yang sama setiap hari, termasuk akhir pekan.",
                "Jauhkan HP 30 menit sebelum tidur; kafein paling lambat siang hari.",
                "Kalau 20 menit belum bisa tidur, bangun sebentar dan lakukan hal tenang, lalu coba lagi.",
            ),
        ),
        Recommendation(
            "connect", "Hubungi satu orang",
            "Rasa kesepian berkurang bukan karena banyaknya teman, tapi karena satu koneksi yang terasa dekat.",
            listOf(
                "Pilih satu orang yang membuatmu nyaman.",
                "Kirim pesan sederhana: “Lagi kepikiran kamu, apa kabar?”",
                "Kalau sempat, ajak bertemu atau telepon sebentar minggu ini.",
            ),
            prompt = "Orang yang ingin aku hubungi minggu ini: … Karena: …",
        ),
        Recommendation(
            "anger", "Beri jeda pada marah",
            "Marah memberi tahu ada batas yang terlewati. Jeda sebentar membantu kamu merespons, bukan bereaksi.",
            listOf(
                "STOP: berhenti, tarik napas, amati apa yang terjadi di badan dan pikiran, lalu pilih langkah berikutnya.",
                "Salurkan energinya: jalan cepat atau olahraga ringan 10 menit.",
                "Tulis batas apa yang terlewati dan apa yang kamu butuhkan.",
            ),
            prompt = "Aku marah karena … Batas yang terlewati: … Yang sebenarnya aku butuhkan: …",
        ),
        Recommendation(
            "worry", "Sediakan “waktu khawatir”",
            "Kekhawatiran yang muncul sepanjang hari bisa ditunda ke satu waktu khusus, sehingga tidak mengganggu aktivitas lain.",
            listOf(
                "Tentukan 15 menit di sore hari sebagai waktu khawatir.",
                "Saat khawatir muncul di luar jam itu, catat singkat lalu tunda.",
                "Di waktu khawatir, tulis semuanya dan pilah: mana yang bisa ditindaklanjuti, mana yang perlu dilepas.",
            ),
            prompt = "Daftar kekhawatiranku hari ini: … Yang bisa aku tindak lanjuti: … Yang perlu aku lepaskan: …",
        ),
        Recommendation(
            "savor", "Simpan hal baiknya (tiga hal baik)",
            "Otak lebih mudah mengingat yang buruk. Menuliskan tiga hal baik setiap malam selama seminggu terbukti meningkatkan rasa bahagia.",
            listOf(
                "Tulis tiga hal baik yang terjadi hari ini, sekecil apa pun.",
                "Untuk masing-masing, tulis kenapa itu terjadi dan apa peranmu.",
            ),
            prompt = "Tiga hal baik hari ini: 1. … 2. … 3. … Kenapa hal-hal itu terjadi: …",
        ),
        Recommendation(
            "keepgoing", "Tulis sedikit, tapi rutin",
            "Analisis jadi lebih akurat kalau ada lebih banyak hari yang tercatat. Satu kalimat dan satu mood setiap hari sudah cukup.",
            listOf(
                "Pakai pengingat harian di jam yang sama.",
                "Kalau sedang malas, cukup pilih mood dan satu emosi.",
            ),
        ),
    ).associateBy { it.id }

    fun rec(id: String): Recommendation = catalogue.getValue(id)

    /** Plain-text summary a user can choose to share with their psychologist. No journal text is included. */
    fun clinicianSummary(r: InsightReport, name: String): String = buildString {
        val f = { d: LocalDate -> "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year) }
        appendLine("RINGKASAN JURNAL MOOD – Tentang Nala")
        if (name.isNotBlank()) appendLine("Nama: $name")
        appendLine("Periode: ${f(r.from)} – ${f(r.to)} (${r.days} hari)")
        appendLine("Dibuat otomatis dari catatan pribadi; bukan hasil asesmen klinis.")
        appendLine()
        appendLine("Hari menulis: ${r.daysWritten} dari ${r.days}; catatan: ${r.entryCount}")
        r.avgMood?.let { appendLine("Mood rata-rata: ${"%.1f".format(it)} / 5 (1 = berat, 5 = senang)") }
        r.prevAvgMood?.let { appendLine("Periode sebelumnya: ${"%.1f".format(it)} / 5") }
        r.variability?.let { appendLine("Variasi harian (SD): ${"%.2f".format(it)}") }
        appendLine("Mood ≤ 2 dalam 14 hari terakhir: ${r.lowDaysLast14} dari ${r.moodDaysLast14} hari tercatat")
        appendLine("Rentetan hari mood ≤ 2 terpanjang: ${r.lowStreak} hari")
        appendLine()
        appendLine("Mood harian:")
        r.dailyMood.forEach { (d, m) -> appendLine("  ${f(d)}  ${"%.1f".format(m)}") }
        if (r.emotions.isNotEmpty()) {
            appendLine()
            appendLine("Emosi yang dilaporkan (frekuensi):")
            appendLine("  " + r.emotions.joinToString(", ") { "${it.first} (${it.second})" })
        }
        if (r.themes.isNotEmpty()) {
            appendLine()
            appendLine("Topik dan rata-rata mood saat topik itu ditulis:")
            r.themes.forEach { appendLine("  ${it.theme.label}: ${"%.1f".format(it.avgMood)} (${it.entries} catatan)") }
        }
        if (r.distortions.isNotEmpty()) {
            appendLine()
            appendLine("Pola pikir yang terdeteksi dari kata kunci:")
            r.distortions.forEach { appendLine("  ${it.distortion.name}: ${it.count}×") }
        }
        if (r.crisisDates.isNotEmpty()) {
            appendLine()
            appendLine("Catatan keselamatan: ada tulisan berisi pikiran tentang kematian/menyakiti diri pada ${r.crisisDates.joinToString(", ") { f(it) }}.")
        }
        if (r.lockedSkipped > 0) {
            appendLine()
            appendLine("${r.lockedSkipped} catatan terkunci tidak dianalisis isinya.")
        }
    }.trimEnd()

    fun round1(v: Double) = (v * 10).roundToInt() / 10.0
}
