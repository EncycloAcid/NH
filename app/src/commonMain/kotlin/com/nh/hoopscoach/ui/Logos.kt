package com.nh.hoopscoach.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.nh.hoopscoach.decodeImageBitmap
import com.nh.hoopscoach.logoBytes

/**
 * School logos shipped in resources/logos, one per team, named by the team's abbreviation.
 * Decoded on first use and kept in a memory cache.
 */
object Logos {
    private val cache = HashMap<String, ImageBitmap>()
    private val missing = HashSet<String>()
    private var cacheBytes = 0
    private const val MAX_BYTES = 24 * 1024 * 1024

    fun get(abbr: String): ImageBitmap? {
        cache[abbr]?.let { return it }
        if (abbr in missing) return null
        val bytes = logoBytes(abbr)
        if (bytes == null) {
            missing += abbr
            return null
        }
        val bmp = runCatching { decodeImageBitmap(bytes) }.getOrNull()
        if (bmp == null) {
            missing += abbr
            return null
        }
        val size = bmp.width * bmp.height * 4
        if (cacheBytes + size > MAX_BYTES && cache.isNotEmpty()) {
            val victim = cache.keys.first()
            val old = cache.remove(victim)
            if (old != null) cacheBytes -= old.width * old.height * 4
        }
        cache[abbr] = bmp
        cacheBytes += size
        return bmp
    }
}
