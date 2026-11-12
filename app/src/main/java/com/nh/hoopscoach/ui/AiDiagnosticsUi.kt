package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nh.hoopscoach.game.AiDiagnostics
import com.nh.hoopscoach.game.Ollama
import com.nh.hoopscoach.game.OllamaConfig
import kotlinx.coroutines.launch

@Composable
fun AiDiagnosticsPanel(config: OllamaConfig, onModel: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var model by remember(config.provider, config.activeModelName) { mutableStateOf(config.activeModelName) }
    var models by remember(config.provider) { mutableStateOf<List<String>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val last = AiDiagnostics.last
    Panel {
        Text("AI status & diagnostics", style = T.bodyB, color = P.ink)
        Text("${config.providerDisplayName} · ${config.activeModelName}", style = T.small, color = P.sub)
        Text("Reports: ${if (config.enabled) "on" else "off"} · Automatic generation: ${if (config.autoGenerate) "on" else "off"}", style = T.small, color = P.sub)
        Text(AiDiagnostics.setupHint(config), style = T.small, color = P.sub)
        Text(last?.let { "Last request: ${it.provider} / ${it.model} · ${it.request}\n${java.util.Date(it.time)}\n${it.result}: ${it.detail}" } ?: "No AI requests recorded yet.", style = T.small, color = P.ink)
        OutlinedTextField(model, { model = it }, label = { Text("Model ID (must be available to your provider)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        SmallButton("Use model", enabled = model.isNotBlank()) { onModel(model.trim()) }
        Box {
            SmallButton(if (fetching) "Fetching models…" else "Fetch available models", enabled = !fetching) {
                fetching = true
                scope.launch {
                    val id = AiDiagnostics.begin(config, "Model list")
                    try {
                        val response = when (config.provider.lowercase()) {
                            "google" -> Ollama.testGoogleConnection(config.googleApiKey)
                            "openai" -> Ollama.testOpenAiConnection(config.openAiApiKey, config.customEndpoint)
                            else -> Ollama.testConnection(config.host)
                        }
                        models = response.getOrNull().orEmpty().distinct()
                        modelMenu = models.isNotEmpty()
                        AiDiagnostics.finish(id, if (response.isSuccess) "success" else "network error", if (response.isSuccess) "Found ${models.size} models. Select one; API generation may incur provider charges. A model list does not verify generation access." else AiDiagnostics.networkReason(response.exceptionOrNull(), config))
                    } catch (e: kotlinx.coroutines.CancellationException) { AiDiagnostics.finish(id, "cancelled", "Model lookup cancelled"); throw e }
                    finally { fetching = false }
                }
            }
            DropdownMenu(modelMenu, { modelMenu = false }) {
                models.forEach { id -> DropdownMenuItem(text = { Text(id, maxLines = 2) }, onClick = { onModel(id); modelMenu = false }) }
            }
        }
        SmallButton(if (testing) "Testing AI…" else "Test AI", enabled = !testing) {
            testing = true
            scope.launch {
                val id = AiDiagnostics.begin(config, "Test AI")
                try {
                    val response = Ollama.generate(config, "Reply with exactly OK.", "Connection test. Reply OK only.", numPredict = 128)
                    AiDiagnostics.finish(id, if (response.isSuccess) "success" else "network error", if (response.isSuccess) "Model returned a response. Reports are also checked against game facts." else AiDiagnostics.networkReason(response.exceptionOrNull(), config))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    AiDiagnostics.finish(id, "cancelled", "Test cancelled")
                    throw e
                } finally { testing = false }
            }
        }
    }
}

@Composable
fun ApiKeyDialog(provider: String, existing: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(existing) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = P.surface,
        title = { Text("$provider API key", style = T.head, color = P.ink) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Use an API key issued by $provider. This app does not support email/password login or browser OAuth. Never enter your account password here. The key is saved with this career; do not share saves containing it.", style = T.small, color = P.sub)
            OutlinedTextField(key, { key = it.trim() }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, colors = fieldColors())
        } },
        confirmButton = { TextButton(enabled = key.isNotBlank(), onClick = { onSave(key) }) { Text("Save key") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
