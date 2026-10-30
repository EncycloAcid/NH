package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Names
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Team

// ---------- Dynasty ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DynastyScreen(vm: GameViewModel, lg: League) {
    val c = lg.userCoach
    val t = lg.user
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenTitle("My Dynasty") { SettingsButton(vm) }
            Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.look, Color.Unspecified, null, 70.dp, coach = true)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Age ${c.age} · ${t.school}", color = C.sub, fontSize = 15.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (c.natCoty > 0) Text("🏆 x${c.natCoty} Nat COTY", color = C.orangeText, fontSize = 14.sp)
                            if (c.confCoty > 0) Text("★ x${c.confCoty} Conf COTY", color = C.orangeText, fontSize = 14.sp)
                            if (c.titles > 0) Text("👑 x${c.titles} Champ", color = C.orangeText, fontSize = 14.sp)
                            if (c.confTitles > 0) Text("⚑ x${c.confTitles} Conf Champ", color = C.orangeText, fontSize = 14.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${t.prestige}", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text("PRESTIGE", style = HoopsType.labelMedium, color = C.sub)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            StatGrid(
                listOf(
                    Triple("W-L", "${c.w}-${c.l}", C.text), Triple("Win%", "%.3f".format(c.winPct).removePrefix("0"), C.text),
                    Triple("Tourn", "${c.tourneys}", C.text), Triple("Champ", "${c.titles}", C.text),
                    Triple("Final 4", "${c.finalFours}", C.text), Triple("Conf", "${c.confTitles}", C.text),
                    Triple("HOF", "${c.hof}", C.text), Triple("Draft", "${c.drafted}", C.text),
                    Triple("Offense", "${c.off}", ratingColor(c.off)), Triple("Defense", "${c.def}", ratingColor(c.def)),
                    Triple("Recruit", "${c.rec}", ratingColor(c.rec)), Triple("Develop", "${c.dev}", ratingColor(c.dev)),
                ),
                columns = 4, size = 24.sp,
            )
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("JOB SECURITY", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.weight(1f))
                    Text(when { c.security >= 75 -> "Untouchable"; c.security >= 50 -> "Safe"; c.security >= 30 -> "Warm seat"; else -> "Hot seat" },
                        color = if (c.security >= 50) C.green else if (c.security >= 30) C.yellow else C.red, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                ProgressBar(c.security / 100f, Modifier.fillMaxWidth(), color = if (c.security >= 50) C.green else if (c.security >= 30) C.yellow else C.red)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Coaching points: ${c.points}", color = C.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    SmallButton(if (c.skills.isEmpty()) "Develop Coach" else "${c.skills.size} skills · Develop") { vm.push(Route.CoachDev) }
                }
            }
            SectionLabel("Coaching History")
        }
        if (lg.phase != Phase.WRAP_UP && lg.phase != Phase.OFFSEASON) item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(C.card)
                    .border(1.dp, C.orange.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("▶", color = C.orange, fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${t.school} (${t.record})", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Season in Progress", color = C.orangeText, fontSize = 14.sp)
                }
                Text("${lg.season}", color = C.sub, fontSize = 16.sp)
            }
        }
        items(c.history.reversed(), key = { "ch${it.season}" }) { h ->
            Row(Modifier.fillMaxWidth().clickable { vm.push(Route.TeamPage(h.teamId)) }.padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(lg.team(h.teamId), 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${h.team} (${h.w}-${h.l})", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(h.result, color = if (h.result.contains("Champion")) C.yellow else C.sub, fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${h.season}", fontSize = 16.sp)
                    Text("Prestige ${h.prestige}", color = C.sub, fontSize = 13.sp)
                }
            }
            Divider(Modifier.padding(horizontal = Gutter))
        }
        item {
            SectionLabel("Program Stats · ${t.school}")
            val hist = t.history
            KeyValue("All-Time Wins (in your league)", "${hist.sumOf { it.w } + if (lg.phase == Phase.REGULAR || lg.phase == Phase.CONF_TOURNEY || lg.phase == Phase.NCAA) t.w else 0}")
            KeyValue("National Titles", "${t.titles}")
            KeyValue("Final Fours", "${t.finalFours}")
            KeyValue("Tournament Appearances", "${t.tourneys}")
            KeyValue("Conference Tournament Titles", "${t.confTitles}")
            SectionLabel("Dynasty Archive")
        }
        items(HistoryKind.entries) { k ->
            Row(Modifier.fillMaxWidth().clickable { vm.push(Route.History(k)) }.padding(horizontal = Gutter, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k.title, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Chevron()
            }
            Divider(Modifier.padding(horizontal = Gutter))
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

// ---------- History ----------

@Composable
fun HistoryScreen(vm: GameViewModel, lg: League, kind: HistoryKind) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackHeader(kind.title, kind.sub, onBack = { vm.back() }) }
        when (kind) {
            HistoryKind.SEASONS -> {
                if (lg.history.isEmpty()) item { EmptyNote("Finish a season to start the record.") }
                items(lg.history.reversed(), key = { "s${it.season}" }) { s ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${s.season}", color = C.orangeText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(66.dp))
                            TeamLogo(lg.team(s.champion), 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("🏆 ${lg.team(s.champion).name}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Runner-up: ${lg.team(s.runnerUp).school}", color = C.sub, fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Final Four: " + s.finalFour.joinToString(", ") { lg.team(it).school }, color = C.sub, fontSize = 14.sp)
                        s.awards.firstOrNull { it.award == "Nat MVP" }?.let { Text("Player of the Year: ${it.name} (${lg.team(it.teamId).school})", color = C.text, fontSize = 14.sp) }
                        if (s.cotyName.isNotEmpty()) Text("Coach of the Year: ${s.cotyName}", color = C.text, fontSize = 14.sp)
                    }
                }
            }
            HistoryKind.AWARDS -> {
                if (lg.history.isEmpty()) item { EmptyNote("Awards are handed out after the title game.") }
                for (s in lg.history.reversed()) {
                    item { SectionLabel("${s.season}") }
                    items(s.awards.filter { it.award in setOf("Nat MVP", "DPOY", "FOY", "MOP", "COTY") }, key = { "a${s.season}${it.award}${it.pid}" }) { a ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(a.award, color = C.orangeText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp))
                            TeamLogo(lg.team(a.teamId), 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text("${lg.team(a.teamId).school} · ${a.line}", color = C.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            HistoryKind.DRAFT -> {
                if (lg.draftHistory.isEmpty()) item { EmptyNote("No drafts yet.") }
                for ((season, picks) in lg.draftHistory.groupBy { it.season }.toSortedMap(compareByDescending { it })) {
                    item { SectionLabel("$season Draft") }
                    items(picks.take(30), key = { "d$season${it.pick}" }) { d ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("#${d.pick}", color = C.orangeText, fontSize = 16.sp, modifier = Modifier.width(46.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.playerName, color = if (d.teamId == lg.userTeamId) C.orangeText else C.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text("${d.school} · ${com.nh.hoopscoach.game.classLabel(d.cls)} · ${d.pos}", color = C.sub, fontSize = 13.sp)
                            }
                            RatingNum(d.ovr, 18.sp)
                        }
                    }
                }
            }
            HistoryKind.HOF -> {
                if (lg.hallOfFame.isEmpty()) item { EmptyNote("Nobody has been inducted yet. Multi-time All-Americans and national players of the year get in.") }
                items(lg.hallOfFame.reversed(), key = { "h${it.season}${it.name}" }) { h ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (h.isCoach) "🎓" else "⭐", fontSize = 22.sp, modifier = Modifier.width(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name + if (h.isCoach) " (Coach)" else " · ${h.pos}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("${h.school} · ${h.summary}", color = C.sub, fontSize = 14.sp)
                        }
                        Text("${h.season}", color = C.sub)
                    }
                    Divider(Modifier.padding(horizontal = Gutter))
                }
            }
            HistoryKind.RECORDS -> {
                items(lg.records, key = { "r${it.label}" }) { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (r.holder.isEmpty()) "Not set yet" else "${r.holder} · ${r.detail}", color = C.sub, fontSize = 14.sp)
                        }
                        Text("${r.value}", color = C.orangeText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    Divider(Modifier.padding(horizontal = Gutter))
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

// ---------- Settings & team editor ----------

@Composable
fun SettingsScreen(vm: GameViewModel, lg: League) {
    var confirm by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) vm.exportSave(uri) }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.importSave(uri) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackHeader("Settings", onBack = { vm.back() })
            SectionLabel("Save File")
            Column(Modifier.padding(horizontal = Gutter)) {
                Text("Back up your dynasty before reinstalling or switching phones.", color = C.sub, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Back Up Save", Modifier.weight(1f)) {
                        runCatching { exporter.launch("HoopsDynasty-${lg.user.abbr}-${lg.season}.hdsave") }.onFailure { vm.toast("No file app available") }
                    }
                    BigButton("Restore Save", Modifier.weight(1f), primary = false) {
                        runCatching { importer.launch(arrayOf("*/*")) }.onFailure { vm.toast("No file app available") }
                    }
                }
            }
            SectionLabel("Your School")
            Card(Modifier.fillMaxWidth().padding(horizontal = Gutter), onClick = { vm.push(Route.EditTeam(lg.userTeamId)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(lg.user, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(lg.user.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Rename, recolor or change the logo", color = C.sub, fontSize = 14.sp)
                    }
                    Chevron()
                }
            }
            SectionLabel("Edit Any School")
            Text("Every school is fictional. Rename them to whatever you like.", color = C.sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = Gutter))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search schools", color = C.dim) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter), colors = fieldColors(),
            )
        }
        val list = lg.teams.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.abbr.contains(query, ignoreCase = true) }.sortedBy { it.school }.take(40)
        items(list, key = { "e${it.id}" }) { t ->
            Row(Modifier.fillMaxWidth().clickable { vm.push(Route.EditTeam(t.id)) }.padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(t, 30.dp)
                Spacer(Modifier.width(12.dp))
                Text(t.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(lg.conf(t.confId).abbr, color = C.sub, fontSize = 14.sp)
            }
            Divider(Modifier.padding(horizontal = Gutter))
        }
        item {
            SectionLabel("Career")
            Column(Modifier.padding(horizontal = Gutter)) {
                BigButton("Start a New Career", Modifier.fillMaxWidth(), primary = false) { confirm = true }
                Spacer(Modifier.height(16.dp))
                Text("Hoops Dynasty · version 1.0\nYour progress saves automatically on this device.", color = C.dim, fontSize = 13.sp)

            }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = C.card2,
        title = { Text("Start over?", color = C.text) },
        text = { Text("This deletes your current dynasty and builds a brand-new league.", color = C.sub) },
        confirmButton = { TextButton({ confirm = false; vm.abandonCareer() }) { Text("Delete and start over", color = C.red) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel", color = C.sub) } },
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = C.text, unfocusedTextColor = C.text, focusedBorderColor = C.orange, unfocusedBorderColor = C.line,
    cursorColor = C.orange, focusedContainerColor = C.card, unfocusedContainerColor = C.card,
)

@Composable
fun EditTeamScreen(vm: GameViewModel, lg: League, teamId: Int) {
    val t = lg.team(teamId)
    var school by remember(teamId) { mutableStateOf(t.school) }
    var mascot by remember(teamId) { mutableStateOf(t.mascot) }
    var abbr by remember(teamId) { mutableStateOf(t.abbr) }
    var colors by remember(teamId) { mutableStateOf(t.primary to t.secondary) }
    var logo by remember(teamId) { mutableIntStateOf(t.logo) }
    val preview = Team(t.id, school, mascot, abbr.ifBlank { "?" }, t.confId, t.prestige, colors.first, colors.second, logo)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackHeader("Edit School", t.name, onBack = { vm.back() })
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) { TeamLogo(preview, 96.dp) }
            Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(school, { school = it.take(28) }, label = { Text("School") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(mascot, { mascot = it.take(20) }, label = { Text("Mascot") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                OutlinedTextField(abbr, { abbr = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(4) }, label = { Text("Abbreviation (up to 4)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            }
            SectionLabel("Logo Shape")
            Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                (0..3).forEach { i ->
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (i == logo) C.orangeDim else C.card).clickable { logo = i }.padding(10.dp)) {
                        TeamLogo(Team(t.id, school, mascot, abbr.ifBlank { "?" }, t.confId, t.prestige, colors.first, colors.second, i), 44.dp)
                    }
                }
            }
            SectionLabel("Colors")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Names.colors.forEach { pair ->
                    val sel = pair == colors
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Color(pair.first)).border(if (sel) 3.dp else 4.dp, if (sel) C.orange else Color(pair.second), CircleShape)
                            .clickable { colors = pair },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = Gutter)) {
                SmallButton("Swap primary and secondary") { colors = colors.second to colors.first }
            }
            Spacer(Modifier.height(20.dp))
            BigButton("Save", Modifier.fillMaxWidth().padding(horizontal = Gutter), enabled = school.isNotBlank() && mascot.isNotBlank() && abbr.isNotBlank()) {
                val s = school.trim(); val m = mascot.trim(); val a = abbr.trim(); val (p1, p2) = colors; val lo = logo
                vm.act { lg2 -> val tm = lg2.team(teamId); tm.school = s; tm.mascot = m; tm.abbr = a; tm.primary = p1; tm.secondary = p2; tm.logo = lo }
                vm.toast("Saved")
                vm.back()
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ---------- New game ----------

@Composable
fun NewGameScreen(vm: GameViewModel) {
    val restore = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.importSave(uri) }
    val lg = vm.draft
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var conf by remember { mutableIntStateOf(-1) }
    var pick by remember { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding()) {
        if (lg == null) { BusyScreen("Building your league"); return@Column }
        LazyColumn(Modifier.weight(1f)) {
            item {
                Text("HOOPS DYNASTY", color = C.orangeText, style = HoopsType.labelLarge, letterSpacing = 2.sp, modifier = Modifier.padding(start = Gutter, top = 20.dp))
                Text("Build a college basketball dynasty", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = Gutter))
                Text("Pick a program, recruit, develop players and chase banners. ${lg.teams.size} fictional schools in ${lg.confs.size} conferences.", color = C.sub, fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 6.dp))
                SectionLabel("Your Coach")
                Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(first, { first = it.take(16) }, placeholder = { Text("First name", color = C.dim) }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    OutlinedTextField(last, { last = it.take(18) }, placeholder = { Text("Last name", color = C.dim) }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                }
                Row(Modifier.fillMaxWidth().padding(end = Gutter), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Choose Your School", Modifier.weight(1f))
                    Text("New league ↻", color = C.orangeText, fontSize = 14.sp, modifier = Modifier.clickable { pick = -1; vm.generateDraft() }.padding(top = 14.dp))
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(-1) + lg.confs.map { it.id }).forEach { id ->
                        val sel = conf == id
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (sel) C.orange else C.card).clickable { conf = id }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(if (id < 0) "All" else lg.conf(id).abbr, color = if (sel) Color.Black else C.sub, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (conf >= 0) Text("${lg.conf(conf).name} · " + when (lg.conf(conf).tier) { 0 -> "Power conference"; 1 -> "Mid-major"; else -> "Low-major" },
                    color = C.sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp))
                Spacer(Modifier.height(6.dp))
            }
            val teams = lg.teams.filter { conf < 0 || it.confId == conf }.sortedByDescending { it.prestige }
            itemsIndexed(teams, key = { _, t -> "nt${t.id}" }) { _, t ->
                val sel = pick == t.id
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (sel) C.orangeDim else C.card).border(1.dp, if (sel) C.orange else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { pick = t.id }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamLogo(t, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${lg.conf(t.confId).abbr} · ${prestigeLabel(t.prestige)} · ${lg.coachOf(t).let { "roster ${com.nh.hoopscoach.game.Gameplan.teamRating(lg, t)}" }}", color = C.sub, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Stars(((t.prestige + 10) / 20).coerceIn(1, 5), 13.sp)
                        Text("${t.prestige}", color = ratingColor(t.prestige), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Gutter, vertical = 10.dp)) {
            val t = if (pick >= 0) lg.team(pick) else null
            BigButton(if (t == null) "Pick a school" else "Start Career at ${t.school}", Modifier.fillMaxWidth(), enabled = t != null) {
                vm.startCareer(pick, first.trim().ifBlank { "Coach" }, last.trim().ifBlank { "Rookie" })
            }
            Text(
                "Have a backup? Restore a save file", color = C.orangeText, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clickable { runCatching { restore.launch(arrayOf("*/*")) } }.padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Suppress("unused")
private val unusedPad = PaddingValues(0.dp)
