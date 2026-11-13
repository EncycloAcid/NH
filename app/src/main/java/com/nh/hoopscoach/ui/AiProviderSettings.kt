package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Dispatches to a per-provider settings panel (each is its own composable for ART verify limits).
 */
@Composable
fun AiProviderConfigSection(
    vm: GameViewModel,
    lg: League,
    scope: CoroutineScope,
    testing: Boolean,
    onTesting: (Boolean) -> Unit,
    testStatus: String,
    onTestStatus: (String) -> Unit,
    testSuccess: Boolean,
    onTestSuccess: (Boolean) -> Unit,
    configuring: Boolean,
    onConfiguring: (Boolean) -> Unit,
    downloadingModel: String?,
    onDownloadingModel: (String?) -> Unit,
    availableModels: List<String>,
    onAvailableModels: (List<String>) -> Unit,
    onShowGoogleLogin: () -> Unit,
    onShowOpenAiLogin: () -> Unit,
    onShowAnthropicLogin: () -> Unit,
    onShowGroqLogin: () -> Unit,
    onShowDeepseekLogin: () -> Unit,
) {
    when (lg.ollama.provider.lowercase()) {
        "google" -> GoogleAiProviderPanel(
            vm, lg, scope, testing, onTesting, onTestStatus, onTestSuccess,
            onShowLogin = onShowGoogleLogin,
        )
        "openai" -> OpenAiProviderPanel(
            vm, lg, scope, testing, onTesting, onTestStatus, onTestSuccess,
            onShowLogin = onShowOpenAiLogin,
        )
        "anthropic" -> AnthropicAiProviderPanel(
            vm, lg, scope, testing, onTesting, onTestStatus, onTestSuccess,
            onShowLogin = onShowAnthropicLogin,
        )
        "groq" -> GroqAiProviderPanel(
            vm, lg, scope, testing, onTesting, onTestStatus, onTestSuccess,
            onShowLogin = onShowGroqLogin,
        )
        "deepseek" -> DeepseekAiProviderPanel(
            vm, lg, scope, testing, onTesting, onTestStatus, onTestSuccess,
            onShowLogin = onShowDeepseekLogin,
        )
        else -> LocalOllamaProviderPanel(
            vm, lg, scope, testing, onTesting, onTestStatus, onTestSuccess,
            configuring = configuring,
            onConfiguring = onConfiguring,
            downloadingModel = downloadingModel,
            onDownloadingModel = onDownloadingModel,
            availableModels = availableModels,
            onAvailableModels = onAvailableModels,
            onShowLogin = {},
        )
    }
}

@Composable
fun GoogleAiProviderPanel(
    vm: GameViewModel,
    lg: League,
    scope: CoroutineScope,
    testing: Boolean,
    onTesting: (Boolean) -> Unit,
    onTestStatus: (String) -> Unit,
    onTestSuccess: (Boolean) -> Unit,
    configuring: Boolean = false,
    onConfiguring: (Boolean) -> Unit = {},
    downloadingModel: String? = null,
    onDownloadingModel: (String?) -> Unit = {},
    availableModels: List<String> = emptyList(),
    onAvailableModels: (List<String>) -> Unit = {},
    onShowLogin: () -> Unit,
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showGoogleKey by remember { mutableStateOf(false) }

        Text("GOOGLE GEMINI AI (CLOUD)", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Spacer(Modifier.height(4.dp))
        Text("Fast cloud AI with a generous free tier. Generates rich articles in 1-2 seconds with zero phone battery or memory overhead.", style = T.small, color = P.sub)
        Spacer(Modifier.height(10.dp))

        if (lg.ollama.isGoogleSignedIn) {
            // Connected Account Profile Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(P.surface2, RoundedCornerShape(10.dp))
                    .border(1.5.dp, P.good.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(P.good.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", style = T.head.copy(fontSize = 16.sp), color = P.good)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                lg.ollama.googleDisplayEmail,
                                style = T.bodyB,
                                color = P.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(6.dp))
                            Tag("KEY SAVED", P.good)
                        }
                        Text("Gemini API key configured", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Edit API key", Modifier.weight(1f)) {
                        onShowLogin()
                    }
                    SecondaryButton("Remove key", Modifier.weight(1f)) {
                        vm.act {
                            it.ollama.googleSignedIn = false
                            it.ollama.googleApiKey = ""
                            it.ollama.googleAccountEmail = ""
                            it.ollama.googleAccountPassword = ""
                            it.ollama.googleAccountName = ""
                        }
                        vm.toast("Gemini key removed")
                    }
                }
            }
        } else {
            // Configure Gemini API Prompt Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(P.surface2, RoundedCornerShape(10.dp))
                    .border(1.dp, P.faint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(P.accentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", style = T.head.copy(fontSize = 16.sp), color = P.accent)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Gemini API access", style = T.bodyB, color = P.ink)
                        Text("A Gemini API key is required for recaps and quotes.", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                    }
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Configure Gemini API", Modifier.fillMaxWidth()) {
                    onShowLogin()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        val gModels = buildList {
            val cur = lg.ollama.googleModel
            if (cur.isNotBlank() && com.nh.hoopscoach.game.Ollama.GOOGLE_MODELS.none { it.id.equals(cur, true) }) {
                add(com.nh.hoopscoach.game.Ollama.CloudModel(cur, cur, "Selected", "From provider list — stays until you change it."))
            }
            addAll(com.nh.hoopscoach.game.Ollama.GOOGLE_MODELS)
        }
        val gIdx = gModels.indexOfFirst { it.id.equals(lg.ollama.googleModel, true) }.coerceAtLeast(0)
        Picker(
            label = if (lg.ollama.autoPickModel) "Gemini model (auto)" else "Gemini model",
            current = gModels[gIdx].name,
            options = gModels.map { it.name to "${it.tag} — ${it.description}" },
            display = "${if (lg.ollama.autoPickModel) "Auto · " else ""}${gModels[gIdx].name} · ${gModels[gIdx].tag}",
        ) { i -> vm.act {
            it.ollama.autoPickModel = false
            it.ollama.googleModel = gModels[i].id
        } }

        Spacer(Modifier.height(10.dp))
        // Advanced & developer toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvancedSettings = !showAdvancedSettings }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (showAdvancedSettings) "▼ Hide Advanced Key Settings" else "▶ Advanced API Key Settings",
                style = T.label.copy(fontSize = 11.sp),
                color = P.sub,
                modifier = Modifier.weight(1f),
            )
        }

        if (showAdvancedSettings) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(P.surface2, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text("Raw Gemini API Key", style = T.bodyB.copy(fontSize = 11.sp), color = P.ink)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = lg.ollama.googleApiKey,
                    onValueChange = { k ->
                        vm.act {
                            it.ollama.googleApiKey = k
                            if (k.isNotBlank()) it.ollama.googleSignedIn = true
                        }
                    },
                    singleLine = true,
                    placeholder = { Text("AIzaSy...", color = P.faint) },
                    visualTransformation = if (showGoogleKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            if (showGoogleKey) "HIDE" else "SHOW",
                            style = T.label.copy(fontSize = 10.sp),
                            color = P.accent,
                            modifier = Modifier.clickable { showGoogleKey = !showGoogleKey }.padding(8.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = if (testing) "Testing..." else "Test Connection",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testing && lg.ollama.isGoogleSignedIn,
                ) {
                    onTesting(true)
                    onTestStatus("Contacting Google Gemini...")
                    scope.launch {
                        val res = com.nh.hoopscoach.game.Ollama.testGoogleConnection(lg.ollama.googleApiKey, lg.ollama.googleAccountEmail)
                        onTesting(false)
                        res.onSuccess {
                            onTestSuccess(true)
                            onTestStatus("Connected to Google AI successfully!")
                            vm.toast("Google Gemini connected!")
                        }.onFailure { err ->
                            onTestSuccess(false)
                            onTestStatus(err.message ?: "Could not connect to Google AI")
                        }
                    }
                }
            }
        }
}

@Composable
fun OpenAiProviderPanel(
    vm: GameViewModel,
    lg: League,
    scope: CoroutineScope,
    testing: Boolean,
    onTesting: (Boolean) -> Unit,
    onTestStatus: (String) -> Unit,
    onTestSuccess: (Boolean) -> Unit,
    configuring: Boolean = false,
    onConfiguring: (Boolean) -> Unit = {},
    downloadingModel: String? = null,
    onDownloadingModel: (String?) -> Unit = {},
    availableModels: List<String> = emptyList(),
    onAvailableModels: (List<String>) -> Unit = {},
    onShowLogin: () -> Unit,
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showOpenAiKey by remember { mutableStateOf(false) }

        Text("OPENAI CHATGPT AI (CLOUD)", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Spacer(Modifier.height(4.dp))
        Text("Industry-leading conversational AI. Connect your OpenAI or ChatGPT account for deep commentary and smart storylines.", style = T.small, color = P.sub)
        Spacer(Modifier.height(10.dp))

        if (lg.ollama.isOpenAiSignedIn) {
            // Connected Account Profile Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(P.surface2, RoundedCornerShape(10.dp))
                    .border(1.5.dp, P.good.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(P.good.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("O", style = T.head.copy(fontSize = 16.sp), color = P.good)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                lg.ollama.openAiDisplayEmail,
                                style = T.bodyB,
                                color = P.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(6.dp))
                            Tag("KEY SAVED", P.good)
                        }
                        Text("OpenAI API key configured", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Edit API key", Modifier.weight(1f)) {
                        onShowLogin()
                    }
                    SecondaryButton("Remove key", Modifier.weight(1f)) {
                        vm.act {
                            it.ollama.openAiSignedIn = false
                            it.ollama.openAiApiKey = ""
                            it.ollama.openAiAccountEmail = ""
                            it.ollama.openAiAccountPassword = ""
                            it.ollama.openAiAccountName = ""
                        }
                        vm.toast("OpenAI key removed")
                    }
                }
            }
        } else {
            // Configure OpenAI API Prompt Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(P.surface2, RoundedCornerShape(10.dp))
                    .border(1.dp, P.faint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(P.accentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("O", style = T.head.copy(fontSize = 16.sp), color = P.accent)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("OpenAI API access", style = T.bodyB, color = P.ink)
                        Text("Configure an OpenAI API key for AI reports.", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                    }
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Configure OpenAI API", Modifier.fillMaxWidth()) {
                    onShowLogin()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        val oModels = buildList {
            val cur = lg.ollama.openAiModel
            if (cur.isNotBlank() && com.nh.hoopscoach.game.Ollama.OPENAI_MODELS.none { it.id.equals(cur, true) }) {
                add(com.nh.hoopscoach.game.Ollama.CloudModel(cur, cur, "Selected", "From provider list — stays until you change it."))
            }
            addAll(com.nh.hoopscoach.game.Ollama.OPENAI_MODELS)
        }
        val oIdx = oModels.indexOfFirst { it.id.equals(lg.ollama.openAiModel, true) }.coerceAtLeast(0)
        Picker(
            label = if (lg.ollama.autoPickModel) "ChatGPT model (auto)" else "ChatGPT model",
            current = oModels[oIdx].name,
            options = oModels.map { it.name to "${it.tag} — ${it.description}" },
            display = "${if (lg.ollama.autoPickModel) "Auto · " else ""}${oModels[oIdx].name} · ${oModels[oIdx].tag}",
        ) { i -> vm.act {
            it.ollama.autoPickModel = false
            it.ollama.openAiModel = oModels[i].id
        } }

        Spacer(Modifier.height(10.dp))
        // Advanced & developer toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvancedSettings = !showAdvancedSettings }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (showAdvancedSettings) "▼ Hide Advanced Key Settings" else "▶ Advanced Key & Custom Endpoint",
                style = T.label.copy(fontSize = 11.sp),
                color = P.sub,
                modifier = Modifier.weight(1f),
            )
        }

        if (showAdvancedSettings) {
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(P.surface2, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text("Raw OpenAI API Key", style = T.bodyB.copy(fontSize = 11.sp), color = P.ink)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = lg.ollama.openAiApiKey,
                    onValueChange = { k ->
                        vm.act {
                            it.ollama.openAiApiKey = k
                            if (k.isNotBlank()) it.ollama.openAiSignedIn = true
                        }
                    },
                    singleLine = true,
                    placeholder = { Text("sk-proj-...", color = P.faint) },
                    visualTransformation = if (showOpenAiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            if (showOpenAiKey) "HIDE" else "SHOW",
                            style = T.label.copy(fontSize = 10.sp),
                            color = P.accent,
                            modifier = Modifier.clickable { showOpenAiKey = !showOpenAiKey }.padding(8.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Custom Endpoint (Optional)", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = lg.ollama.customEndpoint,
                    onValueChange = { ep -> vm.act { it.ollama.customEndpoint = ep } },
                    singleLine = true,
                    placeholder = { Text("https://api.openai.com/v1/chat/completions", color = P.faint) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = if (testing) "Testing..." else "Test Connection",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testing && lg.ollama.isOpenAiSignedIn,
                ) {
                    onTesting(true)
                    onTestStatus("Contacting OpenAI...")
                    scope.launch {
                        val res = com.nh.hoopscoach.game.Ollama.testOpenAiConnection(lg.ollama.openAiApiKey, lg.ollama.customEndpoint, lg.ollama.openAiAccountEmail)
                        onTesting(false)
                        res.onSuccess {
                            onTestSuccess(true)
                            onTestStatus("Connected to OpenAI successfully!")
                            vm.toast("ChatGPT connected!")
                        }.onFailure { err ->
                            onTestSuccess(false)
                            onTestStatus(err.message ?: "Could not connect to OpenAI")
                        }
                    }
                }
            }
        }
}

@Composable
fun AnthropicAiProviderPanel(
    vm: GameViewModel,
    lg: League,
    scope: CoroutineScope,
    testing: Boolean,
    onTesting: (Boolean) -> Unit,
    onTestStatus: (String) -> Unit,
    onTestSuccess: (Boolean) -> Unit,
    configuring: Boolean = false,
    onConfiguring: (Boolean) -> Unit = {},
    downloadingModel: String? = null,
    onDownloadingModel: (String?) -> Unit = {},
    availableModels: List<String> = emptyList(),
    onAvailableModels: (List<String>) -> Unit = {},
    onShowLogin: () -> Unit,
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showAnthropicKey by remember { mutableStateOf(false) }

        Text("ANTHROPIC CLAUDE AI (CLOUD)", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Spacer(Modifier.height(4.dp))
        Text("Claude for sharp beat writing. Haiku stays cheap; Sonnet for richer dynasty copy.", style = T.small, color = P.sub)
        Spacer(Modifier.height(10.dp))
        CloudApiKeyCard(
            letter = "C",
            configured = lg.ollama.isAnthropicSignedIn,
            displayName = lg.ollama.anthropicDisplayEmail,
            statusLine = "Anthropic API key configured",
            promptTitle = "Claude API access",
            promptBlurb = "An Anthropic API key is required for Claude.",
            configureLabel = "Configure Claude API",
            onConfigure = { onShowLogin() },
            onRemove = {
                vm.act {
                    it.ollama.anthropicApiKey = ""
                    it.ollama.anthropicAccountEmail = ""
                }
                vm.toast("Claude key removed")
            },
        )
        Spacer(Modifier.height(12.dp))
        val aModels = buildList {
            val cur = lg.ollama.anthropicModel
            if (cur.isNotBlank() && com.nh.hoopscoach.game.Ollama.ANTHROPIC_MODELS.none { it.id.equals(cur, true) }) {
                add(com.nh.hoopscoach.game.Ollama.CloudModel(cur, cur, "Selected", "From provider list — stays until you change it."))
            }
            addAll(com.nh.hoopscoach.game.Ollama.ANTHROPIC_MODELS)
        }
        val aIdx = aModels.indexOfFirst { it.id.equals(lg.ollama.anthropicModel, true) }.coerceAtLeast(0)
        Picker(
            label = if (lg.ollama.autoPickModel) "Claude model (auto)" else "Claude model",
            current = aModels[aIdx].name,
            options = aModels.map { it.name to "${it.tag} — ${it.description}" },
            display = "${if (lg.ollama.autoPickModel) "Auto · " else ""}${aModels[aIdx].name} · ${aModels[aIdx].tag}",
        ) { i -> vm.act {
            it.ollama.autoPickModel = false
            it.ollama.anthropicModel = aModels[i].id
        } }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable { showAdvancedSettings = !showAdvancedSettings }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (showAdvancedSettings) "▼ Hide Advanced Key Settings" else "▶ Advanced API Key Settings",
                style = T.label.copy(fontSize = 11.sp),
                color = P.sub,
                modifier = Modifier.weight(1f),
            )
        }
        if (showAdvancedSettings) {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().background(P.surface2, RoundedCornerShape(8.dp)).padding(10.dp)) {
                Text("Raw Anthropic API Key", style = T.bodyB.copy(fontSize = 11.sp), color = P.ink)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = lg.ollama.anthropicApiKey,
                    onValueChange = { k -> vm.act { it.ollama.anthropicApiKey = k } },
                    singleLine = true,
                    placeholder = { Text("sk-ant-...", color = P.faint) },
                    visualTransformation = if (showAnthropicKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            if (showAnthropicKey) "HIDE" else "SHOW",
                            style = T.label.copy(fontSize = 10.sp),
                            color = P.accent,
                            modifier = Modifier.clickable { showAnthropicKey = !showAnthropicKey }.padding(8.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = if (testing) "Testing..." else "Test Connection",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testing && lg.ollama.isAnthropicSignedIn,
                ) {
                    onTesting(true)
                    onTestStatus("Contacting Anthropic...")
                    scope.launch {
                        val res = com.nh.hoopscoach.game.Ollama.testAnthropicConnection(lg.ollama.anthropicApiKey)
                        onTesting(false)
                        res.onSuccess {
                            onTestSuccess(true)
                            onTestStatus("Connected to Claude successfully!")
                            vm.toast("Claude connected!")
                        }.onFailure { err ->
                            onTestSuccess(false)
                            onTestStatus(err.message ?: "Could not connect to Claude")
                        }
                    }
                }
            }
        }
}

@Composable
fun GroqAiProviderPanel(
    vm: GameViewModel,
    lg: League,
    scope: CoroutineScope,
    testing: Boolean,
    onTesting: (Boolean) -> Unit,
    onTestStatus: (String) -> Unit,
    onTestSuccess: (Boolean) -> Unit,
    configuring: Boolean = false,
    onConfiguring: (Boolean) -> Unit = {},
    downloadingModel: String? = null,
    onDownloadingModel: (String?) -> Unit = {},
    availableModels: List<String> = emptyList(),
    onAvailableModels: (List<String>) -> Unit = {},
    onShowLogin: () -> Unit,
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showGroqKey by remember { mutableStateOf(false) }

        Text("GROQ AI (CLOUD)", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Spacer(Modifier.height(4.dp))
        Text("Very fast free-tier Llama and Gemma models. Great when Gemini hits rate limits.", style = T.small, color = P.sub)
        Spacer(Modifier.height(10.dp))
        CloudApiKeyCard(
            letter = "Q",
            configured = lg.ollama.isGroqSignedIn,
            displayName = lg.ollama.groqDisplayEmail,
            statusLine = "Groq API key configured",
            promptTitle = "Groq API access",
            promptBlurb = "A Groq API key is required from console.groq.com.",
            configureLabel = "Configure Groq API",
            onConfigure = { onShowLogin() },
            onRemove = {
                vm.act {
                    it.ollama.groqApiKey = ""
                    it.ollama.groqAccountEmail = ""
                }
                vm.toast("Groq key removed")
            },
        )
        Spacer(Modifier.height(12.dp))
        val qModels = buildList {
            val cur = lg.ollama.groqModel
            if (cur.isNotBlank() && com.nh.hoopscoach.game.Ollama.GROQ_MODELS.none { it.id.equals(cur, true) }) {
                add(com.nh.hoopscoach.game.Ollama.CloudModel(cur, cur, "Selected", "From provider list — stays until you change it."))
            }
            addAll(com.nh.hoopscoach.game.Ollama.GROQ_MODELS)
        }
        val qIdx = qModels.indexOfFirst { it.id.equals(lg.ollama.groqModel, true) }.coerceAtLeast(0)
        Picker(
            label = if (lg.ollama.autoPickModel) "Groq model (auto)" else "Groq model",
            current = qModels[qIdx].name,
            options = qModels.map { it.name to "${it.tag} — ${it.description}" },
            display = "${if (lg.ollama.autoPickModel) "Auto · " else ""}${qModels[qIdx].name} · ${qModels[qIdx].tag}",
        ) { i -> vm.act {
            it.ollama.autoPickModel = false
            it.ollama.groqModel = qModels[i].id
        } }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable { showAdvancedSettings = !showAdvancedSettings }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (showAdvancedSettings) "▼ Hide Advanced Key Settings" else "▶ Advanced API Key Settings",
                style = T.label.copy(fontSize = 11.sp),
                color = P.sub,
                modifier = Modifier.weight(1f),
            )
        }
        if (showAdvancedSettings) {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().background(P.surface2, RoundedCornerShape(8.dp)).padding(10.dp)) {
                Text("Raw Groq API Key", style = T.bodyB.copy(fontSize = 11.sp), color = P.ink)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = lg.ollama.groqApiKey,
                    onValueChange = { k -> vm.act { it.ollama.groqApiKey = k } },
                    singleLine = true,
                    placeholder = { Text("gsk_...", color = P.faint) },
                    visualTransformation = if (showGroqKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            if (showGroqKey) "HIDE" else "SHOW",
                            style = T.label.copy(fontSize = 10.sp),
                            color = P.accent,
                            modifier = Modifier.clickable { showGroqKey = !showGroqKey }.padding(8.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = if (testing) "Testing..." else "Test Connection",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testing && lg.ollama.isGroqSignedIn,
                ) {
                    onTesting(true)
                    onTestStatus("Contacting Groq...")
                    scope.launch {
                        val res = com.nh.hoopscoach.game.Ollama.testGroqConnection(lg.ollama.groqApiKey)
                        onTesting(false)
                        res.onSuccess {
                            onTestSuccess(true)
                            onTestStatus("Connected to Groq successfully!")
                            vm.toast("Groq connected!")
                        }.onFailure { err ->
                            onTestSuccess(false)
                            onTestStatus(err.message ?: "Could not connect to Groq")
                        }
                    }
                }
            }
        }
}

@Composable
fun DeepseekAiProviderPanel(
    vm: GameViewModel,
    lg: League,
    scope: CoroutineScope,
    testing: Boolean,
    onTesting: (Boolean) -> Unit,
    onTestStatus: (String) -> Unit,
    onTestSuccess: (Boolean) -> Unit,
    configuring: Boolean = false,
    onConfiguring: (Boolean) -> Unit = {},
    downloadingModel: String? = null,
    onDownloadingModel: (String?) -> Unit = {},
    availableModels: List<String> = emptyList(),
    onAvailableModels: (List<String>) -> Unit = {},
    onShowLogin: () -> Unit,
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showDeepseekKey by remember { mutableStateOf(false) }

        Text("DEEPSEEK AI (CLOUD)", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Spacer(Modifier.height(4.dp))
        Text("Very cheap cloud chat — strong for recaps and quotes without Gemini quotas.", style = T.small, color = P.sub)
        Spacer(Modifier.height(10.dp))
        CloudApiKeyCard(
            letter = "D",
            configured = lg.ollama.isDeepseekSignedIn,
            displayName = lg.ollama.deepseekDisplayEmail,
            statusLine = "DeepSeek API key configured",
            promptTitle = "DeepSeek API access",
            promptBlurb = "A DeepSeek API key is required from platform.deepseek.com.",
            configureLabel = "Configure DeepSeek API",
            onConfigure = { onShowLogin() },
            onRemove = {
                vm.act {
                    it.ollama.deepseekApiKey = ""
                    it.ollama.deepseekAccountEmail = ""
                }
                vm.toast("DeepSeek key removed")
            },
        )
        Spacer(Modifier.height(12.dp))
        val dModels = buildList {
            val cur = lg.ollama.deepseekModel
            if (cur.isNotBlank() && com.nh.hoopscoach.game.Ollama.DEEPSEEK_MODELS.none { it.id.equals(cur, true) }) {
                add(com.nh.hoopscoach.game.Ollama.CloudModel(cur, cur, "Selected", "From provider list — stays until you change it."))
            }
            addAll(com.nh.hoopscoach.game.Ollama.DEEPSEEK_MODELS)
        }
        val dIdx = dModels.indexOfFirst { it.id.equals(lg.ollama.deepseekModel, true) }.coerceAtLeast(0)
        Picker(
            label = if (lg.ollama.autoPickModel) "DeepSeek model (auto)" else "DeepSeek model",
            current = dModels[dIdx].name,
            options = dModels.map { it.name to "${it.tag} — ${it.description}" },
            display = "${if (lg.ollama.autoPickModel) "Auto · " else ""}${dModels[dIdx].name} · ${dModels[dIdx].tag}",
        ) { i -> vm.act {
            it.ollama.autoPickModel = false
            it.ollama.deepseekModel = dModels[i].id
        } }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable { showAdvancedSettings = !showAdvancedSettings }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (showAdvancedSettings) "▼ Hide Advanced Key Settings" else "▶ Advanced API Key Settings",
                style = T.label.copy(fontSize = 11.sp),
                color = P.sub,
                modifier = Modifier.weight(1f),
            )
        }
        if (showAdvancedSettings) {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().background(P.surface2, RoundedCornerShape(8.dp)).padding(10.dp)) {
                Text("Raw DeepSeek API Key", style = T.bodyB.copy(fontSize = 11.sp), color = P.ink)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = lg.ollama.deepseekApiKey,
                    onValueChange = { k -> vm.act { it.ollama.deepseekApiKey = k } },
                    singleLine = true,
                    placeholder = { Text("sk-...", color = P.faint) },
                    visualTransformation = if (showDeepseekKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            if (showDeepseekKey) "HIDE" else "SHOW",
                            style = T.label.copy(fontSize = 10.sp),
                            color = P.accent,
                            modifier = Modifier.clickable { showDeepseekKey = !showDeepseekKey }.padding(8.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = if (testing) "Testing..." else "Test Connection",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testing && lg.ollama.isDeepseekSignedIn,
                ) {
                    onTesting(true)
                    onTestStatus("Contacting DeepSeek...")
                    scope.launch {
                        val res = com.nh.hoopscoach.game.Ollama.testDeepseekConnection(lg.ollama.deepseekApiKey)
                        onTesting(false)
                        res.onSuccess {
                            onTestSuccess(true)
                            onTestStatus("Connected to DeepSeek successfully!")
                            vm.toast("DeepSeek connected!")
                        }.onFailure { err ->
                            onTestSuccess(false)
                            onTestStatus(err.message ?: "Could not connect to DeepSeek")
                        }
                    }
                }
            }
        }
}

@Composable
fun LocalOllamaProviderPanel(
    vm: GameViewModel,
    lg: League,
    scope: CoroutineScope,
    testing: Boolean,
    onTesting: (Boolean) -> Unit,
    onTestStatus: (String) -> Unit,
    onTestSuccess: (Boolean) -> Unit,
    configuring: Boolean = false,
    onConfiguring: (Boolean) -> Unit = {},
    downloadingModel: String? = null,
    onDownloadingModel: (String?) -> Unit = {},
    availableModels: List<String> = emptyList(),
    onAvailableModels: (List<String>) -> Unit = {},
    onShowLogin: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var showSetupGuide by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadStatus by remember { mutableStateOf("") }

        // Local Ollama
        PrimaryButton(
            text = if (configuring) "Auto-detecting Ollama..." else "Auto-Configure (Auto-Detect)",
            modifier = Modifier.fillMaxWidth(),
            enabled = !configuring && downloadingModel == null,
        ) {
            onConfiguring(true)
            onTestStatus("Detecting active Ollama instance on phone or local network...")
            scope.launch {
                val res = com.nh.hoopscoach.game.Ollama.autoConfigure(lg.ollama.host)
                onConfiguring(false)
                res.onSuccess { cfg ->
                    onTestSuccess(true)
                    onTestStatus(cfg.message)
                    onAvailableModels(cfg.models)
                    var chosen = cfg.selectedModel
                    vm.act {
                        it.ollama.enabled = true
                        it.ollama.provider = "ollama"
                        it.ollama.host = cfg.host
                        chosen = if (it.ollama.autoPickModel) {
                            com.nh.hoopscoach.game.Ollama.pickBestModel("ollama", cfg.models)
                        } else cfg.selectedModel
                        it.ollama.model = chosen
                    }
                    vm.toast("Configured: using $chosen")
                }.onFailure { err ->
                    onTestSuccess(false)
                    onTestStatus(err.message ?: "Could not detect Ollama")
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Phone setup guide toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSetupGuide = !showSetupGuide }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (showSetupGuide) "▼ Hide Phone Setup Guide" else "▶ How to run Ollama on your phone (Termux)",
                style = T.label.copy(fontSize = 11.sp),
                color = P.accent,
                modifier = Modifier.weight(1f),
            )
        }

        if (showSetupGuide) {
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(P.surface2, RoundedCornerShape(8.dp))
                    .border(1.dp, P.faint.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text("Run Ollama on your Android device in 3 steps:", style = T.bodyB, color = P.ink)
                Spacer(Modifier.height(4.dp))
                Text("1. Install Termux app on your phone (from F-Droid or GitHub).", style = T.small, color = P.sub)
                Text("2. In Termux, run this command:", style = T.small, color = P.sub)
                Spacer(Modifier.height(4.dp))
                Text(
                    "pkg install -y ollama && ollama serve",
                    style = T.small.copy(fontSize = 11.sp),
                    color = P.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(P.surface, RoundedCornerShape(4.dp))
                        .padding(6.dp)
                )
                Spacer(Modifier.height(6.dp))
                SecondaryButton("Copy Termux command", Modifier.fillMaxWidth()) {
                    clipboard.setText(AnnotatedString("pkg install -y ollama && ollama serve"))
                    vm.toast("Copied to clipboard! Paste in Termux.")
                }
                Spacer(Modifier.height(6.dp))
                Text("3. Tap 'Auto-Configure' above or choose a model below to download!", style = T.small, color = P.sub)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("DOWNLOAD MODEL TO PHONE", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Spacer(Modifier.height(4.dp))
        Text("Download a mobile-optimized model directly into your device's Ollama runtime:", style = T.small, color = P.sub)
        Spacer(Modifier.height(8.dp))

        // Recommended mobile models list
        com.nh.hoopscoach.game.Ollama.RECOMMENDED_MODELS.forEach { preset ->
            val isDownloading = downloadingModel == preset.id
            val isInstalled = availableModels.contains(preset.id) || availableModels.any { it.startsWith(preset.id.substringBefore(":")) }
            val isCurrent = lg.ollama.model.equals(preset.id, ignoreCase = true)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(if (isCurrent) P.accentSoft else P.surface2, RoundedCornerShape(8.dp))
                    .border(
                        width = if (isCurrent) 1.5.dp else 1.dp,
                        color = if (isCurrent) P.accent else P.faint.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(preset.name, style = T.bodyB, color = P.ink)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                preset.tag,
                                style = T.small.copy(fontSize = 10.sp),
                                color = if (preset.tag.contains("Recommended")) P.good else P.sub,
                            )
                        }
                        Text("${preset.size} · ${preset.description}", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                    }
                    Spacer(Modifier.width(8.dp))

                    if (isDownloading) {
                        Text("DOWNLOADING", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                    } else if (isCurrent) {
                        Text("ACTIVE", style = T.label.copy(fontSize = 11.sp), color = P.good)
                    } else if (isInstalled) {
                        SmallButton("Select") {
                            vm.act {
                                it.ollama.autoPickModel = false
                                it.ollama.model = preset.id
                                it.ollama.enabled = true
                            }
                            vm.toast("Active model: ${preset.name}")
                        }
                    } else {
                        SmallButton(
                            text = "Download",
                            primary = preset.tag.contains("Recommended"),
                            enabled = downloadingModel == null,
                        ) {
                            onDownloadingModel(preset.id)
                            downloadProgress = 0f
                            downloadStatus = "Connecting to Ollama on ${lg.ollama.host}..."
                            scope.launch {
                                val res = com.nh.hoopscoach.game.Ollama.pullModel(lg.ollama.host, preset.id) { status, prog, details ->
                                    downloadProgress = prog
                                    downloadStatus = "$status: $details"
                                }
                                onDownloadingModel(null)
                                res.onSuccess {
                                    vm.act {
                                        it.ollama.autoPickModel = false
                                        it.ollama.model = preset.id
                                        it.ollama.enabled = true
                                    }
                                    onTestStatus("Downloaded ${preset.name} successfully! Model is active.")
                                    onTestSuccess(true)
                                    onAvailableModels((availableModels + preset.id).distinct())
                                    vm.toast("${preset.name} downloaded & active!")
                                }.onFailure { err ->
                                    downloadStatus = "Download failed: ${err.message}"
                                    vm.toast("Download error: ${err.message}")
                                }
                            }
                        }
                    }
                }

                if (isDownloading) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(P.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(downloadProgress.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .background(P.accent)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(downloadStatus, style = T.small.copy(fontSize = 11.sp), color = P.accent)
                }
            }
        }

        if (downloadingModel == null && downloadStatus.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(downloadStatus, style = T.small.copy(fontSize = 11.sp), color = P.sub)
        }

        Spacer(Modifier.height(14.dp))
        Text("MANUAL CONNECTION SETTINGS", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Spacer(Modifier.height(4.dp))
        Text("Host endpoint (http://localhost:11434 on phone, http://10.0.2.2:11434 on emulator)", style = T.small.copy(fontSize = 11.sp), color = P.sub)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = lg.ollama.host,
            onValueChange = { h -> vm.act { it.ollama.host = h } },
            singleLine = true,
            placeholder = { Text("http://localhost:11434", color = P.faint) },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        Text("Model name", style = T.small.copy(fontSize = 11.sp), color = P.sub)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = lg.ollama.model,
            onValueChange = { m -> vm.act { it.ollama.model = m } },
            singleLine = true,
            placeholder = { Text("llama3.2:1b", color = P.faint) },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )

        Spacer(Modifier.height(10.dp))
        SecondaryButton(if (testing) "Testing..." else "Test Local Ollama Connection", Modifier.fillMaxWidth(), enabled = !testing && !configuring) {
            onTesting(true)
            onTestStatus("Testing connection to ${lg.ollama.host}...")
            scope.launch {
                val res = com.nh.hoopscoach.game.Ollama.testConnection(lg.ollama.host)
                onTesting(false)
                res.onSuccess { models ->
                    onTestSuccess(true)
                    onAvailableModels(models)
                    onTestStatus(
                        if (models.isNotEmpty()) "Connected! Available models: " + models.joinToString(", ")
                        else "Connected! (No models downloaded yet in Ollama)",
                    )
                }.onFailure { err ->
                    onTestSuccess(false)
                    onTestStatus("Connection failed: ${err.message ?: "Could not reach Ollama"}")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("LOCAL AI SPEED & ACCELERATION", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Spacer(Modifier.height(4.dp))
        Text("Optimize on-device generation speed, CPU threads, and memory allocation for smooth mobile performance.", style = T.small, color = P.sub)
        Spacer(Modifier.height(8.dp))

        val speedKeys = listOf("turbo", "balanced", "quality")
        val speedLabels = listOf("Turbo", "Balanced", "Quality")
        val speedIdx = speedKeys.indexOf(lg.ollama.speedMode).coerceAtLeast(0)
        Segmented(speedLabels, speedIdx, { i -> vm.act { it.ollama.speedMode = speedKeys[i] } }, inset = false)
        Spacer(Modifier.height(4.dp))
        Text(
            when (speedKeys[speedIdx]) {
                "turbo" -> "Fastest (~2–3s) — shorter takes."
                "quality" -> "Detailed (~10s) — richer prose."
                else -> "Standard (~5–7s) — balanced."
            },
            style = T.small.copy(fontSize = 10.sp), color = P.sub,
        )

        // Tip banner if on heavier model
        if (lg.ollama.model.contains("llama3.2:1b") || lg.ollama.model.contains("qwen2.5:0.5b")) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(P.surface2, RoundedCornerShape(8.dp))
                    .border(1.dp, P.gold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                Text("Want maximum speed?", style = T.bodyB.copy(fontSize = 11.sp), color = P.ink)
                Text("SmolLM2 135M (270MB) and Qwen 2.5 0.5B run up to 4x faster on phones.", style = T.small.copy(fontSize = 10.sp), color = P.sub)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Use SmolLM2", Modifier.fillMaxWidth()) {
                    vm.act { it.ollama.model = "smollm2:135m"; it.ollama.autoPickModel = false }
                    vm.toast("Active model switched to SmolLM2 135M")
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // CPU Threads selector
        Text("CPU Thread Allocation", style = T.bodyB, color = P.ink)
        Text(
            "Performance cores on big.LITTLE CPUs (${if (lg.ollama.numThreads == 0) "Auto: ${com.nh.hoopscoach.game.Ollama.defaultOptimalThreads()} cores" else "${lg.ollama.numThreads} cores"}).",
            style = T.small,
            color = P.sub,
        )
        Spacer(Modifier.height(6.dp))
        val threadOptions = listOf(0 to "Auto", 2 to "2", 4 to "4", 6 to "6")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            threadOptions.forEach { (count, txt) ->
                val sel = lg.ollama.numThreads == count
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) P.accent else P.surface2)
                        .clickable { vm.act { it.ollama.numThreads = count } }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(txt, style = T.small.copy(fontSize = 12.sp), color = if (sel) P.onAccent else P.ink)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Keep warm in RAM
        Text("Keep in RAM between games", style = T.bodyB, color = P.ink)
        Text("Prevents 5-15s cold start reloads from storage.", style = T.small, color = P.sub)
        Spacer(Modifier.height(6.dp))
        val ramOptions = listOf(30 to "30m", 10 to "10m", 5 to "5m")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ramOptions.forEach { (mins, txt) ->
                val sel = lg.ollama.keepAliveMinutes == mins
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) P.accent else P.surface2)
                        .clickable { vm.act { it.ollama.keepAliveMinutes = mins } }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(txt, style = T.small.copy(fontSize = 12.sp), color = if (sel) P.onAccent else P.ink)
                }
            }
        }
}

/**
 * Full AI Dynasty Intelligence settings panel.
 * Kept out of [SettingsScreen]'s LazyColumn item so ART can verify the bytecode.
 */
@Composable
fun AiDynastySettingsPanel(
    vm: GameViewModel,
    lg: League,
    scope: CoroutineScope,
    onShowGoogleLogin: () -> Unit,
    onShowOpenAiLogin: () -> Unit,
    onShowAnthropicLogin: () -> Unit,
    onShowGroqLogin: () -> Unit,
    onShowDeepseekLogin: () -> Unit,
) {
    var testStatus by remember { mutableStateOf("") }
    var testSuccess by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var configuring by remember { mutableStateOf(false) }
    var downloadingModel by remember { mutableStateOf<String?>(null) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }

Section("AI Dynasty Intelligence")
Panel {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Enable AI Dynasty Intelligence", style = T.bodyB, color = P.ink)
            Text(
                "Dynamic recaps, quotes, and storylines — free & cheap models preferred.",
                style = T.small,
                color = P.sub,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        AppSwitch(lg.ollama.enabled, { on ->
            vm.act {
                it.ollama.enabled = on
                // Only strip known expensive ids once when turning AI on — never overwrite a custom pick later.
                if (on) {
                    if (it.ollama.autoPickModel) com.nh.hoopscoach.game.Ollama.applyAutoPick(it.ollama)
                    else {
                        it.ollama.googleModel = com.nh.hoopscoach.game.Ollama.snapGoogleModel(it.ollama.googleModel)
                        it.ollama.openAiModel = com.nh.hoopscoach.game.Ollama.snapOpenAiModel(it.ollama.openAiModel)
                        it.ollama.anthropicModel = com.nh.hoopscoach.game.Ollama.snapAnthropicModel(it.ollama.anthropicModel)
                        it.ollama.groqModel = com.nh.hoopscoach.game.Ollama.snapGroqModel(it.ollama.groqModel)
                        it.ollama.deepseekModel = com.nh.hoopscoach.game.Ollama.snapDeepseekModel(it.ollama.deepseekModel)
                        it.ollama.model = com.nh.hoopscoach.game.Ollama.snapLocalModel(it.ollama.model)
                    }
                }
            }
        })
    }

    if (lg.ollama.enabled) {
        Spacer(Modifier.height(14.dp))
        val providers = listOf("ollama", "google", "openai", "anthropic", "groq", "deepseek")
        val providerLabels = listOf("Local", "Gemini", "ChatGPT", "Claude", "Groq", "DeepSeek")
        val providerBlurbs = listOf(
            "On-device Ollama — free, no API key.",
            "Free Gemini flash models in the cloud.",
            "Cheap ChatGPT models (mini / 3.5).",
            "Anthropic Claude — Haiku cheap, Sonnet richer.",
            "Groq free-tier Llama / Gemma — very fast.",
            "DeepSeek — cheap cloud chat for dynasty copy.",
        )
        val providerIdx = providers.indexOfFirst { it.equals(lg.ollama.provider, true) }.coerceAtLeast(0)
        Picker(
            label = "AI provider",
            current = providerLabels[providerIdx],
            options = providerLabels.zip(providerBlurbs),
            display = providerLabels[providerIdx],
        ) { i ->
            vm.act {
                it.ollama.provider = providers[i]
                if (it.ollama.autoPickModel) com.nh.hoopscoach.game.Ollama.applyAutoPick(it.ollama)
            }
            testStatus = ""
        }
        Spacer(Modifier.height(6.dp))
        Text(providerBlurbs[providerIdx], style = T.small, color = P.sub)

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto-pick model", style = T.bodyB, color = P.ink)
                Text(
                    "Picks a free/cheap text model and verifies it works. Skips ids that fail for your key.",
                    style = T.small,
                    color = P.sub,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            AppSwitch(lg.ollama.autoPickModel, { on ->
                vm.act { l ->
                    l.ollama.autoPickModel = on
                    if (on) {
                        com.nh.hoopscoach.game.Ollama.applyAutoPick(l.ollama)
                        com.nh.hoopscoach.game.Ollama.Cache.clear()
                    }
                }
            })
        }

        Spacer(Modifier.height(14.dp))

        AiProviderConfigSection(
            vm = vm,
            lg = lg,
            scope = scope,
            testing = testing,
            onTesting = { testing = it },
            testStatus = testStatus,
            onTestStatus = { testStatus = it },
            testSuccess = testSuccess,
            onTestSuccess = { testSuccess = it },
            configuring = configuring,
            onConfiguring = { configuring = it },
            downloadingModel = downloadingModel,
            onDownloadingModel = { downloadingModel = it },
            availableModels = availableModels,
            onAvailableModels = { availableModels = it },
            onShowGoogleLogin = { onShowGoogleLogin() },
            onShowOpenAiLogin = { onShowOpenAiLogin() },
            onShowAnthropicLogin = { onShowAnthropicLogin() },
            onShowGroqLogin = { onShowGroqLogin() },
            onShowDeepseekLogin = { onShowDeepseekLogin() },
        )

        if (testStatus.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (testSuccess) P.good.copy(alpha = 0.12f) else P.bad.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .border(1.dp, if (testSuccess) P.good.copy(alpha = 0.4f) else P.bad.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(testStatus, style = T.small, color = if (testSuccess) P.good else P.bad)
            }
        }

        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        AiDiagnosticsPanel(
            config = lg.ollama,
            embedded = true,
            onAutoPickChanged = { on -> vm.act { l ->
                l.ollama.autoPickModel = on
                if (on) {
                    com.nh.hoopscoach.game.Ollama.applyAutoPick(l.ollama)
                    com.nh.hoopscoach.game.Ollama.Cache.clear()
                }
            } },
        ) { model -> vm.act { l ->
            when (l.ollama.provider.lowercase()) {
                "google" -> l.ollama.googleModel = model
                "openai" -> l.ollama.openAiModel = model
                "anthropic" -> l.ollama.anthropicModel = model
                "groq" -> l.ollama.groqModel = model
                "deepseek" -> l.ollama.deepseekModel = model
                else -> l.ollama.model = model
            }
            com.nh.hoopscoach.game.Ollama.Cache.clear()
        } }

        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto-generate major stories", style = T.bodyB, color = P.ink)
                Text(
                    "Automatically generate recaps using ${lg.ollama.providerDisplayName}.",
                    style = T.small,
                    color = P.sub,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            AppSwitch(lg.ollama.autoGenerate, { on -> vm.act { it.ollama.autoGenerate = on } })
        }

        Spacer(Modifier.height(10.dp))

        // Response Caching
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Instant Response Cache", style = T.bodyB, color = P.ink)
                Text("Cache results for 0ms instant reload and zero wasted API quota on revisiting games & scouting.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.cacheResponses, { on -> vm.act { it.ollama.cacheResponses = on } })
        }

        if (lg.ollama.cacheResponses) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Clear cache (${com.nh.hoopscoach.game.Ollama.Cache.size()} items)",
                    style = T.small.copy(fontSize = 10.sp),
                    color = P.accent,
                    modifier = Modifier.clickable {
                        com.nh.hoopscoach.game.Ollama.Cache.clear()
                        vm.toast("AI response cache cleared")
                    },
                )
            }
        }

        AiNarrativeControls(
            vm = vm,
            lg = lg,
            scope = scope,
            testing = testing,
            onTesting = { testing = it },
            onTestStatus = { testStatus = it },
            onTestSuccess = { testSuccess = it },
        )
    }
}
}

@Composable
fun AiNarrativeControls(
    vm: GameViewModel,
    lg: League,
    scope: CoroutineScope,
    testing: Boolean,
    onTesting: (Boolean) -> Unit,
    onTestStatus: (String) -> Unit,
    onTestSuccess: (Boolean) -> Unit,
) {
    AiNarrativeControlsA(vm, lg, scope, testing, onTesting, onTestStatus, onTestSuccess)
    AiNarrativeControlsB(vm, lg, scope, testing, onTesting, onTestStatus, onTestSuccess)
}

@Composable
fun AiNarrativeControlsA(
    vm: GameViewModel,
    lg: League,
    scope: CoroutineScope,
    testing: Boolean,
    onTesting: (Boolean) -> Unit,
    onTestStatus: (String) -> Unit,
    onTestSuccess: (Boolean) -> Unit,
) {

        Spacer(Modifier.height(14.dp))
        Text("FINE NARRATIVE CONTROLS", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Spacer(Modifier.height(4.dp))
        Text("The AI controls fine narrative details, quotes, and reactions. Game simulation stats, box scores, records, and ratings are strictly computed by the engine and never modified by AI.", style = T.small, color = P.good)
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Postgame press conferences", style = T.bodyB, color = P.ink)
                Text("AI writes contextual podium questions & player quotes.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.pressConference, { on -> vm.act { it.ollama.pressConference = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI player interviews", style = T.bodyB, color = P.ink)
                Text("After games, players answer in character — personality, performance, confidence, and coach bond.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.playerInterviews, { on -> vm.act { it.ollama.playerInterviews = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Social media hot takes", style = T.bodyB, color = P.ink)
                Text("AI generates analyst, fan, and hater takes on your feed.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.socialFeed, { on -> vm.act { it.ollama.socialFeed = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Beat writer recaps & coach quotes", style = T.bodyB, color = P.ink)
                Text("AI writes game reports and postgame locker room quotes.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.gameRecaps, { on -> vm.act { it.ollama.gameRecaps = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI sports journalism desk", style = T.bodyB, color = P.ink)
                Text("Headlines, hot takes, rankings debates, and recruiting/trade rumors from real results.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.sportsJournalism, { on -> vm.act { it.ollama.sportsJournalism = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI living coaching careers", style = T.bodyB, color = P.ink)
                Text("Reputations, hot seats, job offers, coaching rivalries, and multi-school legacies from real results.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.livingCoachingCareers, { on -> vm.act { it.ollama.livingCoachingCareers = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI court storms & historic moments", style = T.bodyB, color = P.ink)
                Text("Upsets, buzzer-beaters, rivalry wins, championships, and heartbreaking exits from real games.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.historicMoments, { on -> vm.act { it.ollama.historicMoments = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI player storylines", style = T.bodyB, color = P.ink)
                Text("Bench-to-star leaps, blue-chip heat checks, and walk-on heroes from real production.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.playerStorylines, { on -> vm.act { it.ollama.playerStorylines = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI NBA draft universe", style = T.bodyB, color = P.ink)
                Text("Prospect stock, workout profiles, draft-night surprises, and former players' pro careers.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.draftUniverse, { on -> vm.act { it.ollama.draftUniverse = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Trade deadline rumors & grades", style = T.bodyB, color = P.ink)
                Text("AI writes insider scoops and grades completed trades.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.tradeRumors, { on -> vm.act { it.ollama.tradeRumors = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tactical pregame scouting keys", style = T.bodyB, color = P.ink)
                Text("AI writes keys to victory based on opponent matchups.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.scoutingKeys, { on -> vm.act { it.ollama.scoutingKeys = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Draft prospect scouting & pro comparisons", style = T.bodyB, color = P.ink)
                Text("AI scouts draft prospects with player grades and pro comparisons.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.draftScouting, { on -> vm.act { it.ollama.draftScouting = on } })
        }
        Spacer(Modifier.height(8.dp))
}

@Composable
fun AiNarrativeControlsB(
    vm: GameViewModel,
    lg: League,
    scope: CoroutineScope,
    testing: Boolean,
    onTesting: (Boolean) -> Unit,
    onTestStatus: (String) -> Unit,
    onTestSuccess: (Boolean) -> Unit,
) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Injury medical outlooks & recovery", style = T.bodyB, color = P.ink)
                Text("AI medical staff evaluates injury severity and next-man-up tactical outlooks.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.injuryReports, { on -> vm.act { it.ollama.injuryReports = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Award ceremony speeches & tributes", style = T.bodyB, color = P.ink)
                Text("AI writes acceptance speeches and columnist tributes for major award winners.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.awardCeremonies, { on -> vm.act { it.ollama.awardCeremonies = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Halftime locker room speeches", style = T.bodyB, color = P.ink)
                Text("AI delivers locker room addresses and second-half tactical adjustments.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.halftimeSpeeches, { on -> vm.act { it.ollama.halftimeSpeeches = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Training camp player progression", style = T.bodyB, color = P.ink)
                Text("AI breaks down camp standouts and season team projections.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.playerDevelopment, { on -> vm.act { it.ollama.playerDevelopment = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI player personalities", style = T.bodyB, color = P.ink)
                Text("Confident scorers hunt tough shots; unselfish players kick to open teammates. AI assigns styles + scout notes.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.playerPersonalities, { on -> vm.act { it.ollama.playerPersonalities = on } })
        }
        if (lg.ollama.playerPersonalities) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                if (testing) "Assigning personalities…" else "Assign AI personalities to my roster",
                Modifier.fillMaxWidth(),
                enabled = !testing && lg.ollama.enabled,
            ) {
                onTesting(true)
                onTestStatus("Scouting on-court personalities…")
                scope.launch {
                    val roster = lg.rosterOf(lg.user)
                    val res = com.nh.hoopscoach.game.Ollama.assignPlayerPersonalities(lg.ollama, lg, roster)
                    onTesting(false)
                    res.onSuccess { pass ->
                        onTestSuccess(true)
                        onTestStatus(pass.detail)
                        vm.toast(pass.detail)
                        vm.act { } // bump save / redraw
                    }.onFailure { err ->
                        onTestSuccess(false)
                        onTestStatus(err.message ?: "Personality assign failed")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Championship celebrations & Finals MVP", style = T.bodyB, color = P.ink)
                Text("AI writes champagne celebrations, trophy ceremonies, and Finals MVP speeches.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.championshipCelebrations, { on -> vm.act { it.ollama.championshipCelebrations = on } })
        }
        Spacer(Modifier.height(8.dp))
        if (!lg.pro) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("College recruiting pitch letters", style = T.bodyB, color = P.ink)
                    Text("AI drafts custom coach pitch letters targeting recruits' specific priorities.", style = T.small, color = P.sub)
                }
                AppSwitch(lg.ollama.recruitingPitches, { on -> vm.act { it.ollama.recruitingPitches = on } })
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pregame rivalry previews & bad blood", style = T.bodyB, color = P.ink)
                Text("AI writes high-stakes pregame buildup and bulletin-board quotes.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.pregameRivalries, { on -> vm.act { it.ollama.pregameRivalries = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI historical rivalries", style = T.bodyB, color = P.ink)
                Text("Programs, coaches, players, and fans accumulate history — including rivalries that emerge from the schedule.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.historicalRivalries, { on -> vm.act { it.ollama.historicalRivalries = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI university development", style = T.bodyB, color = P.ink)
                Text("Arenas, training centers, recruiting infrastructure, and national brand — from real budgets and upgrades.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.universityDevelopment, { on -> vm.act { it.ollama.universityDevelopment = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI living media ecosystem", style = T.bodyB, color = P.ink)
                Text("Breaking news, analyst debates, fan arguments, recruiting rumors, coach barbs, player social posts, and poll reactions — from real events.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.mediaEcosystem, { on -> vm.act { it.ollama.mediaEcosystem = on } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Free agency rumors & contract demands", style = T.bodyB, color = P.ink)
                Text("AI writes insider scoops on expiring contracts and max contract pursuits.", style = T.small, color = P.sub)
            }
            AppSwitch(lg.ollama.freeAgencyBuzz, { on -> vm.act { it.ollama.freeAgencyBuzz = on } })
        }
        Spacer(Modifier.height(12.dp))
        Text("DEFAULT BEAT WRITER ANGLE", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Spacer(Modifier.height(4.dp))
        val angleOptions = listOf("balanced" to "Balanced", "tactical" to "Tactical", "beat" to "Beat", "skeptic" to "Skeptic", "dual" to "Dual")
        val curAngleIdx = angleOptions.indexOfFirst { it.first == lg.ollama.defaultStoryAngle }.coerceAtLeast(0)
        Segmented(angleOptions.map { it.second }, curAngleIdx, { idx ->
            vm.act { it.ollama.defaultStoryAngle = angleOptions[idx].first }
        }, scroll = true, inset = false)
}

