package com.lembar.journal.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lembar.journal.data.DraftMedia
import com.lembar.journal.data.MediaKind
import com.lembar.journal.domain.Emotions
import com.lembar.journal.domain.Mood
import com.lembar.journal.ui.Fmt
import com.lembar.journal.ui.LocalAuthenticator
import com.lembar.journal.ui.components.Chip
import com.lembar.journal.ui.components.IconAction
import com.lembar.journal.ui.components.MoodFace
import com.lembar.journal.ui.components.PillButton
import com.lembar.journal.ui.components.PrimaryButton
import com.lembar.journal.ui.components.ProgressBar
import com.lembar.journal.ui.components.Tag
import com.lembar.journal.ui.components.TopBar
import com.lembar.journal.ui.lembarViewModel
import com.lembar.journal.ui.theme.JournalFonts
import com.lembar.journal.ui.theme.Lembar
import java.io.File

@Composable
fun EditorScreen(stylusOnly: Boolean, onExit: () -> Unit) {
    val vm = lembarViewModel { c, h -> EditorViewModel(c, h) }
    val s by vm.state.collectAsStateWithLifecycle()
    val auth = LocalAuthenticator.current
    var confirmDiscard by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(s.finished) { if (s.finished) onExit() }
    LaunchedEffect(s.message) {
        s.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(s.needsUnlock) {
        if (s.needsUnlock) {
            auth.authenticate("Buka jurnal", "Jurnal ini terkunci", onSuccess = vm::onUnlocked, onFailure = { onExit() })
        }
    }

    fun leave() {
        if (s.dirty) confirmDiscard = true else onExit()
    }

    BackHandler {
        when {
            s.isNew && s.step == EditorStep.Emotions -> vm.goTo(EditorStep.Mood)
            s.isNew && s.step == EditorStep.Write && s.fixedPrompt == null && s.mood != null -> vm.goTo(EditorStep.Emotions)
            else -> leave()
        }
    }

    Box(Modifier.fillMaxSize().background(Lembar.colors.background)) {
        if (s.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Lembar.colors.ink)
        } else {
            AnimatedContent(s.step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { step ->
                when (step) {
                    EditorStep.Mood -> MoodStep(
                        state = s,
                        onPick = vm::setMood,
                        onNext = { vm.goTo(EditorStep.Emotions) },
                        onSkip = { vm.setMood(null); vm.goTo(EditorStep.Write) },
                        onClose = ::leave,
                    )
                    EditorStep.Emotions -> EmotionStep(
                        state = s,
                        onToggle = vm::toggleEmotion,
                        onAdd = vm::addCustomEmotion,
                        onBack = { vm.goTo(EditorStep.Mood) },
                        onNext = { vm.goTo(EditorStep.Write) },
                        onClose = ::leave,
                    )
                    EditorStep.Write -> WriteStep(s, vm, stylusOnly, onClose = ::leave)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 72.dp))
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Tinggalkan tanpa menyimpan?") },
            text = { Text("Tulisan dan gambar yang belum disimpan akan hilang.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.discard() }) { Text("Buang") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Tetap menulis") } },
        )
    }

    s.reward?.let { RewardDialog(it, onDismiss = vm::dismissReward) }
    if (s.support) com.lembar.journal.ui.insight.SupportDialog(onDismiss = vm::dismissSupport)
}

@Composable
private fun StepHeader(step: Int, onBack: (() -> Unit)?, onClose: () -> Unit) {
    val c = Lembar.colors
    Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) c.ink else c.border),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconAction(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Kembali", onBack)
            } else {
                Spacer(Modifier.size(44.dp))
            }
            Spacer(Modifier.weight(1f))
            IconAction(Icons.Rounded.Close, "Tutup", onClose)
        }
    }
}

@Composable
private fun MoodStep(state: EditorState, onPick: (Mood) -> Unit, onNext: () -> Unit, onSkip: () -> Unit, onClose: () -> Unit) {
    val t = Lembar.type
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        StepHeader(0, null, onClose)
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            Text(Fmt.relativeDay(state.date), style = t.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("Bagaimana perasaanmu hari ini?", style = t.display)
            Spacer(Modifier.height(8.dp))
            Text("Tidak ada jawaban yang salah. Pilih yang paling dekat.", style = t.bodySmall)
            Spacer(Modifier.height(40.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Mood.entries.forEach { m ->
                    val selected = state.mood == m
                    val scale by animateFloatAsState(if (selected) 1.18f else if (state.mood == null) 1f else 0.9f, label = "mood")
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(m) }
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MoodFace(m, 56.dp, Modifier.scale(scale))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            m.label,
                            style = t.caption.copy(
                                color = if (selected) Lembar.colors.ink else Lembar.colors.inkMuted,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            PrimaryButton("lanjut", onNext, Modifier.fillMaxWidth(), enabled = state.mood != null)
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Langsung menulis", style = t.label.copy(color = Lembar.colors.inkMuted))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmotionStep(
    state: EditorState,
    onToggle: (String) -> Unit,
    onAdd: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val t = Lembar.type
    var adding by remember { mutableStateOf(false) }
    val labels = remember(state.mood, state.customEmotions) {
        Emotions.orderedFor(state.mood).map { it.label } + state.customEmotions
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        StepHeader(1, onBack, onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(12.dp))
            Text("Emosi apa yang kamu rasakan sekarang?", style = t.display)
            Spacer(Modifier.height(8.dp))
            Text("Pilih sebanyak yang kamu mau. Menamai perasaan dengan tepat membantu meredakannya.", style = t.bodySmall)
            Spacer(Modifier.height(24.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                labels.forEach { label -> Chip(label, label in state.emotions, { onToggle(label) }) }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Lembar.colors.border, RoundedCornerShape(8.dp))
                        .clickable { adding = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "Tambah emosi", tint = Lembar.colors.ink, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            PrimaryButton(if (state.emotions.isEmpty()) "lewati" else "lanjut", onNext, Modifier.fillMaxWidth())
        }
    }

    if (adding) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Emosi lain") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(24) },
                    singleLine = true,
                    placeholder = { Text("mis. rindu, gugup, haru") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = fieldColors(),
                )
            },
            confirmButton = { TextButton(onClick = { onAdd(text); adding = false }) { Text("Tambah") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Batal") } },
        )
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Lembar.colors.ink,
    unfocusedBorderColor = Lembar.colors.border,
    cursorColor = Lembar.colors.ink,
    focusedTextColor = Lembar.colors.ink,
    unfocusedTextColor = Lembar.colors.ink,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WriteStep(s: EditorState, vm: EditorViewModel, stylusOnly: Boolean, onClose: () -> Unit) {
    val c = Lembar.colors
    val t = Lembar.type
    val context = LocalContext.current
    val auth = LocalAuthenticator.current
    val font = JournalFonts.of(s.fontKey)
    var showFonts by remember { mutableStateOf(false) }
    var showSketch by remember { mutableStateOf(false) }
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isNotEmpty()) vm.addPhotos(uris)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = capturePath
        capturePath = null
        if (path != null) {
            val file = File(path)
            if (ok) vm.addPhotos(listOf(Uri.fromFile(file)), deleteAfter = file) else file.delete()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        TopBar(
            title = Fmt.dayMonth(s.date),
            onBack = onClose,
            backIcon = Icons.Rounded.Close,
        ) {
            PillButton(if (s.saving) "Menyimpan" else "Simpan", onClick = vm::save, enabled = s.canSave)
            Spacer(Modifier.width(10.dp))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            // Mood + emotions summary; tap to revisit the check-in.
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.goTo(EditorStep.Mood) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoodFace(s.mood, 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(s.mood?.label ?: "Pilih mood", style = t.label.copy(color = if (s.mood == null) c.inkMuted else c.ink))
            }
            if (s.emotions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.emotions.forEach { Tag(it) }
                }
            }
            Spacer(Modifier.height(16.dp))

            BasicTextField(
                value = s.title,
                onValueChange = vm::setTitle,
                textStyle = font.titleStyle(c.ink),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (s.title.isEmpty()) Text("Judul (opsional)", style = font.titleStyle(c.inkFaint))
                    inner()
                },
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = s.body,
                onValueChange = vm::setBody,
                textStyle = font.bodyStyle(c.ink),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                decorationBox = { inner ->
                    if (s.body.isEmpty()) Text(s.prompt, style = font.bodyStyle(c.inkFaint))
                    inner()
                },
            )
            if (s.body.isEmpty()) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.nextPrompt() }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Refresh, null, tint = c.inkMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pancingan lain", style = t.caption)
                }
            }

            if (s.media.isNotEmpty() || s.importing > 0) {
                Spacer(Modifier.height(16.dp))
                EditableMedia(s.media, s.importing, vm::removeMedia)
            }
            Spacer(Modifier.height(24.dp))
        }

        // Bottom toolbar (design 2 note toolbar)
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.background)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Icons.Outlined.PhotoCamera, "Kamera", {
                val file = vm.newCaptureFile()
                capturePath = file.absolutePath
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                runCatching { takePhoto.launch(uri) }.onFailure { capturePath = null }
            })
            IconAction(Icons.Outlined.PhotoLibrary, "Galeri", {
                pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            IconAction(Icons.Outlined.Draw, "Sketsa S Pen", { showSketch = true })
            IconAction(Icons.Outlined.TextFields, "Font", { showFonts = true })
            IconAction(
                if (s.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                if (s.locked) "Terkunci" else "Kunci",
                {
                    if (s.locked) {
                        vm.setLocked(false)
                    } else if (auth.isAvailable()) {
                        vm.setLocked(true)
                    } else {
                        android.widget.Toast.makeText(context, "Aktifkan kunci layar HP dulu untuk mengunci jurnal.", android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                tint = if (s.locked) c.onPrimary else c.ink,
                background = if (s.locked) c.primary else Color.Transparent,
            )
            Spacer(Modifier.weight(1f))
            Text("${s.wordCount} kata", style = t.caption, modifier = Modifier.padding(end = 8.dp))
        }
    }

    if (showFonts) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showFonts = false }, sheetState = sheet, containerColor = c.surface) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
                Text("Font jurnal ini", style = t.headline)
                Spacer(Modifier.height(4.dp))
                Text("Setiap jurnal bisa punya font sendiri.", style = t.bodySmall)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(JournalFonts.all, key = { it.key }) { f ->
                        val active = f.key == s.fontKey
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) c.surfaceAlt else Color.Transparent)
                                .clickable { vm.setFont(f.key) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Hari ini aku belajar sesuatu.", style = f.bodyStyle(c.ink, base = 18.sp))
                                Text("${f.label} · ${f.mood}", style = t.caption)
                            }
                            if (active) Icon(Icons.Rounded.Check, null, tint = c.ink)
                        }
                    }
                }
                TextButton(onClick = vm::makeDefaultFont, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Jadikan font bawaan untuk jurnal baru", style = t.label.copy(color = c.inkMuted))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showSketch) {
        SketchPad(
            stylusOnlyDefault = stylusOnly,
            onDismiss = { showSketch = false },
            onSave = { bmp -> showSketch = false; vm.addSketch(bmp) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditableMedia(media: List<DraftMedia>, importing: Int, onRemove: (DraftMedia) -> Unit) {
    val c = Lembar.colors
    val cells: List<DraftMedia?> = media + List(importing) { null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      cells.chunked(3).forEach { row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { item ->
            if (item == null) {
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(c.surfaceAlt),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(22.dp), color = c.ink, strokeWidth = 2.dp) }
                return@forEach
            }
            val (file, sketch) = when (item) {
                is DraftMedia.Existing -> item.attachment.file to (item.attachment.kind == MediaKind.Sketch)
                is DraftMedia.Fresh -> item.image.file to (item.kind == MediaKind.Sketch)
            }
            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(if (sketch) c.paper else c.surfaceAlt),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onRemove(item) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Hapus", tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
        // Keep the last row's cells the same width as full rows.
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
      }
      }
    }
}

@Composable
private fun RewardDialog(reward: Reward, onDismiss: () -> Unit) {
    val c = Lembar.colors
    val t = Lembar.type
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (reward.leveledUp) "Naik ke level ${reward.after.level}" else "Tersimpan", style = t.headline, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                if (reward.leveledUp) reward.after.title else "Satu halaman lagi untuk ceritamu.",
                style = t.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Text("+${reward.gained} XP", style = t.display.copy(color = c.accent))
            Spacer(Modifier.height(16.dp))
            reward.items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(item.label, style = t.bodySmall.copy(color = c.ink), modifier = Modifier.weight(1f))
                    Text("+${item.xp}", style = t.label)
                }
            }
            Spacer(Modifier.height(16.dp))
            ProgressBar(reward.after.progress)
            Spacer(Modifier.height(6.dp))
            Text(
                "Level ${reward.after.level} · ${reward.after.xpIntoLevel}/${reward.after.xpForNext} XP",
                style = t.caption,
            )
            if (reward.streak >= 2) {
                Spacer(Modifier.height(10.dp))
                Text("${reward.streak} hari berturut-turut. Pelan-pelan, yang penting rutin.", style = t.caption, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("lanjut", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
