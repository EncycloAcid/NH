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
 * Provider-specific AI settings body (Local / Gemini / ChatGPT / Claude / Groq / DeepSeek).
 * Kept outside [SettingsScreen] so Compose-generated methods stay under the JVM size limit.
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
    val clipboard = LocalClipboardManager.current
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showSetupGuide by remember { mutableStateOf(false) }
    var showGoogleKey by remember { mutableStateOf(false) }
    var showOpenAiKey by remember { mutableStateOf(false) }
    var showAnthropicKey by remember { mutableStateOf(false) }
    var showGroqKey by remember { mutableStateOf(false) }
    var showDeepseekKey by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadStatus by remember { mutableStateOf("") }

// Provider Specific Configuration
when (lg.ollama.provider.lowercase()) {
    "google" -> {
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
                        onShowGoogleLogin()
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
                    onShowGoogleLogin()
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

    "openai" -> {
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
                        onShowOpenAiLogin()
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
                    onShowOpenAiLogin()
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

    "anthropic" -> {
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
            onConfigure = { onShowAnthropicLogin() },
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

    "groq" -> {
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
            onConfigure = { onShowGroqLogin() },
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

    "deepseek" -> {
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
            onConfigure = { onShowDeepseekLogin() },
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

    else -> {
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
    }
}
