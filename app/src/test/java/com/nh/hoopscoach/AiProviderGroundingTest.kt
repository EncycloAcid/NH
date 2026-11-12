package com.nh.hoopscoach

import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.News
import com.nh.hoopscoach.game.Ollama
import com.nh.hoopscoach.game.OllamaConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class AiProviderGroundingTest {
    @Test fun actualProviderPathRetriesAndLeavesTemplateOnRepeatedInvalidDrafts() = runBlocking {
        val lg = LeagueGen.newLeague()
        lg.user.w = 7; lg.user.l = 3; lg.day = 10
        val original = "Original game-written template."
        val news = News(lg.season, lg.day, "${lg.user.school} outlook", teamId = lg.userTeamId, body = original)
        val calls = AtomicInteger()
        val requests = Collections.synchronizedList(mutableListOf<String>())
        var validSecond = true
        val server = ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        val failure = AtomicReference<Throwable>()
        val worker = thread(isDaemon = true) {
            while (!server.isClosed) try {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val input = socket.getInputStream()
                    val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) {
                        val b = input.read()
                        check(b >= 0 && header.length < 16_384)
                        header.append(b.toChar())
                    }
                    val length = header.lines().first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                    val body = ByteArray(length)
                    var offset = 0
                    while (offset < length) { val n = input.read(body, offset, length - offset); check(n > 0); offset += n }
                    requests += body.toString(Charsets.UTF_8)
                    val call = calls.incrementAndGet()
                    val text = if (validSecond && call == 2) "${lg.user.school} is 7-3." else "Unknown Person scored 999 points."
                    val payload = JsonObject(mapOf("response" to JsonPrimitive(text), "done" to JsonPrimitive(true))).toString().toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(payload); flush()
                    }
                }
            } catch (e: Exception) { if (!server.isClosed) failure.set(e) }
        }
        try {
            val config = OllamaConfig(enabled = true, host = "http://127.0.0.1:${server.localPort}", model = "test", cacheResponses = false)
            val result = Ollama.generateArticle(config, lg, news)
            assertEquals("${lg.user.school} is 7-3.", result.getOrThrow().text)
            assertTrue(result.getOrThrow().isAi)
            assertEquals(2, calls.get())
            assertTrue(requests.all { it.contains("FACT SHEET") && it.contains("current record 7-3") })
            assertTrue(requests.last().contains("previous draft was rejected"))
            validSecond = false
            calls.set(0)
            val rejected = Ollama.generateArticle(config, lg, news)
            assertTrue(rejected.isSuccess)
            assertFalse(rejected.getOrThrow().isAi)
            assertFalse(rejected.getOrThrow().text.contains("97-0"))
            assertEquals(2, calls.get())
            assertEquals(original, news.body)
            assertFalse(news.isAiGenerated)
            assertNull(failure.get())
        } finally { server.close(); worker.join(1_000) }
    }
}
