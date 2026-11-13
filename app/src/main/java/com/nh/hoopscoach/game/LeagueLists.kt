package com.nh.hoopscoach.game

/**
 * Thread-safe reads/writes for league mutable lists. AI desks run on [Dispatchers.IO]
 * while the sim mutates the same ArrayLists on the main/sim thread — iterating without
 * a snapshot throws ConcurrentModificationException.
 */
object LeagueLists {
    /** Copy of [src]; retries briefly if another thread mutates mid-copy. */
    fun <T> snap(src: List<T>): List<T> {
        repeat(4) {
            try {
                return ArrayList(src)
            } catch (_: ConcurrentModificationException) {
                Thread.yield()
            }
        }
        return try { ArrayList(src) } catch (_: ConcurrentModificationException) { emptyList() }
    }

    fun appendNews(lg: League, stories: Collection<News>) {
        if (stories.isEmpty()) return
        synchronized(lg.news) { lg.news.addAll(stories) }
    }

    fun news(lg: League): List<News> = synchronized(lg.news) { ArrayList(lg.news) }
    fun games(lg: League): List<Game> = snap(lg.games)
    fun players(lg: League): List<Player> = snap(lg.players)
    fun recruits(lg: League): List<Recruit> = snap(lg.recruits)
    fun coaches(lg: League): List<Coach> = snap(lg.coaches)
    fun moments(lg: League): List<Moment> = snap(lg.moments)
    fun history(lg: League): List<SeasonSummary> = snap(lg.history)
    fun teams(lg: League): List<Team> = snap(lg.teams)
    fun shadowPros(lg: League): List<ShadowPro> = snap(lg.shadowPros)
}
