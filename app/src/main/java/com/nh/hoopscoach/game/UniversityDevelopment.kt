package com.nh.hoopscoach.game

/**
 * Campus / franchise development facts for AI coverage: arenas, facilities, recruiting
 * infrastructure, and national brand — grounded in [Business] upgrades and [Brand].
 */
object UniversityDevelopment {
    data class Package(val stories: List<News>, val isAi: Boolean, val detail: String)

    fun nextUpgrade(lg: League, t: Team): Business.Upgrade? =
        Business.UPGRADES.firstOrNull { !Business.has(t, it.id) && t.cash >= Business.cost(lg, it) }
            ?: Business.UPGRADES.firstOrNull { !Business.has(t, it.id) }

    fun campusBrief(lg: League): String = buildString {
        Brand.ensure(lg)
        val t = lg.user
        Business.ensure(lg, t)
        appendLine("LEVEL: ${if (lg.pro) "NBA" else "NCAA"} · SEASON ${lg.season} · DAY ${lg.day} · PHASE ${lg.phase}")
        appendLine("PROGRAM: ${if (lg.pro) t.name else t.school} · prestige ${t.prestige} · legacy ${"%.0f".format(t.legacy)} · buzz ${"%.0f".format(t.buzz)} (${Brand.buzzLabel(t.buzz)})")
        appendLine("PROGRAM PULL: ${Brand.pull(lg, t)}/100 · conference exposure ${Brand.exposure(lg, t.confId)} (${Brand.exposureLabel(Brand.exposure(lg, t.confId))})")
        appendLine("ARENA: ${"%,d".format(t.arena)} seats · ticket ${Business.PRICES[t.price.coerceIn(0, 4)]} (\$${Business.ticket(lg, t)}) · cash budget ${Business.money(t.cash)} · gate ${Business.money(t.gate)}")
        appendLine("CROWDS: sellouts ${t.sellouts}/${t.homeGames} · fans ${Fans.mood(t.fans).lowercase()}")
        val built = Business.UPGRADES.filter { Business.has(t, it.id) }
        if (built.isNotEmpty()) {
            appendLine("BUILT FACILITIES:")
            for (u in built) appendLine("- ${u.name}: ${u.what}")
        } else appendLine("BUILT FACILITIES: none yet")
        val pending = Business.UPGRADES.filter { !Business.has(t, it.id) }
        if (pending.isNotEmpty()) {
            appendLine("AVAILABLE PROJECTS:")
            for (u in pending) {
                val c = Business.cost(lg, u)
                val afford = if (t.cash >= c) "AFFORDABLE" else "needs ${Business.money(c - t.cash)} more"
                appendLine("- ${u.name} (${Business.money(c)}, $afford): ${u.what}")
            }
        }
        nextUpgrade(lg, t)?.let { n ->
            appendLine("NEXT LOGICAL PROJECT: ${n.name} (${Business.money(Business.cost(lg, n))})")
        }
        val peers = lg.teams.filter { it.id != t.id }.sortedByDescending { it.upgrades.size + it.arena / 5000 }.take(5)
        if (peers.isNotEmpty()) {
            appendLine("PEER FACILITY SNAPSHOT:")
            for (p in peers) appendLine("- ${p.school}: ${p.upgrades.size} upgrades · ${"%,d".format(p.arena)} seats · prestige ${p.prestige}")
        }
        val recent = lg.news.filter { it.season == lg.season && (it.kind == "facility" || it.source.contains("Campus", true) || it.text.contains("unveil", true)) }.takeLast(6)
        if (recent.isNotEmpty()) {
            appendLine("RECENT CAMPUS NEWS:")
            for (n in recent) appendLine("- ${n.text}")
        }
    }

    fun fallbackPackage(lg: League): Package {
        Brand.ensure(lg)
        val t = lg.user
        Business.ensure(lg, t)
        val outlet = "Hoops Wire Campus"
        val stories = mutableListOf<News>()
        stories += News(
            lg.season, lg.day,
            "Campus report: ${t.school}'s ${"%,d".format(t.arena)}-seat home",
            mine = true, kind = "column",
            body = "Capacity ${"%,d".format(t.arena)}. Budget ${Business.money(t.cash)} after a ${Business.money(t.gate)} gate. Prestige ${t.prestige}, Program Pull ${Brand.pull(lg, t)}, buzz ${Brand.buzzLabel(t.buzz).lowercase()}. Built: ${t.upgrades.size} of ${Business.UPGRADES.size} projects.",
            source = "Staff · $outlet", teamId = t.id,
        )
        val next = nextUpgrade(lg, t)
        if (next != null) {
            stories += News(
                lg.season, lg.day,
                "Blueprint: next up — ${next.name}",
                mine = true, kind = "column",
                body = "${next.what} Costs ${Business.money(Business.cost(lg, next))} (${if (t.cash >= Business.cost(lg, next)) "within budget" else "still fundraising"}). Arena renovations, training centers, recruiting hubs, and brand campaigns stack into national presence.",
                source = "Staff · $outlet", teamId = t.id,
            )
        }
        val built = Business.UPGRADES.filter { Business.has(t, it.id) }.takeLast(2)
        if (built.isNotEmpty()) {
            val u = built.last()
            stories += News(
                lg.season, lg.day,
                "Facility ledger: ${u.name} already online",
                mine = true, kind = "column",
                body = u.what + " The AD's upgrade list shows ${t.upgrades.joinToString()}. Peers are watching Program Pull and the building.",
                source = "Staff · $outlet", teamId = t.id,
            )
        }
        return Package(stories, false, "Game-written campus development (${stories.size}).")
    }
}
