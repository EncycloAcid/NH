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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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

/** One post: avatar, name with a check if verified, handle and day, the text, and the likes. [onOpen] opens the story it's about. */
@Composable
fun PostRow(p: Post, onOpen: (() -> Unit)? = null, lg: League? = null) {
    Row(Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        // Everyone has a profile picture: real accounts their own, the rest drawn (see ProfilePic).
        ProfilePic(lg, p, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.CheckCircle, "Verified", tint = P.accent, modifier = Modifier.size(14.dp)) }
                Spacer(Modifier.width(6.dp))
                Text("@${p.handle}", style = T.small, color = P.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            // A reply to a story says which one (the outlet's own post is the story).
            if (p.re.isNotEmpty() && p.kind != "news") Text("on: ${headline(p.re)}", style = T.small, color = P.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
            Text(p.text, style = if (p.kind == "news") T.bodyB else T.body, color = P.ink, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = P.bad.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(Road.count(p.likes), style = T.small, color = P.sub)
                Spacer(Modifier.width(10.dp))
                Text("${kindLabel(p.kind)} · day ${p.day}", style = T.small, color = P.faint)
                if (onOpen != null) { Spacer(Modifier.weight(1f)); Text("Read", style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = P.accent) }
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

/** A short article: the outlet and date, the headline, and the story. */
@Composable
fun ArticleScreen(vm: GameViewModel, lg: League, index: Int) {
    val n = lg.news.getOrNull(index) ?: run { vm.back(); return }
    // A newspaper page: paper, serif type, the outlet's nameplate, the headline, then the story.
    val paper = if (P.dark) Color(0xFF221F1A) else Color(0xFFF6F1E5)
    val ink = if (P.dark) Color(0xFFEFE8D8) else Color(0xFF1B1915)
    val faded = ink.copy(alpha = 0.6f)
    val serif = androidx.compose.ui.text.font.FontFamily.Serif
    val outlet = n.source.substringAfter(" · ", n.source).ifEmpty { "The Daily" }
    val byline = n.source.substringBefore(" · ", "").takeIf { it.isNotEmpty() && n.source.contains(" · ") }
    Column(Modifier.fillMaxSize()) {
        BackBar(outlet, "${n.season} season · day ${n.day}", onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(horizontal = Gutter, vertical = 10.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(paper).padding(16.dp)) {
                    Text(outlet, style = androidx.compose.ui.text.TextStyle(fontFamily = serif, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = 24.sp),
                        color = ink, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(2.dp).background(ink))
                    Box(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp).height(0.75.dp).background(ink))
                    Text(when (n.kind) { "column" -> "COLUMN"; "quote" -> "INTERVIEW"; "trade", "move" -> "TRANSACTIONS"; "series" -> "PLAYOFFS"; "team" -> "STREAK WATCH"; "game" -> "BIG NIGHT"; "upset", "buzzer", "moment" -> "MOMENTS"; "" -> "NEWS"; else -> "GAME STORY" } + " · ${n.season} · DAY ${n.day}",
                        style = androidx.compose.ui.text.TextStyle(fontFamily = serif, fontSize = 11.sp, letterSpacing = 1.5.sp), color = faded)
                    Text(headline(n.text), style = androidx.compose.ui.text.TextStyle(fontFamily = serif, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = 26.sp, lineHeight = 30.sp),
                        color = ink, modifier = Modifier.padding(top = 6.dp))
                    if (byline != null) Text("By $byline", style = androidx.compose.ui.text.TextStyle(fontFamily = serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 13.sp),
                        color = faded, modifier = Modifier.padding(top = 6.dp))
                    // The picture: the player the story is about, or his team.
                    val pic = lg.playerOrNull(n.pid)
                    val team = lg.teams.getOrNull(n.teamId)
                    if (pic != null || team != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(team?.let { Color(it.primary).copy(alpha = 0.18f) } ?: faded.copy(alpha = 0.1f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (pic != null) PlayerJersey(pic, if (pic.teamId >= 0) lg.team(pic.teamId) else team, 72.dp) else if (team != null) TeamBadge(team, 60.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pic?.name ?: team?.name ?: "", style = androidx.compose.ui.text.TextStyle(fontFamily = serif, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp), color = ink)
                                Text(if (pic != null) "${pic.posLabel}${if (pic.teamId >= 0) " · ${lg.team(pic.teamId).name}" else ""}" else team?.let { "${it.w}-${it.l}" } ?: "",
                                    style = androidx.compose.ui.text.TextStyle(fontFamily = serif, fontSize = 13.sp), color = faded)
                            }
                        }
                    }
                    if (n.body.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (para in n.body.split("\n\n")) Text(para, style = androidx.compose.ui.text.TextStyle(fontFamily = serif, fontSize = 16.sp, lineHeight = 23.sp), color = ink)
                        }
                    }
                }
                // What people said: the players, coaches, analysts and fans, most liked first.
                if (n.reactions.isNotEmpty()) {
                    Section("Reactions · ${n.reactions.size}")
                    GroupList(n.reactions.sortedByDescending { it.likes }, inset = 60.dp) { r -> ReactionRow(r) }
                }
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(8.dp))
                    if (n.gameId >= 0 && lg.games.any { it.id == n.gameId && it.box != null }) SecondaryButton("Box score", Modifier.fillMaxWidth()) { vm.push(Route.Box(n.gameId)) }
                    lg.playerOrNull(n.pid)?.let { pl -> SecondaryButton("${pl.name}'s page", Modifier.fillMaxWidth()) { vm.openPlayer(pl.id) } }
                    lg.teams.getOrNull(n.teamId)?.let { tm -> SecondaryButton("${tm.name} team page", Modifier.fillMaxWidth()) { vm.push(Route.TeamPage(tm.id)) } }
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

/** One reaction to a story: who said it (a real account's avatar, or initials tinted by the mood), their role, what they said, the likes. */
@Composable
fun ReactionRow(r: Reaction) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        val initials = r.who.split(" ").filter { it.isNotEmpty() && it.first().isLetter() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val img = androidx.compose.runtime.remember(r.avatar) { if (r.avatar.isEmpty()) null else Logos.asset(ctx, "social/${r.avatar}.webp") }
        val mood = when (r.tone) { 1 -> P.good; -1 -> P.bad; else -> P.accent }
        Box(Modifier.size(36.dp).clip(CircleShape).background(mood.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            when {
                img != null -> androidx.compose.foundation.Image(img, r.who, Modifier.size(36.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                // Leagues and team accounts keep their initials; people get a face.
                r.role.contains("League") || r.role.contains("team", ignoreCase = true) && !r.role.contains("Teammate") -> Text(initials, style = T.label, color = mood)
                else -> RealFace(r.who, r.who, 36.dp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.who, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (r.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.CheckCircle, "Verified", tint = P.accent, modifier = Modifier.size(14.dp)) }
            }
            Text(r.role, style = T.small, color = P.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.text, style = T.body, color = P.ink, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = P.bad.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(Road.count(r.likes), style = T.small, color = P.sub)
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
    val all = lg.news.filter { it.season >= lg.season - 1 }.reversed()
    val shown = all.filter { newsFilter(lg, it, filter) }
    // The top story: the newest real article, the user's first.
    val top = shown.take(25).filter { it.body.isNotEmpty() }.maxByOrNull { (if (it.mine) 2 else 0) + (if (it.kind in setOf("trade", "series", "game")) 1 else 0) + it.day / 1000.0 + it.season }
    Column(Modifier.fillMaxSize()) {
        BackBar("News", "${lg.season} season · ${all.size} stories", onBack = { vm.back() })
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
