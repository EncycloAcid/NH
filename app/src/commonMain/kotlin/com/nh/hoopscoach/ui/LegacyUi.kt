package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
