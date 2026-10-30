package com.nh.hoopscoach.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * School logos shipped in assets/logos, one per team, named by the team's abbreviation.
 * Decoded on first use and kept in a memory cache.
 */
object Logos {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val missing = HashSet<String>()

    fun get(ctx: Context, abbr: String): ImageBitmap? {
        cache.get(abbr)?.let { return it }
        if (abbr in missing) return null
        val bmp = try {
            ctx.assets.open("logos/${abbr.lowercase()}.webp").use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) { null }
        if (bmp == null) { missing += abbr; return null }
        return bmp.asImageBitmap().also { cache.put(abbr, it) }
    }
}
