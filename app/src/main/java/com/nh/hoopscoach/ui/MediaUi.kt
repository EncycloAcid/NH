package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.News
import com.nh.hoopscoach.game.Post
import com.nh.hoopscoach.game.Road

// ---------- social media ----------

/** Who posted it, as a color for the avatar. */
@Composable
private fun kindColor(kind: String) = when (kind) {
    "team" -> P.accent
    "media" -> P.bad
    "analyst" -> P.good
    "rival" -> P.warn
    "teammate" -> P.gold
    "hater" -> P.faint
    "insider" -> P.accent
    else -> P.sub
}

private fun kindLabel(kind: String) = when (kind) {
    "team" -> "Team"; "media" -> "Highlights"; "analyst" -> "Analyst"; "rival" -> "Your rival"; "teammate" -> "Teammate"; "hater" -> "Doubter"; "insider" -> "Insider"; else -> "Fan"
}

/** One post: avatar, name with a check if verified, handle and day, the text, and the likes. */
@Composable
fun PostRow(p: Post) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        val initials = p.name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
        Box(Modifier.size(36.dp).clip(CircleShape).background(kindColor(p.kind).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(initials, style = T.label, color = kindColor(p.kind))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.CheckCircle, "Verified", tint = P.accent, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(6.dp))
                Text("@${p.handle}", style = T.small, color = P.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text(p.text, style = T.body, color = P.ink, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = P.bad.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(Road.count(p.likes), style = T.small, color = P.sub)
                Spacer(Modifier.width(10.dp))
                Text("${kindLabel(p.kind)} · day ${p.day}", style = T.small, color = P.faint)
            }
        }
    }
}

/** The latest posts about him, for Today. */
@Composable
fun SocialStrip(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val posts = r.feed.filter { it.season == lg.season }.takeLast(2).reversed()
    if (posts.isEmpty()) return
    Section("Social · ${Road.count(r.followers)} followers", action = "See all", onAction = { vm.push(Route.Social) })
    GroupList(posts, inset = 60.dp) { p -> PostRow(p) }
}

/** The whole feed, newest first. */
@Composable
fun SocialScreen(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    Column(Modifier.fillMaxSize()) {
        BackBar("Social", "${Road.count(r.followers)} followers · what people are saying about you", onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f)) {
            val posts = r.feed.reversed()
            if (posts.isEmpty()) item { Panel { Text("Nothing yet. Play a game and the internet will have opinions.", style = T.body, color = P.sub) } }
            else item {
                Spacer(Modifier.height(8.dp))
                GroupList(posts, inset = 60.dp) { p -> PostRow(p) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ---------- articles ----------

/** Where a headline goes when tapped: its article, or its box score. */
fun newsClick(vm: GameViewModel, lg: League, n: News): (() -> Unit)? {
    val i = lg.news.indexOf(n)
    return when {
        n.body.isNotEmpty() && i >= 0 -> ({ vm.push(Route.Article(i)) })
        n.gameId >= 0 && lg.games.any { it.id == n.gameId } -> ({ vm.push(Route.Box(n.gameId)) })
        else -> null
    }
}

/** A short article: the outlet and date, the headline, and the story. */
@Composable
fun ArticleScreen(vm: GameViewModel, lg: League, index: Int) {
    val n = lg.news.getOrNull(index) ?: run { vm.back(); return }
    Column(Modifier.fillMaxSize()) {
        BackBar(n.source.ifEmpty { "News" }, "${n.season} season · day ${n.day}", onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f).padding(horizontal = Gutter + 4.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(when (n.kind) { "column" -> "COLUMN"; "quote" -> "INTERVIEW"; else -> "GAME STORY" }, style = T.label, color = P.accent)
                Text(n.text, style = T.title, color = P.ink, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (para in n.body.split("\n\n")) Text(para, style = T.body, color = P.ink)
                }
                if (n.gameId >= 0 && lg.games.any { it.id == n.gameId && it.box != null }) {
                    Spacer(Modifier.height(16.dp))
                    SecondaryButton("Box score", Modifier.fillMaxWidth()) { vm.push(Route.Box(n.gameId)) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** A headline row: tap it to read the story (a small "Read" tag says there is one). */
@Composable
fun HeadlineRow(vm: GameViewModel, lg: League, n: News) {
    ListRow(onClick = newsClick(vm, lg, n)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (n.mine) P.accent else P.line))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NewsTag(n)
            Text(n.text, style = if (n.mine) T.bodyB else T.body, color = if (n.mine) P.ink else P.sub)
            if (n.body.isNotEmpty()) Text("${n.source.ifEmpty { "Story" }} · Read", style = T.small, color = P.accent)
        }
    }
}

@Suppress("unused") private val keepColor = Color.Unspecified
