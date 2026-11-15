package com.lembar.journal.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lembar.journal.data.Settings
import com.lembar.journal.domain.EntryType
import com.lembar.journal.ui.archive.ArchiveScreen
import com.lembar.journal.ui.components.PrimaryButton
import com.lembar.journal.ui.editor.EditorScreen
import com.lembar.journal.ui.entry.EntryScreen
import com.lembar.journal.ui.home.HomeScreen
import com.lembar.journal.ui.insight.InsightScreen
import com.lembar.journal.ui.onboarding.OnboardingScreen
import com.lembar.journal.ui.settings.SettingsScreen
import com.lembar.journal.ui.stats.StatsScreen
import com.lembar.journal.ui.theme.Lembar
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val WRITE = "write?entryId={entryId}&date={date}&prompt={prompt}&checkin={checkin}&type={type}"
    const val ENTRY = "entry/{id}"
    const val INSIGHT = "insight"

    fun write(
        date: LocalDate? = null,
        entryId: Long? = null,
        prompt: String? = null,
        checkin: Boolean = true,
        type: EntryType = EntryType.Journal,
    ): String {
        val parts = buildList {
            add("type=${type.key}")
            entryId?.let { add("entryId=$it") }
            date?.let { add("date=${it.toEpochDay()}") }
            prompt?.let { add("prompt=${Uri.encode(it)}") }
            add("checkin=$checkin")
        }
        return "write?" + parts.joinToString("&")
    }

    fun entry(id: Long) = "entry/$id"
}

/** Pops only if this screen is still the active one (guards against double callbacks). */
private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) popBackStack()
}

@Composable
fun AppNav(appVm: AppViewModel, settings: Settings, openCheckin: MutableStateFlow<Boolean>) {
    val nav = rememberNavController()
    val start = remember { if (settings.onboarded) Routes.MAIN else Routes.ONBOARDING }
    val wantsCheckin by openCheckin.collectAsState()

    LaunchedEffect(wantsCheckin, settings.onboarded) {
        if (wantsCheckin && settings.onboarded) {
            openCheckin.value = false
            nav.navigate(Routes.write(LocalDate.now())) { launchSingleTop = true }
        }
    }

    val stringArg = { name: String ->
        navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }
    }

    NavHost(nav, start, Modifier.fillMaxSize().background(Lembar.colors.background)) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen { name, h, m, reminder ->
                appVm.completeOnboarding(name, h, m, reminder)
                nav.navigate(Routes.MAIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            }
        }
        composable(Routes.MAIN) {
            MainTabs(
                appVm = appVm,
                settings = settings,
                onWrite = { date, prompt -> nav.navigate(Routes.write(date, prompt = prompt, checkin = prompt == null)) },
                onOpen = { nav.navigate(Routes.entry(it)) },
                onInsight = { nav.navigate(Routes.INSIGHT) { launchSingleTop = true } },
                onNewType = { t -> nav.navigate(Routes.write(LocalDate.now(), checkin = true, type = t)) },
            )
        }
        composable(Routes.INSIGHT) { entry ->
            InsightScreen(
                onBack = { nav.popFrom(entry) },
                // Guided exercises still start with a mood check-in, so the analysis keeps learning.
                onPrompt = { nav.navigate(Routes.write(LocalDate.now(), prompt = it, checkin = true)) },
            )
        }
        composable(
            Routes.WRITE,
            arguments = listOf(stringArg("entryId"), stringArg("date"), stringArg("prompt"), stringArg("checkin"), stringArg("type")),
        ) { entry ->
            EditorScreen(stylusOnly = settings.stylusOnly, onExit = { nav.popFrom(entry) })
        }
        composable(Routes.ENTRY, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            EntryScreen(
                onBack = { nav.popFrom(entry) },
                onEdit = { id -> nav.navigate(Routes.write(entryId = id, checkin = false)) },
                onPrompt = { nav.navigate(Routes.write(LocalDate.now(), prompt = it, checkin = true)) },
            )
        }
    }
}

private data class Tab(val icon: ImageVector, val label: String)

private val tabs = listOf(
    Tab(Icons.Outlined.Home, "Beranda"),
    Tab(Icons.Outlined.AutoStories, "Arsip"),
    Tab(Icons.Outlined.BarChart, "Statistik"),
    Tab(Icons.Outlined.Settings, "Pengaturan"),
)

@Composable
private fun MainTabs(
    appVm: AppViewModel,
    settings: Settings,
    onWrite: (LocalDate, String?) -> Unit,
    onOpen: (Long) -> Unit,
    onInsight: () -> Unit,
    onNewType: (EntryType) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var chooser by rememberSaveable { mutableStateOf(false) }
    val entries by appVm.entries.collectAsStateWithLifecycle()
    val progress by appVm.progress.collectAsStateWithLifecycle()
    val today by appVm.today.collectAsStateWithLifecycle()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val padding = PaddingValues(bottom = 72.dp + navInset)

    Box(Modifier.fillMaxSize()) {
        when (tab) {
            0 -> HomeScreen(
                name = settings.name,
                today = today,
                entries = entries,
                progress = progress,
                contentPadding = padding,
                onCheckIn = { onWrite(it, null) },
                onOpen = onOpen,
                onReflection = { onWrite(today, it) },
                onProgress = { tab = 2 },
                onInsight = onInsight,
            )
            1 -> ArchiveScreen(entries, padding, onOpen)
            2 -> StatsScreen(entries, progress, today, padding, onInsight)
            else -> SettingsScreen(appVm, settings, padding)
        }
        BottomBar(
            selected = tab,
            onSelect = { tab = it },
            onAdd = { chooser = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    if (chooser) NewWritingSheet(onDismiss = { chooser = false }, onPick = { chooser = false; onNewType(it) })
}

private val typeInfo = mapOf(
    EntryType.Journal to (Icons.Outlined.EditNote to "Ceritakan harimu, pilih mood dan emosi"),
    EntryType.Idea to (Icons.Outlined.Lightbulb to "Catat gagasan, rencana, atau proyek"),
    EntryType.Story to (Icons.AutoMirrored.Outlined.MenuBook to "Tulis cerita pendek atau fiksi"),
    EntryType.Pantun to (Icons.Outlined.MusicNote to "Empat baris berima a-b-a-b, langsung dinilai"),
    EntryType.Poem to (Icons.Outlined.AutoAwesome to "Puisi bebas atau berima"),
)

fun EntryType.icon(): ImageVector = typeInfo.getValue(this).first

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NewWritingSheet(onDismiss: () -> Unit, onPick: (EntryType) -> Unit) {
    val c = Lembar.colors
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Mau menulis apa?", style = Lembar.type.headline)
            Spacer(Modifier.height(12.dp))
            EntryType.entries.forEach { t ->
                val (icon, desc) = typeInfo.getValue(t)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPick(t) }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = c.ink, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(t.label, style = Lembar.type.label)
                        Text(desc, style = Lembar.type.caption)
                    }
                }
            }
        }
    }
}

/** Icons left and right, a square "+" in the middle (design 2). */
@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = Lembar.colors
    Column(modifier.fillMaxWidth().background(c.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border.copy(alpha = 0.6f)))
        Row(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                if (i == 2) AddButton(onAdd)
                TabIcon(tab, selected == i) { onSelect(i) }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun TabIcon(tab: Tab, active: Boolean, onClick: () -> Unit) {
    val c = Lembar.colors
    Column(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, tab.label, tint = if (active) c.ink else c.inkFaint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(5.dp))
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (active) c.accent else androidx.compose.ui.graphics.Color.Transparent))
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val c = Lembar.colors
    Box(
        Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(if (c.isDark) 14.dp else 25.dp))
            .background(c.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, "Tulis jurnal", tint = c.onPrimary, modifier = Modifier.size(28.dp))
    }
}

/** Shown over the app when "Kunci aplikasi" is on. Navigation state underneath is kept. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val c = Lembar.colors
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Fingerprint, null, tint = c.ink, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(18.dp))
        Text("Tentang Nala terkunci", style = Lembar.type.headline)
        Spacer(Modifier.height(6.dp))
        Text("Gunakan sidik jari, wajah, atau PIN HP.", style = Lembar.type.bodySmall)
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Buka", onUnlock)
    }
}
