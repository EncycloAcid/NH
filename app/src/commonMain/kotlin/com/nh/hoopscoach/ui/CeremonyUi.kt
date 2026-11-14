package com.nh.hoopscoach.ui

import com.nh.hoopscoach.PlatformBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Ceremony
import com.nh.hoopscoach.game.Farewell
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Reaction
import com.nh.hoopscoach.game.Tribute

/**
 * A career moment on a screen of its own: his retirement (the farewell letter, the numbers, the
 * tributes, the coverage) or his introduction as a head coach (the résumé, the introduction, the
 * opening statement he picks and how the room takes it).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CeremonyScreen(vm: GameViewModel, lg: League, c: Ceremony) {
    PlatformBackHandler(enabled = true) { }
    val team = lg.teams.getOrNull(c.teamId)
    val base = team?.let { teamCard(it) } ?: P.hero
    val second = team?.let { Color(it.secondary) } ?: P.gold
    val fg = onColor(base)
    val accent = if (contrast(second, lerp(base, Color.Black, 0.5f)) >= 2.2f) second else P.gold
    val hire = c.kind == "hire"
    val choosing = c.choices.isNotEmpty()
    // The title fades in, the way a moment should.
    val fade = remember(c) { Animatable(0f) }
    LaunchedEffect(c) { fade.animateTo(1f, tween(900)) }
    Box(Modifier.fillMaxSize().background(P.bg)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(lerp(base, Color.Black, 0.1f), lerp(base, Color.Black, 0.78f))))
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                ) {
                    // His number, huge and faint behind the words.
                    if (c.number > 0) Text(
                        "${c.number}", style = T.hero.copy(fontSize = 220.sp, lineHeight = 200.sp), color = fg.copy(alpha = 0.08f),
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                    Column(Modifier.alpha(fade.value)) {
                        Spacer(Modifier.height(24.dp))
                        Text(c.kicker, style = T.label.copy(fontSize = 13.sp, letterSpacing = 3.sp), color = accent)
                        Spacer(Modifier.height(6.dp))
                        Text(c.title, style = T.hero.copy(fontSize = 50.sp, lineHeight = 50.sp), color = fg)
                        Spacer(Modifier.height(8.dp))
                        Text(c.sub, style = T.bodyB, color = fg.copy(alpha = 0.85f))
                        if (c.badge.isNotEmpty()) Text((if (c.kind == "traded") "⇄ " else "🏆 ") + c.badge, style = T.small, color = accent, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(16.dp))
                        team?.let { TeamBadge(it, 64.dp) }
                        Spacer(Modifier.height(18.dp))
                        // The numbers.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            c.statLabels.forEachIndexed { i, l ->
                                Column(
                                    Modifier.widthIn(min = 92.dp).clip(RoundedCornerShape(14.dp)).background(fg.copy(alpha = 0.1f))
                                        .border(1.dp, fg.copy(alpha = 0.15f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(c.statValues.getOrNull(i) ?: "", style = T.num.copy(fontSize = 24.sp), color = fg)
                                    Text(l, style = T.label.copy(fontSize = 10.sp), color = fg.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
            // The letter, or the introduction.
            item {
                Section(c.letterTitle)
                Panel {
                    c.letter.forEachIndexed { i, para ->
                        Text(
                            para, style = if (c.kind != "retire") T.body else T.body.copy(fontStyle = FontStyle.Italic, lineHeight = 24.sp), color = P.ink,
                            modifier = Modifier.padding(bottom = if (i < c.letter.lastIndex) 12.dp else 0.dp),
                        )
                    }
                }
            }
            if (choosing) item { PressConference(vm, c) }
            // What people said.
            val said = if (choosing && c.chosen >= 0) c.choiceReactions.getOrNull(c.chosen).orEmpty() + c.tributes else c.tributes
            if (said.isNotEmpty()) item {
                Section(when (c.kind) { "retire" -> "Tributes"; "hire" -> "The reaction"; else -> "What they're saying" })
                GroupList(said, inset = 60.dp) { ReactionRow(it.asReaction(), lg) }
            }
            if (c.coverage.isNotEmpty()) item {
                Section("The coverage")
                GroupList(c.coverage) { h ->
                    ListRow {
                        Box(Modifier.width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(base))
                        Spacer(Modifier.width(10.dp))
                        Text(h, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f))
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(P.bg.copy(alpha = 0.94f)).padding(horizontal = Gutter, vertical = 10.dp)) {
            val ready = !choosing || c.chosen >= 0
            PrimaryButton(
                when {
                    !ready -> if (hire) "Choose your opening statement" else "Choose what you say"
                    hire -> "Get to work"
                    lg.ceremonyQueue.isNotEmpty() -> "Next"
                    else -> "Continue"
                },
                Modifier.fillMaxWidth(), enabled = ready,
            ) { vm.act { Farewell.dismiss(it) } }
        }
    }
}

@Composable
private fun PressConference(vm: GameViewModel, c: Ceremony) {
    Section(c.choiceTitle.ifEmpty { "The podium is yours" })
    if (c.chosen < 0) {
        Text(c.choicePrompt.ifEmpty { "Your opening statement sets the tone. Pick one." }, style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 2.dp))
        c.choices.forEachIndexed { i, line ->
            Panel(Modifier.padding(vertical = 4.dp), onClick = { vm.act { Farewell.speak(it, i) } }) {
                Text("\"$line\"", style = T.head, color = P.ink)
                Text(c.choiceQuotes.getOrNull(i) ?: "", style = T.small, color = P.sub, modifier = Modifier.padding(top = 4.dp))
                Text(c.effects.getOrNull(i) ?: Farewell.effect(i), style = T.small.copy(fontSize = 12.sp), color = P.accent, modifier = Modifier.padding(top = 6.dp))
            }
        }
    } else {
        Panel(color = P.hero) {
            Text("YOU SAID", style = T.label, color = P.onHero.copy(alpha = 0.75f))
            Text("\"${c.choices[c.chosen]}\"", style = T.title.copy(fontSize = 24.sp, lineHeight = 28.sp), color = P.onHero)
            Spacer(Modifier.height(6.dp))
            Text(c.choiceQuotes.getOrNull(c.chosen) ?: "", style = T.body.copy(fontStyle = FontStyle.Italic), color = P.onHero.copy(alpha = 0.9f))
            Spacer(Modifier.height(8.dp))
            Text(c.effects.getOrNull(c.chosen) ?: Farewell.effect(c.chosen), style = T.small, color = P.gold, textAlign = TextAlign.Start)
        }
    }
}

private fun Tribute.asReaction() = Reaction(who, role, text, (kotlin.math.abs(text.hashCode()) % 48_000) + 900, avatar, verified)
