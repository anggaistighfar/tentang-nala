package com.lembar.journal.domain

/**
 * Writing prompts. Low-mood prompts lean on self-compassion and expressive
 * writing (no fixing, no judging); high-mood prompts help savour the good part.
 */
object Prompts {
    private val heavy = listOf(
        "Apa yang paling berat hari ini? Tulis saja apa adanya, tanpa disaring.",
        "Kalau sahabatmu mengalami hari seperti ini, apa yang akan kamu katakan padanya?",
        "Apa satu hal kecil yang bisa sedikit meringankan besok?",
        "Di bagian tubuh mana kamu merasakan beratnya hari ini?",
    )
    private val okay = listOf(
        "Hari ini kamu ngapain aja, dari bangun sampai sekarang?",
        "Momen apa yang paling kamu ingat hari ini?",
        "Apa yang menyita pikiranmu hari ini?",
        "Ada obrolan yang masih terngiang sampai sekarang?",
    )
    private val good = listOf(
        "Apa yang membuat hari ini terasa baik? Ceritakan detailnya.",
        "Siapa yang ingin kamu ucapkan terima kasih hari ini, dan kenapa?",
        "Apa yang kamu lakukan hari ini yang bikin kamu bangga?",
        "Momen kecil apa yang ingin kamu simpan dari hari ini?",
    )
    private val general = listOf(
        "Tiga hal yang kamu syukuri hari ini.",
        "Apa yang kamu pelajari tentang dirimu hari ini?",
        "Kalau hari ini jadi satu judul film, apa judulnya?",
    )

    fun forMood(mood: Mood?): List<String> = when (mood) {
        Mood.Heavy, Mood.Down -> heavy + general
        Mood.Good, Mood.Great -> good + general
        else -> okay + general
    }
}

/** Reflection cards on the home screen ("Untukmu"). Tapping one opens a guided entry. */
data class Reflection(val title: String, val prompt: String, val tone: Int)

object Reflections {
    val all = listOf(
        Reflection("Saat kerjaan bikin stres", "Apa yang paling menekan dari pekerjaanmu hari ini, dan bagian mana yang sebenarnya di luar kendalimu?", 0),
        Reflection("Berhenti menyalahkan diri", "Tulis satu kesalahanmu, lalu tulis tanggapan seperti kamu sedang menenangkan teman baik.", 1),
        Reflection("Tiga hal baik hari ini", "Tulis tiga hal baik yang terjadi hari ini dan apa peranmu di dalamnya.", 2),
        Reflection("Surat untuk diri sendiri", "Tulis surat pendek untuk dirimu setahun lagi. Apa yang ingin kamu ingat dari hari ini?", 3),
        Reflection("Sebelum tidur", "Apa yang ingin kamu lepaskan malam ini supaya bisa tidur lebih tenang?", 4),
    )
}
