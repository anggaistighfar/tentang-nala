package com.lembar.journal.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.lembar.journal.AppContainer
import com.lembar.journal.data.Attachment
import com.lembar.journal.data.JournalEntry
import com.lembar.journal.ui.Fmt
import com.lembar.journal.ui.LocalAuthenticator
import com.lembar.journal.ui.components.IconAction
import com.lembar.journal.ui.components.MediaImage
import com.lembar.journal.ui.components.MoodFace
import com.lembar.journal.data.EntryContent
import com.lembar.journal.domain.CheckStatus
import com.lembar.journal.domain.CreativeReview
import com.lembar.journal.domain.CreativeReviewer
import com.lembar.journal.domain.EntryReflection
import com.lembar.journal.domain.InkCodec
import com.lembar.journal.domain.InkStroke
import com.lembar.journal.ui.editor.BodyAlign
import com.lembar.journal.ui.editor.InkCanvas
import com.lembar.journal.ui.editor.formatted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lembar.journal.domain.InsightEngine
import com.lembar.journal.ui.components.PrimaryButton
import com.lembar.journal.ui.components.SecondaryButton
import com.lembar.journal.ui.components.Tag
import com.lembar.journal.ui.insight.SupportCard
import com.lembar.journal.ui.insight.toInsight
import com.lembar.journal.ui.components.TopBar
import com.lembar.journal.ui.lembarViewModel
import com.lembar.journal.ui.theme.JournalFonts
import com.lembar.journal.ui.theme.Lembar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EntryViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val id: Long = handle.get<String>("id")?.toLongOrNull() ?: -1L
    val entry: StateFlow<JournalEntry?> = c.journal.observe(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val unlocked = c.session.unlocked

    private val _text = MutableStateFlow<EntryContent?>(null)
    val text: StateFlow<EntryContent?> = _text
    private val _ink = MutableStateFlow<List<InkStroke>>(emptyList())
    val ink: StateFlow<List<InkStroke>> = _ink

    private suspend fun reload() {
        val content = c.journal.readContent(id)
        _ink.value = withContext(Dispatchers.Default) { InkCodec.decode(content.ink) }
        _text.value = content
    }

    fun loadText() = viewModelScope.launch { reload() }
    fun delete(done: () -> Unit) = viewModelScope.launch { c.journal.delete(id); done() }
    fun setLocked(v: Boolean) = viewModelScope.launch { c.journal.setLocked(id, v); reload() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, onPrompt: (String) -> Unit) {
    val vm = lembarViewModel { c, h -> EntryViewModel(c, h) }
    val entry by vm.entry.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val ink by vm.ink.collectAsStateWithLifecycle()
    val auth = LocalAuthenticator.current
    val context = LocalContext.current
    val c = Lembar.colors
    val t = Lembar.type
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Attachment?>(null) }

    val e = entry ?: run {
        Box(Modifier.fillMaxSize().background(c.background))
        return
    }
    val canRead = !e.locked || unlocked

    fun unlock() = auth.authenticate("Buka jurnal", Fmt.long(e.date), onSuccess = { vm.loadText() })

    // Ask for fingerprint/PIN once when a locked entry is opened, not right after
    // the user locks it themselves.
    LaunchedEffect(e.id) { if (!canRead) unlock() }
    LaunchedEffect(e.id, e.updatedAt, canRead) { if (canRead) vm.loadText() }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        TopBar(Fmt.short(e.date), onBack) {
            Box {
                IconAction(Icons.Rounded.MoreVert, "Menu", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = c.surface) {
                    DropdownMenuItem(
                        text = { Text("Ubah") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = {
                            menu = false
                            if (canRead) onEdit(e.id) else auth.authenticate("Buka jurnal", "", onSuccess = { onEdit(e.id) })
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (e.locked) "Buka kunci" else "Kunci jurnal") },
                        leadingIcon = { Icon(if (e.locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null) },
                        onClick = {
                            menu = false
                            when {
                                e.locked -> auth.authenticate("Buka kunci jurnal", "", onSuccess = { vm.setLocked(false) })
                                auth.isAvailable() -> vm.setLocked(true)
                                else -> android.widget.Toast.makeText(context, "Aktifkan kunci layar HP dulu.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus", color = c.danger) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = c.danger) },
                        onClick = { menu = false; confirmDelete = true },
                    )
                }
            }
        }

        if (!canRead) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.Lock, null, tint = c.ink, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(16.dp))
                Text("Jurnal ini terkunci", style = t.headline)
                Spacer(Modifier.height(6.dp))
                Text("Isi tulisannya dienkripsi di HP ini.", style = t.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Buka", ::unlock, icon = Icons.Outlined.Fingerprint)
            }
            return@Column
        }

        val font = JournalFonts.of(e.fontKey)
        val title = text?.title.orEmpty()
        val body = text?.body.orEmpty()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding(),
        ) {
            if (title.isNotBlank()) {
                Text(title, style = font.titleStyle(c.ink))
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.mood != null) {
                    MoodFace(e.mood, 26.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(e.mood.label, style = t.label)
                    Text("  ·  ", style = t.bodySmall)
                }
                Text("${Fmt.day(e.date.dayOfWeek)}, ${Fmt.time(e.createdAt)}", style = t.bodySmall)
                if (e.locked) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Lock, "Terkunci", tint = c.inkMuted, modifier = Modifier.size(14.dp))
                }
            }
            if (e.emotions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.emotions.forEach { Tag(it) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Spacer(Modifier.height(18.dp))

            // Same width, font and formatting as the editor, so handwriting lands exactly where it was written.
            Column {
                ReadPage(body, ink, font, BodyAlign.of(e.align))
                Column {
                    e.attachments.forEach { a ->
                        Spacer(Modifier.height(14.dp))
                        val ratio = if (a.width > 0 && a.height > 0) (a.width.toFloat() / a.height).coerceIn(0.6f, 1.8f) else 1f
                        MediaImage(
                            a,
                            Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(14.dp)).clickable { viewing = a },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            val reflection = remember(e.id, e.updatedAt, title, body) {
                InsightEngine.reflect(e.toInsight("$title\n$body"))
            }
            if (e.type.creative && !reflection.crisis) {
                val review = remember(e.id, e.updatedAt, title, body) { CreativeReviewer.review(e.type, title, body) }
                if (review != null) ReviewCard(review)
            } else {
                ReflectionCard(reflection, onPrompt)
            }
            Spacer(Modifier.height(16.dp))
            if (e.updatedAt - e.createdAt > 60_000) {
                Text("Terakhir diubah ${Fmt.short(java.time.Instant.ofEpochMilli(e.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())}, ${Fmt.time(e.updatedAt)}", style = t.caption)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus jurnal ini?") },
            text = { Text("Tulisan dan gambarnya akan dihapus dari HP ini dan tidak bisa dikembalikan. XP hari itu ikut dihitung ulang.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Hapus", color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }

    viewing?.let { ImageViewer(it) { viewing = null } }
}

@Composable
private fun ReadPage(body: String, ink: List<InkStroke>, font: com.lembar.journal.ui.theme.JournalFont, align: BodyAlign) {
    val c = Lembar.colors
    val density = androidx.compose.ui.platform.LocalDensity.current
    var width by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val inkBottom = with(density) { ((ink.maxOfOrNull { it.bottom } ?: 0f) * width).toDp() }
    val base = font.bodyStyle(c.ink)
    val text = remember(body, base, c.accent) { formatted(body, base, c.accent) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (ink.isEmpty()) 0.dp else inkBottom + 12.dp)
            .onSizeChanged { width = it.width },
    ) {
        if (body.isNotBlank()) {
            SelectionContainer { Text(text, style = base.copy(textAlign = align.align), modifier = Modifier.fillMaxWidth()) }
        } else if (ink.isEmpty()) {
            Text("Tidak ada tulisan.", style = Lembar.type.bodySmall)
        }
        if (ink.isNotEmpty()) {
            InkCanvas(ink, null, Modifier.matchParentSize())
        }
    }
}

/** "Penilaian Nala" for ideas, stories, pantun and poems. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(r: CreativeReview) {
    val c = Lembar.colors
    val t = Lembar.type
    val good = Color(0xFF4F9A5B)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Psychology, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Penilaian Nala · ${r.type.label}", style = t.label)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.score != null) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val w = 5.dp.toPx()
                        val inset = w / 2
                        val sz = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                        drawArc(c.surfaceAlt, 0f, 360f, false, Offset(inset, inset), sz, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                        drawArc(
                            c.accent, -90f, 360f * r.score / 100f, false, Offset(inset, inset), sz,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        )
                    }
                    Text("${r.score}", style = t.title)
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(r.verdict, style = t.title)
                if (r.score != null) Text("Nilai ${r.score} dari 100", style = t.caption)
            }
        }
        Spacer(Modifier.height(14.dp))
        r.checks.forEach { ch ->
            Row(Modifier.padding(vertical = 5.dp)) {
                val (sym, col) = when (ch.status) {
                    CheckStatus.Good -> "✓" to good
                    CheckStatus.Warn -> "!" to c.accent
                    CheckStatus.Info -> "i" to c.inkMuted
                }
                Box(Modifier.size(22.dp).clip(CircleShape).background(col.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(sym, style = t.caption.copy(color = col, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(ch.label, style = t.label)
                    Text(ch.detail, style = t.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Spacer(Modifier.height(12.dp))
        Text("Catatan psikologis", style = t.label)
        Spacer(Modifier.height(4.dp))
        Text(r.psych, style = t.body)
        if (r.toneWords.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("Suasana: ${r.tone}")
                r.toneWords.forEach { Tag(it) }
            }
        }
        if (r.tips.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Saran", style = t.label)
            r.tips.forEach { tip ->
                Row(Modifier.padding(top = 4.dp)) {
                    Text("•  ", style = t.bodySmall.copy(color = c.accent))
                    Text(tip, style = t.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Penilaian otomatis berdasarkan bentuk dan kata. Rasa dan maknanya tetap milikmu.", style = t.caption)
    }
}

/** "Catatan dari Nala": a short, gentle reflection on this entry. */
@Composable
private fun ReflectionCard(r: EntryReflection, onPrompt: (String) -> Unit) {
    val c = Lembar.colors
    val t = Lembar.type
    if (r.crisis) {
        SupportCard(singleEntry = true)
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Psychology, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Catatan dari Nala", style = t.label)
        }
        Spacer(Modifier.height(10.dp))
        Text(r.validation, style = t.body)
        r.distortion?.let { d ->
            Spacer(Modifier.height(14.dp))
            Text(d.distortion.name, style = t.label)
            Spacer(Modifier.height(4.dp))
            Text(
                "“${d.example}”",
                style = t.bodySmall.copy(color = c.ink),
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.surfaceAlt).padding(10.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text("${d.distortion.explain} ${d.distortion.reframe}", style = t.bodySmall)
        }
        r.suggestion?.let { s ->
            Spacer(Modifier.height(14.dp))
            Text("Saran: ${s.title}", style = t.label)
            Spacer(Modifier.height(4.dp))
            Text(s.steps.first(), style = t.bodySmall)
            if (s.prompt != null) {
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Coba tulis sekarang", { onPrompt(s.prompt) }, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Refleksi otomatis, bukan diagnosis.", style = t.caption)
    }
}

/** Full-screen pinch-to-zoom viewer. */
@Composable
fun ImageViewer(attachment: Attachment, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = attachment.file,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
                    .transformable(state)
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
            )
            IconAction(
                Icons.Rounded.Close, "Tutup", onDismiss,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
                background = Color.Black.copy(alpha = 0.4f),
            )
        }
    }
}
