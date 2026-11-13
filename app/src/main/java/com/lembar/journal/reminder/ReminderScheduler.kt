package com.lembar.journal.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lembar.journal.MainActivity
import com.lembar.journal.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Daily reminder via AlarmManager. Uses an inexact while-idle alarm (no
 * special "exact alarm" permission needed); on a Galaxy it typically fires
 * within a few minutes of the chosen time. Re-armed after each fire and on boot.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Pengingat harian", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Satu pengingat lembut per hari untuk menulis jurnal."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
        } else {
            // Without the exact-alarm permission, ask for a 10-minute window
            // instead of the default inexact window (which can be an hour).
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000L, pendingIntent())
        }
    }

    /** Android 12+ needs the user to allow "Alarms & reminders" for on-the-minute alarms. */
    fun canExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showReminder(streak: Int) {
        if (!canNotify()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_CHECKIN, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val (title, text) = message(streak)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lembar)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    /** Warm, low-pressure copy. A streak is mentioned as encouragement, never as a threat. */
    private fun message(streak: Int): Pair<String, String> {
        if (streak >= 2) {
            return "Hari ke-${streak + 1}?" to "Kamu sudah menulis $streak hari berturut-turut. Dua menit hari ini sudah cukup."
        }
        val options = listOf(
            "Hari ini ngapain aja?" to "Tulis sedikit saja tentang harimu. Satu kalimat juga boleh.",
            "Gimana perasaanmu hari ini?" to "Pilih mood-mu dulu, sisanya bisa menyusul.",
            "Sebelum hari ini lewat" to "Ada momen yang ingin kamu simpan? Foto atau tulisan, bebas.",
            "Waktunya menepi sebentar" to "Tarik napas, lalu ceritakan harimu ke Nala.",
        )
        return options[(LocalDate.now().dayOfYear) % options.size]
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        private const val REQUEST_CODE = 4021
        private const val NOTIFICATION_ID = 21
    }
}
