package com.lembar.journal.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import com.lembar.journal.R
import com.lembar.journal.ui.Fmt
import com.lembar.journal.ui.components.Chip
import com.lembar.journal.ui.components.LembarSwitch
import com.lembar.journal.ui.components.MoodFace
import com.lembar.journal.ui.components.PrimaryButton
import com.lembar.journal.domain.Mood
import com.lembar.journal.ui.theme.Lembar

private val presetTimes = listOf(7 to 0, 12 to 30, 20 to 0, 21 to 0, 22 to 0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onFinish: (name: String, hour: Int, minute: Int, reminder: Boolean) -> Unit) {
    val c = Lembar.colors
    val t = Lembar.type
    var page by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableIntStateOf(3) }
    var reminder by rememberSaveable { mutableStateOf(true) }

    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val (h, m) = presetTimes[time]
        onFinish(name, h, m, granted)
    }

    fun finish() {
        val (h, m) = presetTimes[time]
        if (reminder && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onFinish(name, h, m, reminder)
        }
    }

    BackHandler(enabled = page > 0) { page-- }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(painterResource(R.drawable.ic_stat_lembar), null, tint = c.accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.app_name), style = t.title)
        }
        AnimatedContent(page, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onb") { p ->
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
                when (p) {
                    0 -> {
                        Spacer(Modifier.weight(1f))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = c.inkFaint)) { append("Tulis harimu, ") }
                                withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append("kenali rasamu") }
                                withStyle(SpanStyle(color = c.inkFaint)) { append(", dan simpan ") }
                                withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append("momen kecil") }
                                withStyle(SpanStyle(color = c.inkFaint)) { append(" yang berarti.") }
                            },
                            style = t.display.copy(fontSize = 44.sp, lineHeight = 50.sp),
                        )
                        Spacer(Modifier.height(28.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Mood.entries.forEach { MoodFace(it, 34.dp) }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Semua tersimpan di HP ini saja. Tanpa akun, tanpa internet.", style = t.bodySmall)
                        Spacer(Modifier.height(36.dp))
                    }
                    1 -> {
                        Spacer(Modifier.height(32.dp))
                        Text("Kami panggil kamu siapa?", style = t.display)
                        Spacer(Modifier.height(8.dp))
                        Text("Boleh nama panggilan. Bisa diganti nanti.", style = t.bodySmall)
                        Spacer(Modifier.height(32.dp))
                        BasicTextField(
                            name, { name = it.take(30) },
                            singleLine = true,
                            textStyle = t.headline,
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { page = 2 }),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Column {
                                    if (name.isEmpty()) Text("Nama kamu", style = t.headline.copy(color = c.inkFaint))
                                    inner()
                                    Spacer(Modifier.height(10.dp))
                                    Spacer(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                                }
                            },
                        )
                    }
                    else -> {
                        Spacer(Modifier.height(32.dp))
                        Text("Kapan waktu terbaik untuk menulis?", style = t.display)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Kebiasaan lebih mudah terbentuk kalau menempel pada waktu yang sama setiap hari. Pengingat tidak muncul kalau kamu sudah menulis.",
                            style = t.bodySmall,
                        )
                        Spacer(Modifier.height(28.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            presetTimes.forEachIndexed { i, (h, m) ->
                                Chip(Fmt.time(h, m), selected = reminder && time == i, onClick = { time = i; reminder = true })
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ingatkan saya setiap hari", style = t.label, modifier = Modifier.weight(1f))
                            LembarSwitch(reminder) { reminder = it }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Jam bisa diatur bebas nanti di Pengaturan.", style = t.caption)
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            PrimaryButton(
                when (page) { 0 -> "Mulai"; 1 -> "Lanjut"; else -> "Selesai" },
                onClick = { if (page < 2) page++ else finish() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.width(1.dp))
        }
    }
}
