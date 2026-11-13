package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.OllamaConfig

/** Relative length of one AI writing desk (drives the Settings ETA chip). */
enum class AiWriteSize { SHORT, MEDIUM, LONG, HEAVY }

/**
 * Rough write-time estimates for Settings. These are guidance only —
 * real latency varies with model, network, and queue (one desk at a time).
 */
object AiWriteEta {
    /** Base seconds for a medium-length desk on the active provider / speed mode. */
    fun baseSeconds(config: OllamaConfig): Float {
        val provider = config.provider.lowercase()
        val cloud = when (provider) {
            "cerebras", "groq" -> 1.4f
            "google", "mistral" -> 2.2f
            "openrouter", "deepseek" -> 2.8f
            "openai" -> 3.2f
            "anthropic" -> 4.0f
            else -> when (config.speedMode.lowercase()) {
                "quality" -> 10f
                "balanced" -> 6f
                else -> 3f // turbo / unknown
            }
        }
        return cloud
    }

    fun secondsFor(config: OllamaConfig, size: AiWriteSize): Float {
        val mult = when (size) {
            AiWriteSize.SHORT -> 0.65f
            AiWriteSize.MEDIUM -> 1.0f
            AiWriteSize.LONG -> 1.55f
            AiWriteSize.HEAVY -> 2.2f
        }
        return (baseSeconds(config) * mult).coerceAtLeast(0.8f)
    }

    fun label(seconds: Float): String = when {
        seconds < 1.5f -> "~1s"
        seconds < 2.5f -> "~2s"
        seconds < 4f -> "~${seconds.toInt().coerceAtLeast(3)}s"
        seconds < 9.5f -> "~${seconds.toInt()}s"
        seconds < 15f -> "~${((seconds / 5).toInt() * 5)}s"
        else -> "~${((seconds / 5).toInt() * 5)}s+"
    }

    fun chip(config: OllamaConfig, size: AiWriteSize): String = label(secondsFor(config, size))

    data class Desk(val enabled: Boolean, val size: AiWriteSize)

    /** Sum of enabled desks — useful when auto-generate queues several after a game. */
    fun totalLabel(config: OllamaConfig, desks: List<Desk>): String {
        val on = desks.filter { it.enabled }
        if (on.isEmpty()) return "No desks on"
        val total = on.sumOf { secondsFor(config, it.size).toDouble() }.toFloat()
        val n = on.size
        return if (n == 1) "Est. ${label(total)} per write"
        else "Est. ${label(total)} if $n desks queue after a game"
    }
}

@Composable
fun AiWriteOptionRow(
    title: String,
    blurb: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showEta: Boolean,
    config: OllamaConfig,
    size: AiWriteSize,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f, fill = false))
                if (showEta) {
                    Spacer(Modifier.width(8.dp))
                    Tag(AiWriteEta.chip(config, size), P.accent)
                }
            }
            Text(blurb, style = T.small, color = P.sub)
        }
        Spacer(Modifier.width(8.dp))
        AppSwitch(checked, onCheckedChange)
    }
    Spacer(Modifier.height(8.dp))
}
