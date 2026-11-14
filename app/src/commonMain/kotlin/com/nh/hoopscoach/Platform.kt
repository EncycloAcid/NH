package com.nh.hoopscoach

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/** Paths and version strings that differ per platform. */
object PlatformContext {
    lateinit var filesDir: File
    var versionName: String = "3.4-pro.192"

    val saveFile: File get() = File(filesDir, "dynasty.sav")
}

expect fun hoopsDisplayFont(): FontFamily
expect fun hoopsBodyFont(): FontFamily

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap

/** Raw bytes for any asset path under `src/main/assets`, or null if missing. */
expect fun assetBytes(path: String): ByteArray?

/** Raw logo bytes for a school abbreviation, or null if missing. */
fun logoBytes(abbr: String): ByteArray? = assetBytes("logos/${abbr.lowercase()}.webp")

/** Decode a PNG/WebP/JPEG into an ARGB pixel buffer for sprite work. */
expect fun decodeArgbBitmap(bytes: ByteArray): ArgbBitmap?

/** Build a Compose ImageBitmap from ARGB pixels. */
expect fun imageBitmapFromArgb(pixels: IntArray, width: Int, height: Int): ImageBitmap

/**
 * Decode [bytes], center-crop to a square, scale to at most [maxSide], and return JPEG bytes.
 * Used for profile photos. Returns null on failure.
 */
expect fun squareJpegThumbnail(bytes: ByteArray, maxSide: Int = 512): ByteArray?

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

/**
 * Opens a photo picker and returns image bytes, or null if cancelled / unsupported.
 * Desktop stubs this (photos are Android-only).
 */
@Composable
expect fun rememberProfilePhotoPicker(
    onResult: (bytes: ByteArray?) -> Unit,
): () -> Unit
