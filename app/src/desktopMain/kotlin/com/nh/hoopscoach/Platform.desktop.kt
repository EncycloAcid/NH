package com.nh.hoopscoach

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontVariation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@OptIn(ExperimentalTextApi::class)
private fun resourceFont(path: String, weight: Int) = Font(
    identity = "$path-$weight",
    data = checkNotNull(readResource(path)) { "Missing font resource $path" },
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun readResource(path: String): ByteArray? {
    val loader = Thread.currentThread().contextClassLoader
        ?: PlatformContext::class.java.classLoader
    return loader?.getResourceAsStream(path)?.use { it.readBytes() }
}

actual fun hoopsDisplayFont(): FontFamily = FontFamily(
    resourceFont("fonts/big_shoulders.ttf", 600),
    resourceFont("fonts/big_shoulders.ttf", 700),
    resourceFont("fonts/big_shoulders.ttf", 800),
    resourceFont("fonts/big_shoulders.ttf", 900),
)

actual fun hoopsBodyFont(): FontFamily = FontFamily(
    resourceFont("fonts/manrope.ttf", 400),
    resourceFont("fonts/manrope.ttf", 500),
    resourceFont("fonts/manrope.ttf", 600),
    resourceFont("fonts/manrope.ttf", 700),
    resourceFont("fonts/manrope.ttf", 800),
)

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap =
    Image.makeFromEncoded(bytes).toComposeImageBitmap()

actual fun logoBytes(abbr: String): ByteArray? =
    readResource("logos/${abbr.lowercase()}.webp")

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop uses Escape via the window key handler in Main.kt; nothing to wire here.
}

@Composable
actual fun rememberSaveExporter(
    suggestedName: String,
    getBytes: suspend () -> ByteArray?,
    onResult: (ok: Boolean) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(suggestedName) {
        {
            scope.launch {
                val ok = runCatching {
                    val bytes = getBytes() ?: return@runCatching false
                    val target = withContext(Dispatchers.IO) {
                        pickSaveFile(suggestedName)
                    } ?: return@runCatching false
                    withContext(Dispatchers.IO) { target.writeBytes(bytes) }
                    true
                }.getOrDefault(false)
                onResult(ok)
            }
            Unit
        }
    }
}

@Composable
actual fun rememberSaveImporter(
    onResult: (bytes: ByteArray?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember {
        {
            scope.launch {
                val bytes = runCatching {
                    val file = withContext(Dispatchers.IO) { pickOpenFile() } ?: return@runCatching null
                    withContext(Dispatchers.IO) { file.readBytes() }
                }.getOrNull()
                onResult(bytes)
            }
            Unit
        }
    }
}

private fun pickSaveFile(suggestedName: String): File? {
    val dialog = FileDialog(null as Frame?, "Back up dynasty", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}

private fun pickOpenFile(): File? {
    val dialog = FileDialog(null as Frame?, "Restore dynasty", FileDialog.LOAD)
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}
