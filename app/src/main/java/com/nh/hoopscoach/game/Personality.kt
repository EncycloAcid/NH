package com.nh.hoopscoach.game

/**
 * On-court personality: how a player decides to shoot, pass, or iso.
 * Confident scorers hunt their own look; unselfish players kick tough shots to teammates.
 * Rule-based always; optional AI assigns styles + scout notes when enabled.
 */
object Personality {
    /** Mutually exclusive on-court decision styles (character traits like Loyal stay separate). */
    val ON_COURT: Set<Trait> = setOf(
        Trait.GUNNER, Trait.PASS_FIRST, Trait.SHOWBOAT, Trait.HOTHEAD, Trait.COACHABLE, Trait.STEADY,
    )

    fun onCourtTrait(p: Player): Trait? = ON_COURT.firstOrNull { p.has(it) }

    fun usageMod(p: Player): Double {
        val base = when (onCourtTrait(p)) {
            Trait.SHOWBOAT -> 1.22
            Trait.GUNNER -> 1.18
            Trait.HOTHEAD -> 1.1
            Trait.PASS_FIRST -> 0.84
            Trait.STEADY -> 0.96
            Trait.COACHABLE -> 0.94
            else -> 1.0
        }
        return when {
            onCourtTrait(p) != null -> base
            p.has(Trait.TEAM_PLAYER) -> 0.92
            p.has(Trait.AMBITIOUS) -> 1.06
            else -> 1.0
        }
    }

    fun passMod(p: Player): Double {
        when (onCourtTrait(p)) {
            Trait.PASS_FIRST -> return 1.45
            Trait.STEADY -> return 1.18
            Trait.COACHABLE -> return 1.12
            Trait.GUNNER -> return 0.78
            Trait.SHOWBOAT -> return 0.7
            Trait.HOTHEAD -> return 0.88
            else -> {}
        }
        return if (p.has(Trait.TEAM_PLAYER)) 1.2 else 1.0
    }

    /** Appetite for contested mid-range / threes when the defense is loaded. */
    fun toughShotAppetite(p: Player): Double {
        when (onCourtTrait(p)) {
            Trait.SHOWBOAT -> return 1.42
            Trait.GUNNER -> return 1.35
            Trait.HOTHEAD -> return 1.2
            Trait.PASS_FIRST -> return 0.62
            Trait.STEADY -> return 0.82
            Trait.COACHABLE -> return 0.88
            else -> {}
        }
        return if (p.has(Trait.TEAM_PLAYER)) 0.78 else 1.0
    }

    /**
     * Isolation / hero-ball weight when picking who takes the shot.
     * Showboats and gunners demand the ball; pass-first glue guys fade.
     */
    fun isoMod(p: Player): Double = when (onCourtTrait(p)) {
        Trait.SHOWBOAT -> 1.38
        Trait.GUNNER -> 1.22
        Trait.HOTHEAD -> 1.12
        Trait.PASS_FIRST -> 0.72
        Trait.STEADY -> 0.9
        Trait.COACHABLE -> 0.85
        else -> 1.0
    }

    /**
     * Chance the ball-handler kicks a low-percentage look instead of forcing it.
     * Gunners / showboats almost never; pass-first / steady / team players often.
     * [trail] is offense score − defense (negative = trailing); hotheads force more when down.
     */
    fun kickOutChance(p: Player, pMake: Double, trail: Int = 0): Double {
        if (pMake >= 0.44) return 0.0
        val pressure = ((0.44 - pMake) / 0.32).coerceIn(0.0, 1.0)
        val base = 0.08 + 0.42 * pressure
        var chance = when (onCourtTrait(p)) {
            Trait.PASS_FIRST -> (base * 1.55).coerceAtMost(0.72)
            Trait.STEADY -> (base * 1.35).coerceAtMost(0.62)
            Trait.COACHABLE -> (base * 1.2).coerceAtMost(0.55)
            Trait.GUNNER -> (base * 0.22).coerceAtMost(0.18)
            Trait.SHOWBOAT -> (base * 0.15).coerceAtMost(0.14)
            Trait.HOTHEAD -> (base * 0.45).coerceAtMost(0.32)
            else -> if (p.has(Trait.TEAM_PLAYER)) (base * 1.25).coerceAtMost(0.58)
            else (base * 0.75).coerceAtMost(0.4)
        }
        if (p.has(Trait.HOTHEAD) && trail < 0) chance *= 0.55
        return chance.coerceIn(0.0, 0.72)
    }

    fun onCourtLabel(p: Player): String? = when (onCourtTrait(p)) {
        Trait.GUNNER -> "Confident scorer"
        Trait.PASS_FIRST -> "Unselfish"
        Trait.SHOWBOAT -> "Showboat"
        Trait.HOTHEAD -> "Hothead"
        Trait.COACHABLE -> "Coachable"
        Trait.STEADY -> "Steady"
        else -> if (p.has(Trait.TEAM_PLAYER)) "Team-first" else null
    }

    fun defaultNote(p: Player): String = when (onCourtTrait(p)) {
        Trait.GUNNER -> "Confident scorer who hunts his own look."
        Trait.PASS_FIRST -> "Unselfish — finds the open man on tough possessions."
        Trait.SHOWBOAT -> "Loves the highlight — isolations and contested step-backs."
        Trait.HOTHEAD -> "Gets aggressive when trailing; forces more than he should."
        Trait.COACHABLE -> "Stays in the scheme and takes what coach draws up."
        Trait.STEADY -> "Even-keeled — good shots, willing kick-outs on tough looks."
        else -> ""
    }

    /** Clear prior on-court styles, then set [role]. */
    fun clearOnCourt(p: Player) {
        p.traits.removeAll { it in ON_COURT }
    }

    /** Give veterans without an on-court trait a style that matches their game. Safe to call repeatedly. */
    fun seedOnCourtTrait(p: Player) {
        if (onCourtTrait(p) != null) return
        val scoring = (p[Attr.INS] + p[Attr.MID] + p[Attr.OUT]) / 3.0
        val pas = p[Attr.PAS].toDouble()
        // Deterministic from player id so re-seeding never flips a decision.
        val roll = ((p.id * 1103515245 + 12345) ushr 16).mod(100)
        val pick = when {
            pas >= scoring + 6 && roll < 55 -> Trait.PASS_FIRST
            scoring >= pas + 8 && roll < 40 -> Trait.GUNNER
            scoring >= pas + 5 && roll in 40..54 -> Trait.SHOWBOAT
            roll < 10 -> Trait.HOTHEAD
            roll < 22 -> Trait.STEADY
            roll < 30 -> Trait.COACHABLE
            pas >= scoring + 3 && roll < 50 -> Trait.PASS_FIRST
            scoring >= pas + 3 && roll < 50 -> Trait.GUNNER
            roll < 12 -> if (pas >= scoring) Trait.PASS_FIRST else Trait.GUNNER
            else -> null
        }
        if (pick != null) p.traits += pick
        if (p.personalityNote.isBlank()) {
            val note = defaultNote(p)
            if (note.isNotBlank()) p.personalityNote = note
        }
        trimTraits(p)
    }

    private fun trimTraits(p: Player) {
        if (p.traits.size <= 3) return
        val keep = p.traits.filter { it in ON_COURT || it == Trait.LATE_BLOOMER }.toMutableList()
        for (t in p.traits) {
            if (keep.size >= 3) break
            if (t !in keep) keep += t
        }
        p.traits.clear()
        p.traits += keep
    }

    fun seedRoster(players: Iterable<Player>) {
        for (p in players) seedOnCourtTrait(p)
    }

    /** How he feels about the coaching staff right now. */
    fun coachBond(p: Player): String = when {
        p.happiness >= 82 -> "trusts the coaching staff"
        p.happiness >= 65 -> "respectful but measured with the coach"
        p.happiness >= 48 -> "frustrated with his role under this coach"
        else -> "openly cool on the coaching staff"
    }

    /** Confidence after tonight's line vs his season form. */
    fun confidence(p: Player, line: GameLogEntry): String {
        val avg = p.stats.ppg.coerceAtLeast(1.0)
        return when {
            line.pts >= avg * 1.45 || (line.pts >= 20 && line.ast + line.reb >= 10) -> "riding high"
            line.min >= 15 && line.pts <= avg * 0.45 -> "shaken after a quiet night"
            line.tov >= 5 && line.pts < avg -> "second-guessing himself"
            else -> "even-keeled"
        }
    }

    /** Compact scout card for interview prompts. */
    fun interviewContext(lg: League, p: Player, line: GameLogEntry): String {
        val traits = p.traits.joinToString(", ") { it.label }.ifEmpty { "no standout traits" }
        val note = p.personalityNote.ifBlank { onCourtLabel(p) ?: "standard competitor" }
        return buildString {
            append("Personality: $traits. Scout: $note. ")
            append("Mood/coach bond: ${coachBond(p)} (happiness ${p.happiness}). ")
            append("Confidence tonight: ${confidence(p, line)}. ")
            append("Line: ${line.pts} PTS / ${line.reb} REB / ${line.ast} AST in ${line.min} min")
            if (line.tov >= 4) append(", ${line.tov} TOV")
            append(".")
        }
    }

    /** Rule-based interview when AI is off or fails. */
    fun fallbackInterview(lg: League, g: Game, p: Player, line: GameLogEntry, reporter: String): PlayerInterview {
        val t = lg.team(p.teamId)
        val opp = lg.team(g.opp(t.id))
        val won = g.winner() == t.id
        val gunner = p.has(Trait.GUNNER) || p.has(Trait.SHOWBOAT)
        val passer = p.has(Trait.PASS_FIRST) || p.has(Trait.TEAM_PLAYER) || p.has(Trait.STEADY)
        val hot = p.has(Trait.HOTHEAD)
        val conf = confidence(p, line)
        val bond = coachBond(p)
        val q = when {
            line.pts >= 25 -> "You dropped ${line.pts} on ${opp.school}. Walk us through your night."
            !won && line.min >= 20 -> "Tough loss to ${opp.school}. What was the difference?"
            line.ast >= 8 -> "You piled up ${line.ast} assists. How did the ball move tonight?"
            else -> "What stood out for you in the ${if (won) "win" else "loss"} against ${opp.school}?"
        }
        val answer = when {
            gunner && conf.startsWith("riding") ->
                "I wanted those looks. Coach put the ball in my hands and I attacked — that's my game."
            hot && !won ->
                "I wasn't backing down. Maybe I forced a couple, but I play with fire — that's who I am."
            passer && won ->
                "Credit the guys making shots. I just tried to find the open man and trust what coach drew up."
            p.has(Trait.COACHABLE) ->
                "We stuck to the plan. Coach had us ready — I just executed my job."
            bond.contains("frustrated") || bond.contains("cool") ->
                "We've got work to do. I'll keep competing, but some of this has to get sorted upstairs too."
            conf.contains("shaken") || conf.contains("second-guessing") ->
                "Not my night. I'll watch the film, talk with coach, and be better next time."
            p.has(Trait.CLUTCH) && won ->
                "Big moments don't scare me. We stuck together and closed it out."
            else ->
                "We ${if (won) "executed enough to win" else "didn't get enough stops"}. Next one's what matters."
        }
        val tone = when {
            gunner && conf.startsWith("riding") -> "confident"
            hot && !won -> "fiery"
            passer -> "humble"
            bond.contains("frustrated") || bond.contains("cool") -> "frustrated"
            else -> "measured"
        }
        return PlayerInterview(p.id, p.name, q, answer, reporter, tone, isAi = false, fallbackReason = "Game-written interview")
    }

    /**
     * Apply AI (or rule) assignment.
     * Roles: GUNNER, PASS_FIRST, SHOWBOAT, HOTHEAD, COACHABLE, STEADY, or BALANCED.
     */
    fun applyAssignment(p: Player, role: String, note: String) {
        clearOnCourt(p)
        val key = role.uppercase().replace('-', '_').replace(' ', '_')
        when {
            key in setOf("GUNNER", "CONFIDENT", "CONFIDENT_SCORER", "SCORER", "VOLUME") -> p.traits += Trait.GUNNER
            key in setOf("PASS_FIRST", "UNSELFISH", "PASSER", "PLAYMAKER") -> p.traits += Trait.PASS_FIRST
            key in setOf("SHOWBOAT", "HIGHLIGHT", "ISO", "FLASHY") -> p.traits += Trait.SHOWBOAT
            key in setOf("HOTHEAD", "FIERY", "AGGRESSIVE", "CHIPPY") -> p.traits += Trait.HOTHEAD
            key in setOf("COACHABLE", "SYSTEM", "SCHEME", "PLUGGABLE") -> p.traits += Trait.COACHABLE
            key in setOf("STEADY", "EVEN", "BALANCED_STEADY", "COMPOSURE") -> p.traits += Trait.STEADY
            // BALANCED / unknown: leave without an on-court force
        }
        if (note.isNotBlank()) p.personalityNote = note.trim().take(220)
        else if (p.personalityNote.isBlank()) {
            val n = defaultNote(p)
            if (n.isNotBlank()) p.personalityNote = n
        }
        trimTraits(p)
    }

    fun countOnCourt(players: Iterable<Player>) = players.count { onCourtTrait(it) != null }
}
