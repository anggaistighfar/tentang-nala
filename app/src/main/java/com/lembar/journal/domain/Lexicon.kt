package com.lembar.journal.domain

/**
 * Indonesian keyword lists used by the on-device analysis. Matching is done on
 * normalised text (lowercase, letters/digits only, single spaces) padded with
 * spaces, so " kerja " does not match inside "bekerjasama".
 *
 * These lists are deliberately conservative: a missed theme is better than a
 * wrong one shown to someone who is already struggling.
 */
object Lexicon {

    fun normalize(text: String): String =
        " " + text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim() + " "

    /** True if any phrase occurs as whole words in [normalized]. */
    fun containsAny(normalized: String, phrases: List<String>): Boolean =
        phrases.any { normalized.contains(" $it ") }

    /** First matching phrase and its start index, or null. */
    fun firstMatch(normalized: String, phrases: List<String>): Pair<String, Int>? {
        var best: Pair<String, Int>? = null
        for (p in phrases) {
            val i = normalized.indexOf(" $p ")
            if (i >= 0 && (best == null || i < best.second)) best = p to i
        }
        return best
    }

    /** About [words] words of context around a match, for quoting back to the user. */
    fun snippet(normalized: String, index: Int, phrase: String, words: Int = 4): String {
        val before = normalized.substring(0, index).trim().split(' ').filter { it.isNotEmpty() }.takeLast(words)
        val after = normalized.substring(index + phrase.length + 1).trim().split(' ').filter { it.isNotEmpty() }.take(words)
        val text = (before + phrase + after).joinToString(" ")
        val pre = if (before.size == words) "…" else ""
        val post = if (after.size == words) "…" else ""
        return "$pre$text$post"
    }

    // ---- Safety ---------------------------------------------------------------

    /**
     * Phrases suggesting thoughts of suicide or self-harm. Any hit shows support
     * resources; it never blocks saving and is never sent anywhere.
     */
    val crisis = listOf(
        "bunuh diri", "ingin bunuh diri", "mau bunuh diri", "pengen bunuh diri",
        "ingin mati", "pengen mati", "pingin mati", "kepengen mati", "mau mati aja", "mau mati saja",
        "aku mau mati", "saya mau mati", "gue mau mati", "gw mau mati",
        "mending mati", "lebih baik mati", "lebih baik aku mati", "lebih baik aku tidak ada",
        "lebih baik aku nggak ada", "lebih baik aku gak ada", "lebih baik saya tidak ada",
        "mengakhiri hidup", "akhiri hidup", "mengakhiri hidupku", "mengakhiri semuanya",
        "tidak ingin hidup", "nggak ingin hidup", "gak ingin hidup", "tidak mau hidup",
        "nggak mau hidup", "gak mau hidup", "ga mau hidup", "capek hidup", "lelah hidup",
        "menyakiti diri", "menyakiti diriku", "melukai diri", "melukai diriku",
        "self harm", "selfharm", "nyayat", "menyayat tangan", "sayat tangan",
        "tidak ada gunanya hidup", "nggak ada gunanya hidup", "hidup tidak ada artinya",
        "hidupku tidak berarti", "tidak ada alasan untuk hidup",
    )

    fun hasCrisis(text: String): Boolean = containsAny(normalize(text), crisis)

    // ---- Life themes ------------------------------------------------------------

    enum class Kind { Area, Activity }

    data class Theme(val id: String, val label: String, val kind: Kind, val words: List<String>)

    val themes = listOf(
        Theme(
            "work", "pekerjaan", Kind.Area,
            listOf(
                "kerja", "kerjaan", "bekerja", "pekerjaan", "kantor", "bos", "atasan", "rekan kerja",
                "deadline", "meeting", "rapat", "lembur", "klien", "client", "proyek", "project", "gaji", "resign", "wfh",
            ),
        ),
        Theme(
            "study", "kuliah & sekolah", Kind.Area,
            listOf(
                "kuliah", "kampus", "dosen", "tugas", "skripsi", "tesis", "ujian", "uts", "uas", "sekolah",
                "guru", "kelas", "nilai", "ipk", "belajar", "praktikum", "sidang",
            ),
        ),
        Theme(
            "family", "keluarga", Kind.Area,
            listOf(
                "ibu", "ayah", "mama", "papa", "mamah", "papah", "bapak", "emak", "bunda", "orang tua", "ortu",
                "kakak", "adik", "keluarga", "anak", "anakku", "nenek", "kakek", "mertua",
            ),
        ),
        Theme(
            "partner", "pasangan & asmara", Kind.Area,
            listOf("pacar", "pasangan", "suami", "istri", "gebetan", "mantan", "doi", "ayang", "tunangan", "putus", "pacaran"),
        ),
        Theme(
            "friends", "teman", Kind.Area,
            listOf("teman", "temen", "teman teman", "sahabat", "kawan", "bestie", "nongkrong", "geng"),
        ),
        Theme(
            "sleep", "tidur & energi", Kind.Area,
            listOf(
                "tidur", "begadang", "insomnia", "ngantuk", "susah tidur", "kurang tidur", "capek", "cape",
                "lelah", "kelelahan", "istirahat", "bangun kesiangan",
            ),
        ),
        Theme(
            "health", "kesehatan", Kind.Area,
            listOf("sakit", "pusing", "demam", "dokter", "obat", "rumah sakit", "flu", "batuk", "maag", "migrain", "periksa"),
        ),
        Theme(
            "money", "keuangan", Kind.Area,
            listOf("uang", "duit", "utang", "hutang", "cicilan", "tagihan", "bayar", "boros", "tabungan", "pinjol", "keuangan"),
        ),
        Theme(
            "self", "diri sendiri", Kind.Area,
            listOf("diri sendiri", "diriku", "insecure", "overthinking", "overthink", "minder", "percaya diri", "self love"),
        ),
        // Things that usually lift mood (behavioural activation).
        Theme(
            "move", "bergerak & olahraga", Kind.Activity,
            listOf(
                "olahraga", "lari", "jogging", "gym", "jalan kaki", "jalan pagi", "sepeda", "bersepeda", "renang",
                "berenang", "yoga", "senam", "badminton", "futsal", "sepak bola", "workout", "stretching",
            ),
        ),
        Theme(
            "nature", "keluar rumah & alam", Kind.Activity,
            listOf("jalan jalan", "taman", "pantai", "gunung", "alam", "piknik", "hiking", "sunset", "matahari", "udara segar"),
        ),
        Theme(
            "connect", "waktu bersama orang lain", Kind.Activity,
            listOf("ngobrol", "cerita ke", "curhat", "ketemu", "bertemu", "makan bareng", "kumpul", "telepon", "video call", "main bareng"),
        ),
        Theme(
            "spirit", "ibadah & refleksi", Kind.Activity,
            listOf("ibadah", "sholat", "salat", "shalat", "doa", "berdoa", "mengaji", "ngaji", "gereja", "misa", "pura", "vihara", "meditasi", "dzikir", "zikir"),
        ),
        Theme(
            "hobby", "hobi & kreativitas", Kind.Activity,
            listOf(
                "masak", "memasak", "baca", "membaca", "buku", "musik", "nyanyi", "gitar", "menggambar", "gambar",
                "melukis", "menulis", "nonton", "film", "main game", "berkebun", "foto", "fotografi", "merajut",
            ),
        ),
    )

    // ---- Thinking patterns (CBT cognitive distortions) ---------------------------

    data class Distortion(
        val id: String,
        val name: String,
        val explain: String,
        val reframe: String,
        val phrases: List<String>,
    )

    val distortions = listOf(
        Distortion(
            "label", "Memberi label buruk pada diri",
            "Satu kejadian dijadikan cap untuk seluruh dirimu.",
            "Apa yang sebenarnya terjadi, tanpa kata sifat untuk dirimu? Apa yang akan kamu katakan ke sahabat yang mengalami hal sama?",
            listOf(
                "aku bodoh", "saya bodoh", "gue bodoh", "gw bodoh", "aku goblok", "aku bego", "aku payah", "saya payah",
                "aku gagal", "saya gagal", "aku pecundang", "aku tidak berguna", "aku nggak berguna", "aku gak berguna",
                "aku ga berguna", "saya tidak berguna", "aku beban", "aku cuma beban", "aku jelek", "aku tidak pantas",
                "aku nggak pantas", "aku gak pantas", "aku lemah", "aku menyedihkan", "aku bodoh banget",
            ),
        ),
        Distortion(
            "general", "Generalisasi berlebihan",
            "Kata seperti “selalu” dan “tidak pernah” membuat satu kejadian terasa seperti pola yang pasti.",
            "Benarkah selalu? Coba ingat satu kali ketika hal itu tidak terjadi.",
            listOf(
                "selalu gagal", "selalu salah", "selalu begini", "selalu begitu", "selalu sendiri", "tidak pernah bisa",
                "nggak pernah bisa", "gak pernah bisa", "ga pernah bisa", "tidak pernah berhasil", "nggak pernah berhasil",
                "semua orang benci", "semua orang ninggalin", "tidak ada yang peduli", "nggak ada yang peduli",
                "gak ada yang peduli", "ga ada yang peduli", "tidak ada yang sayang", "nggak ada yang sayang",
                "semuanya salah", "semuanya gagal",
            ),
        ),
        Distortion(
            "catastrophe", "Membayangkan yang terburuk",
            "Pikiran melompat ke akhir paling buruk, seolah itu pasti terjadi.",
            "Apa kemungkinan terburuk, terbaik, dan yang paling mungkin? Kalau yang terburuk terjadi, apa satu langkah yang bisa kamu ambil?",
            listOf(
                "pasti gagal", "pasti hancur", "hancur semua", "semuanya hancur", "hidupku hancur", "berantakan semua",
                "semuanya berantakan", "tamat riwayat", "nggak akan bisa", "gak akan bisa", "tidak akan bisa",
                "ga akan bisa", "nggak bakal bisa", "gak bakal bisa", "pasti ditolak", "pasti dipecat", "mati aku",
            ),
        ),
        Distortion(
            "should", "Tuntutan “harusnya”",
            "Aturan kaku untuk diri sendiri sering berujung rasa bersalah atau kesal.",
            "Ganti “harusnya aku…” dengan “aku ingin…, dan wajar kalau belum”. Apa yang berubah rasanya?",
            listOf(
                "harusnya aku", "seharusnya aku", "mestinya aku", "semestinya aku", "harusnya saya", "seharusnya saya",
                "harusnya gue", "aku harus bisa", "aku harusnya bisa", "seharusnya aku bisa", "harusnya aku bisa",
            ),
        ),
        Distortion(
            "mind", "Menebak isi pikiran orang",
            "Merasa yakin tahu apa yang orang lain pikirkan tentangmu, tanpa bukti langsung.",
            "Apa buktinya? Adakah penjelasan lain untuk sikap mereka?",
            listOf(
                "pasti dia pikir", "pasti mereka pikir", "pasti dia kesal", "pasti mereka kesal", "pasti dia benci",
                "pasti mereka benci", "pasti dia nggak suka", "pasti mereka nggak suka", "pasti dia gak suka",
                "pasti orang orang", "mereka pasti mikir", "dia pasti mikir", "orang pasti mikir",
            ),
        ),
    )
}
