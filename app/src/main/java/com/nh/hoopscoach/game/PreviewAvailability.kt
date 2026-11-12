package com.nh.hoopscoach.game

object PreviewAvailability {
    fun regular(game: Game) = game.type == GameType.CONF || game.type == GameType.NONCONF || game.type == GameType.EVENT

    fun games(lg: League, t: Team): Int = lg.games.count {
        it.involves(t.id) && !it.played && regular(it)
    } + if (lg.pro) 0 else lg.events.count { t.id in it.teams } * 2

    /** Season-average Elo loss from known absences, with replacement minutes and recovery. */
    fun loss(lg: League, t: Team): Double {
        val roster = lg.rosterOf(t).sortedByDescending { it.ovr }
        if (roster.none { it.out }) return 0.0
        val rotation = roster.take(8)
        val remaining = games(lg, t).coerceAtLeast(1)
        val replacement = roster.filter { !it.out }.drop(5).firstOrNull()?.ovr
            ?: roster.filter { !it.out }.minOfOrNull { it.ovr } ?: 45
        val weight = rotation.indices.sumOf { 8 - it }.coerceAtLeast(1)
        return rotation.withIndex().sumOf { (i, p) ->
            if (!p.out) 0.0 else (p.ovr - replacement).coerceAtLeast(0) * 32.0 * (8 - i) / weight *
                (p.injGames.toDouble() / remaining).coerceIn(0.0, 1.0)
        }.coerceAtMost(200.0)
    }
}
