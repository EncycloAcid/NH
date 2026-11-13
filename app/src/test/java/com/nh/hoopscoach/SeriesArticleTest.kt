package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SeriesArticleTest {
    private fun playoffFixture(): Triple<League, Series, News> {
        Rng.r = Random(2000)
        val lg = ProLeague.coachLeague(2000, "SEA", "Test", "Coach")
        lg.day = 109
        lg.phase = Phase.PLAYOFFS
        val winner = lg.user
        val loser = lg.teams.first { it.id != winner.id }
        winner.w = 68; winner.l = 14; loser.w = 51; loser.l = 31
        fun lines(t: Team, pts: List<Int>): List<PlayerLine> {
            val roster = lg.rosterOf(t).take(6)
            return roster.mapIndexed { i, p ->
                PlayerLine(
                    p.id, p.name, p.pos, i < 5,
                    StatLine(
                        gp = 1, pts = pts[i], fgm = pts[i] / 2, fga = pts[i] / 2 + 7,
                        ftm = 5, fta = 5, drb = 2, ast = if (i == 0) 6 else 2, stl = if (i == 0) 3 else 0,
                    ),
                )
            }
        }
        val star: Player = lg.rosterOf(winner).first()
        star.first = "Tyquawn"
        star.last = "Darrisaw"
        val homeLines = lines(winner, listOf(52, 18, 14, 12, 10, 8)).toMutableList()
        homeLines[0] = PlayerLine(star.id, star.name, star.pos, true, homeLines[0].s)
        val g = Game(
            7777, 109, winner.id, loser.id, false, GameType.PLAYOFF, label = "Conference Finals",
            hs = 135, aws = 94,
            box = BoxScore(
                homeLines,
                lines(loser, listOf(22, 18, 16, 14, 12, 12)),
                listOf(intArrayOf(72, 44), intArrayOf(63, 50)),
                quarters = true,
            ),
        )
        val prior = (1..6).map { i ->
            Game(7700 + i, 100 + i, winner.id, loser.id, false, GameType.PLAYOFF, label = "Conference Finals", hs = 100 + i, aws = 95)
        }
        lg.games.clear()
        lg.games.addAll(prior)
        lg.games += g
        val series = Series(
            round = 3, conf = 0, slot = 0, hi = winner.id, lo = loser.id,
            hiWins = 4, loWins = 3,
            gameIds = (prior.map { it.id } + g.id).toMutableList(),
            bestOf = 7,
        )
        lg.proState!!.series.clear()
        lg.proState!!.series += series
        val news = News(
            lg.season, lg.day,
            "${winner.name} beat ${loser.name} in 7 to win the Conference Finals",
            mine = true, gameId = g.id, kind = "series",
            body = "placeholder", source = "Baseline Sports", teamId = winner.id, pid = star.id,
        )
        return Triple(lg, series, news)
    }

    @Test
    fun seriesFallbackKeepsSeriesNarrativeNotSingleGameDump() {
        val (lg, series, news) = playoffFixture()
        val copy = ArticleWriting.news(lg, news)
        assertTrue(copy.contains("Conference Finals"))
        assertTrue(copy.contains("4-3") || copy.contains("in 7"))
        assertFalse(copy.contains("rolled past"))
        val g = lg.game(news.gameId)
        val factsExtra = ArticleWriting.boxFacts(g) + "\n" + ArticleWriting.seriesFacts(lg, g) + "\n" + copy
        assertTrue(ArticleWriting.seriesFacts(lg, g).contains("best of 7"))
        assertTrue(ArticleWriting.boxFacts(g).contains("FTM"))
        val sheet = AiFacts.build(lg, news.text, factsExtra, listOf(lg.player(news.pid)), eventGame = g)
        assertTrue(sheet.text.contains("SERIES") || sheet.text.contains("best of 7"))
        val errors = AiGrounding.validate(copy, sheet)
        assertTrue("series copy should fact-check: $errors", errors.isEmpty())
        assertEquals(series.winner, lg.userTeamId)
    }
}
