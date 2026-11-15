package com.lembar.journal.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** What kind of piece an entry is. Stored as [key] in the database. */
enum class EntryType(val key: String, val label: String, val noun: String, val placeholder: String) {
    Journal("journal", "Jurnal", "jurnal", ""),
    Idea("idea", "Ide", "ide", "Tuliskan idemu. Apa masalahnya, untuk siapa, dan apa langkah pertamanya?"),
    Story("story", "Cerita", "cerita", "Mulailah ceritamu. Siapa tokohnya, di mana, dan apa yang terjadi?"),
    Pantun("pantun", "Pantun", "pantun", "Baris 1 (sampiran, rima a)\nBaris 2 (sampiran, rima b)\nBaris 3 (isi, rima a)\nBaris 4 (isi, rima b)"),
    Poem("poem", "Puisi", "puisi", "Tulis puisimu. Pisahkan bait dengan baris kosong.");

    val creative: Boolean get() = this != Journal

    /** Optional outline inserted by "Pakai kerangka". Uses heading markup. */
    val outline: String?
        get() = when (this) {
            Idea -> "## Ide\n\n## Masalah yang diselesaikan\n\n## Untuk siapa\n\n## Langkah pertama\n\n## Kendala yang mungkin\n"
            Story -> "## Awal\n\n## Konflik\n\n## Akhir\n"
            else -> null
        }

    companion object {
        fun of(key: String?): EntryType = entries.firstOrNull { it.key == key } ?: Journal
    }
}

// ---- Review ---------------------------------------------------------------------------

enum class CheckStatus { Good, Warn, Info }

data class ReviewCheck(val label: String, val status: CheckStatus, val detail: String)

data class CreativeReview(
    val type: EntryType,
    /** 0–100, or null where a score would be meaningless (free verse). */
    val score: Int?,
    val verdict: String,
    val checks: List<ReviewCheck>,
    /** Overall feeling of the piece, from the emotion-word lexicon. */
    val tone: String,
    val toneWords: List<String>,
    val psych: String,
    val tips: List<String>,
)

/**
 * Rule-based reviewer for ideas, stories, pantun and poems. Every rule is
 * explainable to the writer; nothing is guessed by a model. Structure checks
 * for pantun follow the classic form: 4 lines, 8–12 syllables, rhyme a-b-a-b,
 * lines 1–2 sampiran and 3–4 isi.
 */
object CreativeReviewer {

    fun review(type: EntryType, title: String, body: String): CreativeReview? = when (type) {
        EntryType.Journal -> null
        EntryType.Idea -> idea(title, Markup.plain(body))
        EntryType.Story -> story(title, Markup.plain(body))
        EntryType.Pantun -> pantun(Markup.plain(body))
        EntryType.Poem -> poem(Markup.plain(body))
    }

    // ---- Text helpers ----------------------------------------------------------------

    private val vowels = setOf('a', 'i', 'u', 'e', 'o')
    private val wordRe = Regex("[\\p{L}]+")

    fun words(s: String): List<String> = wordRe.findAll(s.lowercase()).map { it.value }.toList()

    /** Indonesian syllables ≈ vowels, with a word-final diphthong (ai, au, oi, ei) counted once. */
    fun syllables(word: String): Int {
        val w = word.lowercase()
        var n = w.count { it in vowels }
        if (w.length >= 2 && w.takeLast(2) in setOf("ai", "au", "oi", "ei")) n -= 1
        return n.coerceAtLeast(if (w.any { it.isLetter() }) 1 else 0)
    }

    fun lineSyllables(line: String) = words(line).sumOf { syllables(it) }

    /** Ending sound of a line: the last vowel group and what follows ("jalan" → "an", "pantai" → "ai"). */
    fun rhymeKey(line: String): String {
        val w = words(line).lastOrNull() ?: return ""
        var i = w.indexOfLast { it in vowels }
        if (i < 0) return w
        while (i > 0 && w[i - 1] in vowels) i--
        return w.substring(i)
    }

    /** "a-b-a-b" style pattern for a group of lines. */
    fun rhymePattern(lines: List<String>): String {
        val seen = LinkedHashMap<String, Char>()
        return lines.joinToString("-") { l ->
            val k = rhymeKey(l)
            seen.getOrPut(k) { 'a' + seen.size }.toString()
        }
    }

    /** Lines grouped into stanzas by blank lines. */
    fun stanzas(text: String): List<List<String>> {
        val out = mutableListOf<MutableList<String>>()
        var cur = mutableListOf<String>()
        text.replace("\r", "").split('\n').forEach { raw ->
            val l = raw.trim()
            if (l.isEmpty()) {
                if (cur.isNotEmpty()) { out += cur; cur = mutableListOf() }
            } else cur += l
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    private data class Tone(val label: String, val words: List<String>, val pos: Int, val neg: Int)

    private fun tone(text: String): Tone {
        val ws = words(text)
        val joined = " " + ws.joinToString(" ") + " "
        val pos = Lexicon.positive.filter { joined.contains(" $it ") }
        val neg = Lexicon.negative.filter { joined.contains(" $it ") }
        val p = pos.sumOf { w -> Regex("(?<= )${Regex.escape(w)}(?= )").findAll(joined).count() }
        val n = neg.sumOf { w -> Regex("(?<= )${Regex.escape(w)}(?= )").findAll(joined).count() }
        val label = when {
            p + n == 0 -> "netral"
            p >= n * 2 -> "hangat dan cerah"
            n >= p * 2 -> "sendu dan berat"
            else -> "campuran: ada terang dan gelapnya"
        }
        return Tone(label, (pos + neg).take(6), p, n)
    }

    private fun verdict(score: Int, good: String, mid: String, low: String) = when {
        score >= 85 -> good
        score >= 60 -> mid
        else -> low
    }

    // ---- Pantun ------------------------------------------------------------------------

    private fun pantun(text: String): CreativeReview {
        val st = stanzas(text).let { s ->
            // No blank lines between stanzas: split every 4 lines.
            if (s.size == 1 && s[0].size > 4) s[0].chunked(4) else s
        }
        val t = tone(text)
        if (st.isEmpty()) {
            return CreativeReview(
                EntryType.Pantun, null, "Belum ada baris pantun", emptyList(), t.label, t.words,
                "Pantun membantu merangkai perasaan dalam irama. Mulai dengan sampiran tentang alam atau benda di sekitarmu.",
                listOf("Tulis 4 baris: dua baris sampiran, lalu dua baris isi."),
            )
        }
        val checks = mutableListOf<ReviewCheck>()
        val scores = mutableListOf<Int>()
        st.forEachIndexed { i, lines ->
            val tag = if (st.size > 1) "Bait ${i + 1}: " else ""
            var sc = 0
            val four = lines.size == 4
            checks += ReviewCheck(
                "${tag}Jumlah baris", if (four) CheckStatus.Good else CheckStatus.Warn,
                if (four) "4 baris, sesuai bentuk pantun." else "${lines.size} baris. Pantun biasanya 4 baris per bait.",
            )
            if (four) sc += 25
            val syl = lines.map { lineSyllables(it) }
            val okSyl = syl.count { it in 8..12 }
            sc += (25 * okSyl / syl.size.coerceAtLeast(1))
            checks += ReviewCheck(
                "${tag}Suku kata", if (okSyl == syl.size) CheckStatus.Good else CheckStatus.Warn,
                "${syl.joinToString(" · ")} suku kata per baris (perkiraan). Idealnya 8–12." +
                    if (okSyl < syl.size) " Baris ${syl.withIndex().filter { it.value !in 8..12 }.joinToString { "${it.index + 1}" }} perlu disesuaikan." else "",
            )
            if (lines.size >= 4) {
                val k = lines.take(4).map { rhymeKey(it) }
                val ac = k[0] == k[2]
                val bd = k[1] == k[3]
                val pattern = rhymePattern(lines.take(4))
                if (ac) sc += 20
                if (bd) sc += 20
                if (k[0] != k[1]) sc += 10
                checks += ReviewCheck(
                    "${tag}Rima akhir", if (ac && bd && k[0] != k[1]) CheckStatus.Good else CheckStatus.Warn,
                    "Pola $pattern (akhiran -${k.joinToString(", -")}). Pantun berima a-b-a-b." +
                        when {
                            pattern == "a-a-a-a" -> " Pola a-a-a-a lebih dekat ke bentuk syair."
                            !ac && !bd -> " Coba samakan akhiran baris 1 & 3, serta baris 2 & 4."
                            !ac -> " Akhiran baris 1 dan 3 belum sama."
                            !bd -> " Akhiran baris 2 dan 4 belum sama."
                            else -> ""
                        },
                )
            }
            scores += sc
        }
        checks += ReviewCheck(
            "Sampiran & isi", CheckStatus.Info,
            "Baris 1–2 sebaiknya sampiran (gambaran alam/benda), baris 3–4 isi (pesan). Bagian ini dinilai oleh perasaanmu sendiri.",
        )
        val score = scores.average().roundToInt()
        val tips = buildList {
            if (checks.any { it.label.endsWith("Rima akhir") && it.status == CheckStatus.Warn }) add("Cari kata berakhiran sama untuk baris 1 & 3 (misalnya -an: jalan, bulan, hujan).")
            if (checks.any { it.label.endsWith("Suku kata") && it.status == CheckStatus.Warn }) add("Baca keras-keras sambil mengetuk jari: tiap ketukan satu suku kata.")
            add("Biarkan isi pantun menyampaikan satu perasaan atau pesan yang jelas.")
        }
        return CreativeReview(
            EntryType.Pantun, score,
            verdict(score, "Pantun yang rapi", "Hampir pas, tinggal dipoles", "Bentuknya masih bisa dirapikan"),
            checks, t.label, t.words,
            "Irama dan rima membuat perasaan lebih mudah diucapkan. Suasana pantunmu terasa ${t.label}" +
                (if (t.neg > t.pos) "; menuangkan rasa berat lewat pantun adalah cara yang sehat untuk melepaskannya." else "."),
            tips,
        )
    }

    // ---- Puisi -------------------------------------------------------------------------

    private fun poem(text: String): CreativeReview {
        val st = stanzas(text)
        val lines = st.flatten()
        val ws = words(text)
        val t = tone(text)
        val checks = mutableListOf<ReviewCheck>()
        checks += ReviewCheck("Bait & baris", CheckStatus.Info, "${st.size} bait, ${lines.size} baris, ${ws.size} kata.")
        if (st.isNotEmpty()) {
            val patterns = st.map { rhymePattern(it) }
            checks += ReviewCheck(
                "Pola rima", CheckStatus.Info,
                patterns.mapIndexed { i, p -> if (st.size > 1) "Bait ${i + 1}: $p" else p }.joinToString(" · ") +
                    if (patterns.all { p -> p.split('-').distinct().size == p.split('-').size }) ". Puisi bebas, tanpa rima tetap." else ".",
            )
        }
        val starts = lines.mapNotNull { words(it).firstOrNull() }.groupingBy { it }.eachCount().filter { it.value >= 2 }
        if (starts.isNotEmpty()) {
            val (w, n) = starts.maxBy { it.value }
            checks += ReviewCheck("Pengulangan (anafora)", CheckStatus.Good, "\u201C$w\u201D membuka $n baris. Pengulangan memberi tekanan pada rasa.")
        }
        val diversity = if (ws.isEmpty()) 0.0 else ws.distinct().size.toDouble() / ws.size
        if (ws.size >= 20) {
            checks += ReviewCheck(
                "Ragam kata", if (diversity >= 0.6) CheckStatus.Good else CheckStatus.Info,
                "${(diversity * 100).roundToInt()}% kata berbeda." + if (diversity < 0.6) " Coba ganti kata yang berulang dengan padanannya." else "",
            )
        }
        val imagery = ws.count { it in Lexicon.imagery }
        checks += ReviewCheck(
            "Citraan", if (imagery >= 2) CheckStatus.Good else CheckStatus.Info,
            if (imagery >= 2) "$imagery kata yang membangun gambaran (alam, cahaya, warna, suara)." else "Tambahkan gambaran indra: apa yang terlihat, terdengar, atau terasa?",
        )
        return CreativeReview(
            EntryType.Poem, null,
            if (lines.isEmpty()) "Belum ada baris puisi" else "Puisi ${st.size} bait",
            checks, t.label, t.words,
            "Puisi memberi ruang untuk perasaan yang sulit dijelaskan secara lurus. Suasananya terasa ${t.label}" +
                (if (t.words.isNotEmpty()) ", terlihat dari kata seperti ${t.words.take(3).joinToString(", ") { "\u201C$it\u201D" }}." else "."),
            listOf(
                "Baca puisimu keras-keras; bagian yang tersendat biasanya perlu dipadatkan.",
                "Satu bait, satu gambaran. Pisahkan bait dengan baris kosong.",
            ),
        )
    }

    // ---- Cerita ------------------------------------------------------------------------

    private fun story(title: String, text: String): CreativeReview {
        val ws = words(text)
        val sentences = text.split(Regex("[.!?]+")).map { it.trim() }.filter { words(it).isNotEmpty() }
        val paragraphs = text.split(Regex("\\n\\s*\\n|\\n")).count { words(it).isNotEmpty() }
        val avgSentence = if (sentences.isEmpty()) 0.0 else ws.size.toDouble() / sentences.size
        val diversity = if (ws.isEmpty()) 0.0 else ws.distinct().size.toDouble() / ws.size
        val dialogue = Regex("[\"\u201C\u201D].+?[\"\u201C\u201D]").findAll(text).count()
        val t = tone(text)

        // Emotional arc: tone of the beginning, middle and end thirds.
        val third = (ws.size / 3).coerceAtLeast(1)
        fun part(i: Int) = ws.drop(i * third).take(if (i == 2) Int.MAX_VALUE else third)
        val arc = (0..2).map { i ->
            val p = part(i)
            val pos = p.count { it in Lexicon.positiveSet }
            val neg = p.count { it in Lexicon.negativeSet }
            pos - neg
        }
        val arcWords = arc.map { v -> if (v > 0) "cerah" else if (v < 0) "tegang" else "datar" }
        val hasTurn = ws.size >= 60 && (arc.max() - arc.min() >= 2 || (arc[1] < arc[0] && arc[1] < arc[2]))

        var score = 0
        score += (20 * (ws.size.coerceAtMost(300)) / 300)
        if (paragraphs >= 3) score += 15
        if (title.isNotBlank()) score += 10
        if (avgSentence in 8.0..22.0) score += 15
        if (diversity >= 0.45 || ws.size < 60) score += 15
        if (hasTurn) score += 15
        if (dialogue > 0) score += 10

        val checks = listOf(
            ReviewCheck("Panjang", if (ws.size >= 150) CheckStatus.Good else CheckStatus.Info, "${ws.size} kata, ${sentences.size} kalimat, $paragraphs paragraf."),
            ReviewCheck("Judul", if (title.isNotBlank()) CheckStatus.Good else CheckStatus.Warn, if (title.isNotBlank()) "\u201C${title.trim()}\u201D" else "Belum ada judul. Judul membantu pembaca masuk ke cerita."),
            ReviewCheck(
                "Panjang kalimat", if (avgSentence in 8.0..22.0) CheckStatus.Good else CheckStatus.Warn,
                "Rata-rata ${"%.0f".format(avgSentence)} kata per kalimat." + when {
                    avgSentence > 22 -> " Kalimat panjang bisa dipecah supaya lebih ringan dibaca."
                    avgSentence in 0.1..7.9 -> " Kalimat sangat pendek; gabungkan sebagian supaya mengalir."
                    else -> ""
                },
            ),
            ReviewCheck(
                "Alur rasa", if (hasTurn) CheckStatus.Good else CheckStatus.Info,
                "Awal ${arcWords[0]}, tengah ${arcWords[1]}, akhir ${arcWords[2]}." +
                    if (hasTurn) " Ada perubahan suasana, tanda cerita punya konflik." else " Cerita terasa datar; coba hadirkan masalah atau kejutan di tengah.",
            ),
            ReviewCheck("Dialog", if (dialogue > 0) CheckStatus.Good else CheckStatus.Info, if (dialogue > 0) "$dialogue kutipan dialog." else "Belum ada dialog. Dialog menghidupkan tokoh."),
            ReviewCheck("Ragam kata", if (diversity >= 0.45) CheckStatus.Good else CheckStatus.Info, "${(diversity * 100).roundToInt()}% kata berbeda."),
        )
        return CreativeReview(
            EntryType.Story, score,
            verdict(score, "Cerita yang utuh", "Cerita yang menjanjikan", "Kerangka cerita mulai terbentuk"),
            checks, t.label, t.words,
            "Menulis cerita memberi jarak aman untuk mengolah perasaan lewat tokoh. Suasana ceritamu ${t.label}" +
                (if (t.neg > t.pos && arc[2] > arc[1]) ", dan berakhir lebih terang; tokohmu menemukan jalan keluar." else "."),
            buildList {
                if (title.isBlank()) add("Beri judul yang memancing rasa ingin tahu.")
                if (!hasTurn) add("Tambahkan satu masalah yang harus dihadapi tokoh utama.")
                if (dialogue == 0) add("Coba satu baris dialog untuk memperkenalkan tokoh.")
                if (isEmpty()) add("Baca ulang dari sudut pandang pembaca baru: bagian mana yang ingin kamu perjelas?")
            },
        )
    }

    // ---- Ide ---------------------------------------------------------------------------

    private data class Part(val label: String, val words: List<String>, val tip: String)

    private val ideaParts = listOf(
        Part("Masalah / tujuan", listOf("masalah", "kenapa", "karena", "tujuan", "supaya", "agar", "biar", "solusi", "kebutuhan"), "Apa masalah yang ingin diselesaikan, atau tujuan yang ingin dicapai?"),
        Part("Untuk siapa", listOf("untuk", "pengguna", "orang", "pelanggan", "target", "teman", "keluarga", "siswa", "warga"), "Siapa yang paling terbantu oleh ide ini?"),
        Part("Langkah pertama", listOf("langkah", "mulai", "pertama", "besok", "minggu", "rencana", "coba", "jadwal", "dulu"), "Apa satu langkah kecil yang bisa dimulai minggu ini?"),
        Part("Sumber daya", listOf("biaya", "modal", "waktu", "alat", "butuh", "perlu", "anggaran", "bantuan"), "Apa yang dibutuhkan: waktu, biaya, atau bantuan siapa?"),
        Part("Kendala", listOf("risiko", "kendala", "tantangan", "hambatan", "kalau", "gagal", "sulit"), "Apa yang bisa menghambat, dan bagaimana mengatasinya?"),
    )

    private fun idea(title: String, text: String): CreativeReview {
        val ws = words("$title $text")
        // Prefix match so suffixes count too: "masalahnya", "langkahnya", "tujuannya".
        fun has(k: String) = ws.any { it == k || (k.length >= 4 && it.startsWith(k)) }
        val found = ideaParts.map { p -> p to p.words.any(::has) }
        val score = 20 * found.count { it.second }
        val count = text.split('\n').count { it.trim().matches(Regex("^(\\d{1,3}\\.|•|-) .+")) }
        val t = tone(text)
        val checks = found.map { (p, ok) ->
            ReviewCheck(p.label, if (ok) CheckStatus.Good else CheckStatus.Warn, if (ok) "Sudah dibahas." else p.tip)
        } + listOfNotNull(
            if (count >= 2) ReviewCheck("Jumlah gagasan", CheckStatus.Info, "$count poin gagasan.") else null,
        )
        return CreativeReview(
            EntryType.Idea, score,
            verdict(score, "Ide yang siap dijalankan", "Ide yang sudah cukup jelas", "Ide yang masih mentah, dan itu wajar"),
            checks, t.label, t.words,
            "Menuliskan ide mengosongkan pikiran dan memberi rasa kendali. " +
                if (t.pos >= t.neg) "Nada idemu bersemangat; manfaatkan energi ini untuk langkah pertama." else "Ada nada khawatir di idemu; memecahnya jadi langkah kecil biasanya meredakan cemas.",
            found.filterNot { it.second }.map { it.first.tip }.ifEmpty { listOf("Tetapkan tanggal untuk langkah pertama, lalu tulis hasilnya di jurnal.") },
        )
    }

    /** Short live hint for the editor: "4 baris · 9·10·9·11 suku kata · rima a-b-a-b". */
    fun liveHint(type: EntryType, body: String): String? {
        if (type != EntryType.Pantun && type != EntryType.Poem) return null
        val st = stanzas(Markup.plain(body))
        val last = st.lastOrNull() ?: return null
        val syl = last.map { lineSyllables(it) }
        val head = if (st.size > 1) "Bait ${st.size} · " else ""
        return "$head${last.size} baris · ${syl.joinToString("·")} suku kata · rima ${rhymePattern(last)}"
    }

    @Suppress("unused")
    private fun near(a: Double, b: Double, tol: Double) = abs(a - b) <= tol
}
