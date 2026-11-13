package com.nh.hoopscoach

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/** Paths and version strings that differ per platform. */
object PlatformContext {
    lateinit var saveFile: File
    var versionName: String = "3.4"
}

expect fun hoopsDisplayFont(): FontFamily
expect fun hoopsBodyFont(): FontFamily

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap

/** Raw logo bytes for a school abbreviation, or null if missing. */
expect fun logoBytes(abbr: String): ByteArray?

/** System back / Escape handling. No-op where the platform has no back gesture. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * Opens a "save file" dialog, then writes bytes from [getBytes] to the chosen location.
 * Returns a launcher; call it when the user taps Back up.
 */
@Composable
expect fun rememberSaveExporter(
    suggestedName: String,
    getBytes: suspend () -> ByteArray?,
    onResult: (ok: Boolean) -> Unit,
): () -> Unit

/**
 * Opens an "open file" dialog and returns the file bytes, or null if cancelled.
 * Returns a launcher; call it when the user taps Restore.
 */
@Composable
expect fun rememberSaveImporter(
    onResult: (bytes: ByteArray?) -> Unit,
): () -> Unit
