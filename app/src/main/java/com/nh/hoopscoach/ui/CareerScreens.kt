package com.nh.hoopscoach.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.nh.hoopscoach.game.SaveManager
import com.nh.hoopscoach.game.SaveSlot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.CoachSkill
import com.nh.hoopscoach.game.Coaching
import com.nh.hoopscoach.game.CollegeEra
import com.nh.hoopscoach.game.EraRules
import com.nh.hoopscoach.game.Gameplan
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Names
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Team

@Composable
fun CareerScreen(vm: GameViewModel, lg: League) {
    val c = lg.userCoach
    val t = lg.user
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenHeader("Head coach · age ${c.age}", c.name, "${t.school} · ${prestigeLabel(t.prestige)}") {
                IconCircle({ vm.push(Route.Settings) }) { Icon(NavIcons.Gear, "Settings", tint = P.sub, modifier = Modifier.size(22.dp)) }
            }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoachBadge(c.first, c.last, 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${c.w}-${c.l}", style = T.hero, color = P.ink)
                        Text("career record · ${"%.3f".format(c.winPct).removePrefix("0")}", style = T.small, color = P.sub)
                    }
                    Ring(c.rating, 58.dp, label = "RATING", textSize = 20.sp)
                }
                Spacer(Modifier.height(14.dp))
                StatRow(
                    listOf(
                        Triple("Titles", "${c.titles}", null), Triple("Final Fours", "${c.finalFours}", null),
                        Triple("NCAA trips", "${c.tourneys}", null), Triple("Conf titles", "${c.confTitles}", null),
                    ),
                    style = T.numSm,
                )
                Spacer(Modifier.height(10.dp))
                StatRow(
                    listOf(
                        Triple("Coach of Yr", "${c.natCoty}", null), Triple("Conf COY", "${c.confCoty}", null),
                        Triple("Drafted", "${c.drafted}", null), Triple("Hall of Fame", "${c.hof}", null),
                    ),
                    style = T.numSm,
                )
            }
            PlayingPast(lg)
            Section("Job security")
            Panel {
                val label = when { c.security >= 75 -> "Untouchable"; c.security >= 50 -> "Safe"; c.security >= 30 -> "Warm seat"; else -> "Hot seat" }
                val col = if (c.security >= 50) P.good else if (c.security >= 30) P.warn else P.bad
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = T.head, color = col, modifier = Modifier.weight(1f))
                    Text("${c.security}/100", style = T.numSm, color = P.sub)
                }
                Spacer(Modifier.height(8.dp))
                Bar(c.security / 100f, Modifier.fillMaxWidth(), color = col, height = 8.dp)
                Spacer(Modifier.height(8.dp))
                Text("Beat your expected wins and go deep in March to keep the boosters happy.", style = T.small, color = P.sub)
                com.nh.hoopscoach.game.CoachContracts.ensure(lg)
                Text("Contract: ${com.nh.hoopscoach.game.CoachContracts.line(lg)} · earned ${com.nh.hoopscoach.game.CoachContracts.money(c.earnings)} so far", style = T.small, color = P.ink, modifier = Modifier.padding(top = 6.dp))
            }
            Section("Coaching skills", action = "Develop", onAction = { vm.push(Route.CoachDev) })
            Panel(onClick = { vm.push(Route.CoachDev) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Ring(c.off, 46.dp, label = "OFF"); Ring(c.def, 46.dp, label = "DEF"); Ring(c.rec, 46.dp, label = "REC"); Ring(c.dev, 46.dp, label = "DEV")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${c.points} coaching point${if (c.points == 1) "" else "s"} to spend" + if (c.skills.isNotEmpty()) " · ${c.skills.joinToString { it.label }}" else "",
                    style = T.small, color = if (c.points > 0) P.accent else P.sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
            Section("Coaching history")
        }
        item {
            val rows = mutableListOf<@Composable () -> Unit>()
            if (lg.phase != Phase.WRAP_UP && lg.phase != Phase.OFFSEASON) rows += {
                ListRow {
                    TeamBadge(t, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${t.school} · ${t.record}", style = T.bodyB, color = P.ink)
                        Text("In progress", style = T.small, color = P.accent)
                    }
                    Text("${lg.season}", style = T.numSm, color = P.sub)
                }
            }
            c.history.reversed().forEach { h ->
                rows += {
                    ListRow(onClick = { vm.push(Route.TeamPage(h.teamId)) }) {
                        TeamBadge(lg.team(h.teamId), 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${h.team} · ${h.w}-${h.l}", style = T.bodyB, color = P.ink)
                            Text(h.result, style = T.small, color = if (h.result.contains("Champion")) P.gold else P.sub)
                        }
                        Text("${h.season}", style = T.numSm, color = P.sub)
                    }
                }
            }
            GroupList(rows) { it() }
        }
        item { TrophyRoomLink(vm, lg) }
        item { CoachRivalsCard(lg) }
        item {
            Section("Archive")
            GroupList(HistoryKind.entries) { k ->
                ListRow(onClick = { vm.push(Route.History(k)) }) {
                    Text(k.title, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f))
                    Chevron()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun CoachDevScreen(vm: GameViewModel, lg: League) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Coach development", "Spend coaching points", onBack = { vm.back() })
            CoachPoints(vm, lg)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun CoachPoints(vm: GameViewModel, lg: League, earned: Int = 0) {
    val c = lg.userCoach
    Panel {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("COACHING POINTS", style = T.label, color = P.sub)
                if (earned > 0) Text("+$earned earned this season", style = T.small, color = P.good)
            }
            Text("${c.points}", style = T.hero, color = P.accent)
        }
        Spacer(Modifier.height(10.dp))
        Hairline()
        listOf("Offense" to c.off, "Defense" to c.def, "Recruiting" to c.rec, "Development" to c.dev).forEachIndexed { i, (label, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = T.bodyB, color = P.ink)
                    Text("${Coaching.cost(i)} points per level", style = T.small, color = P.sub)
                }
                Text("$v", style = T.num, color = tier(v), modifier = Modifier.padding(horizontal = 14.dp))
                SmallButton("+1", primary = true, enabled = c.points >= Coaching.cost(i) && v < 99) { vm.act { Coaching.buyRating(it.userCoach, i) } }
            }
        }
        Hairline()
        Spacer(Modifier.height(10.dp))
        Text("SKILLS · ${Coaching.SKILL_COST} POINTS EACH, PERMANENT", style = T.label, color = P.sub)
        CoachSkill.entries.forEach { s ->
            val owned = c.has(s)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(P.surface2).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.label, style = T.bodyB, color = P.ink)
                    Text(s.blurb, style = T.small, color = P.sub)
                }
                Spacer(Modifier.width(8.dp))
                if (owned) Tag("OWNED", P.good) else SmallButton("Learn", enabled = c.points >= Coaching.SKILL_COST) { vm.act { Coaching.buySkill(it.userCoach, s) } }
            }
        }
    }
}

// ---------- settings ----------

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = P.ink, unfocusedTextColor = P.ink, focusedBorderColor = P.accent, unfocusedBorderColor = P.line,
    cursorColor = P.accent, focusedContainerColor = P.surface, unfocusedContainerColor = P.surface,
    focusedLabelColor = P.accent, unfocusedLabelColor = P.sub,
)

@Composable
fun SettingsScreen(vm: GameViewModel, lg: League) {
    var confirm by remember { mutableStateOf(false) }
    var slotToClone by remember { mutableStateOf<SaveSlot?>(null) }
    var cloneTitle by remember { mutableStateOf("") }
    var slotToRename by remember { mutableStateOf<SaveSlot?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var slotToDelete by remember { mutableStateOf<SaveSlot?>(null) }
    var confirmNewDynasty by remember { mutableStateOf(false) }
    var exportSlotId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var testStatus by remember { mutableStateOf("") }
    var testSuccess by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var configuring by remember { mutableStateOf(false) }
    var downloadingModel by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatus by remember { mutableStateOf("") }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSetupGuide by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportSlot(exportSlotId ?: vm.activeSlotId, uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importSave(uri) }

    LaunchedEffect(Unit) {
        vm.refreshSaveSlots()
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Settings", onBack = { vm.back() })
            Section("Dynasty Saves & Slots")
            Panel {
                val active = vm.activeSlot ?: vm.saveSlots.firstOrNull { it.id == vm.activeSlotId }
                val schoolName = active?.school ?: lg.user.school
                val seasonYear = active?.season ?: lg.season
                val rec = active?.record ?: "${lg.user.w}-${lg.user.l}"
                val title = active?.title ?: "$schoolName Dynasty"
                val modeLabel = active?.mode ?: if (lg.pro) "NBA Coach" else "College Coach"
                val person = active?.coachOrPlayer ?: lg.coaches.firstOrNull { it.id == lg.userCoachId }?.name ?: "Coach"
                val timeStr = active?.let { SaveManager.formatSaveTime(it.lastSaved) } ?: "Just now"
                val sizeStr = active?.let { SaveManager.formatFileSize(it.sizeBytes) } ?: ""

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (active != null) {
                        SaveSlotBadge(active, 48.dp)
                    } else {
                        TeamBadge(lg.user, 48.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.background(P.accentSoft, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("ACTIVE", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                            }
                        }
                        Text("$schoolName · $seasonYear ($rec) · $modeLabel", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$person · Saved $timeStr${if (sizeStr.isNotBlank()) " · $sizeStr" else ""}", style = T.small.copy(fontSize = 11.sp), color = P.faint)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Snapshot", Modifier.weight(1f)) {
                        val activeSlot = active ?: SaveSlot("default", title, schoolName, lg.user.mascot, lg.user.abbr, seasonYear, rec, modeLabel, person, lg.user.primary.toInt(), lg.user.secondary.toInt(), System.currentTimeMillis())
                        slotToClone = activeSlot
                        cloneTitle = "$schoolName Checkpoint ($seasonYear)"
                    }
                    SecondaryButton("Rename", Modifier.weight(0.7f)) {
                        val activeSlot = active ?: SaveSlot("default", title, schoolName, lg.user.mascot, lg.user.abbr, seasonYear, rec, modeLabel, person, lg.user.primary.toInt(), lg.user.secondary.toInt(), System.currentTimeMillis())
                        slotToRename = activeSlot
                        renameTitle = title
                    }
                    SecondaryButton("Export", Modifier.weight(0.7f)) {
                        exportSlotId = vm.activeSlotId
                        runCatching { exporter.launch("HoopsDynasty-${lg.user.abbr}-$seasonYear.hdsave") }.onFailure { vm.toast("No file app available") }
                    }
                }
            }

            val otherSlots = vm.saveSlots.filter { it.id != vm.activeSlotId }
            if (otherSlots.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("OTHER SAVED DYNASTIES (${otherSlots.size})", style = T.label, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
                for (slot in otherSlots) {
                    Panel(Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SaveSlotBadge(slot, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(slot.title, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${slot.school} · ${slot.season} (${slot.record}) · ${slot.mode}", style = T.small, color = P.sub, maxLines = 1)
                                Text("${slot.coachOrPlayer} · Saved ${SaveManager.formatSaveTime(slot.lastSaved)} · ${SaveManager.formatFileSize(slot.sizeBytes)}", style = T.small.copy(fontSize = 11.sp), color = P.faint)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryButton("Load", Modifier.weight(1f)) {
                                vm.loadSlot(slot.id)
                            }
                            SecondaryButton("Snapshot", Modifier.weight(0.7f)) {
                                slotToClone = slot
                                cloneTitle = "${slot.school} Branch"
                            }
                            SecondaryButton("Rename", Modifier.weight(0.7f)) {
                                slotToRename = slot
                                renameTitle = slot.title
                            }
                            SecondaryButton("Delete", Modifier.weight(0.7f)) {
                                slotToDelete = slot
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("+ New Dynasty", Modifier.weight(1f)) {
                    confirmNewDynasty = true
                }
                SecondaryButton("Import Save", Modifier.weight(1f)) {
                    runCatching { importer.launch(arrayOf("*/*")) }.onFailure { vm.toast("No file app available") }
                }
            }
            Section("Portraits")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Retro portraits for everyone", style = T.bodyB, color = P.ink)
                        Text("Draw real players as retro bit portraits too, instead of their headshots.", style = T.small, color = P.sub)
                    }
                    androidx.compose.material3.Switch(lg.retroAll, { on -> vm.act { it.retroAll = on } })
                }
            }
            Section("AI Stories · Ollama")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ollama controls stories", style = T.bodyB, color = P.ink)
                        Text("Run an AI model directly on your phone or computer to generate game recaps, storylines and breaking news.", style = T.small, color = P.sub)
                    }
                    androidx.compose.material3.Switch(lg.ollama.enabled, { on -> vm.act { it.ollama.enabled = on } })
                }

                Spacer(Modifier.height(12.dp))

                // One-tap Auto-configure & detect
                PrimaryButton(
                    text = if (configuring) "Auto-detecting Ollama..." else "Auto-Configure (Auto-Detect)",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !configuring && downloadingModel == null,
                ) {
                    configuring = true
                    testStatus = "Detecting active Ollama instance on phone or local network..."
                    scope.launch {
                        val res = com.nh.hoopscoach.game.Ollama.autoConfigure(lg.ollama.host)
                        configuring = false
                        res.onSuccess { cfg ->
                            testSuccess = true
                            testStatus = cfg.message
                            availableModels = cfg.models
                            vm.act {
                                it.ollama.enabled = true
                                it.ollama.host = cfg.host
                                it.ollama.model = cfg.selectedModel
                            }
                            vm.toast("Configured: using ${cfg.selectedModel}")
                        }.onFailure { err ->
                            testSuccess = false
                            testStatus = err.message ?: "Could not detect Ollama"
                        }
                    }
                }

                if (testStatus.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(testStatus, style = T.small, color = if (testSuccess) P.good else P.bad)
                }

                Spacer(Modifier.height(10.dp))

                // Phone setup guide toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSetupGuide = !showSetupGuide }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showSetupGuide) "▼ Hide Phone Setup Guide" else "▶ How to run Ollama on your phone (Termux)",
                        style = T.label.copy(fontSize = 11.sp),
                        color = P.accent,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (showSetupGuide) {
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(P.surface2, RoundedCornerShape(8.dp))
                            .border(1.dp, P.faint.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text("Run Ollama on your Android device in 3 steps:", style = T.bodyB, color = P.ink)
                        Spacer(Modifier.height(4.dp))
                        Text("1. Install Termux app on your phone (from F-Droid or GitHub).", style = T.small, color = P.sub)
                        Text("2. In Termux, run this command:", style = T.small, color = P.sub)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "pkg install -y ollama && ollama serve",
                            style = T.small.copy(fontSize = 11.sp),
                            color = P.accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(P.surface, RoundedCornerShape(4.dp))
                                .padding(6.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        SecondaryButton("Copy Termux command", Modifier.fillMaxWidth()) {
                            clipboard.setText(AnnotatedString("pkg install -y ollama && ollama serve"))
                            vm.toast("Copied to clipboard! Paste in Termux.")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("3. Tap 'Auto-Configure' above or choose a model below to download!", style = T.small, color = P.sub)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("DOWNLOAD MODEL TO PHONE", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                Spacer(Modifier.height(4.dp))
                Text("Download a mobile-optimized model directly into your device's Ollama runtime:", style = T.small, color = P.sub)
                Spacer(Modifier.height(8.dp))

                // Recommended mobile models list
                com.nh.hoopscoach.game.Ollama.RECOMMENDED_MODELS.forEach { preset ->
                    val isDownloading = downloadingModel == preset.id
                    val isInstalled = availableModels.contains(preset.id) || availableModels.any { it.startsWith(preset.id.substringBefore(":")) }
                    val isCurrent = lg.ollama.model.equals(preset.id, ignoreCase = true)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(if (isCurrent) P.accentSoft else P.surface2, RoundedCornerShape(8.dp))
                            .border(
                                width = if (isCurrent) 1.5.dp else 1.dp,
                                color = if (isCurrent) P.accent else P.faint.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(preset.name, style = T.bodyB, color = P.ink)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        preset.tag,
                                        style = T.small.copy(fontSize = 10.sp),
                                        color = if (preset.tag.contains("Recommended")) P.good else P.sub,
                                    )
                                }
                                Text("${preset.size} · ${preset.description}", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                            }
                            Spacer(Modifier.width(8.dp))

                            if (isDownloading) {
                                Text("DOWNLOADING", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                            } else if (isCurrent) {
                                Text("ACTIVE", style = T.label.copy(fontSize = 11.sp), color = P.good)
                            } else if (isInstalled) {
                                SmallButton("Select") {
                                    vm.act {
                                        it.ollama.model = preset.id
                                        it.ollama.enabled = true
                                    }
                                    vm.toast("Active model: ${preset.name}")
                                }
                            } else {
                                SmallButton(
                                    text = "Download",
                                    primary = preset.tag.contains("Recommended"),
                                    enabled = downloadingModel == null,
                                ) {
                                    downloadingModel = preset.id
                                    downloadProgress = 0f
                                    downloadStatus = "Connecting to Ollama on ${lg.ollama.host}..."
                                    scope.launch {
                                        val res = com.nh.hoopscoach.game.Ollama.pullModel(lg.ollama.host, preset.id) { status, prog, details ->
                                            downloadProgress = prog
                                            downloadStatus = "$status: $details"
                                        }
                                        downloadingModel = null
                                        res.onSuccess {
                                            vm.act {
                                                it.ollama.model = preset.id
                                                it.ollama.enabled = true
                                            }
                                            testStatus = "Downloaded ${preset.name} successfully! Model is active."
                                            testSuccess = true
                                            availableModels = (availableModels + preset.id).distinct()
                                            vm.toast("${preset.name} downloaded & active!")
                                        }.onFailure { err ->
                                            downloadStatus = "Download failed: ${err.message}"
                                            vm.toast("Download error: ${err.message}")
                                        }
                                    }
                                }
                            }
                        }

                        if (isDownloading) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(P.surface)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(downloadProgress.coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .background(P.accent)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(downloadStatus, style = T.small.copy(fontSize = 11.sp), color = P.accent)
                        }
                    }
                }

                if (downloadingModel == null && downloadStatus.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(downloadStatus, style = T.small.copy(fontSize = 11.sp), color = P.sub)
                }

                if (lg.ollama.enabled) {
                    Spacer(Modifier.height(14.dp))
                    Text("MANUAL CONNECTION SETTINGS", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                    Spacer(Modifier.height(4.dp))
                    Text("Host endpoint (http://localhost:11434 on phone, http://10.0.2.2:11434 on emulator)", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = lg.ollama.host,
                        onValueChange = { h -> vm.act { it.ollama.host = h } },
                        singleLine = true,
                        placeholder = { Text("http://localhost:11434", color = P.faint) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Model name", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = lg.ollama.model,
                        onValueChange = { m -> vm.act { it.ollama.model = m } },
                        singleLine = true,
                        placeholder = { Text("llama3.2:1b", color = P.faint) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-generate major stories", style = T.bodyB, color = P.ink)
                            Text("Automatically generate recaps for your games using Ollama.", style = T.small, color = P.sub)
                        }
                        androidx.compose.material3.Switch(lg.ollama.autoGenerate, { on -> vm.act { it.ollama.autoGenerate = on } })
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("FINE NARRATIVE CONTROLS", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                    Spacer(Modifier.height(4.dp))
                    Text("The AI controls fine narrative details, quotes, and reactions. Game simulation stats, box scores, records, and ratings are strictly computed by the engine and never modified by AI.", style = T.small, color = P.good)
                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Postgame press conferences", style = T.bodyB, color = P.ink)
                            Text("AI writes contextual podium questions & player quotes.", style = T.small, color = P.sub)
                        }
                        androidx.compose.material3.Switch(lg.ollama.pressConference, { on -> vm.act { it.ollama.pressConference = on } })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Social media hot takes", style = T.bodyB, color = P.ink)
                            Text("AI generates analyst, fan, and hater takes on your feed.", style = T.small, color = P.sub)
                        }
                        androidx.compose.material3.Switch(lg.ollama.socialFeed, { on -> vm.act { it.ollama.socialFeed = on } })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Beat writer recaps & coach quotes", style = T.bodyB, color = P.ink)
                            Text("AI writes newspaper reports and postgame locker room quotes.", style = T.small, color = P.sub)
                        }
                        androidx.compose.material3.Switch(lg.ollama.gameRecaps, { on -> vm.act { it.ollama.gameRecaps = on } })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Trade deadline rumors & grades", style = T.bodyB, color = P.ink)
                            Text("AI writes insider scoops and grades completed trades.", style = T.small, color = P.sub)
                        }
                        androidx.compose.material3.Switch(lg.ollama.tradeRumors, { on -> vm.act { it.ollama.tradeRumors = on } })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tactical pregame scouting keys", style = T.bodyB, color = P.ink)
                            Text("AI writes keys to victory based on opponent matchups.", style = T.small, color = P.sub)
                        }
                        androidx.compose.material3.Switch(lg.ollama.scoutingKeys, { on -> vm.act { it.ollama.scoutingKeys = on } })
                    }
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(if (testing) "Testing..." else "Test connection", Modifier.fillMaxWidth(), enabled = !testing && !configuring) {
                        testing = true
                        testStatus = "Testing connection to ${lg.ollama.host}..."
                        scope.launch {
                            val res = com.nh.hoopscoach.game.Ollama.testConnection(lg.ollama.host)
                            testing = false
                            res.onSuccess { models ->
                                testSuccess = true
                                availableModels = models
                                testStatus = if (models.isNotEmpty()) "Connected! Available models: " + models.joinToString(", ")
                                else "Connected! (No models downloaded yet in Ollama)"
                            }.onFailure { err ->
                                testSuccess = false
                                testStatus = "Connection failed: ${err.message ?: "Could not reach Ollama"}"
                            }
                        }
                    }
                }
            }
            lg.road?.let { rd ->
                Section("Big moments")
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Shot meter", style = T.bodyB, color = P.ink)
                            Text("Time your own release in big moments: perfect timing adds to your chance, a bad one takes away.", style = T.small, color = P.sub)
                        }
                        androidx.compose.material3.Switch(rd.shotMeter, { on -> vm.act { it.road?.shotMeter = on } })
                    }
                }
            }
            if (!lg.pro) {
                val era = EraRules.of(lg)
                Section("Era rules · ${lg.season}")
                Panel {
                    Text(era.title, style = T.bodyB, color = P.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(era.blurb, style = T.small, color = P.sub)
                    Spacer(Modifier.height(8.dp))
                    Text(era.rulesSummary(), style = T.small, color = P.ink)
                    if (era != CollegeEra.MODERN) {
                        Spacer(Modifier.height(8.dp))
                        Text("Rules advance with NCAA history as seasons pass.", style = T.small, color = P.faint)
                    }
                }
            }
            Section("Schools")
            Panel {
                Text("Fix a name, nickname or colors for any school.", style = T.small, color = P.sub)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search schools", color = P.faint) }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            }
            Spacer(Modifier.height(10.dp))
        }
        item {
            val list = lg.teams.filter { query.isBlank() || it.name.contains(query, true) || it.abbr.contains(query, true) }.sortedBy { it.school }
                .let { if (query.isBlank()) listOf(lg.user) else it.take(30) }
            GroupList(list) { t ->
                ListRow(onClick = { vm.push(Route.EditTeam(t.id)) }) {
                    TeamBadge(t, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = T.bodyB, color = P.ink)
                        Text(lg.conf(t.confId).name, style = T.small, color = P.sub)
                    }
                    Chevron()
                }
            }
        }
        item {
            Section("Career")
            Panel {
                SecondaryButton("Start a new career", Modifier.fillMaxWidth()) { confirm = true }
                Spacer(Modifier.height(10.dp))
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
                Text("Hoops Dynasty $version · Schools and conferences are real; all players and coaches are fictional. School logos belong to their schools.", style = T.small, color = P.faint)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = P.surface,
        title = { Text("Start over?", style = T.head, color = P.ink) },
        text = { Text("This deletes your current dynasty and builds a brand-new league. Back up first if you might want it later.", style = T.body, color = P.sub) },
        confirmButton = { TextButton({ confirm = false; vm.abandonCareer() }) { Text("Delete and start over", color = P.bad) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel", color = P.sub) } },
    )

    if (slotToClone != null) AlertDialog(
        onDismissRequest = { slotToClone = null }, containerColor = P.surface,
        title = { Text("Snapshot / Clone Save", style = T.head, color = P.ink) },
        text = {
            Column {
                Text("Create a snapshot checkpoint of '${slotToClone?.title}'. You can return to this checkpoint anytime.", style = T.body, color = P.sub)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = cloneTitle,
                    onValueChange = { cloneTitle = it.take(32) },
                    label = { Text("Snapshot Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }
        },
        confirmButton = {
            TextButton({
                val s = slotToClone
                slotToClone = null
                if (s != null) vm.cloneSlot(s.id, cloneTitle)
            }) { Text("Create Snapshot", color = P.accent) }
        },
        dismissButton = { TextButton({ slotToClone = null }) { Text("Cancel", color = P.sub) } }
    )

    if (slotToRename != null) AlertDialog(
        onDismissRequest = { slotToRename = null }, containerColor = P.surface,
        title = { Text("Rename Dynasty", style = T.head, color = P.ink) },
        text = {
            Column {
                Text("Choose a new name for this save slot:", style = T.body, color = P.sub)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = renameTitle,
                    onValueChange = { renameTitle = it.take(32) },
                    label = { Text("Dynasty Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }
        },
        confirmButton = {
            TextButton({
                val s = slotToRename
                slotToRename = null
                if (s != null && renameTitle.isNotBlank()) vm.renameSlot(s.id, renameTitle)
            }) { Text("Save", color = P.accent) }
        },
        dismissButton = { TextButton({ slotToRename = null }) { Text("Cancel", color = P.sub) } }
    )

    if (slotToDelete != null) AlertDialog(
        onDismissRequest = { slotToDelete = null }, containerColor = P.surface,
        title = { Text("Delete Dynasty?", style = T.head, color = P.bad) },
        text = { Text("Permanently delete '${slotToDelete?.title}' (${slotToDelete?.school}, ${slotToDelete?.season})? All backups in this slot will be erased. This cannot be undone.", style = T.body, color = P.sub) },
        confirmButton = {
            TextButton({
                val s = slotToDelete
                slotToDelete = null
                if (s != null) vm.deleteSlot(s.id)
            }) { Text("Delete", color = P.bad) }
        },
        dismissButton = { TextButton({ slotToDelete = null }) { Text("Cancel", color = P.sub) } }
    )

    if (confirmNewDynasty) AlertDialog(
        onDismissRequest = { confirmNewDynasty = false }, containerColor = P.surface,
        title = { Text("Start New Dynasty?", style = T.head, color = P.ink) },
        text = { Text("Your current dynasty will stay safely saved in its slot. You can switch back anytime from Settings or the Start screen.", style = T.body, color = P.sub) },
        confirmButton = {
            TextButton({
                confirmNewDynasty = false
                vm.startNewDynasty()
            }) { Text("Start New", color = P.accent) }
        },
        dismissButton = { TextButton({ confirmNewDynasty = false }) { Text("Cancel", color = P.sub) } }
    )
}

@Composable
fun EditTeamScreen(vm: GameViewModel, lg: League, teamId: Int) {
    val t = lg.team(teamId)
    var school by remember(teamId) { mutableStateOf(t.school) }
    var mascot by remember(teamId) { mutableStateOf(t.mascot) }
    var abbr by remember(teamId) { mutableStateOf(t.abbr) }
    var colors by remember(teamId) { mutableStateOf(t.primary to t.secondary) }
    val preview = Team(t.id, school, mascot, abbr.ifBlank { "?" }, t.confId, t.prestige, colors.first, colors.second, 0)
    val palette = (listOf(t.primary to t.secondary) + Names.colors).distinct()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Edit school", t.name, onBack = { vm.back() })
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) { TeamBadge(preview, 96.dp) }
            Panel {
                OutlinedTextField(school, { school = it.take(28) }, label = { Text("School") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(mascot, { mascot = it.take(22) }, label = { Text("Nickname") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(abbr, { abbr = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(4) }, label = { Text("Abbreviation (up to 4)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            }
            Section("Colors")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                palette.forEach { pair ->
                    val sel = pair == colors
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color(pair.first)).border(if (sel) 3.dp else 5.dp, if (sel) P.accent else Color(pair.second), CircleShape)
                            .clickable { colors = pair },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = Gutter)) { SmallButton("Swap colors") { colors = colors.second to colors.first } }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Save", Modifier.fillMaxWidth().padding(horizontal = Gutter), enabled = school.isNotBlank() && mascot.isNotBlank() && abbr.isNotBlank()) {
                val s = school.trim(); val m = mascot.trim(); val a = abbr.trim(); val (p1, p2) = colors
                vm.act { val tm = it.team(teamId); tm.school = s; tm.mascot = m; tm.abbr = a; tm.primary = p1; tm.secondary = p2 }
                vm.toast("Saved")
                vm.back()
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ---------- new career ----------

@Composable
fun NewGameScreen(vm: GameViewModel) {
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importSave(uri) }
    val lg = vm.draft
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var conf by remember { mutableIntStateOf(-1) }
    var query by remember { mutableStateOf("") }
    var pick by remember { mutableIntStateOf(-1) }
    var mode by remember { mutableIntStateOf(if (vm.roadHero != null) 1 else 0) }
    var eraIdx by remember { mutableIntStateOf(EraRules.pickable.indexOf(CollegeEra.MODERN).coerceAtLeast(0)) }
    val eras = EraRules.pickable

    LaunchedEffect(Unit) {
        vm.refreshSaveSlots()
    }

    Column(Modifier.fillMaxSize().background(P.bg).statusBarsPadding()) {
        if (lg == null) { BusyScreen("Building your league"); return@Column }
        val header: @Composable () -> Unit = {
            if (vm.saveSlots.isNotEmpty()) {
                Column(Modifier.padding(top = 10.dp)) {
                    Text("RESUME SAVED DYNASTY", style = T.label, color = P.accent, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp))
                    for (s in vm.saveSlots.take(2)) {
                        Panel(Modifier.padding(bottom = 6.dp).clickable { vm.loadSlot(s.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SaveSlotBadge(s, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${s.school} · ${s.season} (${s.record}) · ${s.mode}", style = T.small, color = P.sub, maxLines = 1)
                                    Text("Saved ${SaveManager.formatSaveTime(s.lastSaved)}", style = T.small.copy(fontSize = 11.sp), color = P.faint)
                                }
                                Spacer(Modifier.width(8.dp))
                                PrimaryButton("Resume", Modifier.height(34.dp)) { vm.loadSlot(s.id) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Column(Modifier.padding(start = Gutter + 4.dp, end = Gutter, top = 16.dp, bottom = 6.dp)) {
                Text("NEW CAREER", style = T.label, color = P.accent)
                Text("HOOPS DYNASTY", style = T.hero.copy(fontSize = 46.sp, lineHeight = 46.sp), color = P.ink)
                Text(
                    when (mode) {
                        0 -> "Take over any of ${lg.teams.size} Division I programs. Recruit, coach, develop and build a dynasty."
                        1 -> "Road to the League: create a high school senior, pick a school, earn your minutes and chase the draft."
                        else -> "Coach an NBA team: today's league or any season since 1986-87, with that year's real rosters. Set the rotation, make trades, win over the owner."
                    },
                    style = T.body, color = P.sub,
                )
            }
            if (vm.roadHero == null) {
                Spacer(Modifier.height(8.dp))
                Segmented(listOf("Coach college", "Play", "Coach NBA"), mode, { mode = it })
            }
        }
        if (mode == 1) { RoadNewGame(vm, lg, header); return@Column }
        if (mode == 2) { NbaCoachNewGame(vm, header); return@Column }
        LazyColumn(Modifier.weight(1f)) {
            item {
                header()
                Section("Your coach")
                Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(first, { first = it.take(16) }, placeholder = { Text("First name", color = P.faint) }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    OutlinedTextField(last, { last = it.take(18) }, placeholder = { Text("Last name", color = P.faint) }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                }
                Section("Era")
                Chips(eras.map { it.label }, eraIdx, { i ->
                    if (i != eraIdx) {
                        eraIdx = i
                        pick = -1
                        vm.generateDraft(EraRules.startYear(eras[i]))
                    }
                })
                val era = eras[eraIdx]
                Panel(Modifier.padding(top = 10.dp)) {
                    Text("${era.title} · ${EraRules.startYear(era)}", style = T.bodyB, color = P.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(era.blurb, style = T.small, color = P.sub)
                    Spacer(Modifier.height(8.dp))
                    Text(era.rulesSummary(), style = T.small, color = P.ink)
                }
                Section("Choose a school")
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search ${lg.teams.size} schools", color = P.faint) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter), colors = fieldColors())
                Spacer(Modifier.height(10.dp))
                Chips(listOf("All") + lg.confs.map { it.abbr }, conf + 1, { conf = it - 1 })
                Spacer(Modifier.height(10.dp))
            }
            val teams = lg.teams.filter { (conf < 0 || it.confId == conf) && (query.isBlank() || it.name.contains(query, true) || it.abbr.contains(query, true)) }
                .sortedByDescending { it.prestige }
            item {
                if (teams.isEmpty()) Empty("No schools match.")
                GroupList(teams.take(if (conf < 0 && query.isBlank()) 60 else 400), inset = 66.dp) { t ->
                    val sel = pick == t.id
                    ListRow(onClick = { pick = t.id }, modifier = if (sel) Modifier.background(P.accentSoft) else Modifier) {
                        TeamBadge(t, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${lg.conf(t.confId).abbr} · ${prestigeLabel(t.prestige)} · roster ${Gameplan.teamRating(lg, t)}", style = T.small, color = P.sub, maxLines = 1)
                        }
                        Ring(t.prestige, 40.dp, textSize = 15.sp)
                    }
                }
                if (conf < 0 && query.isBlank()) Text("Showing the top 60 by prestige. Search or pick a conference to see everyone.", style = T.small, color = P.faint,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 10.dp))
                Spacer(Modifier.height(10.dp))
            }
        }
        Column(Modifier.fillMaxWidth().background(P.bg).navigationBarsPadding().padding(horizontal = Gutter, vertical = 10.dp)) {
            val t = if (pick >= 0) lg.team(pick) else null
            PrimaryButton(if (t == null) "Pick a school" else "Coach ${t.school}", Modifier.fillMaxWidth(), enabled = t != null) {
                vm.startCareer(pick, first.trim().ifBlank { "Coach" }, last.trim().ifBlank { "Rookie" })
            }
            Text("Restore a backup", style = T.bodyB.copy(fontSize = 14.sp), color = P.accent, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { runCatching { restore.launch(arrayOf("*/*")) } }.padding(top = 10.dp))
        }
    }
}
