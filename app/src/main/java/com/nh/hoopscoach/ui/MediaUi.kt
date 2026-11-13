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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.nh.hoopscoach.game.Ollama
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.News
import com.nh.hoopscoach.game.Post
import com.nh.hoopscoach.game.Reaction
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
    "self" -> P.accent
    "league" -> P.gold
    "news" -> P.ink
    "player" -> P.gold
    "coach" -> P.accent
    "recruit" -> P.good
    else -> P.sub
}

private fun kindLabel(kind: String) = when (kind) {
    "team" -> "Team"; "media" -> "Highlights"; "analyst" -> "Analyst"; "rival" -> "Your rival"; "teammate" -> "Teammate"; "hater" -> "Doubter"; "insider" -> "Insider"; "self" -> "You"; "league" -> "League"; "news" -> "News"; "player" -> "Player"; "coach" -> "Coach"; "recruit" -> "Recruit"; else -> "Fan"
}

/** Relative time against the league clock. */
private fun whenAgo(lg: League?, p: Post): String {
    if (lg == null) return "day ${p.day}"
    if (p.season != lg.season) return "last season"
    val d = (lg.day - p.day).coerceAtLeast(0)
    return when (d) {
        0 -> "just now"
        1 -> "yesterday"
        in 2..6 -> "${d}d ago"
        else -> "day ${p.day}"
    }
}

/** Mood emojis that make a post feel alive — derived from kind + text, stable per post. */
private fun vibe(p: Post): String = when {
    p.kind == "media" || p.text.contains("🚨") || p.text.contains("🔥") -> "🔥 👏 😮"
    p.kind == "hater" -> "💀 🍿 😬"
    p.kind == "team" && (p.text.contains("W") || p.text.contains("win", true)) -> "🏆 🙌 💙"
    p.kind == "rival" -> "👀 😤 🫡"
    p.kind == "analyst" || p.kind == "insider" -> "📊 🧠 ✍️"
    p.kind == "news" -> "📰 💬"
    else -> "❤️ 💬 🔁"
}

/** One post: avatar, name with a check if verified, handle and day, the text, and the likes. [onOpen] opens the story it's about. */
@Composable
fun PostRow(p: Post, onOpen: (() -> Unit)? = null, lg: League? = null) {
    var appeared by remember(p.season, p.day, p.handle, p.text) { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enter by animateFloatAsState(if (appeared) 1f else 0f, tween(280), label = "post")
    val replies = (p.likes / 17).coerceAtLeast(if (p.likes > 20) 1 else 0)
    val reposts = (p.likes / 11).coerceAtLeast(if (p.likes > 40) 1 else 0)
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = 0.35f + 0.65f * enter; translationY = (1f - enter) * 10f }
            .then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            ProfilePic(lg, p, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (p.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.CheckCircle, "Verified", tint = P.accent, modifier = Modifier.size(14.dp)) }
                    Spacer(Modifier.width(6.dp))
                    Text("@${p.handle}", style = T.small, color = P.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(" · ${whenAgo(lg, p)}", style = T.small, color = P.faint, maxLines = 1)
                }
                // A reply to a story says which one (the outlet's own post is the story).
                if (p.re.isNotEmpty() && p.kind != "news") {
                    Text("Replying to a story", style = T.label.copy(fontSize = 10.sp), color = P.accent, modifier = Modifier.padding(top = 3.dp))
                    Text(headline(p.re), style = T.small, color = P.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(p.text, style = if (p.kind == "news") T.bodyB else T.body, color = P.ink, modifier = Modifier.padding(top = 3.dp))
                // Highlight / clip posts get a little media card so the feed isn't all text.
                if (p.kind == "media" || (p.kind == "team" && p.gameId >= 0 && p.likes >= 80)) {
                    Spacer(Modifier.height(8.dp))
                    MediaCard(p, lg)
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, null, tint = P.bad.copy(alpha = 0.85f), modifier = Modifier.size(13.dp).scale(if (appeared && p.likes > 200) 1.08f else 1f))
                    Spacer(Modifier.width(3.dp))
                    Text(Road.count(p.likes), style = T.small, color = P.sub)
                    Spacer(Modifier.width(12.dp))
                    Text("💬 ${Road.count(replies)}", style = T.small, color = P.sub)
                    Spacer(Modifier.width(12.dp))
                    Text("🔁 ${Road.count(reposts)}", style = T.small, color = P.sub)
                    Spacer(Modifier.width(10.dp))
                    Text(kindLabel(p.kind), style = T.small, color = kindColor(p.kind).copy(alpha = 0.85f))
                    if (onOpen != null) { Spacer(Modifier.weight(1f)); Text("Read", style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = P.accent) }
                }
                Text(vibe(p), style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** A faux clip / highlight tile under big posts. */
@Composable
private fun MediaCard(p: Post, lg: League?) {
    val accent = kindColor(p.kind)
    val pl = lg?.let { resolvePlayer(it, p.name, p.handle) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.12f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            if (pl != null) PlayerSocialPic(lg!!, pl, 48.dp)
            else Text("▶", style = T.head, color = accent)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (p.kind == "media") "Highlight clip" else "Game night", style = T.label.copy(fontSize = 10.sp), color = accent)
            Text(p.text.take(72) + if (p.text.length > 72) "…" else "", style = T.small, color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The latest posts about him, for Today. */
@Composable
fun SocialStrip(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val posts = r.feed.filter { it.season == lg.season }.takeLast(2).reversed()
    if (posts.isEmpty()) return
    Section("Social · ${Road.count(r.followers)} followers", action = "See all", onAction = { vm.socialTab = 0; vm.push(Route.Social) })
    GroupList(posts, inset = 60.dp) { p -> PostRow(p, lg = lg) }
    // The latest big story around the league, a tap away from that tab.
    r.leagueFeed.lastOrNull { it.kind == "news" && it.season == lg.season }?.let { n ->
        Spacer(Modifier.height(8.dp))
        GroupList(listOf(n)) { p ->
            ListRow(onClick = { vm.socialTab = 1; vm.push(Route.Social) }) {
                Column(Modifier.weight(1f)) {
                    Text("AROUND THE LEAGUE · ${p.name.uppercase()}", style = T.label.copy(fontSize = 10.sp), color = P.sub, maxLines = 1)
                    Text(headline(p.re.ifEmpty { p.text }), style = T.bodyB, color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${r.leagueFeed.count { it.re == p.re && it.re.isNotEmpty() } - 1} reactions · see the league feed", style = T.small, color = P.accent)
                }
            }
        }
    }
}

/** Where a league post goes when tapped: the story it's about, if it's still in the news. */
private fun storyClick(vm: GameViewModel, lg: League, p: Post): (() -> Unit)? {
    if (p.re.isEmpty()) return null
    val n = lg.news.lastOrNull { it.text == p.re && it.season == p.season } ?: return null
    return newsClick(vm, lg, n)
}

/** The feed in two tabs: what people say about you, and what's happening around the league. Newest first. */
@Composable
fun SocialScreen(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    var kind by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        BackBar("Social", "${Road.count(r.followers)} followers", onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f)) {
            item {
                Spacer(Modifier.height(6.dp))
                Segmented(listOf("You", "Around the league"), vm.socialTab, { vm.socialTab = it })
            }
            if (vm.socialTab == 0) {
                item { ProfileCard(vm, lg) }
                val posts = r.feed.reversed()
                if (posts.isEmpty()) item { Spacer(Modifier.height(10.dp)); Panel { Text("Nothing yet. Play a game and the internet will have opinions.", style = T.body, color = P.sub) } }
                else item {
                    Spacer(Modifier.height(8.dp))
                    GroupList(posts, inset = 60.dp) { p -> PostRow(p, lg = lg) }
                }
            } else {
                val all = r.leagueFeed.reversed()
                // Trending: the stories people engaged with most over the last few days.
                val recent = all.filter { it.season == lg.season && lg.day - it.day <= (if (lg.pro) 7 else 3) && it.re.isNotEmpty() }
                val trending = recent.groupBy { it.re }.map { (re, ps) -> re to ps.sumOf { it.likes } }.sortedByDescending { it.second }.take(3)
                if (trending.isNotEmpty()) item {
                    Section("Trending")
                    GroupList(trending, inset = 46.dp) { (re, likes) ->
                        val open = storyClick(vm, lg, all.first { it.re == re })
                        ListRow(onClick = open) {
                            Text("${trending.indexOfFirst { it.first == re } + 1}", style = T.numSm, color = P.accent, modifier = Modifier.width(22.dp))
                            Column(Modifier.weight(1f)) {
                                Text(headline(re), style = T.bodyB, color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${Road.count(likes)} likes · ${all.count { it.re == re }} posts", style = T.small, color = P.sub)
                            }
                        }
                    }
                }
                // Filters: everything, the stories themselves, or what people said.
                item {
                    Spacer(Modifier.height(10.dp))
                    Chips(listOf("Everything", "Stories", "Players & coaches", "Analysts & insiders", "Fans"), kind, { kind = it })
                }
                val shown = all.filter { p ->
                    when (kind) {
                        1 -> p.kind == "news"
                        2 -> p.kind in setOf("player", "coach", "recruit", "teammate")
                        3 -> p.kind in setOf("analyst", "insider", "media", "league")
                        4 -> p.kind in setOf("fan", "hater")
                        else -> true
                    }
                }.take(100)
                if (shown.isEmpty()) item { Spacer(Modifier.height(10.dp)); Panel { Text("Nothing here yet. Sim a few days and the league will start talking.", style = T.body, color = P.sub) } }
                else item {
                    Spacer(Modifier.height(8.dp))
                    GroupList(shown, inset = 60.dp) { p -> PostRow(p, storyClick(vm, lg, p), lg) }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ---------- articles ----------

/** Where a headline goes when tapped: its article, or its box score. */
fun newsClick(vm: GameViewModel, lg: League, n: News): (() -> Unit)? {
    val i = lg.news.indexOf(n)
    return when {
        (n.body.isNotEmpty() || n.reactions.isNotEmpty()) && i >= 0 -> ({ vm.push(Route.Article(i)) })
        n.gameId >= 0 && lg.games.any { it.id == n.gameId } -> ({ vm.push(Route.Box(n.gameId)) })
        else -> null
    }
}

/** A short article: outlet, headline, and body in the same chrome as the rest of the app. */
@Composable
fun ArticleScreen(vm: GameViewModel, lg: League, index: Int) {
    val n = lg.news.getOrNull(index) ?: run { vm.back(); return }
    var generating by remember { androidx.compose.runtime.mutableStateOf(false) }
    var ollamaError by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val outlet = n.source.substringAfter(" · ", n.source).ifEmpty { "Hoops Wire" }
    val byline = n.source.substringBefore(" · ", "").takeIf { it.isNotEmpty() && n.source.contains(" · ") }
    val kind = when (n.kind) {
        "column" -> "COLUMN"
        "quote" -> "INTERVIEW"
        "trade", "move" -> "TRANSACTIONS"
        "series" -> "PLAYOFFS"
        "team" -> "STREAK WATCH"
        "game" -> "BIG NIGHT"
        "upset", "buzzer", "moment" -> "MOMENTS"
        "" -> "NEWS"
        else -> "GAME STORY"
    }
    Column(Modifier.fillMaxSize()) {
        BackBar(outlet, "${n.season} season · day ${n.day}", onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f)) {
            item {
                Spacer(Modifier.height(8.dp))
                Panel {
                    Text(outlet.uppercase(), style = T.label, color = P.accent)
                    Spacer(Modifier.height(4.dp))
                    Text("$kind · ${n.season} · DAY ${n.day}", style = T.small, color = P.sub)
                    Spacer(Modifier.height(8.dp))
                    Text(headline(n.text), style = T.title, color = P.ink)
                    if (byline != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("By $byline", style = T.small, color = P.sub)
                    }
                    val pic = lg.playerOrNull(n.pid)
                    val team = lg.teams.getOrNull(n.teamId)
                    if (pic != null || team != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(team?.let { Color(it.primary).copy(alpha = 0.16f) } ?: P.surface2)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (pic != null) PlayerJersey(pic, if (pic.teamId >= 0) lg.team(pic.teamId) else team, 72.dp)
                            else if (team != null) TeamBadge(team, 60.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pic?.name ?: team?.name ?: "", style = T.bodyB, color = P.ink)
                                Text(
                                    if (pic != null) "${pic.posLabel}${if (pic.teamId >= 0) " · ${lg.team(pic.teamId).name}" else ""}"
                                    else team?.let { "${it.w}-${it.l}" } ?: "",
                                    style = T.small,
                                    color = P.sub,
                                )
                            }
                        }
                    }
                    if (n.body.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (para in n.body.split("\n\n")) {
                                if (para.isNotBlank()) Text(para.trim(), style = T.body, color = P.ink)
                            }
                        }
                    }
                    if (lg.ollama.enabled) {
                        Spacer(Modifier.height(14.dp))
                        Hairline()
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("AI STORY · ${lg.ollama.model}", style = T.label, color = P.accent)
                                Text(
                                    when {
                                        generating -> "Writing with local AI..."
                                        n.source.contains("Ollama") -> "Generated by ${lg.ollama.model}"
                                        else -> "Rewrite this piece with your free/cheap local model."
                                    },
                                    style = T.small,
                                    color = P.sub,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            SmallButton(
                                if (generating) "Writing..." else if (n.source.contains("Ollama")) "Regenerate" else "Rewrite",
                                enabled = !generating,
                                primary = true,
                            ) {
                                generating = true
                                ollamaError = null
                                scope.launch {
                                    val res = Ollama.generateArticle(lg.ollama, lg, n)
                                    generating = false
                                    res.onSuccess { text ->
                                        vm.act {
                                            val target = it.news.getOrNull(index) ?: return@act
                                            target.body = text
                                            if (!target.source.contains("Ollama")) {
                                                target.source = "${target.source} · Ollama ${lg.ollama.model}".trim(' ', '·')
                                            }
                                        }
                                    }.onFailure { err ->
                                        ollamaError = err.message ?: "Failed"
                                        vm.toast("AI: ${err.message ?: "Connection failed"}")
                                    }
                                }
                            }
                        }
                        if (ollamaError != null) {
                            Spacer(Modifier.height(6.dp))
                            Text("AI error: $ollamaError", style = T.small, color = P.bad)
                        }
                    }
                }
                if (n.reactions.isNotEmpty()) {
                    Section("Reactions · ${n.reactions.size}")
                    GroupList(n.reactions.sortedByDescending { it.likes }, inset = 60.dp) { r -> ReactionRow(r, lg) }
                }
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(8.dp))
                    if (n.gameId >= 0 && lg.games.any { it.id == n.gameId && it.box != null }) {
                        SecondaryButton("Box score", Modifier.fillMaxWidth()) { vm.push(Route.Box(n.gameId)) }
                    }
                    lg.playerOrNull(n.pid)?.let { pl ->
                        SecondaryButton("${pl.name}'s page", Modifier.fillMaxWidth()) { vm.openPlayer(pl.id) }
                    }
                    lg.teams.getOrNull(n.teamId)?.let { tm ->
                        SecondaryButton("${tm.name} team page", Modifier.fillMaxWidth()) { vm.push(Route.TeamPage(tm.id)) }
                    }
                    SecondaryButton("All news", Modifier.fillMaxWidth()) { vm.push(Route.News) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** A headline as shown: "No. 1" never breaks across lines. */
fun headline(text: String): String = text.replace("No. ", "No.\u00A0")

/** A headline row: tap it to read the story (a chevron says there is one). */
@Composable
fun HeadlineRow(vm: GameViewModel, lg: League, n: News) {
    ListRow(onClick = newsClick(vm, lg, n)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (n.mine) P.accent else P.line))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NewsTag(n)
            Text(headline(n.text), style = if (n.mine) T.bodyB else T.body, color = if (n.mine) P.ink else P.sub)
            // One line under the headline: the byline (shortened if it must), then the reactions and "Read", never wrapped.
            val said = if (n.reactions.isEmpty()) null else "${n.reactions.size}\u00A0${if (n.reactions.size == 1) "reaction" else "reactions"}"
            if (n.body.isNotEmpty() || said != null) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (n.body.isNotEmpty()) Text(n.source.ifEmpty { "Story" }, style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (said != null) {
                    if (n.body.isNotEmpty()) Text("  ·  ", style = T.small, color = P.faint)
                    Icon(Icons.Default.Favorite, null, tint = P.bad.copy(alpha = 0.6f), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(said, style = T.small, color = P.sub, maxLines = 1, softWrap = false)
                }
            }
        }
        // A story to read: a chevron says so, instead of a "Read" link on every row.
        if (n.body.isNotEmpty() || n.reactions.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Chevron() }
    }
}

@Suppress("unused") private val keepColor = Color.Unspecified

/** One reaction to a story: who said it (player headshot when we know him, else a real account / face), their role, what they said, the likes. */
@Composable
fun ReactionRow(r: Reaction, lg: League? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        val initials = r.who.split(" ").filter { it.isNotEmpty() && it.first().isLetter() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val img = androidx.compose.runtime.remember(r.avatar) { if (r.avatar.isEmpty()) null else Logos.asset(ctx, "social/${r.avatar}.webp") }
        val pl = remember(lg, r.who) { lg?.let { resolvePlayer(it, r.who) } }
        val mood = when (r.tone) { 1 -> P.good; -1 -> P.bad; else -> P.accent }
        Box(Modifier.size(40.dp).clip(CircleShape).background(mood.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            when {
                // Players always show their real headshot / portrait — even if a stock avatar was attached.
                pl != null -> PlayerSocialPic(lg!!, pl, 40.dp)
                img != null -> androidx.compose.foundation.Image(img, r.who, Modifier.size(40.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                // Leagues and team accounts keep their initials; people get a face.
                r.role.contains("League") || (r.role.contains("team", ignoreCase = true) && !r.role.contains("Teammate") && !r.role.contains("coach", ignoreCase = true)) ->
                    Text(initials, style = T.label, color = mood)
                else -> RealFace(r.who, r.who, 40.dp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.who, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (r.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.CheckCircle, "Verified", tint = P.accent, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(6.dp))
                Text(r.role, style = T.small, color = P.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text(r.text, style = T.body, color = P.ink, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = P.bad.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(Road.count(r.likes), style = T.small, color = P.sub)
                Spacer(Modifier.width(10.dp))
                Text(if (r.tone > 0) "🔥 👏" else if (r.tone < 0) "😬 💀" else "💬 👀", style = T.small, color = P.faint)
            }
        }
    }
}

// ---------- the news page ----------

/** Which stories a filter shows. */
private fun newsFilter(lg: League, n: News, f: Int): Boolean = when (f) {
    1 -> n.mine || n.teamId == lg.userTeamId
    2 -> n.kind in setOf("game", "recap", "article", "series", "upset", "buzzer", "moment", "rivalry") || n.rivalry
    3 -> n.kind == "trade" || n.kind == "move" || n.text.startsWith("Free agency") || n.text.startsWith("Front office") || n.text.startsWith("Coaching change") || n.text.contains(" signs ")
    4 -> n.kind == "column" || n.kind == "quote"
    else -> true
}

/** The news page: the top story up front, then everything, newest first, with filters. */
@Composable
fun NewsScreen(vm: GameViewModel, lg: League) {
    var filter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var showOllamaDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var customTopic by remember { androidx.compose.runtime.mutableStateOf("") }
    var writingStory by remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val all = lg.news.filter { it.season >= lg.season - 1 }.reversed()
    val shown = all.filter { newsFilter(lg, it, filter) }
    // The top story: the newest real article, the user's first.
    val top = shown.take(25).filter { it.body.isNotEmpty() }.maxByOrNull { (if (it.mine) 2 else 0) + (if (it.kind in setOf("trade", "series", "game")) 1 else 0) + it.day / 1000.0 + it.season }
    Column(Modifier.fillMaxSize()) {
        BackBar("News", "${lg.season} season · ${all.size} stories", onBack = { vm.back() }, actions = {
            if (lg.ollama.enabled) {
                SmallButton("AI story") { showOllamaDialog = true }
            }
        })
        LazyColumn(Modifier.weight(1f)) {
            item {
                Spacer(Modifier.height(6.dp))
                Segmented(listOf("All", "Your team", "Games", "Moves", "Columns"), filter, { filter = it }, scroll = true)
            }
            if (top != null) item {
                Spacer(Modifier.height(10.dp))
                TopStory(vm, lg, top)
            }
            if (filter == 0) item { LeagueInjuries(vm, lg) }
            if (shown.isEmpty()) item { Spacer(Modifier.height(10.dp)); Panel { Text("No stories here yet.", style = T.body, color = P.sub) } }
            val byDay = shown.filter { it !== top }.take(120).groupBy { it.season * 1000 + it.day }
            byDay.forEach { (key, list) ->
                item {
                    Section(if (key / 1000 != lg.season) "Last season" else if (key % 1000 == 0) "Preseason" else "Day ${key % 1000}")
                    GroupList(list, inset = 34.dp) { n -> HeadlineRow(vm, lg, n) }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showOllamaDialog) {
        val topics = listOf(
            "Locker room pulse & team chemistry",
            "Coach on the hot seat",
            "Rivalry tension & bad blood",
            "Breakout star feature",
            "Conference title race breakdown",
        )
        AlertDialog(
            onDismissRequest = { if (!writingStory) showOllamaDialog = false },
            containerColor = P.surface,
            title = { Text("Generate Hoops Wire story", style = T.head, color = P.ink) },
            text = {
                Column {
                    Text("${lg.ollama.model} will write a breaking article for today's news.", style = T.small, color = P.sub)
                    Spacer(Modifier.height(10.dp))
                    Text("SUGGESTED BEATS", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                    Spacer(Modifier.height(4.dp))
                    topics.forEach { t ->
                        Text(
                            "• $t",
                            style = T.body,
                            color = P.accent,
                            modifier = Modifier.fillMaxWidth().clickable { customTopic = t }.padding(vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("OR CUSTOM TOPIC", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customTopic,
                        onValueChange = { customTopic = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Recruiting drama, injury impact...", color = P.faint) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                    if (writingStory) {
                        Spacer(Modifier.height(8.dp))
                        Text("Writing article with Ollama... please wait.", style = T.small, color = P.accent)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val topic = customTopic.trim().ifEmpty { "Season outlook and team pulse" }
                        writingStory = true
                        scope.launch {
                            val res = Ollama.generateCustomStory(lg.ollama, lg, topic)
                            writingStory = false
                            showOllamaDialog = false
                            customTopic = ""
                            res.onSuccess { story ->
                                vm.act { it.news.add(story) }
                                vm.push(Route.Article(lg.news.lastIndex))
                            }.onFailure { err ->
                                vm.toast("Ollama: ${err.message ?: "Failed"}")
                            }
                        }
                    },
                    enabled = !writingStory,
                ) { Text(if (writingStory) "Writing..." else "Write story", color = P.accent) }
            },
            dismissButton = {
                TextButton({ showOllamaDialog = false }, enabled = !writingStory) { Text("Cancel", color = P.sub) }
            },
        )
    }
}

/** The lead story: its picture, headline and opening paragraph. */
@Composable
private fun TopStory(vm: GameViewModel, lg: League, n: News) {
    val t = lg.teams.getOrNull(n.teamId)
    val p = lg.playerOrNull(n.pid)
    val primary = t?.let { Color(it.primary) } ?: P.hero
    val fg = onColor(primary)
    Column(
        Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(primary, androidx.compose.ui.graphics.lerp(primary, Color.Black, 0.5f))))
            .clickable { newsClick(vm, lg, n)?.invoke() }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TOP STORY · ${n.source.substringAfter(" · ", n.source).ifEmpty { "News" }.uppercase()}", style = T.label, color = fg.copy(alpha = 0.8f), modifier = Modifier.weight(1f), maxLines = 1)
            if (t != null) TeamBadge(t, 30.dp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p != null) { PlayerJersey(p, if (p.teamId >= 0) lg.team(p.teamId) else t, 64.dp); Spacer(Modifier.width(12.dp)) }
            Text(headline(n.text), style = T.title.copy(fontSize = 24.sp, lineHeight = 26.sp), color = fg, modifier = Modifier.weight(1f))
        }
        n.body.split("\n\n").firstOrNull()?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = T.small, color = fg.copy(alpha = 0.9f), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Text("Read the story →", style = T.bodyB, color = fg)
    }
}
