package com.nh.hoopscoach.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How a cloud AI vendor exposes developer access to third-party apps.
 *
 * Claude Pro / Max, ChatGPT Plus, and Google Gemini Advanced (chat) subscriptions
 * only work inside those vendors' own apps. They cannot authorize this game.
 * Developer access uses a separate console access key (often with a free tier).
 */
data class AiAccountProvider(
    val id: String,
    val displayName: String,
    /** Browser URL for the developer keys console. */
    val signInUrl: String,
    val keyPlaceholder: String,
    val freeNote: String,
)

fun aiAccountProvider(id: String): AiAccountProvider = when (id.lowercase()) {
    "google" -> AiAccountProvider(
        id = "google",
        displayName = "Google Gemini (AI Studio)",
        signInUrl = "https://aistudio.google.com/apikey",
        keyPlaceholder = "AIza…",
        freeNote = "Gemini Advanced / the Gemini chat app cannot power this game. Use a free AI Studio developer key instead. Never enter your Google password here.",
    )
    "openai" -> AiAccountProvider(
        id = "openai",
        displayName = "OpenAI (developer)",
        signInUrl = "https://platform.openai.com/api-keys",
        keyPlaceholder = "sk-…",
        freeNote = "ChatGPT Plus / the ChatGPT app cannot power this game. Use a separate OpenAI developer key (billed separately).",
    )
    "anthropic" -> AiAccountProvider(
        id = "anthropic",
        displayName = "Anthropic Claude (API)",
        signInUrl = "https://console.anthropic.com/settings/keys",
        keyPlaceholder = "sk-ant-…",
        freeNote = "Claude Pro / Max / claude.ai chat cannot power this game — Anthropic blocks third-party apps from using those subscriptions. Use a Claude Console developer key instead.",
    )
    "groq" -> AiAccountProvider(
        id = "groq",
        displayName = "Groq",
        signInUrl = "https://console.groq.com/keys",
        keyPlaceholder = "gsk_…",
        freeNote = "Free tier available. Create a developer key at Groq Console, then paste it.",
    )
    "deepseek" -> AiAccountProvider(
        id = "deepseek",
        displayName = "DeepSeek",
        signInUrl = "https://platform.deepseek.com/api_keys",
        keyPlaceholder = "sk-…",
        freeNote = "Create a developer key at DeepSeek, then paste it.",
    )
    "openrouter" -> AiAccountProvider(
        id = "openrouter",
        displayName = "OpenRouter",
        signInUrl = "https://openrouter.ai/keys",
        keyPlaceholder = "sk-or-…",
        freeNote = "Free :free models available. Create a key at OpenRouter, then paste it.",
    )
    "cerebras" -> AiAccountProvider(
        id = "cerebras",
        displayName = "Cerebras",
        signInUrl = "https://cloud.cerebras.ai/",
        keyPlaceholder = "csk-…",
        freeNote = "Free tier available. Create a developer key at Cerebras Cloud, then paste it.",
    )
    "mistral" -> AiAccountProvider(
        id = "mistral",
        displayName = "Mistral",
        signInUrl = "https://console.mistral.ai/api-keys/",
        keyPlaceholder = "…",
        freeNote = "Free tier available. Create a developer key at Mistral Console, then paste it.",
    )
    else -> AiAccountProvider(
        id = id,
        displayName = id,
        signInUrl = "",
        keyPlaceholder = "developer key",
        freeNote = "Chatbot subscriptions cannot authorize this app. Use a developer key from the provider console. Never enter your account password.",
    )
}

/**
 * Developer connect flow: open the vendor console, create a key, paste it.
 * Consumer chat subscriptions (Claude Pro, ChatGPT Plus, Gemini Advanced) are not usable here.
 */
@Composable
fun AccountConnectDialog(
    providerId: String,
    existingKey: String,
    existingEmail: String = "",
    onSave: (key: String, email: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val info = remember(providerId) { aiAccountProvider(providerId) }
    val uri = LocalUriHandler.current
    var key by remember(existingKey) { mutableStateOf(existingKey) }
    var email by remember(existingEmail) { mutableStateOf(existingEmail) }
    var showKey by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.surface,
        title = { Text("Add ${info.displayName}", style = T.head, color = P.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Chatbot subscriptions (Claude Pro, ChatGPT Plus, Gemini Advanced) only work in those apps — they cannot run this game.\n\n" +
                        "1. Open the developer console and create a key (many have a free tier).\n" +
                        "2. Paste the key below — never enter your account password.\n\n" +
                        info.freeNote,
                    style = T.small,
                    color = P.sub,
                )
                Spacer(Modifier.height(10.dp))
                if (info.signInUrl.isNotBlank()) {
                    PrimaryButton("Open ${info.displayName} console", Modifier.fillMaxWidth()) {
                        runCatching { uri.openUri(info.signInUrl) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    email,
                    { email = it.trim().take(80) },
                    label = { Text("Account email (optional)") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    key,
                    { key = it.trim() },
                    label = { Text("Developer access key") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            if (showKey) "HIDE" else "SHOW",
                            style = T.label.copy(fontSize = 10.sp),
                            color = P.accent,
                            modifier = Modifier.clickable { showKey = !showKey }.padding(8.dp),
                        )
                    },
                    placeholder = { Text(info.keyPlaceholder, color = P.faint) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = key.isNotBlank(), onClick = { onSave(key, email) }) {
                Text("Save key")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Back-compat wrapper — prefer [AccountConnectDialog]. */
@Composable
fun ApiKeyDialog(provider: String, existing: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val id = when {
        provider.contains("Gemini", true) || provider.contains("Google", true) -> "google"
        provider.contains("OpenAI", true) || provider.contains("ChatGPT", true) -> "openai"
        provider.contains("Claude", true) || provider.contains("Anthropic", true) -> "anthropic"
        provider.contains("Groq", true) -> "groq"
        provider.contains("DeepSeek", true) -> "deepseek"
        provider.contains("OpenRouter", true) -> "openrouter"
        provider.contains("Cerebras", true) -> "cerebras"
        provider.contains("Mistral", true) -> "mistral"
        else -> provider.lowercase()
    }
    AccountConnectDialog(id, existing, onSave = { key, _ -> onSave(key) }, onDismiss = onDismiss)
}
