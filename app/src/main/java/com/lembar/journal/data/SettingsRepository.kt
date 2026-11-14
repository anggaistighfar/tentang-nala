package com.lembar.journal.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val key: String, val label: String) {
    System("system", "Ikuti sistem"),
    Paper("paper", "Kertas (terang)"),
    Night("night", "Malam (gelap)");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: System
    }
}

data class Settings(
    val onboarded: Boolean = false,
    val name: String = "",
    val themeMode: ThemeMode = ThemeMode.System,
    val defaultFont: String = "lora",
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val appLock: Boolean = false,
    val photoQuality: PhotoQuality = PhotoQuality.Seimbang,
    val stylusOnly: Boolean = true,
    /** 0 = never backed up. */
    val lastBackupAt: Long = 0L,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val theme = stringPreferencesKey("theme")
        val font = stringPreferencesKey("default_font")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val appLock = booleanPreferencesKey("app_lock")
        val photoQuality = stringPreferencesKey("photo_quality")
        val stylusOnly = booleanPreferencesKey("stylus_only")
        val lastBackup = longPreferencesKey("last_backup_at")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        onboarded = this[Keys.onboarded] ?: false,
        name = this[Keys.name] ?: "",
        themeMode = ThemeMode.fromKey(this[Keys.theme]),
        defaultFont = this[Keys.font] ?: "lora",
        reminderEnabled = this[Keys.reminderEnabled] ?: true,
        reminderHour = this[Keys.reminderHour] ?: 21,
        reminderMinute = this[Keys.reminderMinute] ?: 0,
        appLock = this[Keys.appLock] ?: false,
        photoQuality = PhotoQuality.fromKey(this[Keys.photoQuality]),
        stylusOnly = this[Keys.stylusOnly] ?: true,
        lastBackupAt = this[Keys.lastBackup] ?: 0L,
    )

    suspend fun completeOnboarding(name: String, hour: Int, minute: Int, reminder: Boolean) {
        context.dataStore.edit {
            it[Keys.name] = name.trim()
            it[Keys.reminderHour] = hour
            it[Keys.reminderMinute] = minute
            it[Keys.reminderEnabled] = reminder
            it[Keys.onboarded] = true
        }
    }

    suspend fun setName(value: String) = context.dataStore.edit { it[Keys.name] = value.trim() }
    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.key }
    suspend fun setDefaultFont(key: String) = context.dataStore.edit { it[Keys.font] = key }
    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) = context.dataStore.edit {
        it[Keys.reminderEnabled] = enabled
        it[Keys.reminderHour] = hour
        it[Keys.reminderMinute] = minute
    }
    suspend fun setAppLock(value: Boolean) = context.dataStore.edit { it[Keys.appLock] = value }
    suspend fun setPhotoQuality(value: PhotoQuality) = context.dataStore.edit { it[Keys.photoQuality] = value.key }
    suspend fun setStylusOnly(value: Boolean) = context.dataStore.edit { it[Keys.stylusOnly] = value }
    suspend fun setLastBackup(value: Long) = context.dataStore.edit { it[Keys.lastBackup] = value }
}
