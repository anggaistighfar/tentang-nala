package com.lembar.journal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lembar.journal.AppContainer
import com.lembar.journal.LembarApp
import com.lembar.journal.data.JournalEntry
import com.lembar.journal.data.PhotoQuality
import com.lembar.journal.data.Settings
import com.lembar.journal.data.ThemeMode
import com.lembar.journal.domain.Progress
import com.lembar.journal.domain.XpRules
import com.lembar.journal.security.Authenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
inline fun <reified VM : ViewModel> lembarViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as LembarApp
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(app.container, createSavedStateHandle()) } },
    )
}

val LocalAuthenticator = staticCompositionLocalOf<Authenticator> { error("Authenticator not provided") }

/** Activity-scoped state shared by all tabs: settings, entries, XP. */
class AppViewModel(private val c: AppContainer) : ViewModel() {

    /** null until DataStore has loaded once (avoids flashing onboarding). */
    val settings: StateFlow<Settings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val entries: StateFlow<List<JournalEntry>> = c.journal.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today

    val progress: StateFlow<Progress> = combine(entries, _today) { list, day -> XpRules.compute(list.map { it.facts() }, day) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, XpRules.compute(emptyList(), LocalDate.now()))

    val session = c.session

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes

    fun refreshToday() {
        _today.value = LocalDate.now()
    }

    fun refreshStorage() {
        viewModelScope.launch(Dispatchers.IO) { _storageBytes.value = c.media.usedBytes() }
    }

    fun completeOnboarding(name: String, hour: Int, minute: Int, reminder: Boolean) = viewModelScope.launch {
        c.settings.completeOnboarding(name, hour, minute, reminder)
        if (reminder) c.reminders.schedule(hour, minute) else c.reminders.cancel()
    }

    fun setName(v: String) = viewModelScope.launch { c.settings.setName(v) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { c.settings.setTheme(v) }
    fun setDefaultFont(v: String) = viewModelScope.launch { c.settings.setDefaultFont(v) }
    fun setAppLock(v: Boolean) = viewModelScope.launch { c.settings.setAppLock(v) }
    fun setPhotoQuality(v: PhotoQuality) = viewModelScope.launch { c.settings.setPhotoQuality(v) }
    fun setStylusOnly(v: Boolean) = viewModelScope.launch { c.settings.setStylusOnly(v) }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        c.settings.setReminder(enabled, hour, minute)
        if (enabled) c.reminders.schedule(hour, minute) else c.reminders.cancel()
    }

    // ---- Backup ------------------------------------------------------------------

    sealed interface BackupStatus {
        data object Idle : BackupStatus
        data class Working(val label: String) : BackupStatus
        data class Done(val title: String, val message: String) : BackupStatus
        data class Failed(val message: String) : BackupStatus
    }

    private val _backup = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backup: StateFlow<BackupStatus> = _backup

    fun clearBackupStatus() { _backup.value = BackupStatus.Idle }

    fun exportBackup(uri: android.net.Uri, password: CharArray) = viewModelScope.launch {
        _backup.value = BackupStatus.Working("Membuat cadangan…")
        _backup.value = try {
            val r = c.backup.export(uri, password)
            c.settings.setLastBackup(System.currentTimeMillis())
            BackupStatus.Done(
                "Cadangan tersimpan",
                "${r.entries} jurnal dan ${r.media} gambar (${Fmt.bytes(r.bytes)}). Simpan file ini di tempat aman, misalnya Google Drive, dan ingat password-nya.",
            )
        } catch (t: Throwable) {
            BackupStatus.Failed("Cadangan gagal dibuat: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            password.fill(' ')
        }
    }

    fun restoreBackup(uri: android.net.Uri, password: CharArray) = viewModelScope.launch {
        _backup.value = BackupStatus.Working("Memulihkan jurnal…")
        _backup.value = try {
            val r = c.backup.restore(uri, password)
            BackupStatus.Done(
                "Pemulihan selesai",
                buildString {
                    append("${r.added} jurnal ditambahkan")
                    if (r.media > 0) append(" (${r.media} gambar)")
                    append(".")
                    if (r.skipped > 0) append(" ${r.skipped} jurnal sudah ada di HP ini, jadi dilewati.")
                    append(" Tidak ada jurnal yang dihapus atau ditimpa.")
                },
            )
        } catch (_: com.lembar.journal.data.backup.WrongPasswordException) {
            BackupStatus.Failed("Password salah, atau file cadangan rusak. Jurnalmu tidak berubah.")
        } catch (_: com.lembar.journal.data.backup.NotABackupException) {
            BackupStatus.Failed("File ini bukan cadangan Tentang Nala. Jurnalmu tidak berubah.")
        } catch (t: Throwable) {
            BackupStatus.Failed("Pemulihan gagal: ${t.message ?: t.javaClass.simpleName}. Jurnalmu tidak berubah.")
        } finally {
            password.fill(' ')
        }
    }

    fun hasLockedEntries() = entries.value.any { it.locked }

    fun canNotify() = c.reminders.canNotify()
    fun canExactAlarm() = c.reminders.canExact()

    /** Re-arm with the current settings (e.g. after the exact-alarm permission changes). */
    fun rescheduleReminder() = viewModelScope.launch {
        val s = c.settings.current()
        if (s.onboarded && s.reminderEnabled) c.reminders.schedule(s.reminderHour, s.reminderMinute)
    }
}
