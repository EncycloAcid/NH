package com.nh.hoopscoach.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.AiDiagnostics
import com.nh.hoopscoach.game.Ollama
import com.nh.hoopscoach.game.OllamaConfig
import kotlinx.coroutines.launch

/**
 * AI connection status, model list fetch, and Test AI.
 * Auto-pick lives in the main AI settings panel — not duplicated here.
 */
@Composable
fun AiDiagnosticsPanel(
    config: OllamaConfig,
    onAutoPickChanged: ((Boolean) -> Unit)? = null,
    embedded: Boolean = false,
    onModel: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var model by remember(config.provider, config.activeModelName) { mutableStateOf(config.activeModelName) }
    var models by remember(config.provider) { mutableStateOf<List<Ollama.CatalogEntry>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var showLast by remember { mutableStateOf(false) }
    var showCustomId by remember { mutableStateOf(false) }
    val last = AiDiagnostics.last

    @Composable
    fun ColumnScope.body() {
        Text("AI status & diagnostics", style = T.bodyB, color = P.ink)
        Text(
            "${config.providerDisplayName} · ${config.activeModelName}",
            style = T.small,
            color = P.sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "Reports: ${if (config.enabled) "on" else "off"} · Auto-generate: ${if (config.autoGenerate) "on" else "off"}",
            style = T.small,
            color = P.sub,
        )
        Text(AiDiagnostics.setupHint(config), style = T.small, color = P.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showLast = !showLast }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (last == null) "No AI requests yet"
                else "${if (showLast) "▼" else "▶"} Last: ${last.result} · ${last.request}",
                style = T.small,
                color = P.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showLast && last != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${last.provider} / ${last.model}\n${java.util.Date(last.time)}\n${last.detail}",
                style = T.small,
                color = P.sub,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                SecondaryButton(
                    if (fetching) "Fetching…" else "Fetch models",
                    Modifier.fillMaxWidth(),
                    enabled = !fetching,
                ) {
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
                            if (response.isSuccess && config.autoPickModel) {
                                val pick = Ollama.pickBestModel(config.provider, ids)
                                model = pick
                                onModel(pick)
                            }
                            AiDiagnostics.finish(
                                id,
                                if (response.isSuccess) "success" else "network error",
                                if (response.isSuccess) {
                                    "Found ${models.size} models in ${models.map { it.category }.distinct().size} categories. Select one; API generation may incur provider charges."
                                } else AiDiagnostics.networkReason(response.exceptionOrNull(), config),
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
                AppMenu(modelMenu, { modelMenu = false }, title = "Model list · price", minWidth = 280.dp, maxWidth = 360.dp, maxHeight = 420.dp) {
                    if (models.isEmpty()) {
                        AppMenuItem("No models", "Fetch again after connecting", enabled = false) {}
                    } else {
                        grouped.forEachIndexed { i, (_, entries) ->
                            val category = entries.first().category
                            Text(
                                category.uppercase(),
                                style = T.label,
                                color = P.accent,
                                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = if (i == 0) 2.dp else 8.dp, bottom = 4.dp),
                            )
                            entries.forEach { entry ->
                                AppMenuItem(
                                    title = entry.title,
                                    subtitle = "${entry.price} · ${entry.id}",
                                    selected = entry.id.equals(model, ignoreCase = true) ||
                                        entry.id.equals(config.activeModelName, ignoreCase = true),
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
            SecondaryButton(
                if (testing) "Testing…" else "Test AI",
                Modifier.weight(1f),
                enabled = !testing,
            ) {
                testing = true
                scope.launch {
                    val id = AiDiagnostics.begin(config, "Test AI")
                    try {
                        val response = Ollama.generate(
                            config,
                            "Reply with exactly OK.",
                            "Connection test. Reply OK only.",
                            numPredict = 128,
                        )
                        AiDiagnostics.finish(
                            id,
                            if (response.isSuccess) "success" else "network error",
                            if (response.isSuccess) "Model returned a response."
                            else AiDiagnostics.networkReason(response.exceptionOrNull(), config),
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        AiDiagnostics.finish(id, "cancelled", "Test cancelled")
                        throw e
                    } finally {
                        testing = false
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showCustomId = !showCustomId }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (showCustomId) "▼ Hide custom model ID" else "▶ Custom model ID",
                style = T.label.copy(fontSize = 11.sp),
                color = P.sub,
            )
        }
        if (showCustomId) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                model,
                { model = it },
                label = { Text("Model ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Use model", Modifier.fillMaxWidth(), enabled = model.isNotBlank()) {
                onAutoPickChanged?.invoke(false)
                onModel(model.trim())
            }
        }
    }

    if (embedded) {
        Column(Modifier.fillMaxWidth()) { body() }
    } else {
        Panel { body() }
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
                Text(
                    "Use an API key issued by $provider. This app does not support email/password login or browser OAuth. Never enter your account password here. The key is saved with this career; do not share saves containing it.",
                    style = T.small,
                    color = P.sub,
                )
                OutlinedTextField(
                    key,
                    { key = it.trim() },
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = { TextButton(enabled = key.isNotBlank(), onClick = { onSave(key) }) { Text("Save key") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
