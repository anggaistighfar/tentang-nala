package com.lembar.journal.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lembar.journal.AppContainer
import com.lembar.journal.data.DraftMedia
import com.lembar.journal.data.EntryDraft
import com.lembar.journal.data.JournalRepository
import com.lembar.journal.data.MediaKind
import com.lembar.journal.domain.LevelInfo
import com.lembar.journal.domain.CreativeReviewer
import com.lembar.journal.domain.EntryType
import com.lembar.journal.domain.InkCodec
import com.lembar.journal.domain.InkStroke
import com.lembar.journal.domain.Lexicon
import com.lembar.journal.domain.Markup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.lembar.journal.domain.Mood
import com.lembar.journal.domain.Prompts
import com.lembar.journal.domain.XpItem
import com.lembar.journal.domain.XpRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

enum class EditorStep { Mood, Emotions, Write }

data class Reward(
    val gained: Int,
    val items: List<XpItem>,
    val before: LevelInfo,
    val after: LevelInfo,
    val streak: Int,
) {
    val leveledUp: Boolean get() = after.level > before.level
}

data class EditorState(
    val loading: Boolean = true,
    val needsUnlock: Boolean = false,
    val isNew: Boolean = true,
    val step: EditorStep = EditorStep.Write,
    val date: LocalDate = LocalDate.now(),
    val mood: Mood? = null,
    val emotions: List<String> = emptyList(),
    val customEmotions: List<String> = emptyList(),
    val title: String = "",
    /** Body text with cursor and IME composition; formatting lives in the text as Markup. */
    val bodyValue: TextFieldValue = TextFieldValue(""),
    val align: BodyAlign = BodyAlign.Start,
    val ink: List<InkStroke> = emptyList(),
    val penMode: Boolean = false,
    val penInk: Boolean = true,
    val type: EntryType = EntryType.Journal,
    val pen: PenSettings = PenSettings(),
    val canUndoInk: Boolean = false,
    val canRedoInk: Boolean = false,
    /** Body before the last "Rapikan", so the snackbar can undo it. */
    val tidyUndo: TextFieldValue? = null,
    val fontKey: String = "lora",
    val locked: Boolean = false,
    val media: List<DraftMedia> = emptyList(),
    val importing: Int = 0,
    val fixedPrompt: String? = null,
    val promptIndex: Int = 0,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val reward: Reward? = null,
    /** Entry mentioned suicide/self-harm: show support resources before anything else. */
    val support: Boolean = false,
    val finished: Boolean = false,
    val message: String? = null,
) {
    val body: String get() = bodyValue.text
    val lineKind: Markup.Kind get() = Markup.kindAt(Markup.Doc(body, bodyValue.selection.start, bodyValue.selection.end))
    val prompt: String
        get() = fixedPrompt
            ?: if (type.creative) type.placeholder
            else Prompts.forMood(mood).let { it[promptIndex % it.size] }
    val liveHint: String? get() = CreativeReviewer.liveHint(type, body)
    val canSave: Boolean
        get() = !saving && importing == 0 && (title.isNotBlank() || body.isNotBlank() || media.isNotEmpty() || ink.isNotEmpty() || mood != null)
    val wordCount: Int get() = JournalRepository.countWords(title) + JournalRepository.countWords(Markup.plain(body))
    val mediaRoom: Int get() = MAX_MEDIA - media.size - importing

    companion object {
        const val MAX_MEDIA = 10
    }
}

class EditorViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {

    private val entryId: Long = handle.get<String>("entryId")?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
    private val startDate: LocalDate = handle.get<String>("date")?.toLongOrNull()?.takeIf { it >= 0 }
        ?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
    private val prompt: String? = handle.get<String>("prompt")?.takeIf { it.isNotBlank() }
    private val checkin: Boolean = handle.get<String>("checkin")?.toBoolean() ?: true
    private val startType: EntryType = EntryType.of(handle.get<String>("type"))

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    /**
     * The body field's value, held in Compose snapshot state so the text field sees
     * every change (including auto-inserted "2. ") in the same frame. A Flow would
     * lag one frame, and fast typing would overwrite the correction.
     */
    var bodyField by mutableStateOf(TextFieldValue(""))
        private set

    private fun putBody(v: TextFieldValue, dirty: Boolean = true) {
        bodyField = v
        _state.update { it.copy(bodyValue = v, dirty = it.dirty || dirty) }
    }

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val settings = c.settings.current()
        if (entryId <= 0) {
            _state.value = EditorState(
                loading = false,
                isNew = true,
                step = if (checkin && startType == EntryType.Journal) EditorStep.Mood else EditorStep.Write,
                date = startDate,
                type = startType,
                penInk = settings.penInk,
                fontKey = settings.defaultFont,
                fixedPrompt = prompt,
                promptIndex = (startDate.dayOfYear % 7),
            )
            return
        }
        val entry = c.journal.get(entryId) ?: run {
            _state.update { it.copy(loading = false, finished = true) }
            return
        }
        val known = entry.emotions.toSet()
        _state.value = EditorState(
            loading = true,
            isNew = false,
            step = EditorStep.Write,
            date = entry.date,
            mood = entry.mood,
            emotions = entry.emotions,
            customEmotions = known.filter { label -> com.lembar.journal.domain.Emotions.all.none { it.label == label } },
            title = entry.title,
            type = entry.type,
            penInk = settings.penInk,
            align = BodyAlign.of(entry.align),
            fontKey = entry.fontKey,
            locked = entry.locked,
            media = entry.attachments.map { DraftMedia.Existing(it) },
        )
        if (entry.locked && !c.session.unlocked.value) {
            _state.update { it.copy(needsUnlock = true) }
        } else {
            loadText()
        }
    }

    fun onUnlocked() {
        viewModelScope.launch { loadText() }
    }

    private suspend fun loadText() {
        val content = c.journal.readContent(entryId)
        val ink = InkCodec.decode(content.ink)
        bodyField = TextFieldValue(content.body, TextRange(content.body.length))
        _state.update {
            it.copy(
                loading = false, needsUnlock = false,
                title = content.title,
                bodyValue = bodyField,
                ink = ink,
            )
        }
    }

    // ---- Body text & formatting ------------------------------------------------------

    private fun TextFieldValue.doc() = Markup.Doc(text, selection.start, selection.end)
    private fun Markup.Doc.value() = TextFieldValue(text, TextRange(selStart, selEnd))

    fun setBody(new: TextFieldValue) {
        val old = bodyField
        if (new.text == old.text) {
            // Cursor moved or IME composition changed: only keep the cursor out of hidden markers.
            val snapped = Markup.snap(new.doc())
            val v = if (snapped.selStart == new.selection.start) new else new.copy(selection = TextRange(snapped.selStart))
            putBody(v, dirty = false)
            return
        }
        val fixed = Markup.afterEdit(old.doc(), new.doc())
        val v = if (fixed.text == new.text && fixed.selStart == new.selection.start && fixed.selEnd == new.selection.end) new else fixed.value()
        putBody(v)
        if (_state.value.tidyUndo != null) _state.update { it.copy(tidyUndo = null) }
    }

    fun toggleLine(kind: Markup.Kind) = putBody(Markup.toggle(bodyField.doc(), kind).value())

    fun cycleAlign() = edit { it.copy(align = it.align.next()) }

    fun tidy() {
        val before = bodyField
        val text = Markup.tidy(before.text)
        if (text == before.text) {
            _state.update { it.copy(message = "Tulisanmu sudah rapi") }
            return
        }
        putBody(TextFieldValue(text, TextRange(text.length)))
        _state.update { it.copy(tidyUndo = before) }
    }

    fun undoTidy() {
        val prev = _state.value.tidyUndo ?: return
        putBody(prev)
        _state.update { it.copy(tidyUndo = null) }
    }

    fun clearTidyUndo() = _state.update { it.copy(tidyUndo = null) }

    // ---- Handwriting on the page ----------------------------------------------------

    private val inkUndo = ArrayDeque<List<InkStroke>>()
    private val inkRedo = ArrayDeque<List<InkStroke>>()

    private fun commitInk(next: List<InkStroke>) {
        inkUndo.addLast(_state.value.ink)
        if (inkUndo.size > 100) inkUndo.removeFirst()
        inkRedo.clear()
        edit { it.copy(ink = next, canUndoInk = true, canRedoInk = false) }
    }

    fun addStroke(stroke: InkStroke) {
        if (_state.value.ink.sumOf { it.points.size } + stroke.points.size > InkCodec.MAX_POINTS) {
            _state.update { it.copy(message = "Halaman ini sudah penuh tulisan tangan. Lanjutkan di jurnal baru.") }
            return
        }
        commitInk(_state.value.ink + stroke)
    }

    fun eraseStrokes(gone: List<InkStroke>) = commitInk(_state.value.ink.filterNot { it in gone })

    fun undoInk() {
        val prev = inkUndo.removeLastOrNull() ?: return
        inkRedo.addLast(_state.value.ink)
        edit { it.copy(ink = prev, canUndoInk = inkUndo.isNotEmpty(), canRedoInk = true) }
    }

    fun redoInk() {
        val next = inkRedo.removeLastOrNull() ?: return
        inkUndo.addLast(_state.value.ink)
        edit { it.copy(ink = next, canUndoInk = true, canRedoInk = inkRedo.isNotEmpty()) }
    }

    fun setPenMode(on: Boolean) = _state.update { it.copy(penMode = on) }
    fun setPenInk(on: Boolean) {
        _state.update { it.copy(penInk = on) }
        viewModelScope.launch { c.settings.setPenInk(on) }
    }

    fun setType(t: EntryType) = edit { it.copy(type = t) }

    /** Inserts the type's outline (headings) at the end of the body. */
    fun insertOutline() {
        val outline = _state.value.type.outline ?: return
        val cur = bodyField.text.trimEnd()
        val text = if (cur.isEmpty()) outline else "$cur\n\n$outline"
        val firstGap = text.indexOf("\n", text.indexOf(outline) ) + 1
        putBody(TextFieldValue(text, TextRange(firstGap.coerceIn(0, text.length))))
    }
    fun setPen(p: PenSettings) = _state.update { it.copy(pen = p) }

    private inline fun edit(crossinline f: (EditorState) -> EditorState) = _state.update { f(it).copy(dirty = true) }

    fun goTo(step: EditorStep) = _state.update { it.copy(step = step) }
    fun setMood(m: Mood?) = edit { it.copy(mood = m) }
    fun toggleEmotion(label: String) = edit {
        it.copy(emotions = if (label in it.emotions) it.emotions - label else it.emotions + label)
    }
    fun addCustomEmotion(raw: String) {
        val label = raw.trim().lowercase().take(24)
        if (label.isEmpty()) return
        edit {
            it.copy(
                customEmotions = (it.customEmotions + label).distinct(),
                emotions = (it.emotions + label).distinct(),
            )
        }
    }
    fun setTitle(v: String) = edit { it.copy(title = v) }
    fun setFont(key: String) = edit { it.copy(fontKey = key) }
    fun setLocked(v: Boolean) = edit { it.copy(locked = v) }
    fun nextPrompt() = _state.update { it.copy(promptIndex = it.promptIndex + 1, fixedPrompt = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun makeDefaultFont() = viewModelScope.launch {
        c.settings.setDefaultFont(_state.value.fontKey)
        _state.update { it.copy(message = "Font bawaan diganti") }
    }

    fun newCaptureFile(): File = c.media.newCaptureFile()

    fun addPhotos(uris: List<Uri>, deleteAfter: File? = null) {
        val room = _state.value.mediaRoom
        val picked = uris.take(room.coerceAtLeast(0))
        if (picked.isEmpty()) {
            if (uris.isNotEmpty()) _state.update { it.copy(message = "Maksimal ${EditorState.MAX_MEDIA} gambar per jurnal") }
            deleteAfter?.delete()
            return
        }
        _state.update { it.copy(importing = it.importing + picked.size) }
        viewModelScope.launch {
            val quality = c.settings.current().photoQuality
            for (uri in picked) {
                val stored = c.media.importPhoto(uri, quality)
                edit {
                    it.copy(
                        importing = it.importing - 1,
                        media = if (stored != null) it.media + DraftMedia.Fresh(MediaKind.Photo, stored) else it.media,
                        message = if (stored == null) "Gambar tidak bisa dibaca" else it.message,
                    )
                }
            }
            deleteAfter?.delete()
        }
    }

    fun addSketch(bitmap: Bitmap) {
        if (_state.value.mediaRoom <= 0) {
            _state.update { it.copy(message = "Maksimal ${EditorState.MAX_MEDIA} gambar per jurnal") }
            return
        }
        viewModelScope.launch {
            val stored = c.media.saveSketch(bitmap)
            bitmap.recycle()
            edit { it.copy(media = it.media + DraftMedia.Fresh(MediaKind.Sketch, stored)) }
        }
    }

    fun removeMedia(item: DraftMedia) {
        if (item is DraftMedia.Fresh) c.media.delete(item.image.fileName)
        edit { it.copy(media = it.media - item) }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val today = LocalDate.now()
            val before = XpRules.compute(c.journal.entries.first().map { it.facts() }, today)
            c.journal.save(
                EntryDraft(
                    id = entryId.takeIf { it > 0 },
                    date = s.date,
                    title = s.title.trim(),
                    body = s.body.trimEnd(),
                    mood = s.mood,
                    emotions = s.emotions,
                    fontKey = s.fontKey,
                    locked = s.locked,
                    media = s.media,
                    ink = InkCodec.encode(s.ink),
                    align = s.align.key,
                    type = s.type,
                ),
            )
            val after = XpRules.compute(c.journal.entries.first().map { it.facts() }, today)
            val gained = after.level.totalXp - before.level.totalXp
            val reward = if (gained > 0) {
                Reward(
                    gained = gained,
                    items = after.days[s.date.toEpochDay()]?.items.orEmpty(),
                    before = before.level,
                    after = after.level,
                    streak = after.currentStreak,
                )
            } else null
            val support = Lexicon.hasCrisis("${s.title}\n${s.body}")
            _state.update {
                it.copy(
                    saving = false, dirty = false,
                    // A cheerful XP popup right after a crisis entry would feel dismissive.
                    reward = if (support) null else reward,
                    support = support,
                    finished = !support && reward == null,
                )
            }
        }
    }

    fun dismissReward() = _state.update { it.copy(reward = null, finished = true) }
    fun dismissSupport() = _state.update { it.copy(support = false, finished = true) }

    /** Called when the user leaves without saving: throw away files written for this draft. */
    fun discard() {
        _state.value.media.filterIsInstance<DraftMedia.Fresh>().forEach { c.media.delete(it.image.fileName) }
        _state.update { it.copy(dirty = false, finished = true, media = emptyList()) }
    }
}
