package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Interviews, articles and the social feed over a pro season. */
class MediaTest {
    @Test
    fun aSeasonInTheMedia() {
        Rng.r = Random(6)
        val lg = ProLeague.create(ProTest().toDraft(21))
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        // A second-year starter, so he has the big nights the media writes about whatever the draw.
        val h = lg.hero!!
        h.proYears = 1
        repeat(40) { if (h.ovr < 88) for (i in h.attrs.indices) h.attrs[i] = (h.attrs[i] + 1).coerceAtMost(99) }
        com.nh.hoopscoach.game.Tactics.autoAllocate(lg, lg.team(h.teamId))
        val r = lg.road!!
        val topics = mutableSetOf<String>()
        var reporters = 0
        var g = 0
        while (lg.phase == Phase.REGULAR && g++ < 200) {
            Sim.playDay(lg)
            r.press?.let { q ->
                topics += q.topic
                if (q.reporter.contains(" · ")) reporters++
                Road.answer(lg, Rng.int(0, q.answers.size - 1))
            }
        }
        val articles = lg.news.filter { it.body.isNotEmpty() }
        println("topics ${topics.sorted()}; ${articles.size} articles (${articles.groupingBy { it.kind }.eachCount()}); ${r.feed.size} posts (${r.feed.groupingBy { it.kind }.eachCount()})")
        articles.firstOrNull { it.kind == "article" }?.let { println("--- ${it.source}: ${it.text}\n${it.body}") }
        articles.firstOrNull { it.kind == "column" }?.let { println("--- ${it.source}: ${it.text}\n${it.body}") }
        r.feed.takeLast(6).forEach { println("@${it.handle} (${it.kind}): ${it.text} [${it.likes}]") }
        assertTrue("several kinds of questions", topics.size >= 5)
        assertTrue("every question has a reporter", reporters > 0)
        assertTrue("game stories", articles.count { it.kind == "article" } >= 3)
        assertTrue("award-race columns", articles.count { it.kind == "column" } >= 3)
        assertTrue("answers make stories", articles.any { it.kind == "quote" })
        assertTrue("the team posts its finals", r.feed.count { it.kind == "team" } >= 15)
        assertTrue("quote stories don't flood the news", articles.count { it.kind == "quote" } in 1..25)
        assertTrue("fans and analysts react", r.feed.any { it.kind == "fan" } && r.feed.any { it.kind == "analyst" })
        assertTrue("the feed is capped", r.feed.size <= 80)
        assertTrue("it all saves", SaveIO.decode(SaveIO.encode(lg)).road!!.feed.size == r.feed.size)
    }
}
