package com.lembar.journal

import android.app.Application
import com.lembar.journal.data.EntryCipher
import com.lembar.journal.data.JournalRepository
import com.lembar.journal.data.MediaStorage
import com.lembar.journal.data.SettingsRepository
import com.lembar.journal.data.backup.BackupManager
import com.lembar.journal.data.db.LembarDatabase
import com.lembar.journal.reminder.ReminderScheduler
import com.lembar.journal.security.SessionLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-rolled dependency container; the app is small enough not to need Hilt. */
class AppContainer(app: Application) {
    // Lazy: nothing heavy (Keystore, Room) runs before the first screen needs it.
    val database by lazy { LembarDatabase.create(app) }
    val media by lazy { MediaStorage(app) }
    val cipher by lazy { EntryCipher() }
    val journal by lazy { JournalRepository(database, cipher, media) }
    val backup by lazy { BackupManager(app, database, cipher, media) }
    val settings = SettingsRepository(app)
    val reminders = ReminderScheduler(app)
    val session = SessionLock()
}

class LembarApp : Application() {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.reminders.ensureChannel()
        appScope.launch {
            val s = container.settings.current()
            if (s.onboarded && s.reminderEnabled) container.reminders.schedule(s.reminderHour, s.reminderMinute)
            container.journal.pruneOrphanMedia()
        }
    }
}
