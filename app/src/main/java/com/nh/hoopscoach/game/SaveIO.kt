package com.nh.hoopscoach.game

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object SaveIO {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(lg: League): ByteArray {
        val text = json.encodeToString(League.serializer(), lg)
        val bos = ByteArrayOutputStream(text.length / 4)
        GZIPOutputStream(bos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return bos.toByteArray()
    }

    fun decode(bytes: ByteArray): League {
        val text = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
        val lg = json.decodeFromString(League.serializer(), text)
        lg.reindex()
        Rivalries.ensure(lg)
        Brand.ensure(lg)
        Recruiting.ensure(lg)
        return lg
    }
}
