package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.Defense
import com.nh.hoopscoach.game.Gameplan
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Mood
import com.nh.hoopscoach.game.Morale
import com.nh.hoopscoach.game.Offense
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Recruit
import com.nh.hoopscoach.game.Recruiting
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Team
import com.nh.hoopscoach.game.Trait

private val PROGRAM_TABS = listOf("Summary", "Players", "Gameplan", "Training", "Recruit")

fun prestigeLabel(p: Int) = when {
    p >= 90 -> "Blueblood"; p >= 78 -> "Power Program"; p >= 64 -> "Contender"; p >= 50 -> "Mid-Major"; p >= 35 -> "Rebuilding"; else -> "Low-Major"
}

@Composable
fun jerseyOf(t: Team): Pair<Color, Color> {
    val p = Color(t.primary)
    val s = Color(t.secondary)
    return p to (if (s == p) Color.White else s)
}

@Composable
fun PlayerAvatar(p: Player, lg: League, size: androidx.compose.ui.unit.Dp) {
    val t = if (p.teamId >= 0) lg.team(p.teamId) else null
    val (j, trim) = if (t != null) jerseyOf(t) else Color(0xFF3A3F4B) to Color.White
    Avatar(p.look, j, p.number, size, trim = trim)
}

@Composable
fun ProgramScreen(vm: GameViewModel, lg: League) {
    val t = lg.user
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenTitle("Program") {
                if (vm.programTab == 2) SmallButton("Auto-Allocate") {
                    vm.act { Gameplan.autoAllocate(it, it.user); it.user.plan.auto = true }
                    vm.toast("Rotation set automatically")
                }
            }
            TeamHeader(vm, lg, t)
            Spacer(Modifier.height(14.dp))
            Segmented(PROGRAM_TABS, vm.programTab, { vm.programTab = it }, fontSize = 13.sp)
        }
        when (vm.programTab) {
            0 -> summary(vm, lg, t)
            1 -> roster(vm, lg, t)
            2 -> gameplan(vm, lg, t)
            3 -> training(vm, lg, t)
            else -> recruiting(vm, lg)
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
fun TeamHeader(vm: GameViewModel, lg: League, t: Team) {
    val standing = Season.standings(lg, t.confId).indexOf(t) + 1
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(t, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(teamLabel(t) + " " + t.mascot, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("#$standing in ${lg.conf(t.confId).abbr} · Coach ${lg.coachOf(t).name}", color = C.sub, fontSize = 15.sp, maxLines = 1)
        }
    }
    Spacer(Modifier.height(10.dp))
    val power = com.nh.hoopscoach.game.Rankings.power(t)
    val ovr = Gameplan.teamRating(lg, t)
    val off = Gameplan.teamRating(lg, t) { Ratings.offense(it) }
    val def = Gameplan.teamRating(lg, t) { Ratings.defense(it) }
    StatGrid(
        listOf(
            Triple("Record", t.record, C.text),
            Triple("Power", power.toString(), ratingColor(power)),
            Triple("Overall", ovr.toString(), ratingColor(ovr)),
            Triple("Offense", off.toString(), ratingColor(off)),
            Triple("Defense", def.toString(), ratingColor(def)),
        ),
        size = 24.sp,
    )
}

// ---------- Summary ----------

private fun LazyListScope.summary(vm: GameViewModel, lg: League, t: Team) {
    item {
        Spacer(Modifier.height(16.dp))
        val budget = com.nh.hoopscoach.game.Finance.budget(lg, t)
        val prevBudget = com.nh.hoopscoach.game.Finance.budgetAt(lg, t, t.prevPrestige)
        val spent = Morale.nilSpent(lg, t)
        val players = lg.rosterOf(t)
        val under = players.filter { val e = Ratings.expectedNil(it); e - it.nil > maxOf(25, e / 12) }
        val underAmt = under.sumOf { Ratings.expectedNil(it) - it.nil }
        val moods = players.groupingBy { Morale.mood(it) }.eachCount()
        Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard("Prestige", t.prestige.toString(), Modifier.weight(1f)) {
                    Text(prestigeLabel(t.prestige).uppercase(), color = C.orangeText, style = HoopsType.labelLarge, letterSpacing = 1.sp)
                }
                InfoCard("Budget", money(budget), Modifier.weight(1f)) {
                    val d = budget - prevBudget
                    Text(
                        if (d == 0) "Same as last year" else (if (d > 0) "↑ +" else "↓ -") + money(kotlin.math.abs(d)) + " vs last year",
                        color = if (d > 0) C.green else if (d < 0) C.red else C.sub, fontSize = 13.sp, maxLines = 1,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard("NIL Spend", money(spent), Modifier.weight(1f), valueSize = 26) {
                    Text(
                        if (under.isEmpty()) "Everyone at market" else "${money(underAmt)} under · ${under.size} player${if (under.size == 1) "" else "s"}",
                        color = if (under.isEmpty()) C.green else C.yellow, fontSize = 13.sp, maxLines = 1,
                    )
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(spent.toFloat() / budget, Modifier.fillMaxWidth(), color = if (spent > budget) C.red else C.green)
                }
                val risk = (moods[Mood.TRANSFER] ?: 0) + (moods[Mood.UNHAPPY] ?: 0)
                InfoCard("Happiness", if (risk == 0) "All Good" else "$risk At Risk", Modifier.weight(1f), valueSize = 22, valueColor = if (risk == 0) C.green else C.red) {
                    Text("${moods[Mood.HAPPY] ?: 0} Happy · ${risk} Unhappy", color = C.sub, fontSize = 13.sp, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                        val total = players.size.coerceAtLeast(1).toFloat()
                        for ((m, c) in listOf(Mood.HAPPY to C.green, Mood.CONTENT to C.dim, Mood.UNHAPPY to C.yellow, Mood.TRANSFER to C.red)) {
                            val n = moods[m] ?: 0
                            if (n > 0) Box(Modifier.weight(n / total).height(6.dp).background(c))
                        }
                    }
                }
            }
        }
    }
    val attention = lg.rosterOf(t).filter { Morale.mood(it) == Mood.TRANSFER || Morale.mood(it) == Mood.UNHAPPY }.sortedBy { it.happiness }
    item { SectionLabel("Attention ${if (attention.isEmpty()) "" else attention.size}") }
    if (attention.isEmpty()) item { EmptyNote("No one needs your attention right now.") }
    items(attention, key = { it.id }) { p ->
        Row(Modifier.fillMaxWidth().clickable { vm.openPlayer(p.id) }.padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(p, lg, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val m = Morale.mood(p)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = if (m == Mood.TRANSFER) C.red else C.yellow, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(m.label, color = if (m == Mood.TRANSFER) C.red else C.yellow, fontSize = 15.sp)
                }
                Text(Morale.reason(lg, t, p), color = C.sub, fontSize = 14.sp)
            }
            Text(p.cls, color = C.sub, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 6.dp))
            Text(p.pos.name, color = C.sub, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 6.dp))
            RatingNum(p.ovr, 28.sp)
        }
        Divider(Modifier.padding(horizontal = Gutter))
    }
    item {
        SectionLabel("Coaching Staff")
        val c = lg.coachOf(t)
        StatGrid(
            listOf(
                Triple("Offense", c.off.toString(), ratingColor(c.off)),
                Triple("Defense", c.def.toString(), ratingColor(c.def)),
                Triple("Recruit", c.rec.toString(), ratingColor(c.rec)),
                Triple("Develop", c.dev.toString(), ratingColor(c.dev)),
            ),
            columns = 4,
        )
    }
}

@Composable
private fun InfoCard(
    label: String, value: String, modifier: Modifier, valueSize: Int = 34, valueColor: Color = C.text,
    extra: @Composable () -> Unit,
) {
    Card(modifier.heightIn(min = 128.dp)) {
        Text(label.uppercase(), style = HoopsType.labelMedium, color = C.sub)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = valueSize.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        extra()
    }
}

// ---------- Players ----------

fun traitColor(t: Trait) = when (t) {
    Trait.AMBITIOUS -> C.orangeText; Trait.BAG_CHASER -> C.yellow; Trait.DISLOYAL -> C.red
    Trait.LOYAL -> C.green; Trait.TEAM_PLAYER -> C.green; Trait.GYM_RAT -> C.blue; Trait.CLUTCH -> C.blue
}

private fun LazyListScope.roster(vm: GameViewModel, lg: League, t: Team) {
    val slots = t.plan.slots
    val starters = slots.mapNotNull { s -> lg.playerOrNull(s.starter)?.takeIf { it.teamId == t.id } }
    val reserves = slots.mapNotNull { s -> lg.playerOrNull(s.reserve)?.takeIf { it.teamId == t.id } }
    val bench = lg.rosterOf(t).filter { it !in starters && it !in reserves }.sortedByDescending { it.ovr }
    item { SectionLabel("Starters", action = "Gameplan", onAction = { vm.programTab = 2 }) }
    items(starters, key = { "s${it.id}" }) { PlayerRow(vm, lg, t, it) }
    item { SectionLabel("Rotation") }
    items(reserves, key = { "r${it.id}" }) { PlayerRow(vm, lg, t, it) }
    item { SectionLabel("Bench Warmers") }
    items(bench, key = { "b${it.id}" }) { PlayerRow(vm, lg, t, it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerRow(vm: GameViewModel, lg: League, t: Team, p: Player) {
    val mins = Gameplan.plannedMinutes(t, p.id)
    val role = Gameplan.role(t, p.id)
    Row(Modifier.fillMaxWidth().clickable { vm.openPlayer(p.id) }.padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(p, lg, 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.short, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                when (Morale.mood(p)) {
                    Mood.TRANSFER -> { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Warning, "At risk", tint = C.red, modifier = Modifier.size(16.dp)) }
                    Mood.UNHAPPY -> { Spacer(Modifier.width(6.dp)); Text("☹", color = C.yellow, fontSize = 15.sp) }
                    else -> {}
                }
            }
            if (role != "Bench") Text("Expected $role ($mins MPG)", color = C.sub, fontSize = 14.sp)
            else Text(p.style.label, color = C.sub, fontSize = 14.sp)
            if (p.stats.gp > 0) Text("${f1(p.stats.ppg)}p  ${f1(p.stats.apg)}a  ${f1(p.stats.rpg)}r", color = C.sub, fontSize = 14.sp)
            if (p.traits.isNotEmpty() || p.country != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.traits.forEach { Text(it.label, color = traitColor(it), fontSize = 14.sp) }
                    p.country?.let { Text("✈ $it", color = C.blue, fontSize = 14.sp) }
                }
            }
        }
        Text(p.cls, color = C.sub, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 5.dp))
        Text(p.pos.name, color = C.sub, fontSize = 15.sp, modifier = Modifier.width(30.dp))
        RatingNum(p.ovr, 28.sp, Modifier.width(44.dp))
    }
    Divider(Modifier.padding(horizontal = Gutter))
}

// ---------- Gameplan ----------

private fun LazyListScope.gameplan(vm: GameViewModel, lg: League, t: Team) {
    item {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("OFFENSE", style = HoopsType.labelMedium, color = C.sub)
                Spacer(Modifier.height(6.dp))
                Picker(t.plan.offense.label, Offense.entries.map { it.label to it.blurb }) { i -> vm.act { it.user.plan.offense = Offense.entries[i] } }
            }
            Column(Modifier.weight(1f)) {
                Text("DEFENSE", style = HoopsType.labelMedium, color = C.sub)
                Spacer(Modifier.height(6.dp))
                Picker(t.plan.defense.label, Defense.entries.map { it.label to it.blurb }) { i -> vm.act { it.user.plan.defense = Defense.entries[i] } }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 18.dp, bottom = 8.dp)) {
            Text("STARTER", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.weight(1f))
            Text("RESERVE", style = HoopsType.labelMedium, color = C.sub)
        }
    }
    items(Pos.entries) { pos -> SlotCard(vm, lg, t, pos.ordinal) }
}

@Composable
fun Picker(current: String, options: List<Pair<String, String>>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, C.line, RoundedCornerShape(12.dp)).background(C.card)
                .clickable { open = true }.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current, color = C.orangeText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, null, tint = C.sub)
        }
        DropdownMenu(open, { open = false }, Modifier.background(C.card2)) {
            options.forEachIndexed { i, (label, blurb) ->
                DropdownMenuItem(
                    text = { Column { Text(label, color = C.text, fontSize = 16.sp); Text(blurb, color = C.sub, fontSize = 13.sp) } },
                    onClick = { open = false; onPick(i) },
                )
            }
        }
    }
}

@Composable
private fun SlotCard(vm: GameViewModel, lg: League, t: Team, idx: Int) {
    val slot = t.plan.slots.getOrNull(idx) ?: return
    val st = lg.playerOrNull(slot.starter)
    val rs = lg.playerOrNull(slot.reserve)
    var choose by remember { mutableStateOf<Boolean?>(null) }
    var minutes by remember(slot.starter, slot.reserve, vm.rev) { mutableIntStateOf(slot.starterMin) }
    var su by remember(slot.starter, vm.rev) { mutableIntStateOf(slot.starterUsg) }
    var ru by remember(slot.reserve, vm.rev) { mutableIntStateOf(slot.reserveUsg) }
    Card(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { choose = true }, verticalAlignment = Alignment.CenterVertically) {
                if (st != null) RatingNum(st.ovr, 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(st?.short ?: "Pick starter", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(Pos.entries[idx].name, color = C.dim, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Row(Modifier.weight(1f).clickable { choose = false }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text(rs?.short ?: "Pick reserve", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                if (rs != null) RatingNum(rs.ovr, 22.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$minutes min", color = C.orangeText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(58.dp))
            HoopsSlider(
                value = minutes.toFloat(), onValueChange = { minutes = kotlin.math.round(it).toInt() }, range = 0f..40f,
                onFinished = { val m = minutes; vm.act { slot.starterMin = m; it.user.plan.auto = false } },
                enabled = rs != null, modifier = Modifier.weight(1f),
            )
            Text("${40 - minutes} min", color = C.sub, fontSize = 14.sp, modifier = Modifier.width(54.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            UsageSlider("USG", su, Modifier.weight(1f)) { v -> su = v; vm.act { slot.starterUsg = v } }
            UsageSlider("USG", ru, Modifier.weight(1f), enabled = rs != null) { v -> ru = v; vm.act { slot.reserveUsg = v } }
        }
    }
    if (choose != null) {
        val starter = choose == true
        AlertDialog(
            onDismissRequest = { choose = null },
            containerColor = C.card2,
            title = { Text(if (starter) "Starting ${Pos.entries[idx]}" else "Backup ${Pos.entries[idx]}", color = C.text) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(lg.rosterOf(t).sortedWith(compareByDescending<Player> { it.pos.ordinal == idx }.thenByDescending { it.ovr })) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                choose = null
                                vm.act { Gameplan.swapInto(it, it.user, idx, starter, p.id) }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.pos.name, color = C.sub, modifier = Modifier.width(34.dp))
                            Text(p.short, color = C.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(Gameplan.role(t, p.id), color = C.dim, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
                            RatingNum(p.ovr, 18.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ choose = null }) { Text("Close", color = C.orangeText) } },
        )
    }
}

@Composable
private fun UsageSlider(label: String, value: Int, modifier: Modifier, enabled: Boolean = true, onSet: (Int) -> Unit) {
    var v by remember(value) { mutableIntStateOf(value) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, color = C.sub, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("$v", color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        HoopsSlider(
            value = v.toFloat(), onValueChange = { v = kotlin.math.round(it).toInt() }, range = 1f..99f, enabled = enabled,
            onFinished = { onSet(v) }, active = Color(0xFF8A4A26),
        )
    }
}

// ---------- Training ----------

private fun LazyListScope.training(vm: GameViewModel, lg: League, t: Team) {
    val slots = t.plan.slots
    val starters = slots.mapNotNull { lg.playerOrNull(it.starter)?.takeIf { p -> p.teamId == t.id } }
    val reserves = slots.mapNotNull { lg.playerOrNull(it.reserve)?.takeIf { p -> p.teamId == t.id } }
    val bench = lg.rosterOf(t).filter { it !in starters && it !in reserves }
    item {
        Text(
            "Players earn XP from games and practice. Every ${Ratings.xpNeeded()} XP is a level-up that raises the three ratings of their training focus.",
            color = C.sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = Gutter, vertical = 14.dp),
        )
    }
    for ((label, list) in listOf("Starters" to starters, "Rotation" to reserves, "Bench" to bench)) {
        item { SectionLabel(label) }
        items(list, key = { "t$label${it.id}" }) { p -> TrainingRow(vm, lg, p) }
    }
}

@Composable
private fun TrainingRow(vm: GameViewModel, lg: League, p: Player) {
    Row(Modifier.fillMaxWidth().clickable { vm.openPlayer(p.id) }.padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(p, lg, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.short, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${p.pos} · ${p.focus.label}", color = C.sub, fontSize = 14.sp, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            ProgressBar(p.xp.toFloat() / Ratings.xpNeeded(), Modifier.fillMaxWidth(0.85f), height = 5.dp)
        }
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(C.card).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("⚡ ${p.banked}", color = if (p.banked > 0) C.yellow else C.dim, fontSize = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).background(C.orangeDim).clickable { vm.push(Route.Focus(p.id)) }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(p.focus.short, color = C.orangeText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Edit, null, tint = C.orangeText, modifier = Modifier.size(15.dp))
        }
    }
    Divider(Modifier.padding(horizontal = Gutter))
}

@Composable
fun TrainingFocusScreen(vm: GameViewModel, lg: League, playerId: Int) {
    val p = lg.playerOrNull(playerId) ?: return
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            BackHeader("Training Focus", "Choose an archetype for ${p.name}", onBack = { vm.back() })
            if (p.banked > 0) {
                Text(
                    "${p.banked} banked level-up${if (p.banked > 1) "s" else ""}: pick an archetype below to spend them.",
                    color = C.yellow, fontSize = 14.sp, modifier = Modifier.padding(horizontal = Gutter, vertical = 6.dp),
                )
            }
        }
        items(Arch.entries) { a ->
            val current = p.focus == a
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (current) C.orangeDim else C.card)
                    .border(1.dp, if (current) C.orange.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable {
                        vm.act {
                            val pl = it.player(playerId)
                            if (a != Arch.MANUAL && pl.banked > 0) Ratings.spendBanked(pl, a)
                            pl.focus = a
                        }
                        vm.back()
                    }.padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = if (current) C.orangeText else C.text, modifier = Modifier.weight(1f))
                    if (current) Pill("CURRENT", C.orangeText)
                    else if (a == p.style) Pill("NATURAL", C.green)
                }
                Spacer(Modifier.height(4.dp))
                Text(a.blurb, color = C.sub, fontSize = 15.sp)
                if (a.boosts.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        a.boosts.forEach { Pill("${it.short} +1", Color.White, bg = C.orange, size = 13.sp) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------- Recruiting ----------

private fun LazyListScope.recruiting(vm: GameViewModel, lg: League) {
    val u = lg.user
    val open = Recruiting.openSpots(lg, u)
    val pending = Recruiting.pendingOffers(lg)
    val commits = lg.recruits.filter { it.committed == u.id }
    item {
        Spacer(Modifier.height(14.dp))
        if (lg.recruits.isEmpty()) {
            EmptyNote("The next recruiting class opens when the new season starts.")
            return@item
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Row {
                StatCell("Points", "${lg.recruitPoints}", Modifier.weight(1f), C.orangeText)
                StatCell("Open", "$open", Modifier.weight(1f), if (open > 0) C.green else C.red)
                StatCell("Offers", "$pending", Modifier.weight(1f))
                StatCell("Commits", "${commits.size}", Modifier.weight(1f))
                val rank = Recruiting.classRank(lg, u.id)
                StatCell("Class", if (rank > 0) "#$rank" else "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "You get ${Recruiting.dailyPoints(lg)} points a day. Calls cost ${Recruiting.COST_CALL}, home visits ${Recruiting.COST_VISIT}, scouting ${Recruiting.COST_SCOUT}. A recruit commits once his interest passes 100 and you've offered.",
                color = C.sub, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
    if (lg.recruits.isEmpty()) return
    item {
        Segmented(listOf("My Board", "Top Prospects", "Commits"), vm.recruitView, { vm.recruitView = it })
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val opts = listOf<Pos?>(null) + Pos.entries
            opts.forEach { pos ->
                val sel = vm.recruitPos == pos
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) C.orangeDim else C.card).clickable { vm.recruitPos = pos }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(pos?.name ?: "All", color = if (sel) C.orangeText else C.sub, fontSize = 14.sp) }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    val list = when (vm.recruitView) {
        0 -> lg.recruits.filter { it.onBoard || u.id in it.offers || it.committed == u.id }
        1 -> lg.recruits.filter { it.committed < 0 }.take(200)
        else -> commits
    }.filter { vm.recruitPos == null || it.p.pos == vm.recruitPos }.sortedBy { it.p.recruitRank }
    if (list.isEmpty()) item {
        EmptyNote(if (vm.recruitView == 0) "Your board is empty. Add prospects from Top Prospects." else "No one here yet.")
    }
    items(list, key = { "rec${it.p.id}" }) { r -> RecruitRow(vm, lg, r) }
}

@Composable
private fun RecruitRow(vm: GameViewModel, lg: League, r: Recruit) {
    val u = lg.user
    val mine = r.interest[u.id] ?: 0
    val leader = r.interest.entries.filter { it.key != u.id }.maxByOrNull { it.value }
    Row(Modifier.fillMaxWidth().clickable { vm.push(Route.RecruitView(r.p.id)) }.padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("#${r.p.recruitRank}", color = C.sub, fontSize = 13.sp)
            Text("${r.p.stars}★", color = C.yellow, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(r.p.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.p.pos} · ${Ratings.heightLabel(r.p.height)} · ${r.p.hometown}", color = C.sub, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.height(5.dp))
            when {
                r.committed == u.id -> Text("Committed to you", color = C.green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                r.committed >= 0 -> Text("Committed to ${lg.team(r.committed).school}", color = C.dim, fontSize = 13.sp)
                else -> {
                    ProgressBar(mine / 100f, Modifier.fillMaxWidth(0.9f), height = 5.dp, color = if (u.id in r.offers) C.orange else C.dim)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "You $mine" + (leader?.let { " · ${lg.team(it.key).abbr} ${it.value}" } ?: "") + if (u.id in r.offers) " · Offered" else "",
                        color = C.sub, fontSize = 12.sp, maxLines = 1,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (r.scouted) "${r.estOvr}" else "~${r.estOvr}", color = ratingColor(r.estOvr + 8), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("POT ${if (r.scouted) "" else "~"}${r.estPot}", color = C.sub, fontSize = 12.sp)
        }
    }
    Divider(Modifier.padding(horizontal = Gutter))
}

@Composable
fun RecruitScreen(vm: GameViewModel, lg: League, playerId: Int) {
    val r = lg.recruits.firstOrNull { it.p.id == playerId }
    if (r == null) {
        Column { BackHeader("Recruit", onBack = { vm.back() }); EmptyNote("This prospect has already signed.") }
        return
    }
    val u = lg.user
    val p = r.p
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            BackHeader(p.name, "${p.stars}★ ${p.pos} · #${p.recruitRank} overall · #${p.posRank} ${p.pos}", onBack = { vm.back() })
            Row(Modifier.padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.look, Color(0xFF3A3F4B), null, 76.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Stars(p.stars, 20.sp)
                    Text("${Ratings.heightLabel(p.height)} · ${p.weight} lbs", color = C.text, fontSize = 15.sp)
                    Text("${p.hometown} · ${p.highSchool}", color = C.sub, fontSize = 14.sp)
                    if (p.traits.isNotEmpty()) Text(p.traits.joinToString(" · ") { it.label }, color = C.sub, fontSize = 14.sp)
                }
            }
            StatGrid(
                listOf(
                    Triple("Overall", (if (r.scouted) "" else "~") + r.estOvr, ratingColor(r.estOvr + 8)),
                    Triple("Potential", (if (r.scouted) "" else "~") + r.estPot, ratingColor(r.estPot)),
                    Triple("Style", p.style.short, C.text),
                    Triple("NIL Ask", money(r.nilAsk), C.text),
                ),
                columns = 4, size = 20.sp,
            )
        }
        item {
            SectionLabel("Your Pitch")
            Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Points available", color = C.sub, modifier = Modifier.weight(1f))
                    Text("${lg.recruitPoints}", color = C.orangeText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                val committed = r.committed >= 0
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallButton("Scout (${Recruiting.COST_SCOUT})", Modifier.weight(1f), enabled = !r.scouted) {
                        vm.act { if (!Recruiting.scout(it, r)) vm.toast("Not enough points") }
                    }
                    SmallButton("Call (${Recruiting.COST_CALL})", Modifier.weight(1f), enabled = !committed) {
                        vm.act { if (!Recruiting.call(it, r)) vm.toast("Not enough points") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallButton("Home Visit (${Recruiting.COST_VISIT})", Modifier.weight(1f), enabled = !committed && Recruiting.visitReady(lg, r)) {
                        vm.act { if (!Recruiting.visit(it, r)) vm.toast("Not enough points") }
                    }
                    if (u.id in r.offers) SmallButton("Withdraw Offer", Modifier.weight(1f), enabled = !committed) { vm.act { Recruiting.withdraw(it, r) } }
                    else SmallButton("Offer Scholarship", Modifier.weight(1f), primary = true, enabled = !committed) {
                        vm.act { if (!Recruiting.offer(it, r)) vm.toast("No scholarships left to offer") }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NIL offer", color = C.sub, fontSize = 13.sp)
                        Text(money(r.nilOffer), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    SmallButton("−") { vm.act { Recruiting.setNil(it, r, r.nilOffer - 25) } }
                    Spacer(Modifier.width(8.dp))
                    SmallButton("+") { vm.act { Recruiting.setNil(it, r, r.nilOffer + 25) } }
                    Spacer(Modifier.width(8.dp))
                    SmallButton("Match ask") { vm.act { Recruiting.setNil(it, r, r.nilAsk) } }
                }
                if (!r.onBoard) {
                    Spacer(Modifier.height(12.dp))
                    BigButton("Add to Board", Modifier.fillMaxWidth(), primary = false) { vm.act { Recruiting.addToBoard(it, r) } }
                }
            }
        }
        item {
            SectionLabel("Interest")
            val ranked = r.interest.entries.sortedByDescending { it.value }.take(6)
            ranked.forEach { (tid, v) ->
                val t = lg.team(tid)
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(t, 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(t.school, color = if (tid == u.id) C.orangeText else C.text, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            if (tid in r.offers) Text("Offered", color = C.green, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        ProgressBar(v / 100f, Modifier.fillMaxWidth(), color = if (tid == u.id) C.orange else C.dim, height = 5.dp)
                    }
                    Text("$v", fontSize = 16.sp, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
            if (r.committed >= 0) Text("Committed to ${lg.team(r.committed).school}", color = C.green, modifier = Modifier.padding(Gutter))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Suppress("unused")
private val pad = PaddingValues(0.dp)
