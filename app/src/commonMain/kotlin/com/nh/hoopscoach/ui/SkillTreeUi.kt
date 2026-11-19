package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.SkillTree

/**
 * The player's archetype skill tree: four skills in three tiers, what each raises, and an Unlock button where
 * you can spend his points (your players, or yourself in a player career).
 */
@Composable
fun SkillTreePanel(vm: GameViewModel, lg: League, p: Player, canSpend: Boolean) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    SkillTree.ensure(p)
    val tree = SkillTree.tree(p.style)
    Section("Skill tree · ${p.style.label}")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SKILL POINTS", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                Text("${p.skillPts}", style = T.num, color = if (p.skillPts > 0) P.accent else P.ink)
            }
            Text("A point every season he plays, two for a big one.", style = T.small, color = P.faint, modifier = Modifier.weight(1.4f))
        }
        if (tree.isEmpty()) {
            Text("Manual Allocation has no tree. Pick an archetype (Training focus) to unlock its skills.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 10.dp))
        }
        for (tier in 1..3) {
            val row = tree.filter { it.tier == tier }
            if (row.isEmpty()) continue
            Text(when (tier) { 1 -> "TIER 1 · IMPROVING"; 2 -> "TIER 2 · ENHANCING"; else -> "MASTERY" }, style = T.label.copy(fontSize = 10.sp),
                color = if (tier == 3) P.gold else P.sub, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            row.forEach { s ->
                val have = s.id in p.skills
                val can = canSpend && SkillTree.unlockable(p, s)
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape)
                        .background(if (have) P.accentSoft else P.surface2.copy(alpha = 0.5f))
                        .border(1.dp, if (have) P.accent.copy(alpha = 0.5f) else P.line, shape).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(if (have) P.accent else P.surface2), contentAlignment = Alignment.Center) {
                        Icon(if (have) GameIcons.Star else GameIcons.Lock, null, tint = if (have) P.onAccent else P.faint, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = T.bodyB.copy(fontSize = 14.sp), color = if (have) P.ink else P.sub)
                        Text(s.blurb, style = T.small, color = if (have) P.accent else P.faint)
                    }
                    Spacer(Modifier.width(8.dp))
                    when {
                        have -> Text("UNLOCKED", style = T.label.copy(fontSize = 9.sp), color = P.accent)
                        can -> SmallButton("Unlock · ${s.cost}", primary = true) { var msg = ""; vm.act { msg = SkillTree.unlock(p, s) }; vm.toast(msg.ifEmpty { s.name }) }
                        else -> Text(SkillTree.lockedReason(p, s).ifEmpty { "${s.cost} pts" }, style = T.small.copy(fontSize = 11.sp), color = P.faint)
                    }
                }
            }
        }
        val other = SkillTree.unlocked(p).filter { sk -> tree.none { it.id == sk.id } }
        if (other.isNotEmpty()) Text("Also learned: " + other.joinToString(", ") { it.name }, style = T.small, color = P.sub, modifier = Modifier.padding(top = 10.dp))
    }
}
