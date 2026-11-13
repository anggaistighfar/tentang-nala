package com.lembar.journal.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lembar.journal.data.PhotoQuality
import com.lembar.journal.data.Settings
import com.lembar.journal.data.ThemeMode
import com.lembar.journal.ui.AppViewModel
import com.lembar.journal.ui.Fmt
import com.lembar.journal.ui.LocalAuthenticator
import com.lembar.journal.ui.components.LembarSwitch
import com.lembar.journal.ui.components.SettingRow
import com.lembar.journal.ui.editor.fieldColors
import com.lembar.journal.ui.theme.JournalFonts
import com.lembar.journal.ui.theme.Lembar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, settings: Settings, contentPadding: PaddingValues) {
    val c = Lembar.colors
    val t = Lembar.type
    val context = LocalContext.current
    val auth = LocalAuthenticator.current
    val storage by vm.storageBytes.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entries.size) { vm.refreshStorage() }

    // Re-check the exact-alarm permission whenever the user comes back from system settings.
    var exactAllowed by remember { mutableStateOf(vm.canExactAlarm()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        val now = vm.canExactAlarm()
        if (now != exactAllowed) {
            exactAllowed = now
            vm.rescheduleReminder()
        }
        onPauseOrDispose {}
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setReminder(granted, settings.reminderHour, settings.reminderMinute)
        if (!granted) {
            android.widget.Toast.makeText(context, "Izinkan notifikasi Tentang Nala di Pengaturan HP agar pengingat muncul.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("Pengaturan", style = t.display)
            Spacer(Modifier.height(8.dp))
        }

        Group("Profil") {
            SettingRow(Icons.Outlined.Person, "Nama", settings.name.ifBlank { "Belum diisi" }, onClick = { dialog = "name" })
        }
        Group("Tampilan") {
            SettingRow(Icons.Outlined.DarkMode, "Tema", settings.themeMode.label, onClick = { dialog = "theme" })
            SettingRow(Icons.Outlined.TextFields, "Font bawaan jurnal", JournalFonts.of(settings.defaultFont).label, onClick = { dialog = "font" })
        }
        Group("Pengingat") {
            SettingRow(
                Icons.Outlined.Notifications, "Pengingat harian",
                if (settings.reminderEnabled) "Tidak muncul kalau kamu sudah menulis hari itu" else "Mati",
                trailing = {
                    LembarSwitch(settings.reminderEnabled) { on ->
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !vm.canNotify()) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.setReminder(on, settings.reminderHour, settings.reminderMinute)
                        }
                    }
                },
            )
            if (settings.reminderEnabled) {
                SettingRow(Icons.Outlined.Schedule, "Jam pengingat", Fmt.time(settings.reminderHour, settings.reminderMinute), onClick = { dialog = "time" })
                if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingRow(
                        Icons.Outlined.Notifications, "Pengingat tepat waktu",
                        "Saat ini bisa meleset hingga 10 menit. Ketuk untuk mengizinkan \"Alarm & pengingat\".",
                        onClick = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(android.net.Uri.parse("package:${context.packageName}")),
                            )
                        },
                    )
                }
            }
        }
        Group("Privasi") {
            SettingRow(
                Icons.Outlined.Fingerprint, "Kunci aplikasi",
                "Minta sidik jari / PIN setiap membuka Tentang Nala",
                trailing = {
                    LembarSwitch(settings.appLock) { on ->
                        if (!auth.isAvailable()) {
                            android.widget.Toast.makeText(context, "Aktifkan kunci layar HP dulu.", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            auth.authenticate(
                                "Konfirmasi",
                                if (on) "Aktifkan kunci aplikasi" else "Matikan kunci aplikasi",
                                onSuccess = { vm.setAppLock(on) },
                                force = true,
                            )
                        }
                    }
                },
            )
            Text(
                "Jurnal yang dikunci dienkripsi (AES-256) dengan kunci yang tersimpan di chip keamanan HP. Foto tetap di penyimpanan privat aplikasi, tidak muncul di Galeri.",
                style = t.caption,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Group("S Pen") {
            SettingRow(
                Icons.Outlined.Draw, "Abaikan jari saat pakai S Pen",
                "Telapak tangan tidak ikut menggambar di sketsa",
                trailing = { LembarSwitch(settings.stylusOnly) { vm.setStylusOnly(it) } },
            )
        }
        Group("Penyimpanan") {
            SettingRow(Icons.Outlined.HighQuality, "Kualitas foto", "${settings.photoQuality.label} · maks ${settings.photoQuality.maxEdge}px", onClick = { dialog = "quality" })
            SettingRow(Icons.Outlined.Storage, "Terpakai di HP", "${Fmt.bytes(storage)} untuk ${entries.size} catatan")
        }
        Group("Tentang") {
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
            }
            SettingRow(Icons.Outlined.Info, "Tentang Nala $version", "Semua catatan hanya tersimpan di HP ini. Tidak ada akun, tidak ada server.")
        }
    }

    when (dialog) {
        "name" -> {
            var text by remember { mutableStateOf(settings.name) }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Nama panggilan") },
                text = { OutlinedTextField(text, { text = it.take(30) }, singleLine = true, colors = fieldColors()) },
                confirmButton = { TextButton(onClick = { vm.setName(text); dialog = null }) { Text("Simpan") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Batal") } },
            )
        }
        "theme" -> ChoiceDialog("Tema", ThemeMode.entries, settings.themeMode, { it.label }, { vm.setTheme(it); dialog = null }) { dialog = null }
        "font" -> ChoiceDialog("Font bawaan", JournalFonts.all, JournalFonts.of(settings.defaultFont), { "${it.label} · ${it.mood}" }, { vm.setDefaultFont(it.key); dialog = null }) { dialog = null }
        "quality" -> ChoiceDialog(
            "Kualitas foto", PhotoQuality.entries, settings.photoQuality,
            {
                when (it) {
                    PhotoQuality.Hemat -> "Hemat · ±150 KB per foto"
                    PhotoQuality.Seimbang -> "Seimbang · ±250 KB per foto"
                    PhotoQuality.Tajam -> "Tajam · ±450 KB per foto"
                }
            },
            { vm.setPhotoQuality(it); dialog = null },
        ) { dialog = null }
        "time" -> {
            val state = rememberTimePickerState(settings.reminderHour, settings.reminderMinute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Jam pengingat") },
                text = {
                    TimePicker(
                        state,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = c.surfaceAlt,
                            selectorColor = c.primary,
                            timeSelectorSelectedContainerColor = c.primary,
                            timeSelectorSelectedContentColor = c.onPrimary,
                            timeSelectorUnselectedContainerColor = c.surfaceAlt,
                        ),
                    )
                },
                confirmButton = { TextButton(onClick = { vm.setReminder(true, state.hour, state.minute); dialog = null }) { Text("Simpan") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Batal") } },
            )
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val c = Lembar.colors
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = Lembar.type.caption, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)) { content() }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Lembar.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(o) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(o == selected, { onPick(o) }, colors = RadioButtonDefaults.colors(selectedColor = c.primary))
                        Text(label(o), style = Lembar.type.body)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
