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
import com.lembar.journal.domain.Lexicon
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
    val body: String = "",
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
    val prompt: String
        get() = fixedPrompt ?: Prompts.forMood(mood).let { it[promptIndex % it.size] }
    val canSave: Boolean
        get() = !saving && importing == 0 && (title.isNotBlank() || body.isNotBlank() || media.isNotEmpty() || mood != null)
    val wordCount: Int get() = JournalRepository.countWords(title) + JournalRepository.countWords(body)
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

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val settings = c.settings.current()
        if (entryId <= 0) {
            _state.value = EditorState(
                loading = false,
                isNew = true,
                step = if (checkin) EditorStep.Mood else EditorStep.Write,
                date = startDate,
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
            body = entry.body,
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
        val (title, body) = c.journal.readText(entryId)
        _state.update { it.copy(loading = false, needsUnlock = false, title = title, body = body) }
    }

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
    fun setBody(v: String) = edit { it.copy(body = v) }
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
