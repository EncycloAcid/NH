package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.OnDeviceAi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
private fun stateColor(s: OnDeviceAi.State): Color = when (s) {
    OnDeviceAi.State.READY -> P.good
    OnDeviceAi.State.DOWNLOADABLE, OnDeviceAi.State.DOWNLOADING, OnDeviceAi.State.UNKNOWN -> P.warn
    else -> P.bad
}

/** Where Gemini Nano stands on this phone, with the one thing to do about it (download, check again). */
@Composable
private fun NanoStatus(scope: CoroutineScope, onChanged: () -> Unit = {}) {
    var status by remember { mutableStateOf(OnDeviceAi.last) }
    var progress by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { status = OnDeviceAi.refresh(); onChanged() }
    fun recheck() { scope.launch { busy = true; status = OnDeviceAi.refresh(); busy = false; onChanged() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(9.dp).clip(CircleShape).background(stateColor(status.state)))
        Spacer(Modifier.width(8.dp))
        Text(if (busy) "Checking…" else OnDeviceAi.stateLabel(status), style = T.bodyB.copy(fontSize = 14.sp), color = P.ink, modifier = Modifier.weight(1f))
        if (status.model.isNotBlank()) Text(status.model, style = T.small, color = P.faint)
    }
    if (status.detail.isNotBlank()) Text(status.detail, style = T.small, color = P.sub, modifier = Modifier.padding(top = 4.dp))
    if (progress.isNotBlank()) Text(progress, style = T.small, color = P.accent, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(10.dp))
    when (status.state) {
        OnDeviceAi.State.DOWNLOADABLE -> PrimaryButton(if (busy) "Downloading…" else "Download Gemini Nano", Modifier.fillMaxWidth(), enabled = !busy) {
            val e = OnDeviceAi.engine ?: return@PrimaryButton
            scope.launch {
                busy = true
                val r = e.download { progress = it }
                progress = r.exceptionOrNull()?.message ?: "Download complete"
                status = OnDeviceAi.refresh()
                busy = false
                onChanged()
            }
        }
        OnDeviceAi.State.READY -> {}
        else -> SmallButton(if (busy) "Checking…" else "Check again", enabled = !busy && OnDeviceAi.supported) { recheck() }
    }
}

/** The settings panel for Gemini Nano as the AI provider. */
@Composable
fun NanoProviderPanel(
    vm: GameViewModel, lg: League, scope: CoroutineScope, testing: Boolean,
    onTesting: (Boolean) -> Unit, onTestStatus: (String) -> Unit, onTestSuccess: (Boolean) -> Unit,
) {
    var ready by remember { mutableStateOf(OnDeviceAi.ready) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(GameIcons.Sparkle, P.accent, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("GEMINI NANO · ON THIS PHONE", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                Text(OnDeviceAi.label(), style = T.head, color = P.ink)
            }
        }
        Text("Google's on-device model, through Android's AICore. It runs on your phone's chip (Tensor, on a Pixel): no account, no key, and nothing leaves the phone.",
            style = T.small, color = P.sub, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        if (!OnDeviceAi.supported) {
            Text(OnDeviceAi.NOT_HERE + " On the PC, pick Local (Ollama) or a cloud provider.", style = T.small, color = P.warn)
        } else {
            NanoStatus(scope) { ready = OnDeviceAi.ready; vm.redraw() }
            com.nh.hoopscoach.game.AiTraffic.status().filter { it.startsWith("Gemini Nano") }.forEach {
                Text("$it. The game writes its own lines until then; Test Gemini Nano tries now.", style = T.small, color = P.warn, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(14.dp))
            NanoModelTable(vm, lg, everythingElse = true)
            if (ready) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(if (testing) "Writing…" else "Test Gemini Nano", Modifier.fillMaxWidth(), enabled = !testing) {
                    onTesting(true)
                    onTestStatus("Asking Gemini Nano on this phone…")
                    scope.launch {
                        com.nh.hoopscoach.game.AiTraffic.reset()
                        val res = com.nh.hoopscoach.game.Ollama.testNano(lg.ollama)
                        onTesting(false)
                        res.onSuccess { line ->
                            onTestSuccess(true)
                            onTestStatus("Gemini Nano: \"${line.trim().trim('"')}\"")
                        }.onFailure { err ->
                            onTestSuccess(false)
                            onTestStatus(err.message ?: "Gemini Nano didn't answer.")
                        }
                    }
                }
            }
        }
        Text("It answers only while the game is on screen, within a daily allowance AICore sets for each app, and it keeps prompts short. When it can't answer, the game's own writing fills in.",
            style = T.small, color = P.faint, modifier = Modifier.padding(top = 12.dp))
    }
}

/**
 * With another provider picked: dialogue (press conferences, interviews, speeches, posts, recruiting letters)
 * can still be written by Gemini Nano on the phone, while longer articles stay with that provider.
 */
@Composable
fun NanoDialogueRow(vm: GameViewModel, lg: League, scope: CoroutineScope) {
    if (!OnDeviceAi.supported) return
    val box = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(box).border(1.dp, if (lg.ollama.nanoForDialogue) P.accent.copy(alpha = 0.6f) else P.line, box).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(GameIcons.Sparkle, P.accent, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Dialogue on Gemini Nano", style = T.bodyB, color = P.ink)
                Text("Press conferences, interviews, speeches, posts and recruiting letters written privately on this phone. Articles stay with ${lg.ollama.providerDisplayName}.",
                    style = T.small, color = P.sub)
            }
            Spacer(Modifier.width(8.dp))
            AppSwitch(lg.ollama.nanoForDialogue, { on -> vm.act { it.ollama.nanoForDialogue = on } })
        }
        if (lg.ollama.nanoForDialogue) {
            Spacer(Modifier.height(10.dp))
            NanoStatus(scope) { vm.redraw() }
            if (!OnDeviceAi.ready) Text("Until it's ready, dialogue stays with ${lg.ollama.providerDisplayName}.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            NanoModelTable(vm, lg, everythingElse = false)
        }
    }
}

/**
 * Which of the phone's two Gemini Nano models writes each kind of copy: the fast one (E2B) for quick, short
 * lines, the full one (E4B) for the writing that needs more. Tap to switch a row.
 */
@Composable
fun NanoModelTable(vm: GameViewModel, lg: League, everythingElse: Boolean) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    Text("MODEL BY FEATURE", style = T.label.copy(fontSize = 10.sp), color = P.sub)
    Text("Fast (E2B) answers quickest; Full (E4B) writes better. AICore runs whichever of the two this phone has.",
        style = T.small, color = P.faint, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
    @Composable
    fun row(label: String, blurb: String, fast: Boolean, set: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = T.bodyB.copy(fontSize = 14.sp), color = P.ink)
                Text(blurb, style = T.small, color = P.sub)
            }
            Spacer(Modifier.width(8.dp))
            SmallButton("Fast · E2B", primary = fast) { if (!fast) set(true) }
            Spacer(Modifier.width(6.dp))
            SmallButton("Full · E4B", primary = !fast) { if (fast) set(false) }
        }
    }
    OnDeviceAi.Desk.entries.forEach { d ->
        row(d.label, d.blurb, OnDeviceAi.fast(lg.ollama, d)) { f -> vm.act { it.ollama.nanoSizes[d.name] = if (f) "fast" else "full" } }
    }
    if (everythingElse) row("Everything else", "Articles, recaps and the rest", lg.ollama.nanoFast) { f -> vm.act { it.ollama.nanoFast = f } }
}
