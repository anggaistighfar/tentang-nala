package com.lembar.journal.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lembar.journal.LembarApp
import com.lembar.journal.domain.XpRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fires once a day. Skips the notification if the user already wrote today. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LembarApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                val c = app.container
                val settings = c.settings.current()
                if (!settings.reminderEnabled) return@launch
                c.reminders.schedule(settings.reminderHour, settings.reminderMinute)

                val today = LocalDate.now()
                if (!c.journal.hasEntryFor(today)) {
                    val entries = c.journal.entries.first()
                    val streak = XpRules.compute(entries.map { it.facts() }, today).currentStreak
                    c.reminders.showReminder(streak)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms are wiped on reboot and on clock changes; put the daily one back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LembarApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                val s = app.container.settings.current()
                if (s.onboarded && s.reminderEnabled) app.container.reminders.schedule(s.reminderHour, s.reminderMinute)
            } finally {
                pending.finish()
            }
        }
    }
}
