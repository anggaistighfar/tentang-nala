package com.lembar.journal.domain

/**
 * Five-point mood scale. Five points is enough to show trends without making
 * people agonise over the choice (common in clinical mood charts).
 */
enum class Mood(val value: Int, val label: String) {
    Heavy(1, "Berat"),
    Down(2, "Sedih"),
    Okay(3, "Biasa"),
    Good(4, "Baik"),
    Great(5, "Senang");

    companion object {
        fun of(value: Int): Mood? = entries.firstOrNull { it.value == value }
    }
}

/** Valence groups an emotion falls into, used to order chips for the chosen mood. */
enum class Valence { Negative, Neutral, Positive }

data class Emotion(val label: String, val valence: Valence)

object Emotions {
    /**
     * Naming a feeling precisely ("affect labeling") lowers its intensity, so the
     * list is deliberately more specific than good/bad.
     */
    val all: List<Emotion> = listOf(
        Emotion("kesal", Valence.Negative),
        Emotion("cemas", Valence.Negative),
        Emotion("takut", Valence.Negative),
        Emotion("tertekan", Valence.Negative),
        Emotion("sedih", Valence.Negative),
        Emotion("kesepian", Valence.Negative),
        Emotion("bersalah", Valence.Negative),
        Emotion("malu", Valence.Negative),
        Emotion("marah", Valence.Negative),
        Emotion("kecewa", Valence.Negative),
        Emotion("lelah", Valence.Neutral),
        Emotion("bosan", Valence.Neutral),
        Emotion("tidak semangat", Valence.Neutral),
        Emotion("bingung", Valence.Neutral),
        Emotion("tenang", Valence.Neutral),
        Emotion("lega", Valence.Positive),
        Emotion("produktif", Valence.Positive),
        Emotion("puas", Valence.Positive),
        Emotion("bersyukur", Valence.Positive),
        Emotion("percaya diri", Valence.Positive),
        Emotion("bangga", Valence.Positive),
        Emotion("dicintai", Valence.Positive),
        Emotion("bahagia", Valence.Positive),
        Emotion("bersemangat", Valence.Positive),
    )

    /** Mood-congruent emotions first, so the likely ones are near the top. */
    fun orderedFor(mood: Mood?): List<Emotion> {
        val priority = when (mood) {
            Mood.Heavy, Mood.Down -> listOf(Valence.Negative, Valence.Neutral, Valence.Positive)
            Mood.Good, Mood.Great -> listOf(Valence.Positive, Valence.Neutral, Valence.Negative)
            else -> listOf(Valence.Neutral, Valence.Positive, Valence.Negative)
        }
        return all.sortedBy { priority.indexOf(it.valence) }
    }

    const val SEPARATOR = "|"

    fun encode(labels: Collection<String>): String =
        labels.map { it.replace(SEPARATOR, " ").trim() }.filter { it.isNotEmpty() }.distinct().joinToString(SEPARATOR)

    fun decode(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split(SEPARATOR).filter { it.isNotBlank() }
}
