package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Ancestor
import com.nh.hoopscoach.game.Attr
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LegacyMode
import com.nh.hoopscoach.game.PRO_YEAR
import com.nh.hoopscoach.game.Pos

/** A rating as a phrase: "the three", "perimeter defense". */
private fun skillWords(a: Attr) = when (a) {
    Attr.INS -> "finishing inside"; Attr.MID -> "the mid-range"; Attr.OUT -> "the three"; Attr.HND -> "ball handling"; Attr.PAS -> "passing"
    Attr.REB -> "rebounding"; Attr.PER -> "perimeter defense"; Attr.IDF -> "interior defense"; Attr.STL -> "steals"; Attr.BLK -> "shot blocking"
}

private fun clsName(c: Int) = when (c) { 1 -> "Freshman"; 2 -> "Sophomore"; 3 -> "Junior"; 4 -> "Senior"; else -> "Grad" }

/** The father, drawn: his face in his school's colors. */
@Composable
private fun AncestorPortrait(lg: League, a: Ancestor, size: androidx.compose.ui.unit.Dp) {
    val t = lg.teams.firstOrNull { it.school == a.school }
    BasketballCharacterCanvas(
        face = Face.decode(a.face) ?: Face.random(a.name.hashCode().toLong()), jerseyKey = SpriteSheets.jerseyKey(t?.abbr, t?.slot, t?.name),
        primary = (t?.primary ?: 0xFF5C6B7AL).toInt(), secondary = (t?.secondary ?: 0xFFFFFFFFL).toInt(), number = a.number, label = t?.abbr,
        background = Color(0xFF1A1714), corner = 12.dp, headshot = true, modifier = Modifier.size(size),
    )
}

/**
 * At the end of a playing career: hand it to the next generation. Play his son's career, or coach him at the
 * old school. Both start a new save; this career stays where it is.
 */
@Composable
fun NextGeneration(vm: GameViewModel, lg: League) {
    if (!LegacyMode.available(lg)) return
    val h = lg.hero ?: return
    val dad = remember(lg) { LegacyMode.ancestorOf(lg) } ?: return
    val years = LegacyMode.gap(lg)
    val stars = LegacyMode.sonStars(dad)
    val skills = dad.strengths.map { skillWords(Attr.entries[it]) }
    var play by remember { mutableStateOf(false) }
    var coach by remember { mutableStateOf(false) }
    Section("The next generation")
    Panel(color = P.hero) {
        Text("$years YEARS LATER", style = T.label, color = P.onHero.copy(alpha = 0.8f))
        Text("YOUR SON", style = T.hero.copy(fontSize = 34.sp, lineHeight = 34.sp), color = P.onHero)
        Text(
            "A high-school senior with your name, your No. ${h.number}, your build and an edge in what you did best: ${skills.joinToString(", ")}. " +
                (if (stars == 5) "Every program in the country knows the name." else "College coaches know the name.") +
                " Your career goes up in ${dad.school}'s history, and the press won't let him forget whose kid he is.",
            style = T.small, color = P.onHero.copy(alpha = 0.92f), modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        // On the hero color the app's own buttons disappear: white for the main choice, glass for the other.
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { play = true }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text("Play as your son", style = T.bodyB, color = P.hero)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).clickable { coach = true }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text("Coach him at ${dad.school}", style = T.bodyB, color = P.onHero)
        }
        Text("Either one starts a new save. This career stays as it is.", style = T.small.copy(fontSize = 12.sp), color = P.onHero.copy(alpha = 0.7f),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
    if (play) {
        var first by remember { mutableStateOf(dad.first) }
        var pos by remember { mutableStateOf(Pos.entries.firstOrNull { it.name == dad.pos } ?: Pos.SG) }
        AlertDialog(
            onDismissRequest = { play = false }, containerColor = P.surface,
            title = { Text("Your son", style = T.head, color = P.ink) },
            text = {
                Column {
                    OutlinedTextField(first, { first = it.take(16) }, label = { Text("First name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    Spacer(Modifier.height(12.dp))
                    Text("POSITION", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pos.entries.forEach { p -> SmallButton(p.name, primary = p == pos) { pos = p } }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("${first.trim().ifEmpty { dad.first }} ${LegacyMode.sonLast(dad, first)} · $pos · No. ${h.number} · $stars★",
                        style = T.bodyB, color = P.accent)
                    Text("Named for you, he's ${LegacyMode.sonLast(dad, dad.first).substringAfterLast(" ")}. ${dad.school} will offer him a scholarship.",
                        style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp))
                }
            },
            confirmButton = { TextButton({ play = false; vm.legacyPlay(first, pos) }) { Text("See his offers", style = T.bodyB, color = P.accent) } },
            dismissButton = { TextButton({ play = false }) { Text("Cancel", color = P.sub) } },
        )
    }
    if (coach) AlertDialog(
        onDismissRequest = { coach = false }, containerColor = P.surface,
        title = { Text("Coach your son?", style = T.head, color = P.ink) },
        text = {
            Text("You take the job at ${dad.school} for the ${lg.season + years} season. Your son is a $stars-star in this year's recruiting class. " +
                "He leans your way, but other programs want him too: you'll still have to recruit him.", style = T.body, color = P.sub)
        },
        confirmButton = { TextButton({ coach = false; vm.legacyCoach() }) { Text("Take the job", style = T.bodyB, color = P.accent) } },
        dismissButton = { TextButton({ coach = false }) { Text("Not yet", color = P.sub) } },
    )
}

/** Player mode, a son's career: his father (and his father's father), and the two of them year by year. */
@Composable
fun FamilyPanel(lg: League) {
    val dad = LegacyMode.father(lg) ?: return
    val h = lg.hero ?: return
    Section(if (lg.lineage.size > 1) "Your family" else "Your father")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AncestorPortrait(lg, dad, 64.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("YOUR FATHER", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                Text(dad.name, style = T.title.copy(fontSize = 20.sp, lineHeight = 22.sp), color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dad.played.verdict, style = T.small, color = P.accent)
                Text("${dad.played.college} · ${dad.played.collegeYears}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dad.played.draft + if (dad.played.proSeasons > 0) " · ${dad.played.proSeasons} pro seasons" else "", style = T.small, color = P.sub, maxLines = 1)
            }
        }
        val honors = (dad.played.honors + dad.played.collegeHonors).take(4)
        if (honors.isNotEmpty()) Text(honors.joinToString(" · "), style = T.small, color = P.gold, modifier = Modifier.padding(top = 8.dp))
        // You and him, class by class.
        val mine = h.career.filter { it.cls != PRO_YEAR && it.s.gp > 0 }.associate { it.cls to it.s.ppg }.toMutableMap()
        if (!lg.pro && h.stats.gp > 0) mine[h.year] = h.stats.ppg
        val his = dad.seasons.associate { it.cls to it.ppg }
        val classes = (mine.keys + his.keys).filter { it in 1..5 }.sorted()
        if (classes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Hairline()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("POINTS PER GAME", style = T.label.copy(fontSize = 9.sp), color = P.faint, modifier = Modifier.weight(1f))
                Text("YOU", style = T.label.copy(fontSize = 9.sp), color = P.accent, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                Text("HIM", style = T.label.copy(fontSize = 9.sp), color = P.faint, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
            }
            classes.forEach { c ->
                val a = mine[c]; val b = his[c]
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(clsName(c), style = T.small, color = P.ink, modifier = Modifier.weight(1f))
                    Text(a?.let { "%.1f".format(it) } ?: "—", style = T.numSm, color = if (a != null && b != null && a > b) P.good else P.ink,
                        modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                    Text(b?.let { "%.1f".format(it) } ?: "—", style = T.numSm, color = P.sub, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                }
            }
        }
        lg.lineage.dropLast(1).asReversed().forEachIndexed { i, g ->
            Text("Your ${"great-".repeat(i)}grandfather, ${g.name}: ${g.played.verdict.lowercase()}, ${g.played.college}.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Coach mode, legacy: your son, a recruit in this class or a player now. */
@Composable
fun YourSonPanel(vm: GameViewModel, lg: League) {
    val id = lg.legacySonId
    if (id < 0) return
    val p = lg.playerOrNull(id)
    val r = if (p == null) lg.recruits.firstOrNull { it.p.id == id } else null
    val son = p ?: r?.p ?: return
    Section("Your son")
    Panel(onClick = { if (p != null) vm.openPlayer(id) else vm.push(Route.RecruitView(id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerJersey(son, lg.teams.getOrNull(son.teamId), 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(son.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = when {
                    p != null && p.teamId == lg.userTeamId -> "Plays for you · ${p.cls}" + if (p.stats.gp > 0) " · %.1f ppg".format(p.stats.ppg) else ""
                    p != null && p.teamId >= 0 -> "Plays for ${lg.team(p.teamId).school}" + if (p.stats.gp > 0) " · %.1f ppg".format(p.stats.ppg) else ""
                    r != null && r.committed == lg.userTeamId -> "Committed to you"
                    r != null && r.committed >= 0 -> "Committed to ${lg.team(r.committed).school}"
                    r != null -> "No. ${son.recruitRank} recruit · ${son.stars}★ · your interest ${r.interest[lg.userTeamId] ?: 0}"
                    else -> son.left.ifEmpty { "" }
                }
                Text(line, style = T.small, color = P.sub, maxLines = 2)
            }
            Chevron()
        }
    }
}

/**
 * Today, for a son (legacy mode): how big the name is, this season against his father's at the same point,
 * the father's marks he's chasing, whether his father will be in the stands next game, and the badges he
 * was born with a head start on.
 */
@Composable
fun LegacyCard(vm: GameViewModel, lg: League) {
    val dad = LegacyMode.father(lg) ?: return
    val h = lg.hero ?: return
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val tier = LegacyMode.tier(lg)
    val marks = LegacyMode.marks(lg)
    val family = dad.last.substringBefore(" Jr.").substringBefore(" II").substringBefore(" IV").trim()
    val r = lg.road
    val F = com.nh.hoopscoach.game.Family
    Section("The $family legacy", action = "You vs Dad", onAction = { vm.push(Route.FamilyCompare) })
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AncestorPortrait(lg, dad, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("GENERATION ${lg.lineage.size + 1} · ${LegacyMode.TIER_NAMES[tier].uppercase()}", style = T.label.copy(fontSize = 10.sp), color = P.gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dad.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(LegacyMode.resume(dad).replaceFirstChar { it.uppercase() } + ".", style = T.small, color = P.sub, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        // The two of them: how close they are, and what that does.
        if (r != null) {
            Spacer(Modifier.height(10.dp))
            Hairline()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("YOU AND YOUR FATHER", style = T.label.copy(fontSize = 9.sp), color = P.faint, modifier = Modifier.weight(1f))
                Text(F.bondLabel(r.dadBond).uppercase(), style = T.label.copy(fontSize = 10.sp), color = bondColor(r.dadBond))
            }
            Bar(r.dadBond / 100f, Modifier.fillMaxWidth().padding(top = 5.dp), color = bondColor(r.dadBond), height = 5.dp)
            Text(when {
                r.dadBond >= 80 -> "Close. Seeing him in the stands lifts you more than anyone."
                r.dadBond >= 55 -> "Good. He's at the big games, and it helps."
                r.dadBond >= 25 -> "Distant. He still comes to the big ones, but it doesn't lift you like it used to."
                else -> "Strained. He's skipping the regular-season trips. Pick up when he calls."
            }, style = T.small, color = P.sub, modifier = Modifier.padding(top = 4.dp))
            val next = F.nextGame(lg)
            if (next != null && r.dadPrep == next.id) Text("Film session with your father done: a lift on your shots next game.", style = T.small, color = P.good, modifier = Modifier.padding(top = 4.dp))
            if (r.feudWith.isNotEmpty()) com.nh.hoopscoach.game.Rival.player(lg)?.let { rv ->
                Text("The feud: ${rv.name}, son of your father's rival ${r.feudWith}" + (lg.teams.getOrNull(rv.teamId)?.let { ", plays for ${if (lg.pro) it.name else it.school}." } ?: "."),
                    style = T.small, color = P.bad, modifier = Modifier.padding(top = 4.dp))
            }
        }
        // Once a season, before the first games: a workout on one of his best skills.
        if (F.workoutOpen(lg)) {
            Spacer(Modifier.height(10.dp))
            Hairline()
            Text("PRESEASON WORKOUT WITH DAD", style = T.label.copy(fontSize = 10.sp), color = P.accent, modifier = Modifier.padding(top = 8.dp))
            Text("He'll work with you on one of the things he did best. Pick one: +${F.workoutGain(lg)}, more the closer you are.", style = T.small, color = P.sub)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dad.strengths.mapNotNull { Attr.entries.getOrNull(it) }.forEach { a ->
                    SmallButton("${a.label} +${F.workoutGain(lg)}", Modifier.weight(1f)) {
                        var msg = ""
                        vm.act { msg = F.workout(it, a.ordinal) }
                        if (msg.isNotEmpty()) vm.toast(msg)
                    }
                }
            }
        }
        // This season against his father's at the same point.
        val dadPpg = if (!lg.pro) dad.seasons.firstOrNull { it.cls == h.year }?.ppg else LegacyMode.proPpg(dad).takeIf { it > 0 }
        if (dadPpg != null) {
            Spacer(Modifier.height(10.dp))
            Hairline()
            val mine = h.stats.ppg
            val ahead = h.stats.gp > 0 && mine >= dadPpg
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (!lg.pro) "THIS SEASON VS DAD AS A ${clsName(h.year).uppercase()}" else "THIS SEASON VS DAD'S NBA AVERAGE", style = T.label.copy(fontSize = 9.sp), color = P.faint)
                    Text(when {
                        h.stats.gp == 0 -> "The season hasn't started."
                        ahead -> "Out of his shadow so far."
                        mine >= dadPpg - 2 -> "Right on his heels."
                        else -> "Still chasing him."
                    }, style = T.small, color = if (ahead) P.good else P.sub)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (h.stats.gp > 0) "%.1f".format(mine) else "—", style = T.numSm, color = if (ahead) P.good else P.ink)
                    Text("YOU", style = T.label.copy(fontSize = 8.sp), color = P.accent)
                }
                Spacer(Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.1f".format(dadPpg), style = T.numSm, color = P.sub)
                    Text("DAD", style = T.label.copy(fontSize = 8.sp), color = P.faint)
                }
            }
        }
        // He'll be there for the next big one.
        lg.games.filter { !it.played && it.involves(h.teamId) }.minByOrNull { it.day }?.takeIf { LegacyMode.dadInStands(lg, it) }?.let { g ->
            val opp = lg.team(g.opp(h.teamId))
            Text("Your father will be in the stands for ${if (lg.pro) opp.name else opp.school}: a lift on your shots.", style = T.small, color = P.accent, modifier = Modifier.padding(top = 8.dp))
        }
        if (marks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Hairline()
            Text("CHASING DAD · ${marks.count { it.done }} OF ${marks.size}", style = T.label.copy(fontSize = 10.sp), color = P.sub, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            marks.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (m.done) P.good else P.surface2), contentAlignment = Alignment.Center) {
                        if (m.done) androidx.compose.material3.Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(m.goal, style = T.small, color = if (m.done) P.ink else P.sub, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Text(m.dad, style = T.small.copy(fontSize = 11.sp), color = P.faint, maxLines = 1)
                }
            }
            Text("Each one you pass: a skill point and a bigger following.", style = T.small.copy(fontSize = 11.sp), color = P.faint, modifier = Modifier.padding(top = 6.dp))
        }
        if (dad.badges.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Hairline()
            Text("HIS BADGES · YOU STARTED WITH A HEAD START", style = T.label.copy(fontSize = 9.sp), color = P.faint, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dad.badges.entries.sortedByDescending { it.value }.take(7).forEach { (name, t) ->
                    com.nh.hoopscoach.game.Badge.entries.firstOrNull { it.name == name }?.let { BadgeMedal(it, t, 26.dp) }
                }
            }
        }
        val log = lg.road?.legacyLog.orEmpty()
        if (log.isNotEmpty()) Text(log.takeLast(2).joinToString("\n"), style = T.small.copy(fontSize = 11.sp), color = P.faint, modifier = Modifier.padding(top = 8.dp))
    }
}


@Composable
private fun bondColor(b: Int): Color = when { b >= 80 -> P.good; b >= 55 -> P.accent; b >= 35 -> P.warn; else -> P.bad }

/** His father calls: what he says, and how you answer (the bond, and maybe a film session for the next game). */
@Composable
fun DadCallCard(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val F = com.nh.hoopscoach.game.Family
    val c = F.call(lg) ?: return
    val dad = F.father(lg) ?: return
    Section("Family", action = "Let it ring", onAction = { vm.act { F.skip(it, c) } })
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AncestorPortrait(lg, dad, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title.uppercase(), style = T.label, color = P.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dad.name + " · " + F.bondLabel(lg.road?.dadBond ?: 60).lowercase(), style = T.small, color = P.sub)
            }
        }
        Text(c.text, style = T.body, color = P.ink, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        c.answers.forEachIndexed { i, a ->
            ChoiceRow(onClick = {
                var reaction = ""
                vm.act { reaction = F.answer(it, c, i) }
                if (reaction.isNotEmpty()) vm.toast(reaction)
            }) {
                Column(Modifier.weight(1f)) {
                    Text(a.text, style = T.body, color = P.ink)
                    val hints = listOfNotNull(
                        if (a.bond >= 3) "closer" else if (a.bond < 0) "he notices" else null,
                        if (a.prep) "film session: a lift next game" else null,
                        if (a.mood >= 2) "feels good" else if (a.mood < 0) "mood ↓" else null,
                        if (a.fatigue >= 2) "a late night" else null,
                    )
                    if (hints.isNotEmpty()) Text(hints.joinToString(" · "), style = T.small.copy(fontSize = 11.sp), color = P.faint)
                }
            }
        }
    }
}

/** You against your father: college year by year, the pros, the honors, his marks, and the two of you. */
@Composable
fun FamilyCompareScreen(vm: GameViewModel, lg: League) {
    val dad = LegacyMode.father(lg) ?: run { vm.back(); return }
    val h = lg.hero ?: run { vm.back(); return }
    val r = lg.road
    val p = dad.played
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("You vs Dad", "${h.name} · ${dad.name}", onBack = { vm.back() })
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayerJersey(h, lg.teams.getOrNull(h.teamId), 64.dp)
                        Text(h.name, style = T.bodyB, color = P.ink, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text("YOU", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                    }
                    Text("VS", style = T.head, color = P.faint)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        AncestorPortrait(lg, dad, 64.dp)
                        Text(dad.name, style = T.bodyB, color = P.ink, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text("DAD", style = T.label.copy(fontSize = 10.sp), color = P.faint)
                    }
                }
            }
        }
        // College, class by class: points, rebounds, assists.
        item {
            Section("College, year by year")
            Panel {
                CompareHead("Pts · reb · ast")
                val classes = (dad.seasons.map { it.cls } + h.career.filter { it.cls != PRO_YEAR && it.s.gp > 0 }.map { it.cls } + (if (!lg.pro && h.stats.gp > 0) listOf(h.year) else emptyList()))
                    .filter { it in 1..5 }.distinct().sorted()
                if (classes.isEmpty()) Text("No college seasons yet.", style = T.small, color = P.sub)
                classes.forEach { c ->
                    val d = dad.seasons.lastOrNull { it.cls == c }
                    val mine = h.career.lastOrNull { it.cls == c && it.cls != PRO_YEAR && it.s.gp > 0 }?.s
                        ?: h.stats.takeIf { !lg.pro && h.year == c && it.gp > 0 }
                    CompareRow(clsName(c),
                        mine?.let { "%.1f · %.1f · %.1f".format(it.ppg, it.rpg, it.apg) } ?: "—",
                        d?.let { "%.1f · %.1f · %.1f".format(it.ppg, it.rpg, it.apg) } ?: "—",
                        (mine?.ppg ?: -1.0) - (d?.ppg ?: 99.0))
                }
            }
        }
        // The pros.
        item {
            Section("The pros")
            Panel {
                CompareHead("")
                val myPro = h.career.count { it.cls == PRO_YEAR && it.s.gp > 0 } + if (lg.pro && h.stats.gp > 0 && h.career.none { it.season == lg.season && it.cls == PRO_YEAR }) 1 else 0
                val (myPpg, myGp) = LegacyMode.careerPpg(lg, h, pro = true)
                val dadPick = LegacyMode.pick(dad)
                val pickMine = r?.draftPick?.takeIf { it > 0 }
                CompareRow("Draft", pickMine?.let { "No. $it" } ?: if (r?.undrafted == true) "Undrafted" else "—", dadPick?.let { "No. $it" } ?: "Undrafted",
                    if (pickMine != null) ((dadPick ?: 99) - pickMine).toDouble() else -1.0)
                CompareRow("NBA seasons", "$myPro", "${p.proSeasons}", (myPro - p.proSeasons).toDouble())
                CompareRow("Points a game", if (myGp > 0) "%.1f".format(myPpg) else "—", LegacyMode.proPpg(dad).takeIf { it > 0 }?.let { "%.1f".format(it) } ?: "—",
                    if (myGp > 0) myPpg - LegacyMode.proPpg(dad) else -1.0)
                CompareRow("All-Star", "${r?.allStars ?: 0}", "${p.allStars}", ((r?.allStars ?: 0) - p.allStars).toDouble())
                CompareRow("Rings", "${r?.rings ?: 0}", "${p.rings}", ((r?.rings ?: 0) - p.rings).toDouble())
                CompareRow("MVP", "${r?.mvps ?: 0}", "${p.mvps}", ((r?.mvps ?: 0) - p.mvps).toDouble())
            }
        }
        if (p.collegeHonors.isNotEmpty()) item {
            Section("His college honors")
            Panel { p.collegeHonors.forEach { Text("• $it", style = T.small, color = P.sub) } }
        }
        val marks = LegacyMode.marks(lg)
        if (marks.isNotEmpty()) item {
            Section("Chasing Dad · ${marks.count { it.done }} of ${marks.size}")
            Panel {
                marks.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (m.done) P.good else P.surface2), contentAlignment = Alignment.Center) {
                            if (m.done) androidx.compose.material3.Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(m.goal, style = T.small, color = if (m.done) P.ink else P.sub, modifier = Modifier.weight(1f))
                        Text(m.dad, style = T.small.copy(fontSize = 11.sp), color = P.faint, maxLines = 1)
                    }
                }
            }
        }
        if (r != null) item {
            Section("The two of you")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(com.nh.hoopscoach.game.Family.bondLabel(r.dadBond), style = T.head, color = bondColor(r.dadBond), modifier = Modifier.weight(1f))
                    Text("${r.dadBond}/100", style = T.numSm, color = P.sub)
                }
                Bar(r.dadBond / 100f, Modifier.fillMaxWidth().padding(top = 6.dp), color = bondColor(r.dadBond), height = 5.dp)
                Text("Answer when he calls, take his film sessions and work out with him in the preseason, and you get closer. The closer you are, the more it lifts you to see him in the stands.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(top = 6.dp))
                if (r.legacyLog.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    r.legacyLog.asReversed().take(12).forEach { Text(it, style = T.small.copy(fontSize = 12.sp), color = P.faint) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CompareHead(what: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(what.uppercase(), style = T.label.copy(fontSize = 9.sp), color = P.faint, modifier = Modifier.weight(1f))
        Text("YOU", style = T.label.copy(fontSize = 9.sp), color = P.accent, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
        Text("DAD", style = T.label.copy(fontSize = 9.sp), color = P.faint, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
    }
}

/** A line of the comparison; [lead] above zero means you're ahead of him there. */
@Composable
private fun CompareRow(label: String, mine: String, dad: String, lead: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.body, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(mine, style = T.bodyB.copy(fontSize = 14.sp), color = if (lead > 0 && mine != "—") P.good else P.ink, modifier = Modifier.width(110.dp), textAlign = TextAlign.End, maxLines = 1)
        Text(dad, style = T.body.copy(fontSize = 14.sp), color = P.sub, modifier = Modifier.width(110.dp), textAlign = TextAlign.End, maxLines = 1)
    }
}
