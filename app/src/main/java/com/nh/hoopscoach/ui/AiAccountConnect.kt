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
 * How a cloud AI vendor exposes free/paid access to third-party apps.
 * Consumer chat logins (ChatGPT app, claude.ai, etc.) never authorize API calls —
 * you sign into the vendor account in the browser, then paste an access key from that account.
 */
data class AiAccountProvider(
    val id: String,
    val displayName: String,
    /** Browser URL where the user signs into their vendor account / keys console. */
    val signInUrl: String,
    val keyPlaceholder: String,
    val freeNote: String,
)

fun aiAccountProvider(id: String): AiAccountProvider = when (id.lowercase()) {
    "google" -> AiAccountProvider(
        id = "google",
        displayName = "Google Gemini",
        signInUrl = "https://aistudio.google.com/apikey",
        keyPlaceholder = "AIza…",
        freeNote = "Sign in with Google, create a free Gemini key, then paste it here. Never enter your Google password in this app.",
    )
    "openai" -> AiAccountProvider(
        id = "openai",
        displayName = "OpenAI / ChatGPT",
        signInUrl = "https://platform.openai.com/api-keys",
        keyPlaceholder = "sk-…",
        freeNote = "Sign in with your OpenAI / ChatGPT account on the site, create an access key, then paste it. ChatGPT Plus alone does not authorize this app.",
    )
    "anthropic" -> AiAccountProvider(
        id = "anthropic",
        displayName = "Anthropic Claude",
        signInUrl = "https://console.anthropic.com/settings/keys",
        keyPlaceholder = "sk-ant-…",
        freeNote = "Sign in to Anthropic, create an access key from your account, then paste it. claude.ai chat login alone does not authorize this app.",
    )
    "groq" -> AiAccountProvider(
        id = "groq",
        displayName = "Groq",
        signInUrl = "https://console.groq.com/keys",
        keyPlaceholder = "gsk_…",
        freeNote = "Sign in at Groq Console (free tier), create an access key, then paste it.",
    )
    "deepseek" -> AiAccountProvider(
        id = "deepseek",
        displayName = "DeepSeek",
        signInUrl = "https://platform.deepseek.com/api_keys",
        keyPlaceholder = "sk-…",
        freeNote = "Sign in to DeepSeek, create an access key, then paste it.",
    )
    "openrouter" -> AiAccountProvider(
        id = "openrouter",
        displayName = "OpenRouter",
        signInUrl = "https://openrouter.ai/keys",
        keyPlaceholder = "sk-or-…",
        freeNote = "Sign in to OpenRouter (free :free models), create an access key, then paste it.",
    )
    "cerebras" -> AiAccountProvider(
        id = "cerebras",
        displayName = "Cerebras",
        signInUrl = "https://cloud.cerebras.ai/",
        keyPlaceholder = "csk-…",
        freeNote = "Sign in to Cerebras Cloud (free tier), create an access key, then paste it.",
    )
    "mistral" -> AiAccountProvider(
        id = "mistral",
        displayName = "Mistral",
        signInUrl = "https://console.mistral.ai/api-keys/",
        keyPlaceholder = "…",
        freeNote = "Sign in to Mistral Console (free tier), create an access key, then paste it.",
    )
    else -> AiAccountProvider(
        id = id,
        displayName = id,
        signInUrl = "",
        keyPlaceholder = "access key",
        freeNote = "Sign in to your provider account in the browser, create an access key, then paste it here. Never enter your account password.",
    )
}

/**
 * Account connect flow: open the vendor site to sign in, then paste the access key from that account.
 * Passwords and in-app OAuth for ChatGPT/Claude chat are not supported by these vendors.
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
        title = { Text("Connect ${info.displayName}", style = T.head, color = P.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "1. Tap Sign in to open ${info.displayName} in your browser and log into your account.\n" +
                        "2. Create a free access key from that account.\n" +
                        "3. Paste the key below — never enter your account password here.\n\n" +
                        info.freeNote,
                    style = T.small,
                    color = P.sub,
                )
                Spacer(Modifier.height(10.dp))
                if (info.signInUrl.isNotBlank()) {
                    PrimaryButton("Sign in to ${info.displayName}", Modifier.fillMaxWidth()) {
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
                    label = { Text("Access key from your account") },
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
                Text("Connect account")
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
