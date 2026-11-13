package com.nh.hoopscoach.game

/**
 * On-court personality: how a player decides to shoot or pass.
 * Confident scorers hunt their own look; unselfish players kick tough shots to teammates.
 */
object Personality {
    fun usageMod(p: Player): Double = when {
        p.has(Trait.GUNNER) -> 1.18
        p.has(Trait.PASS_FIRST) -> 0.84
        p.has(Trait.TEAM_PLAYER) -> 0.92
        p.has(Trait.AMBITIOUS) -> 1.06
        else -> 1.0
    }

    fun passMod(p: Player): Double = when {
        p.has(Trait.PASS_FIRST) -> 1.45
        p.has(Trait.TEAM_PLAYER) -> 1.2
        p.has(Trait.GUNNER) -> 0.78
        else -> 1.0
    }

    /** Appetite for contested mid-range / threes when the defense is loaded. */
    fun toughShotAppetite(p: Player): Double = when {
        p.has(Trait.GUNNER) -> 1.35
        p.has(Trait.PASS_FIRST) -> 0.62
        p.has(Trait.TEAM_PLAYER) -> 0.78
        else -> 1.0
    }

    /**
     * Chance the ball-handler kicks a low-percentage look instead of forcing it.
     * Gunners almost never; pass-first / team players often.
     */
    fun kickOutChance(p: Player, pMake: Double): Double {
        if (pMake >= 0.44) return 0.0
        val pressure = ((0.44 - pMake) / 0.32).coerceIn(0.0, 1.0)
        val base = 0.08 + 0.42 * pressure
        return when {
            p.has(Trait.PASS_FIRST) -> (base * 1.55).coerceAtMost(0.72)
            p.has(Trait.TEAM_PLAYER) -> (base * 1.25).coerceAtMost(0.58)
            p.has(Trait.GUNNER) -> (base * 0.22).coerceAtMost(0.18)
            else -> (base * 0.75).coerceAtMost(0.4)
        }
    }

    fun onCourtLabel(p: Player): String? = when {
        p.has(Trait.GUNNER) -> "Confident scorer"
        p.has(Trait.PASS_FIRST) -> "Unselfish"
        p.has(Trait.TEAM_PLAYER) && !p.has(Trait.GUNNER) -> "Team-first"
        else -> null
    }

    /** Give veterans without an on-court trait a style that matches their game. Safe to call repeatedly. */
    fun seedOnCourtTrait(p: Player) {
        if (p.has(Trait.GUNNER) || p.has(Trait.PASS_FIRST)) return
        val scoring = (p[Attr.INS] + p[Attr.MID] + p[Attr.OUT]) / 3.0
        val pas = p[Attr.PAS].toDouble()
        // Deterministic from player id so re-seeding never flips a decision.
        val roll = ((p.id * 1103515245 + 12345) ushr 16).mod(100)
        when {
            pas >= scoring + 5 && roll < 60 -> p.traits += Trait.PASS_FIRST
            scoring >= pas + 5 && roll < 60 -> p.traits += Trait.GUNNER
            roll < 14 -> p.traits += if (pas >= scoring) Trait.PASS_FIRST else Trait.GUNNER
        }
        if (p.traits.size > 3) {
            val keep = p.traits.filter { it == Trait.GUNNER || it == Trait.PASS_FIRST || it == Trait.LATE_BLOOMER }.toMutableList()
            for (t in p.traits) {
                if (keep.size >= 3) break
                if (t !in keep) keep += t
            }
            p.traits.clear()
            p.traits += keep
        }
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
        val gunner = p.has(Trait.GUNNER)
        val passer = p.has(Trait.PASS_FIRST) || p.has(Trait.TEAM_PLAYER)
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
            passer && won ->
                "Credit the guys making shots. I just tried to find the open man and trust what coach drew up."
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
            passer -> "humble"
            bond.contains("frustrated") || bond.contains("cool") -> "frustrated"
            else -> "measured"
        }
        return PlayerInterview(p.id, p.name, q, answer, reporter, tone, isAi = false, fallbackReason = "Game-written interview")
    }

    /** Apply AI (or rule) assignment: role is GUNNER, PASS_FIRST, or BALANCED. */
    fun applyAssignment(p: Player, role: String, note: String) {
        p.traits.removeAll { it == Trait.GUNNER || it == Trait.PASS_FIRST }
        when (role.uppercase().replace('-', '_').replace(' ', '_')) {
            "GUNNER", "CONFIDENT", "CONFIDENT_SCORER", "SCORER", "VOLUME" -> p.traits += Trait.GUNNER
            "PASS_FIRST", "UNSELFISH", "PASSER", "PLAYMAKER" -> p.traits += Trait.PASS_FIRST
            else -> { /* balanced: no on-court force */ }
        }
        if (note.isNotBlank()) p.personalityNote = note.trim().take(220)
    }
}
