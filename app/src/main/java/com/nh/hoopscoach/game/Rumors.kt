package com.nh.hoopscoach.game

/**
 * The rumor mill: in the two weeks before the trade deadline, sources say who's shopping whom (more
 * about the user's player when he's unhappy or has asked out); every trade breaks on the insider's
 * feed with fans reacting; and when he hits free agency, the market talks.
 */
object Rumors {
    private const val INSIDER = "NBA Insider"
    private const val HANDLE = "nbainsider"

    fun daily(lg: League) {
        if (!lg.pro || lg.phase != Phase.REGULAR) return
        val dl = Trades.deadlineDay(lg)
        if (lg.day !in (dl - 14) until dl) return
        val sellers = lg.teams.filter { it.w + it.l >= 10 && it.winPct < 0.45 }
        val buyers = lg.teams.filter { it.w + it.l >= 10 && it.winPct >= 0.55 }
        if (sellers.isNotEmpty() && buyers.isNotEmpty() && Rng.chance(0.65)) {
            val s = Rng.pick(sellers)
            val b = Rng.pick(buyers)
            val p = lg.rosterOf(s).filter { it.age >= 26 && it.id != lg.heroId }.maxByOrNull { it.ovr + Rng.d() * 4 }
            if (p != null) {
                val text = Rng.pick(listOf(
                    "The ${b.name} have called the ${s.name} about ${p.name}",
                    "The ${s.name} are listening on ${p.name}, and the ${b.name} are among the teams interested",
                    "${p.name} is drawing interest from the ${b.name} ahead of the deadline",
                    "The ${b.name} are looking for help at ${p.pos}; ${p.name} of the ${s.name} is on their list",
                ))
                lg.news += News(lg.season, lg.day, "Rumor: $text", s.id == lg.userTeamId || b.id == lg.userTeamId)
                if (Feed.mine(lg, s.id, b.id)) Media.post(lg, INSIDER, HANDLE, "insider", "$text, per league sources.", Media.likes(lg, 0.02), verified = true)
            }
        }
        // About him: when he's unhappy or has asked out, the phones ring.
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        val mood = Trades.mood(lg)
        val restless = r.suitors.isNotEmpty() || mood.score < 50
        if (restless && Rng.chance(0.35)) {
            val caller = lg.teams.filter { it.id != h.teamId }.maxByOrNull { it.winPct + Rng.d() * 0.3 } ?: return
            val text = Rng.pick(listOf(
                "The ${caller.name} have checked in with the ${lg.user.name} about ${h.name}",
                "${h.name} is ${mood.label.lowercase()} in ${lg.user.school}, and the ${caller.name} have noticed",
                "Rival executives expect the ${lg.user.name} to listen on ${h.name}. The ${caller.name} are the team to watch",
            ))
            lg.news += News(lg.season, lg.day, "Rumor: $text", true)
            Media.post(lg, INSIDER, HANDLE, "insider", "$text, per sources.", Media.likes(lg, 0.06), verified = true)
            Media.post(lg, Rng.pick(listOf("Hoops Twitter", "Ball Knowledge", "Bench Mob")), Rng.pick(listOf("hoopstalk", "ballknowledge", "benchmob")), "fan",
                Rng.pick(listOf("If ${h.short} gets moved I'm done with this franchise.", "Trade him while his value is high. Sorry.", "${caller.mascot} fans better not get their hopes up.")),
                Media.likes(lg, 0.01))
        }
    }

    /** A trade breaks: the insider has it first, and fans of the user's team react. */
    fun breaking(lg: League, t: Trade) {
        if (!lg.pro) return
        val a = lg.team(t.teamA)
        val b = lg.team(t.teamB)
        val text = "The ${a.name} are trading ${t.toB.joinToString(" and ")} to the ${b.name} for ${t.toA.joinToString(" and ")}"
        if (t.hero || Feed.mine(lg, a.id, b.id)) Media.post(lg, INSIDER, HANDLE, "insider", "BREAKING: $text.", Media.likes(lg, if (t.hero) 0.12 else 0.03), verified = true)
        if (t.teamA == lg.userTeamId || t.teamB == lg.userTeamId) {
            val got = if (t.teamA == lg.userTeamId) t.toA else t.toB
            Media.post(lg, "${lg.user.mascot} Nation", "${lg.user.abbr.lowercase()}nation", "fan",
                Rng.pick(listOf("Welcome to town, ${got.first()}!", "Not sure how I feel about this one.", "Front office cooking. ${got.first()} fits right in.", "We gave up too much. Fight me.")),
                Media.likes(lg, 0.02))
        }
    }

    /** He hits free agency: how many teams called, and the favorite. */
    fun freeAgency(lg: League) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        if (r.proOffers.isEmpty()) return
        val best = r.proOffers.maxBy { it.salary }
        val fav = lg.team(best.teamId)
        val text = "${r.proOffers.size} teams have made offers to ${h.name}. The ${fav.name} are the early favorite"
        lg.news += News(lg.season, 0, "Free agency: $text", true)
        Media.post(lg, INSIDER, HANDLE, "insider", "$text, per sources.", Media.likes(lg, 0.08), verified = true)
    }
}
