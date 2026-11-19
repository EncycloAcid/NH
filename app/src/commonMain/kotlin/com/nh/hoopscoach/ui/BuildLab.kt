package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Attr
import com.nh.hoopscoach.game.Builds
import com.nh.hoopscoach.game.GoatBuild
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Ratings
import kotlin.math.roundToInt

/** 2K-style build lab: GOAT presets, offspring hybrids, or custom attribute sliders. */
@Composable
fun BuildLabScreen(vm: GameViewModel, lg: League, playerId: Int) {
    val p = lg.playerOrNull(playerId)
    if (p == null || p.teamId != lg.userTeamId) {
        Column { BackBar("Build Lab", onBack = { vm.back() }); Empty("Only players on your roster can use the Build Lab.") }
        return
    }
    var tab by remember(playerId) { mutableIntStateOf(0) }
    val goats = remember(p.pos) { Builds.nearby(p.pos) }
    var goatId by remember(playerId) { mutableStateOf(goats.firstOrNull()?.id ?: "") }
    var parentA by remember(playerId) { mutableStateOf(goats.getOrNull(0)?.id ?: "") }
    var parentB by remember(playerId) { mutableStateOf(goats.getOrNull(1)?.id ?: goats.getOrNull(0)?.id ?: "") }
    var bias by remember(playerId) { mutableFloatStateOf(0.5f) }
    var custom by remember(playerId) { mutableStateOf(p.attrs.copyOf()) }

    val goat = Builds.byId(goatId) ?: goats.firstOrNull()
    val a = Builds.byId(parentA) ?: goats.getOrNull(0)
    val b = Builds.byId(parentB) ?: goats.getOrNull(1) ?: a

    val previewAttrs = when (tab) {
        0 -> goat?.let { Builds.scaleToOvr(p.pos, it.attrs, p.ovr) } ?: p.attrs
        1 -> if (a != null && b != null) Builds.scaleToOvr(p.pos, Builds.offspring(a, b, bias), p.ovr) else p.attrs
        else -> {
            val budget = p.attrs.sum()
            val sum = custom.sum().coerceAtLeast(1)
            val scaled = IntArray(10) { i -> (custom[i].toDouble() * budget / sum).toInt().coerceIn(20, 99) }
            Builds.scaleToOvr(p.pos, scaled, p.ovr)
        }
    }
    val previewName = Builds.buildName(p.pos, previewAttrs)
    val likes = Builds.closestGoats(p.pos, previewAttrs, 3)
    val previewOvr = Ratings.ovr(p.pos, previewAttrs)
    val previewArch = when (tab) {
        0 -> goat?.arch ?: Builds.bestArch(p.pos, previewAttrs)
        1 -> if (a != null && b != null) (if (bias >= 0.5f) a.arch else b.arch) else Builds.bestArch(p.pos, previewAttrs)
        else -> Builds.bestArch(p.pos, previewAttrs)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Build Lab", p.name, onBack = { vm.back() })
            Panel {
                Text(previewName, style = T.head, color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append("Closest to: ")
                        append(likes.take(2).joinToString(" · ") { it.name })
                        if (likes.size > 2) append("  · also ${likes[2].name}")
                    },
                    style = T.small, color = P.accent,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(previewOvr, 48.dp, label = "OVR", textSize = 17.sp())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(previewArch.label, style = T.bodyB, color = P.ink)
                        Text("Overall stays near ${p.ovr}. Ratings reshape to the build.", style = T.small, color = P.sub)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("GOAT", "Offspring", "Custom"), tab, { tab = it }, scroll = true)
            Spacer(Modifier.height(10.dp))
        }

        when (tab) {
            0 -> {
                item {
                    Section("Play like a legend")
                    Text(
                        "Flavor-only NBA names. Applying a preset reshapes his ratings and play style.",
                        style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
                    )
                }
                items(goats, key = { it.id }) { g ->
                    GoatRow(g, selected = g.id == goat?.id) { goatId = g.id }
                }
            }
            1 -> {
                item {
                    Section("Offspring")
                    Text(
                        "Blend two legends. Bias slides toward parent A or B.",
                        style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
                    )
                    if (a != null) {
                        Picker(
                            "Parent A", a.name, goats.map { "${it.name} (${it.pos})" to it.blurb },
                            Modifier.padding(horizontal = Gutter).fillMaxWidth(),
                        ) { parentA = goats[it].id }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (b != null) {
                        Picker(
                            "Parent B", b.name, goats.map { "${it.name} (${it.pos})" to it.blurb },
                            Modifier.padding(horizontal = Gutter).fillMaxWidth(),
                        ) { parentB = goats[it].id }
                        Spacer(Modifier.height(8.dp))
                    }
                    Panel {
                        val pct = (bias * 100).roundToInt()
                        Text("Bias · $pct% ${(a?.name ?: "A")} / ${100 - pct}% ${(b?.name ?: "B")}", style = T.bodyB, color = P.ink)
                        HoopsSlider(bias, { bias = it }, 0.15f..0.85f)
                    }
                }
            }
            else -> {
                item {
                    Section("Build your own")
                    Text(
                        "Redistribute his attribute points. Overall is nudged back to ${p.ovr}.",
                        style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
                    )
                    Panel {
                        Attr.entries.forEachIndexed { i, attr ->
                            Text("${attr.label} · ${custom[i]}", style = T.bodyB, color = P.ink)
                            HoopsSlider(custom[i].toFloat(), { v ->
                                val next = custom.copyOf()
                                next[i] = v.roundToInt().coerceIn(20, 99)
                                custom = next
                            }, 20f..99f)
                            if (i < 9) Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Section("Preview ratings")
            Panel {
                Attr.entries.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { attr ->
                            val v = previewAttrs[attr.ordinal]
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(attr.short, style = T.label, color = P.sub, modifier = Modifier.weight(1f))
                                    Text("$v", style = T.numSm, color = tier(v))
                                }
                                Bar(v / 99f, Modifier.fillMaxWidth(), height = 5.dp)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", Modifier.weight(1f)) { vm.back() }
                PrimaryButton("Apply build", Modifier.weight(1f), enabled = when (tab) {
                    0 -> goat != null
                    1 -> a != null && b != null
                    else -> true
                }) {
                    val g = goat
                    val pa = a
                    val pb = b
                    val biasNow = bias
                    val customNow = custom.copyOf()
                    val which = tab
                    vm.act {
                        val pl = it.player(playerId)
                        when (which) {
                            0 -> if (g != null) Builds.applyGoat(pl, g)
                            1 -> if (pa != null && pb != null) Builds.applyOffspring(pl, pa, pb, biasNow)
                            else -> Builds.applyCustom(pl, customNow)
                        }
                    }
                    vm.toast("Build applied")
                    vm.back()
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun GoatRow(g: GoatBuild, selected: Boolean, onClick: () -> Unit) {
    Panel(
        Modifier.padding(bottom = 8.dp),
        color = if (selected) P.accentSoft else P.surface,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.name, style = T.head, color = if (selected) P.accent else P.ink)
                Text("${g.pos} · ${g.arch.label}", style = T.small, color = P.sub)
                Text(g.blurb, style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp))
            }
            if (selected) Tag("SELECTED", P.accent, filled = true)
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
