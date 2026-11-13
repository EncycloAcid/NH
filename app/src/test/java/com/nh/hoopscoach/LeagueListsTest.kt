package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

class LeagueListsTest {
    @Test fun eventBriefSurvivesConcurrentNewsMutation() {
        Rng.r = Random(21)
        val lg = Offseason.newGame(0, "Test", "Coach")
        repeat(40) { i ->
            lg.news += News(lg.season, lg.day, "Seed headline $i", false)
        }
        val fails = AtomicInteger()
        val start = CountDownLatch(1)
        val writers = (1..4).map {
            thread {
                start.await()
                repeat(80) { i ->
                    LeagueLists.appendNews(lg, listOf(News(lg.season, lg.day, "AI desk $it-$i", false)))
                }
            }
        }
        val readers = (1..4).map {
            thread {
                start.await()
                repeat(40) {
                    try {
                        Journalism.eventBrief(lg)
                        SituationAwareness.leagueBrief(lg)
                    } catch (e: ConcurrentModificationException) {
                        fails.incrementAndGet()
                    }
                }
            }
        }
        start.countDown()
        (writers + readers).forEach { it.join(10_000) }
        assertEquals("eventBrief/leagueBrief must not CME under concurrent news writes", 0, fails.get())
        assertTrue(LeagueLists.news(lg).size > 40)
    }

    @Test fun articlePathSurvivesConcurrentGamesMutation() {
        Rng.r = Random(22)
        val lg = Offseason.newGame(0, "Test", "Coach")
        val u = lg.user
        val opp = lg.teams.first { it.id != u.id }
        repeat(30) { i ->
            lg.addGame(
                Game(
                    id = 10_000 + i,
                    day = i + 1,
                    home = u.id,
                    away = opp.id,
                    neutral = false,
                    type = GameType.NONCONF,
                    hs = if (i % 2 == 0) 70 + i else -1,
                    aws = if (i % 2 == 0) 65 + i else -1,
                ),
            )
        }
        val news = News(lg.season, lg.day, "${u.school} holds serve", mine = true, gameId = 10_000, teamId = u.id)
        val fails = AtomicInteger()
        val start = CountDownLatch(1)
        val mutators = (1..4).map {
            thread {
                start.await()
                repeat(80) { i ->
                    lg.addGame(
                        Game(
                            id = 20_000 + it * 1000 + i,
                            day = lg.day + i,
                            home = u.id,
                            away = opp.id,
                            neutral = false,
                            type = GameType.NONCONF,
                        ),
                    )
                }
            }
        }
        val readers = (1..4).map {
            thread {
                start.await()
                repeat(40) {
                    try {
                        ArticleWriting.news(lg, news)
                        AiFacts.build(lg, "Headline: ${news.text}\nTeam: ${u.school}", "Event: ${news.text}")
                        AiFacts.version(lg)
                    } catch (e: ConcurrentModificationException) {
                        fails.incrementAndGet()
                    }
                }
            }
        }
        start.countDown()
        (mutators + readers).forEach { it.join(15_000) }
        assertEquals("generateArticle helpers must not CME under concurrent games writes", 0, fails.get())
    }
}
