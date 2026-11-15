package com.lembar.journal.ui.insight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lembar.journal.AppContainer
import com.lembar.journal.data.JournalEntry
import com.lembar.journal.domain.InsightEngine
import com.lembar.journal.domain.InsightEntry
import com.lembar.journal.domain.InsightReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

fun JournalEntry.toInsight(textOverride: String? = null) = InsightEntry(
    date = date,
    mood = mood?.value ?: 0,
    emotions = emotions,
    text = textOverride ?: if (locked) "" else "$title\n$body",
    locked = locked,
    type = type,
    title = if (locked) "" else title,
    body = textOverride ?: if (locked) "" else body,
)

class InsightViewModel(private val c: AppContainer) : ViewModel() {

    val periods = listOf(7, 30, 90)
    private val _period = MutableStateFlow(30)
    val period: StateFlow<Int> = _period

    /** Decrypted text of locked entries, only filled after the user authenticates. Kept in memory only. */
    private val unlockedText = MutableStateFlow<Map<Long, String>>(emptyMap())
    val includesLocked: StateFlow<Boolean> get() = _includesLocked
    private val _includesLocked = MutableStateFlow(false)

    val report: StateFlow<InsightReport?> = combine(c.journal.entries, _period, unlockedText) { list, days, extra ->
        InsightEngine.analyze(list.map { it.toInsight(extra[it.id]) }, LocalDate.now(), days)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val name: StateFlow<String> = MutableStateFlow("").also { flow ->
        viewModelScope.launch { flow.value = c.settings.current().name }
    }

    fun setPeriod(days: Int) { _period.value = days }

    fun includeLocked() = viewModelScope.launch {
        val locked = c.journal.entries.first().filter { it.locked }
        val map = locked.associate { e -> e.id to c.journal.readText(e.id).let { (t, b) -> "$t\n$b" } }
        unlockedText.value = map
        _includesLocked.value = true
    }

    override fun onCleared() {
        unlockedText.value = emptyMap()
    }
}
