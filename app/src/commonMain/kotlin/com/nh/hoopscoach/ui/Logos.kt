package com.nh.hoopscoach.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.nh.hoopscoach.assetBytes
import com.nh.hoopscoach.decodeImageBitmap
import com.nh.hoopscoach.logoBytes

/**
 * School logos and other asset images. Decoded on first use and kept in a memory cache.
 */
object Logos {
    private val cache = HashMap<String, ImageBitmap>()
    private val missing = HashSet<String>()
    private var cacheBytes = 0
    private const val MAX_BYTES = 24 * 1024 * 1024

    fun get(abbr: String): ImageBitmap? {
        val bytes = logoBytes(abbr) ?: run {
            missing += "logos/${abbr.lowercase()}.webp"
            return null
        }
        return fromBytes("logos/${abbr.lowercase()}.webp", bytes)
    }

    /** Any image in the assets folder, cached. */
    fun asset(path: String): ImageBitmap? {
        cache[path]?.let { return it }
        if (path in missing) return null
        val bytes = assetBytes(path)
        if (bytes == null) {
            missing += path
            return null
        }
        return fromBytes(path, bytes)
    }

    private fun fromBytes(path: String, bytes: ByteArray): ImageBitmap? {
        cache[path]?.let { return it }
        val bmp = runCatching { decodeImageBitmap(bytes) }.getOrNull()
        if (bmp == null) {
            missing += path
            return null
        }
        val size = bmp.width * bmp.height * 4
        if (cacheBytes + size > MAX_BYTES && cache.isNotEmpty()) {
            val victim = cache.keys.first()
            val old = cache.remove(victim)
            if (old != null) cacheBytes -= old.width * old.height * 4
        }
        cache[path] = bmp
        cacheBytes += size
        return bmp
    }
}
