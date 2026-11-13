package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.AiDiagnostics
import com.nh.hoopscoach.game.Ollama
import com.nh.hoopscoach.game.OllamaConfig
import kotlinx.coroutines.launch

@Composable
fun AiDiagnosticsPanel(
    config: OllamaConfig,
    onAutoPickChanged: ((Boolean) -> Unit)? = null,
    onModel: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var model by remember(config.provider, config.activeModelName) { mutableStateOf(config.activeModelName) }
    var models by remember(config.provider) { mutableStateOf<List<Ollama.CatalogEntry>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val last = AiDiagnostics.last
    fun applyPick(available: List<String> = models.map { it.id }) {
        val pick = Ollama.pickBestModel(config.provider, available)
        model = pick
        onModel(pick)
    }
    Panel {
        Text("AI status & diagnostics", style = T.bodyB, color = P.ink)
        Text("${config.providerDisplayName} · ${config.activeModelName}", style = T.small, color = P.sub)
        Text("Reports: ${if (config.enabled) "on" else "off"} · Automatic generation: ${if (config.autoGenerate) "on" else "off"}", style = T.small, color = P.sub)
        Text(AiDiagnostics.setupHint(config), style = T.small, color = P.sub)
        Text(last?.let { "Last request: ${it.provider} / ${it.model} · ${it.request}\n${java.util.Date(it.time)}\n${it.result}: ${it.detail}" } ?: "No AI requests recorded yet.", style = T.small, color = P.ink)
        Spacer(Modifier.height(8.dp))
        if (onAutoPickChanged != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-pick model", style = T.bodyB, color = P.ink)
                    Text("Keep a proven free/cheap text model for ${config.shortActionName}. Skips retired, preview, image, and audio ids. Re-applies when you fetch the provider list.", style = T.small, color = P.sub)
                }
                AppSwitch(config.autoPickModel, { on ->
                    onAutoPickChanged(on)
                    if (on) applyPick()
                })
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(model, { model = it }, label = { Text("Model ID (must be available to your provider)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        Spacer(Modifier.height(8.dp))
        SmallButton("Use model", enabled = model.isNotBlank()) {
            onAutoPickChanged?.invoke(false)
            onModel(model.trim())
        }
        Spacer(Modifier.height(8.dp))
        SmallButton("Auto-pick model") { applyPick() }
        Spacer(Modifier.height(8.dp))
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
                        val ids = response.getOrNull().orEmpty()
                        models = Ollama.catalogFetchedModels(config.provider, ids)
                        modelMenu = models.isNotEmpty()
                        if (response.isSuccess && config.autoPickModel) applyPick(ids)
                        AiDiagnostics.finish(
                            id,
                            if (response.isSuccess) "success" else "network error",
                            if (response.isSuccess) "Found ${models.size} models in ${models.map { it.category }.distinct().size} categories. Select one; API generation may incur provider charges. A model list does not verify generation access."
                            else AiDiagnostics.networkReason(response.exceptionOrNull(), config),
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        AiDiagnostics.finish(id, "cancelled", "Model lookup cancelled")
                        throw e
                    } finally {
                        fetching = false
                    }
                }
            }
            val grouped = remember(models) { models.groupBy { it.categoryOrder to it.category }.toList() }
            AppMenu(modelMenu, { modelMenu = false }, title = "Model list · price", minWidth = 280.dp, maxWidth = 360.dp, maxHeight = 480.dp) {
                if (models.isEmpty()) {
                    AppMenuItem("No models", "Fetch again after connecting", enabled = false) {}
                } else {
                    grouped.forEachIndexed { i, (_, entries) ->
                        val category = entries.first().category
                        Text(category.uppercase(), style = T.label, color = P.accent, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = if (i == 0) 2.dp else 8.dp, bottom = 4.dp))
                        entries.forEach { entry ->
                            AppMenuItem(
                                title = entry.title,
                                subtitle = "${entry.price} · ${entry.id}",
                                selected = entry.id.equals(model, ignoreCase = true) || entry.id.equals(config.activeModelName, ignoreCase = true),
                            ) {
                                model = entry.id
                                onAutoPickChanged?.invoke(false)
                                onModel(entry.id)
                                modelMenu = false
                            }
                        }
                        if (i < grouped.lastIndex) AppMenuDivider()
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
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
                } finally {
                    testing = false
                }
            }
        }
    }
}

@Composable
fun ApiKeyDialog(provider: String, existing: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(existing) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.surface,
        title = { Text("$provider API key", style = T.head, color = P.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Use an API key issued by $provider. This app does not support email/password login or browser OAuth. Never enter your account password here. The key is saved with this career; do not share saves containing it.", style = T.small, color = P.sub)
                OutlinedTextField(key, { key = it.trim() }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, colors = fieldColors())
            }
        },
        confirmButton = { TextButton(enabled = key.isNotBlank(), onClick = { onSave(key) }) { Text("Save key") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
