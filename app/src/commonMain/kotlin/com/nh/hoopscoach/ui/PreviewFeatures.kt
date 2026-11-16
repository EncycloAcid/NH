package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.em
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Feature
import com.nh.hoopscoach.game.FeatureItem
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team

// ---------- the rotating feature pages and the alternate covers ----------

/** A feature page: its deck, then its items (grouped under their tags, like tiers, or each with a tag chip). */
internal fun LazyListScope.featurePage(vm: GameViewModel, lg: League, f: Feature) {
    item { Deck(f.deck) }
    if (f.grouped) {
        val groups = f.items.groupBy { it.tag }
        for ((tag, items) in groups) item {
            Section("$tag · ${items.size}")
            GroupList(items, inset = 60.dp) { FeatureRow(vm, lg, it, showTag = false) }
        }
    } else item {
        Spacer(Modifier.height(10.dp))
        GroupList(f.items, inset = 60.dp) { FeatureRow(vm, lg, it, showTag = true) }
    }
}

@Composable
private fun tagColor(tag: String): Color = when {
    tag.startsWith("OVER") || tag == "LOCK" || tag.contains("TOP 10") -> P.good
    tag.startsWith("UNDER") || tag == "HOT" -> P.bad
    tag == "WARM" || tag == "EXPIRING" -> P.warn
    tag.startsWith("Q") -> P.accent
    else -> P.accent
}

@Composable
private fun FeatureRow(vm: GameViewModel, lg: League, it: FeatureItem, showTag: Boolean) {
    val team = lg.teams.getOrNull(it.teamId)
    val player = lg.playerOrNull(it.pid)
    ListRow(onClick = when { player != null -> ({ vm.openPlayer(player.id) }); team != null -> ({ vm.push(Route.TeamPage(team.id)) }); else -> null }) {
        when {
            player != null && player.teamId >= 0 -> PlayerJersey(player, lg.team(player.teamId), 44.dp)
            team != null -> TeamBadge(team, 40.dp)
            else -> Box(Modifier.width(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (showTag && it.tag.isNotEmpty()) { Tag(it.tag.uppercase(), tagColor(it.tag)); Spacer(Modifier.height(3.dp)) }
            Text(it.head, style = T.bodyB, color = P.ink)
            Text(it.body, style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ---------- the covers: printed magazine fronts ----------

/**
 * A magazine front: the team's color full bleed, the masthead across the top at full width, the issue
 * line and price under a rule, the cover itself, and a barcode in the corner. The reader's own team rides
 * on a flap at the bottom.
 */
@Composable
internal fun CoverFrame(masthead: String, issue: String, footer: String, base: Color, fg: Color, accent: Color, userBox: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        Column(
            Modifier.fillMaxWidth().heightIn(min = w * 1.3f)
                .background(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.08f), base, lerp(base, Color.Black, 0.62f))))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // The masthead fills the width, the way a printed nameplate does.
            androidx.compose.foundation.text.BasicText(
                masthead.uppercase(),
                style = T.hero.copy(color = fg, lineHeight = 1.0.em, letterSpacing = (-0.5).sp),
                maxLines = 1,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 120.sp, stepSize = 2.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            // Over the cover star, who stands in front of the masthead.
            Row(Modifier.zIndex(1f).fillMaxWidth().background(Color.Black.copy(alpha = 0.85f)).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(issue.uppercase(), style = T.label.copy(fontSize = 10.sp, letterSpacing = 1.5.sp), color = Color.White, modifier = Modifier.weight(1f).padding(vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.background(accent).padding(horizontal = 8.dp, vertical = 4.dp)) { Text("$9.99", style = T.label.copy(fontSize = 10.sp), color = onColor(accent)) }
            }
            Spacer(Modifier.height(10.dp))
            content()
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(12.dp))
            userBox()
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Barcode()
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(footer, style = T.label.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = fg.copy(alpha = 0.8f), maxLines = 2)
                    Text("DISPLAY UNTIL MARCH", style = T.label.copy(fontSize = 8.sp, letterSpacing = 1.sp), color = fg.copy(alpha = 0.6f))
                }
            }
        }
    }
}

/** The barcode in the cover's corner: black bars in a white box. */
@Composable
private fun Barcode() {
    val bars = remember { listOf(2, 1, 1, 3, 1, 2, 2, 1, 3, 1, 1, 2, 1, 1, 3, 2, 1, 2, 1, 3, 1, 1, 2, 2, 1, 3, 1, 2, 1, 1) }
    Column(Modifier.background(Color.White).padding(horizontal = 5.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Canvas(Modifier.width(66.dp).height(28.dp)) {
            val unit = size.width / bars.sum()
            var x = 0f
            bars.forEachIndexed { i, b ->
                if (i % 2 == 0) drawRect(Color.Black, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(b * unit, size.height))
                x += b * unit
            }
        }
        Text("0 74470 02026 5", style = T.label.copy(fontSize = 7.sp, letterSpacing = 0.5.sp), color = Color.Black)
    }
}

/** The round sticker slapped on a cover: "NO. 1 / 24% TO WIN IT ALL". */
@Composable
private fun CoverSticker(top: String, bottom: String, bg: Color, modifier: Modifier = Modifier) {
    val fg = onColor(bg)
    Box(
        modifier.size(92.dp).androidx_rotate(-10f).clip(androidx.compose.foundation.shape.CircleShape).background(bg).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(top, style = T.hero.copy(fontSize = 24.sp, lineHeight = 24.sp), color = fg)
            Text(bottom, style = T.label.copy(fontSize = 8.sp, letterSpacing = 0.5.sp, lineHeight = 9.sp), color = fg, textAlign = TextAlign.Center, maxLines = 3)
        }
    }
}

private fun Modifier.androidx_rotate(deg: Float) = this.then(Modifier.graphicsLayer { rotationZ = deg })

/** Cover lines down the left: the kicker in the spot color, the line in white. */
@Composable
private fun CoverLines(lines: List<Pair<String, String>>, fg: Color, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        lines.forEach { (k, h) ->
            Text(k.uppercase(), style = T.label.copy(fontSize = 10.sp, letterSpacing = 1.5.sp), color = accent, maxLines = 1)
            Text(h, style = T.h2.copy(fontSize = 17.sp, lineHeight = 19.sp), color = fg, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
        }
    }
}

private fun coverColors(team: Team): Triple<Color, Color, Color> {
    val base = teamCard(team)
    val fg = onColor(base)
    val secondary = Color(team.secondary)
    val accent = if (contrast(secondary, base) >= 2.2f) secondary else if (fg == Color.White) Color(0xFFFFD34D) else Color(0xFF8B1E1E)
    return Triple(base, fg, accent)
}

/** White cover type with a soft shadow, so it reads over the photo. */
private fun TextStyleOver(base: androidx.compose.ui.text.TextStyle) =
    base.copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(0f, 3f), 10f))

/**
 * The cover photo: the star cut out, his head in front of the masthead, standing in the right half
 * of the cover so the left is free for the lines.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.CoverStar(team: Team, star: Player?, w: androidx.compose.ui.unit.Dp) {
    Box(Modifier.align(Alignment.BottomEnd).offset(x = w * 0.43f).wrapContentSize(unbounded = true, align = Alignment.BottomEnd)) {
        if (star != null) PlayerJersey(star, team, w * 1.26f, plate = false) else TeamBadge(team, w * 0.6f)
    }
}

/** The classic front: the favorite's star on the right, its name across the bottom, lines down the left. */
@Composable
internal fun ClassicCover(
    masthead: String, issue: String, footer: String, team: Team, star: Player?, headline: String, name: String, label: String, dek: String,
    sticker: Pair<String, String>, lines: List<Pair<String, String>>, userBox: @Composable () -> Unit,
) {
    val (base, fg, accent) = coverColors(team)
    CoverFrame(masthead, issue, footer, base, fg, accent, userBox) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(w * 1.1f)) {
                CoverStar(team, star, w)
                Column(Modifier.align(Alignment.TopStart).fillMaxWidth(0.42f)) {
                    Text(headline.uppercase(), style = TextStyleOver(T.hero.copy(fontSize = 32.sp, lineHeight = 31.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)), color = accent, maxLines = 3)
                    Spacer(Modifier.height(12.dp))
                    CoverLines(lines, fg, accent)
                }
                CoverSticker(sticker.first, sticker.second, accent, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = (-4).dp))
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.72f)) {
                    Text(label, style = TextStyleOver(T.label.copy(letterSpacing = 1.5.sp)), color = accent)
                    Text(name.uppercase(), style = TextStyleOver(T.hero.copy(fontSize = 54.sp, lineHeight = 50.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)), color = Color.White, maxLines = 2)
                    Text(dek, style = TextStyleOver(T.small.copy(fontSize = 13.sp)), color = Color.White, maxLines = 3)
                }
            }
        }
    }
}

/**
 * A feature cover: the year's biggest story on the front, in its team's colors, with the player at its
 * center when there is one.
 */
@Composable
internal fun FeatureCover(
    masthead: String, season: String, team: Team, player: Player?, kicker: String, headline: String, deck: String,
    lines: List<Pair<String, String>>, count: Int, onRead: () -> Unit, onSkip: (() -> Unit)? = null, userBox: @Composable () -> Unit,
) {
    val (base, fg, accent) = coverColors(team)
    CoverFrame(masthead, season, "THE COVER STORY · AND ${lines.size} MORE INSIDE", base, fg, accent, userBox) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(w * 1.14f)) {
                CoverStar(team, player, w)
                Column(Modifier.align(Alignment.TopStart).fillMaxWidth(0.42f)) {
                    Text("ALSO INSIDE", style = T.label.copy(fontSize = 10.sp, letterSpacing = 1.5.sp), color = fg.copy(alpha = 0.75f))
                    Spacer(Modifier.height(6.dp))
                    CoverLines(lines, fg, accent)
                }
                // The cover story across the bottom: kicker, headline, a line of deck.
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.8f)) {
                    Text(kicker.uppercase(), style = TextStyleOver(T.label.copy(fontSize = 13.sp, letterSpacing = 3.sp)), color = accent)
                    Text(headline.uppercase(), style = TextStyleOver(T.hero.copy(fontSize = 44.sp, lineHeight = 42.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)), color = Color.White, maxLines = 3)
                    Text(deck, style = TextStyleOver(T.body.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp)),
                        color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A versus cover, for a two-horse race: the two favorites side by side in their colors, their odds on a shared bar. */
@Composable
internal fun VersusCover(
    masthead: String, season: String, headline: String, a: Team, b: Team, aOdds: Double, bOdds: Double, aLine: String, bLine: String,
    lines: List<Pair<String, String>>, count: Int, onRead: () -> Unit, onSkip: (() -> Unit)? = null, userBox: @Composable () -> Unit,
) {
    val gold = Color(0xFFFFD34D)
    CoverFrame(masthead, season, "THE RACE FOR THE TITLE", Color(0xFF15181E), Color.White, gold, userBox) {
        Text("THE RACE", style = T.hero.copy(fontSize = 70.sp, lineHeight = 66.sp, letterSpacing = 1.sp), color = Color.White)
        Text(headline.uppercase(), style = T.title.copy(fontSize = 22.sp, lineHeight = 24.sp), color = gold)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            for ((i, pair) in listOf(a to aLine, b to bLine).withIndex()) {
                val (t, line) = pair
                val c = teamCard(t)
                val fg = onColor(c)
                Column(
                    Modifier.weight(1f).background(Brush.verticalGradient(listOf(c, lerp(c, Color.Black, 0.5f)))).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamBadge(t, 76.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(t.school.uppercase(), style = T.title.copy(fontSize = 20.sp, lineHeight = 21.sp), color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(odd(if (t.id == a.id) aOdds else bOdds), style = T.hero.copy(fontSize = 34.sp, lineHeight = 34.sp), color = fg)
                    Text(line, style = T.small, color = fg.copy(alpha = 0.85f), textAlign = TextAlign.Center, maxLines = 2)
                }
                if (i == 0) Text("VS", style = T.hero.copy(fontSize = 26.sp, lineHeight = 26.sp), color = gold, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        val share = (aOdds / (aOdds + bOdds).coerceAtLeast(1e-6)).toFloat()
        Row(Modifier.fillMaxWidth().height(10.dp)) {
            Box(Modifier.weight(share.coerceIn(0.05f, 0.95f)).height(10.dp).background(Color(a.primary)))
            Box(Modifier.weight((1 - share).coerceIn(0.05f, 0.95f)).height(10.dp).background(Color(b.primary)))
        }
        Spacer(Modifier.height(14.dp))
        CoverLines(lines, Color.White, gold)
    }
}

/**
 * The bold predictions; once the season is over, each one marked right or wrong, with what really
 * happened and the tally at the top.
 */
@Composable
internal fun BoldPredictions(bold: List<String>, graded: List<Int>, outcomes: List<String>) {
    val done = graded.count { it >= 0 }
    Section(if (done > 0) "Bold predictions · ${graded.count { it == 1 }} of $done right" else "Bold predictions")
    GroupList(bold.withIndex().toList()) { (i, text) ->
        val g = graded.getOrNull(i) ?: -1
        ListRow {
            Text("${i + 1}", style = T.num.copy(fontSize = 22.sp), color = when (g) { 1 -> P.good; 0 -> P.bad; else -> P.accent }, modifier = Modifier.width(34.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = T.body, color = if (g == 0) P.sub else P.ink)
                outcomes.getOrNull(i)?.takeIf { g >= 0 && it.isNotEmpty() }?.let { Text(it, style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp)) }
            }
            if (g >= 0) {
                Spacer(Modifier.width(8.dp))
                Tag(if (g == 1) "RIGHT" else "WRONG", if (g == 1) P.good else P.bad)
            }
        }
    }
}
