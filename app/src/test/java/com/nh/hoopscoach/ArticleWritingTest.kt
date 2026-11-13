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
    fun aiStory(): String = """
        Duke found its answer after halftime. Jared Dawson scored 32 points as the Blue Devils came back to beat NC State 90-86 on day 39, turning a two-point deficit at the break into a four-point victory. Dawson also had six rebounds and four assists for Duke.

        Dawson's 16-of-21 shooting gave the Blue Devils their biggest individual contribution. Fletcher McDaniel supplied another 18 points, and Domantas Petrauskas added 14. Between them, Duke's three leading scorers accounted for 64 of its 90 points, with the remaining 26 coming from the rest of the team.

        NC State had plenty of scoring of its own: Mason Jordan led the Wolfpack with 26 points and six rebounds, while Levi Ford added 22 points. Those two gave NC State 48 of its 86 points, but the Wolfpack could not preserve the advantage it carried into halftime.

        The difference was in the two halves. NC State led 44-42 at intermission, only for Duke to win the scoring battle 48-42 after the break. Duke made 45 of its 75 field-goal attempts, compared with 43 of 73 for NC State. Both teams finished with 10 assists; the Blue Devils' extra two baskets proved enough to separate the sides on the scoreboard.

        Duke currently sits at 7-3, with NC State at 6-4. The teams will not have long to wait for another meeting: the Blue Devils visit the Wolfpack on day 42. NC State will have home court for that rematch after falling short on Duke's floor in this one.
    """.trimIndent()

    fun league(blowout: Boolean = false): Pair<League, News> {
        Rng.r = kotlin.random.Random(150)
        val lg = LeagueGen.newLeague()
        lg.day = 39
        val home = lg.teams.first { it.school == "Duke" }
        lg.userTeamId = home.id
        val away = lg.teams.firstOrNull { it.mascot == "Wolfpack" } ?: lg.teams.first { it.id != home.id }
        home.w = 7; home.l = 3; away.w = 6; away.l = 4
        listOf(home to listOf("Jared Dawson", "Fletcher McDaniel", "Domantas Petrauskas"), away to listOf("Mason Jordan", "Levi Ford", "Nathan Washington")).forEach { (t, names) ->
            lg.rosterOf(t).zip(names).forEach { (p, name) -> p.first = name.substringBefore(' '); p.last = name.substringAfter(' ') }
        }
        fun lines(t: Team, points: List<Int>) = lg.rosterOf(t).take(6).mapIndexed { i, p -> PlayerLine(p.id, p.name, p.pos, i < 5, StatLine(gp = 1, pts = points[i], fgm = points[i] / 2, fga = points[i] / 2 + 5, ftm = points[i] % 2, fta = points[i] % 2, drb = 6 - i, ast = 4 - i.coerceAtMost(4))) }
        val g = if (blowout) Game(9010, 39, home.id, away.id, false, GameType.CONF, hs = 110, aws = 70, box = BoxScore(lines(home, listOf(38,24,18,14,10,6)), lines(away, listOf(20,16,12,10,8,4)), listOf(intArrayOf(56,32), intArrayOf(54,38))))
            else Game(9001, 39, home.id, away.id, false, GameType.CONF, hs = 90, aws = 86, box = BoxScore(lines(home, listOf(32,18,14,12,8,6)), lines(away, listOf(26,22,14,10,8,6)), listOf(intArrayOf(42,44), intArrayOf(48,42))))
        lg.games.clear(); lg.games += g
        lg.games += Game(9002, 42, away.id, home.id, false, GameType.CONF)
        val star = lg.playerOrNull(g.box!!.home.first().pid)!!
        return lg to News(lg.season, lg.day, "${star.last} scores ${g.box!!.home.first().s.pts}, ${home.mascot} top ${away.mascot} ${g.hs}-${g.aws}", gameId = g.id, kind = "article", teamId = home.id, pid = star.id, source = "Baseline Sports")
    }
}

class ArticleWritingTest {
    @Test fun teamShootingCountsMissesByPlayersWhoDidNotScore() {
        val (lg, _) = ArticleFixture.league(blowout = true)
        val g = lg.games.first()
        g.box!!.home.first().s.let { it.pts += 6; it.fgm += 3; it.fga += 3 }
        g.box!!.home.last().s.let { it.pts = 0; it.fgm = 0 }
        assertTrue(ArticleWriting.game(lg, g).contains("55 of 88 shots"))
    }
    private val banned = listOf("The result belongs alongside", "The focus moves", "Those matchups are the next opportunity", "The period scoring offers another view", "received production from several players", "The final margin was", "played at home against", "0 of 0", "outside the current Top 25", "rather than relying", "staff weighs its options", "next scheduled test", "not a guarantee")

    @Test fun seededNarrativesCoverComebacksAndBlowoutsWithoutFiller() {
        val dir = File("C:/Users/Administrator/avatar_samples/ai-ui/150").apply { mkdirs() }
        for (blowout in listOf(false, true)) {
            val (lg, n) = ArticleFixture.league(blowout)
            val game = lg.games.first()
            val copy = ArticleWriting.news(lg, n)
            assertEquals(copy, ArticleWriting.news(lg, n))
            val words = copy.split(Regex("\\s+")).size
            assertTrue("$words words: $copy", words in 140..400)
            assertFalse(copy.contains('%'))
            assertFalse(copy.contains("leaving"))
            assertFalse(copy.contains("scoresheet"))
            assertFalse(copy.contains("Bench scoring"))
            assertTrue(copy.split("\n\n").size in 4..6)
            banned.forEach { assertFalse(it, copy.contains(it, true)) }
            assertEquals(1, Regex("day 42").findAll(copy).count())
            assertEquals(1, Regex("Jared Dawson").findAll(copy).count())
            assertEquals(1, Regex("Duke Blue Devils").findAll(copy).count())
            val sentences = PreviewEditor.sentences(copy)
            assertEquals(sentences.size, sentences.distinct().size)
            if (blowout) { assertTrue(copy.contains("110-70")); assertTrue(copy.contains("56-32")) }
            else { assertTrue(copy.contains("Down 2 at the break")); assertTrue(copy.contains("48-42 after halftime")) }
            File(dir, if (blowout) "sample-blowout.txt" else "sample-comeback.txt").writeText(copy)
            val styles = (game.id..game.id + 10).map { id -> ArticleWriting.game(lg, Game(id, game.day, game.home, game.away, false, game.type, hs = game.hs, aws = game.aws, box = game.box)).substringBefore("\n\n") }.distinct()
            assertTrue(styles.size > 1)
        }
    }

    @Test fun missingShootingAndPeriodDataNeverBecomeZeroStatLines() {
        val (lg, n) = ArticleFixture.league()
        val g = lg.games.first()
        (g.box!!.home + g.box!!.away).forEach { it.s.fga = 0; it.s.fgm = 0; it.s.fta = 0; it.s.ftm = 0 }
        g.box = BoxScore(g.box!!.home, g.box!!.away, emptyList())
        val copy = ArticleWriting.news(lg, n)
        banned.forEach { assertFalse(it, copy.contains(it, true)) }
        assertFalse(copy.contains("halftime"))
        assertFalse(copy.contains("shots from the field"))
        assertFalse(copy.contains("NaN"))
        assertFalse(copy.contains("Infinity"))
    }

    @Test fun otherArticleFallbacksAndProviderFixtureAvoidBoilerplate() {
        val (lg, n) = ArticleFixture.league()
        for (kind in listOf("preview", "press", "feats", "rivalry", "freeagency")) {
            val copy = ArticleWriting.news(lg, News(lg.season, lg.day, "Dawson leads Duke", kind = kind, teamId = lg.userTeamId, pid = n.pid))
            banned.forEach { assertFalse("$kind: $it", copy.contains(it, true)) }
            assertTrue(copy.split("\n\n").size in 4..6)
        }
        assertNotEquals(ArticleWriting.news(lg, n), ArticleFixture.aiStory())
        assertTrue(ArticleWriting.errors(ArticleFixture.aiStory(), OllamaConfig(provider = "google")).isEmpty())
    }
    @Test fun cloudCompletionMetadataAndAllTextPartsAreRespected() {
        assertEquals("A complete story.", Ollama.googleResponse("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"private reasoning"},{"text":"A complete "},{"text":"story."}]}}]}"""))
        assertEquals("Cut off to", Ollama.googleResponse("""{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"Cut off to"}]}}]}"""))
        assertTrue(runCatching { Ollama.googleResponse("""{"candidates":[{"finishReason":"SAFETY","content":{"parts":[{"text":"blocked"}]}}]}""") }.isFailure)
        assertTrue(runCatching { Ollama.googleResponse("""{"candidates":[]}""") }.isFailure)
        assertEquals("Complete.", Ollama.openAiResponse("""{"choices":[{"finish_reason":"stop","message":{"content":"Complete."}}]}"""))
        assertEquals("Cut off to", Ollama.openAiResponse("""{"choices":[{"finish_reason":"length","message":{"content":"Cut off to"}}]}"""))
        assertTrue(runCatching { Ollama.openAiResponse("""{"choices":[{"finish_reason":"content_filter","message":{"content":"Cut off to"}}]}""") }.isFailure)
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
        assertTrue(ArticleWriting.errors(story, OllamaConfig()).toString(), ArticleWriting.errors(story, OllamaConfig()).isEmpty())
        assertTrue(story.contains("32 points, 6 rebounds and 4 assists"))
        assertTrue(story.contains("48")); assertTrue(story.contains("day 42"))
        assertTrue(story.contains("7-3")); assertTrue(story.contains("6-4"))
        assertFalse(story.contains('“'))
        val dir = File("C:/Users/Administrator/avatar_samples/ai-ui/150").apply { mkdirs() }
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
