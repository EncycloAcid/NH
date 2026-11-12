package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.serialization.json.*

object ArticleFixture {
    fun league(): Pair<League, News> {
        val lg = LeagueGen.newLeague()
        lg.day = 39
        val home = lg.teams.first { it.school == "Duke" }
        lg.userTeamId = home.id
        val away = lg.teams.firstOrNull { it.mascot == "Wolfpack" } ?: lg.teams.first { it.id != home.id }
        home.w = 7; home.l = 3; away.w = 6; away.l = 4
        fun lines(t: Team, points: List<Int>) = lg.rosterOf(t).take(6).mapIndexed { i, p -> PlayerLine(p.id, p.name, p.pos, i < 5, StatLine(gp = 1, pts = points[i], fgm = points[i] / 2, fga = points[i] / 2 + 5, ftm = points[i] % 2, fta = points[i] % 2, drb = 6 - i, ast = 4 - i.coerceAtMost(4))) }
        val g = Game(9001, 39, home.id, away.id, false, GameType.CONF, hs = 90, aws = 86, box = BoxScore(lines(home, listOf(32,18,14,12,8,6)), lines(away, listOf(26,22,14,10,8,6)), listOf(intArrayOf(42,44), intArrayOf(48,42))))
        lg.games.clear(); lg.games += g
        lg.games += Game(9002, 42, away.id, home.id, false, GameType.CONF)
        val star = lg.playerOrNull(g.box!!.home.first().pid)!!
        return lg to News(lg.season, lg.day, "${star.last} scores 32, ${home.mascot} top ${away.mascot} 90-86", gameId = g.id, kind = "article", teamId = home.id, pid = star.id, source = "Baseline Sports")
    }
}

class ArticleWritingTest {
    @Test fun cloudCompletionMetadataAndAllTextPartsAreRespected() {
        assertEquals("A complete story.", Ollama.googleResponse("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"private reasoning"},{"text":"A complete "},{"text":"story."}]}}]}"""))
        assertTrue(runCatching { Ollama.googleResponse("""{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"Cut off to"}]}}]}""") }.isFailure)
        assertTrue(runCatching { Ollama.googleResponse("""{"candidates":[]}""") }.isFailure)
        assertEquals("Complete.", Ollama.openAiResponse("""{"choices":[{"finish_reason":"stop","message":{"content":"Complete."}}]}"""))
        for (reason in listOf("length", "content_filter")) assertTrue(runCatching { Ollama.openAiResponse("""{"choices":[{"finish_reason":"$reason","message":{"content":"Cut off to"}}]}""") }.isFailure)
        assertTrue(runCatching { Ollama.openAiResponse("""{"choices":[]}""") }.isFailure)
    }
    @Test fun realisticCutoffIsFactuallyValidButCannotReceiveACheck() = runBlocking {
        val (lg, news) = ArticleFixture.league()
        val p = lg.playerOrNull(news.pid)!!
        val partial = "${p.name} delivered a standout performance, scoring 32 points to"
        val facts = AiFacts.build(lg, "${p.name} ${lg.team(lg.games.first().away).name}", ArticleWriting.boxFacts(lg.games.first()), listOf(p))
        assertEquals(emptyList<String>(), AiGrounding.validate(partial, facts))
        assertTrue(ArticleWriting.errors(partial, OllamaConfig()).contains("unfinished article sentence"))
        val server = ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        val calls = AtomicInteger()
        val mode = AtomicReference("short")
        val worker = thread(isDaemon = true) {
            while (!server.isClosed) try { server.accept().use { socket ->
                socket.soTimeout = 10_000
                val input = socket.getInputStream()
                val header = StringBuilder()
                while (!header.endsWith("\r\n\r\n")) { val b = input.read(); check(b >= 0); header.append(b.toChar()) }
                val length = header.lines().first { it.startsWith("Content-Length:", true) }.substringAfter(':').trim().toInt()
                val bytesIn = ByteArray(length); var offset = 0
                while (offset < length) { val n = input.read(bytesIn, offset, length - offset); check(n > 0); offset += n }
                calls.incrementAndGet()
                val body = buildJsonObject { put("response", if (mode.get() == "short") partial else ArticleWriting.news(lg, news)); if (mode.get() != "disconnect") put("done", true); if (mode.get() == "length") put("done_reason", "length") }.toString()
                val bytes = body.toByteArray()
                socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(bytes); flush() }
            } } catch (_: Exception) { }
        }
        try {
            val cfg = OllamaConfig(enabled = true, model = "test-small", host = "http://127.0.0.1:${server.localPort}", cacheResponses = false)
            for (failure in listOf("short", "length", "disconnect")) {
                mode.set(failure); calls.set(0)
                val draft = Ollama.generateArticle(cfg, lg, news).getOrThrow()
                assertFalse(failure, draft.isAi)
                assertEquals(failure, 2, calls.get())
                assertTrue(draft.text.contains("32 points"))
                assertTrue(draft.text.split("\n\n").size in 4..7)
                assertTrue(draft.text.endsWith('.'))
            }
        } finally { server.close(); worker.join(1000) }
    }

    @Test fun fallbackIsAFullBoxScoreStoryAndDoesNotInventAPlay() {
        val (lg, news) = ArticleFixture.league()
        val story = ArticleWriting.news(lg, news)
        assertTrue(ArticleWriting.errors(story, OllamaConfig(provider = "google")).toString(), ArticleWriting.errors(story, OllamaConfig(provider = "google")).isEmpty())
        assertTrue(story.contains("32 points, 6 rebounds and 4 assists"))
        assertTrue(story.contains("48")); assertTrue(story.contains("day 42"))
        assertTrue(story.contains("7-3")); assertTrue(story.contains("6-4"))
        assertFalse(story.contains('“'))
        val dir = File("C:/Users/Administrator/avatar_samples/ai-ui/149").apply { mkdirs() }
        File(dir, "sample-full-story.txt").writeText(news.text + "\n\n" + story)
        println("SAMPLE STORY (${story.split(Regex("\\s+")).size} words):\n$story")
    }

    @Test fun missingBoxAndScheduleStayFactualAndParagraphsSurviveParsing() {
        val (lg, news) = ArticleFixture.league()
        lg.games.first().box = null; lg.games.removeAt(1)
        val copy = ArticleWriting.news(lg, news)
        assertEquals(4, copy.split("\n\n").size)
        assertTrue(copy.contains("not retained"))
        assertFalse(copy.contains("32 points"))
        val raw = "SCOOP 1: One | $copy\nSCOOP 2: Two | $copy"
        assertEquals(listOf(copy, copy), ArticleWriting.briefs(raw, "SCOOP").map { it.second })
        assertTrue(ArticleWriting.errors("HEADLINE: Complete title\nBODY: $copy\nCOACH_QUOTE: —", OllamaConfig()).none { it.contains("unfinished") })
    }
}
