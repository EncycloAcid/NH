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
}
