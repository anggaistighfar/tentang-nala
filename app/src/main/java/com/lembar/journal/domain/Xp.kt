package com.lembar.journal.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The few numbers XP needs from an entry. No text, so locked entries count too. */
data class EntryFacts(
    val epochDay: Long,
    val createdAt: Long,
    val mood: Int,
    val emotionCount: Int,
    val wordCount: Int,
    val mediaCount: Int,
)

data class XpItem(val label: String, val xp: Int)

data class DayXp(val epochDay: Long, val items: List<XpItem>) {
    val total: Int get() = items.sumOf { it.xp }
}

data class LevelInfo(
    val level: Int,
    val title: String,
    val totalXp: Int,
    val xpIntoLevel: Int,
    val xpForNext: Int,
) {
    val progress: Float get() = if (xpForNext == 0) 0f else xpIntoLevel.toFloat() / xpForNext
}

data class Progress(
    val level: LevelInfo,
    val currentStreak: Int,
    val longestStreak: Int,
    val journaledDays: Int,
    val days: Map<Long, DayXp>,
)

/**
 * XP is derived from the entries every time instead of stored, so editing or
 * deleting an entry can never leave XP out of sync.
 *
 * Design choices (habit-building, not grinding):
 *  - XP is per day, not per entry, so splitting one day into ten entries
 *    does not pay ten times.
 *  - A day only counts if it was written on that day or the day after.
 *    Back-filling last month is allowed, it just earns nothing.
 *  - Streak bonus is capped, and a broken streak costs nothing already earned.
 */
object XpRules {
    const val BASE = 40
    const val MOOD = 10
    const val PER_EMOTION = 5
    const val EMOTION_CAP = 15
    const val WORDS_PER_XP = 10
    const val WORD_CAP = 30
    const val PER_MEDIA = 10
    const val MEDIA_CAP = 20
    const val PER_EXTRA_ENTRY = 5
    const val EXTRA_ENTRY_CAP = 10
    const val STREAK_STEP = 5
    const val STREAK_CAP = 35

    fun compute(entries: List<EntryFacts>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Progress {
        val byDay = entries
            .filter { it.qualifies(zone) }
            .groupBy { it.epochDay }
            .toSortedMap()

        val days = LinkedHashMap<Long, DayXp>()
        var run = 0
        var prevDay: Long? = null
        var longest = 0
        for ((day, list) in byDay) {
            run = if (prevDay != null && day == prevDay + 1) run + 1 else 1
            longest = maxOf(longest, run)
            prevDay = day
            days[day] = DayXp(day, itemsFor(list, run))
        }

        val todayDay = today.toEpochDay()
        val current = when {
            todayDay in days -> streakEndingAt(days.keys, todayDay)
            (todayDay - 1) in days -> streakEndingAt(days.keys, todayDay - 1)
            else -> 0
        }
        val total = days.values.sumOf { it.total }
        return Progress(levelFor(total), current, longest, days.size, days)
    }

    private fun EntryFacts.qualifies(zone: ZoneId): Boolean {
        val writtenOn = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate().toEpochDay()
        return writtenOn in epochDay..(epochDay + 1)
    }

    private fun itemsFor(list: List<EntryFacts>, streakRun: Int): List<XpItem> {
        val items = mutableListOf(XpItem("Menulis hari ini", BASE))
        if (list.any { it.mood > 0 }) items += XpItem("Memilih mood", MOOD)
        val emotions = minOf(list.sumOf { it.emotionCount } * PER_EMOTION, EMOTION_CAP)
        if (emotions > 0) items += XpItem("Menamai emosi", emotions)
        val words = minOf(list.sumOf { it.wordCount } / WORDS_PER_XP, WORD_CAP)
        if (words > 0) items += XpItem("Kata yang ditulis", words)
        val media = minOf(list.sumOf { it.mediaCount } * PER_MEDIA, MEDIA_CAP)
        if (media > 0) items += XpItem("Momen yang diabadikan", media)
        val extra = minOf((list.size - 1) * PER_EXTRA_ENTRY, EXTRA_ENTRY_CAP)
        if (extra > 0) items += XpItem("Catatan tambahan", extra)
        val streak = minOf((streakRun - 1) * STREAK_STEP, STREAK_CAP)
        if (streak > 0) items += XpItem("Bonus $streakRun hari beruntun", streak)
        return items
    }

    private fun streakEndingAt(days: Set<Long>, end: Long): Int {
        var n = 0
        var d = end
        while (d in days) { n++; d-- }
        return n
    }

    /** XP needed to go from level L to L+1 is 100 + 50*(L-1). */
    fun xpAtLevelStart(level: Int): Int {
        val l = level - 1
        return 100 * l + 25 * l * (l - 1)
    }

    fun levelFor(totalXp: Int): LevelInfo {
        var level = 1
        while (xpAtLevelStart(level + 1) <= totalXp) level++
        val start = xpAtLevelStart(level)
        val next = xpAtLevelStart(level + 1)
        return LevelInfo(level, titleFor(level), totalXp, totalXp - start, next - start)
    }

    fun titleFor(level: Int): String = when {
        level <= 2 -> "Lembar Kosong"
        level <= 4 -> "Pencatat Pemula"
        level <= 7 -> "Penjelajah Rasa"
        level <= 11 -> "Perenung"
        level <= 16 -> "Penulis Harian"
        level <= 24 -> "Penjaga Cerita"
        else -> "Arsitek Batin"
    }
}
