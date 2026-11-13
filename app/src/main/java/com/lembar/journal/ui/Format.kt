package com.lembar.journal.ui

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Indonesian labels, hard-coded so they don't depend on the phone's locale data. */
object Fmt {
    private val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    private val daysShort = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
    private val months = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )
    private val monthsShort = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

    fun day(d: DayOfWeek) = days[d.value - 1]
    fun dayShort(d: DayOfWeek) = daysShort[d.value - 1]
    fun month(m: Int) = months[m - 1]
    fun monthShort(m: Int) = monthsShort[m - 1]

    /** "Jumat, 13 November" */
    fun dayMonth(date: LocalDate) = "${day(date.dayOfWeek)}, ${date.dayOfMonth} ${month(date.monthValue)}"

    /** "13 Nov 2026" */
    fun short(date: LocalDate) = "${date.dayOfMonth} ${monthShort(date.monthValue)} ${date.year}"

    /** "13 November 2026" */
    fun long(date: LocalDate) = "${date.dayOfMonth} ${month(date.monthValue)} ${date.year}"

    fun monthYear(ym: YearMonth) = "${month(ym.monthValue)} ${ym.year}"

    /** "14.36" (Indonesian uses a dot). */
    fun time(epochMillis: Long): String {
        val t = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
        return "%02d.%02d".format(t.hour, t.minute)
    }

    fun time(hour: Int, minute: Int) = "%02d.%02d".format(hour, minute)

    fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Hari ini"
        today.minusDays(1) -> "Kemarin"
        else -> dayMonth(date)
    }

    fun bytes(size: Long): String = when {
        size >= 1024L * 1024 -> "%.1f MB".format(size / 1024.0 / 1024.0)
        size >= 1024 -> "%.0f KB".format(size / 1024.0)
        else -> "$size B"
    }

    fun greeting(hour: Int) = when (hour) {
        in 4..10 -> "Selamat pagi"
        in 11..14 -> "Selamat siang"
        in 15..17 -> "Selamat sore"
        else -> "Selamat malam"
    }
}
