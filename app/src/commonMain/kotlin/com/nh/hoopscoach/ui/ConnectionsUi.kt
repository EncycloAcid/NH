package com.nh.hoopscoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Connections
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LinkKind
import com.nh.hoopscoach.game.WebLink
import com.nh.hoopscoach.game.WebNode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The color a kind of link is drawn in: program ties in the accent, the tree green, rivals red, players gold. */
@Composable
private fun linkColor(k: LinkKind): Color = when (k.group) {
    "Coaching tree" -> P.good
    "Rivals" -> P.bad
    "Players" -> P.gold
    else -> P.accent
}

private val GROUPS = listOf("Coaching tree", "Rivals", "Players", "Program")

/**
 * The relationship web: a coach or a program at the middle, the people and programs tied to it around
 * it, and every link listed below. Tapping a coach or a program puts it at the middle; tapping a player
 * opens his card.
 */
@Composable
fun ConnectionsScreen(vm: GameViewModel, lg: League, focus: WebNode) {
    val links = remember(focus, vm.rev) { Connections.linksFor(lg, focus) }
    val groups = remember(links) { listOf("All") + GROUPS.filter { g -> links.any { it.kind.group == g } } }
    var filter by remember(focus) { mutableIntStateOf(0) }
    val group = groups.getOrNull(filter)?.takeIf { it != "All" }
    val shown = if (group == null) links else links.filter { it.kind.group == group }
    val open: (WebLink) -> Unit = { l -> openNode(vm, l.node) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Connections", Connections.title(lg, focus), onBack = { vm.back() }) {
                teamOf(lg, focus)?.let { tid -> SmallButton(if (focus.kind == WebNode.TEAM) "Team page" else "His team") { vm.push(Route.TeamPage(tid)) } }
            }
        }
        if (links.isEmpty()) {
            item {
                Panel { Empty("No connections yet. A coaching tree, rivals and former players build up season by season.") }
            }
            return@LazyColumn
        }
        item {
            Panel(padding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                Web(lg, focus, Connections.onWeb(shown), open)
            }
        }
        if (groups.size > 2) item { Spacer(Modifier.height(10.dp)); Chips(groups, filter, { filter = it }) }
        for (kind in LinkKind.entries) {
            val rows = shown.filter { it.kind == kind }.sortedByDescending { it.strength }
            if (rows.isEmpty()) continue
            item(key = kind.name) {
                Section(sectionTitle(kind, rows.size))
                GroupList(rows) { l ->
                    ListRow(onClick = l.node?.let { { open(l) } }) {
                        NodeAvatar(lg, l.node, l.title, 40.dp, linkColor(l.kind))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.title, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(l.detail, style = T.small, color = P.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (l.node != null) Chevron()
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun sectionTitle(k: LinkKind, n: Int) = when (k) {
    LinkKind.COACHES -> "Programs"
    LinkKind.MENTOR -> "Mentor"
    LinkKind.TREE -> "Coaching tree · $n"
    LinkKind.STAFF -> "Current staff"
    LinkKind.FORMER_COACH -> "Former head coaches"
    LinkKind.COACH_RIVAL -> "Coaching rivals"
    LinkKind.TEAM_RIVAL -> "Rivalries"
    LinkKind.PLAYED_AT -> "Played at"
    LinkKind.PLAYER -> "Former players · $n"
    LinkKind.LEGEND -> "Program legends"
}

private fun teamOf(lg: League, n: WebNode): Int? = when (n.kind) {
    WebNode.TEAM -> n.id
    WebNode.COACH -> lg.coaches.firstOrNull { it.id == n.id }?.teamId?.takeIf { it >= 0 }
    else -> null
}

private fun openNode(vm: GameViewModel, n: WebNode?) {
    when (n?.kind) {
        WebNode.COACH, WebNode.TEAM -> vm.push(Route.Web(n.kind, n.id))
        WebNode.PLAYER -> vm.openPlayer(n.id)
        WebNode.STAFF -> vm.push(Route.Staff)
        else -> {}
    }
}

/** The web: the focus in the middle, up to ten links around it on an ellipse, a line to each. */
@Composable
private fun Web(lg: League, focus: WebNode, links: List<WebLink>, open: (WebLink) -> Unit) {
    val nodeW = 76.dp
    val nodeH = 66.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(if (links.size <= 4) 250.dp else 320.dp)) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val cx = w / 2
        val cy = h / 2
        val rx = (w / 2 - with(density) { (nodeW / 2).toPx() }).coerceAtLeast(1f)
        val ry = (h / 2 - with(density) { (nodeH / 2).toPx() }).coerceAtLeast(1f)
        val points = links.indices.map { i ->
            val a = -kotlin.math.PI / 2 + 2 * kotlin.math.PI * i / links.size
            Offset(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
        }
        val colors = links.map { linkColor(it.kind) }
        Canvas(Modifier.fillMaxSize()) {
            links.forEachIndexed { i, l ->
                drawLine(
                    colors[i].copy(alpha = 0.55f), Offset(cx, cy), points[i],
                    strokeWidth = (1.2f + l.strength * 0.35f) * density.density, cap = StrokeCap.Round,
                )
            }
        }
        // The middle.
        val center = 70.dp
        Box(Modifier.offset { IntOffset((cx - center.toPx() / 2).toInt(), (cy - center.toPx() / 2).toInt()) }) {
            NodeAvatar(lg, focus, Connections.title(lg, focus), center, P.ink)
        }
        links.forEachIndexed { i, l ->
            val p = points[i]
            Column(
                Modifier.offset { IntOffset((p.x - nodeW.toPx() / 2).toInt(), (p.y - nodeH.toPx() / 2).toInt()) }
                    .width(nodeW).clip(RoundedCornerShape(10.dp))
                    .then(if (l.node != null) Modifier.clickable { open(l) } else Modifier)
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NodeAvatar(lg, l.node, l.title, 42.dp, colors[i])
                Text(
                    shortName(lg, l), style = T.small.copy(fontSize = 11.sp, lineHeight = 13.sp), color = P.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    l.kind.label.uppercase(), style = T.label.copy(fontSize = 9.sp, letterSpacing = 0.4.sp), color = colors[i],
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A short label for a node on the web: a last name, or a team's abbreviation. */
private fun shortName(lg: League, l: WebLink): String = when (l.node?.kind) {
    WebNode.TEAM -> lg.teams.getOrNull(l.node.id)?.let { if (lg.pro) it.mascot else it.abbr } ?: l.title
    else -> l.title.substringAfterLast(' ').ifBlank { l.title }
}

/** A team's logo, a player's portrait, or a coach's initials, in a ring of the link's color. */
@Composable
private fun NodeAvatar(lg: League, n: WebNode?, title: String, size: Dp, ring: Color) {
    val inner = size - 6.dp
    Box(Modifier.size(size).clip(CircleShape).border(2.dp, ring.copy(alpha = 0.85f), CircleShape), contentAlignment = Alignment.Center) {
        when (n?.kind) {
            WebNode.TEAM -> lg.teams.getOrNull(n.id)?.let { TeamBadge(it, inner) }
            WebNode.PLAYER -> lg.anyPlayer(n.id)?.let { p ->
                val t = lg.teams.getOrNull(p.teamId.takeIf { it >= 0 } ?: p.lastTeam)
                Box(Modifier.size(inner).clip(CircleShape)) { PixelPortrait(p, t, inner) }
            } ?: Initials(title, inner)
            WebNode.COACH -> lg.coaches.firstOrNull { it.id == n.id }?.let { CoachBadge(it.first, it.last, inner) } ?: Initials(title, inner)
            else -> Initials(title, inner)
        }
    }
}

@Composable
private fun Initials(name: String, size: Dp) {
    val parts = name.trim().split(' ')
    CoachBadge(parts.firstOrNull().orEmpty(), parts.drop(1).lastOrNull().orEmpty(), size, color = P.surface2, textColor = P.sub)
}

/** The way in from the Career page: how many people and programs are tied to the coach. */
@Composable
fun ConnectionsCard(vm: GameViewModel, lg: League) {
    val me = WebNode(WebNode.COACH, lg.userCoachId)
    val links = remember(vm.rev) { Connections.linksFor(lg, me) }
    val open = { vm.push(Route.Web(me.kind, me.id)) }
    Section("Connections", action = "Open", onAction = open)
    Panel(onClick = open) {
        val tree = links.count { it.kind == LinkKind.TREE }
        val rivals = links.count { it.kind == LinkKind.COACH_RIVAL }
        val players = links.count { it.kind == LinkKind.PLAYER }
        Row(Modifier.fillMaxWidth()) {
            Stat("Coaching tree", "$tree", Modifier.weight(1f), color = P.good, style = T.numSm)
            Stat("Rivals", "$rivals", Modifier.weight(1f), color = P.bad, style = T.numSm)
            Stat("Former players", "$players", Modifier.weight(1f), color = P.gold, style = T.numSm)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Your mentor, coaching tree, rivals and former players as a web. Tap anyone to see their connections.",
            style = T.small, color = P.sub,
        )
    }
}
