package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** One season-long storyline: who it's about, how far it's gone, how hot it is, and what's happened. */
@Serializable
class Storyline(
    val kind: String,
    val season: Int,
    /** The people in it: a player, coach or team id (see [Storylines]). */
    val a: Int,
    val b: Int = -1,
    var stage: Int = 0,
    /** How heated it's gotten, from your choices. */
    var heat: Int = 0,
    var nextDay: Int = 0,
    var pending: Boolean = false,
    var pendingSince: Int = 0,
    var done: Boolean = false,
    /** A promise made along the way (judged at the end). */
    var flag: String = "",
    val log: MutableList<String> = mutableListOf(),
)

/**
 * Season-long storylines, three parts each, each part a choice in your inbox (left unanswered a week, it
 * settles itself the quiet way).
 *  Your player: the rival who trash-talks you, a teammate feud that grows, your coach on the hot seat.
 *  Your program: a rival coach taking shots, two of your players feuding, and your own hot seat.
 * What you choose moves followers, leadership, teammates' moods, the fans, the boosters and your job security,
 * and every storyline ends in the news.
 */
object Storylines {
    class Choice(val text: String, val hint: String)
    class Beat(val kicker: String, val head: String, val text: String, val choices: List<Choice>)

    val TITLES = mapOf(
        "trash" to "The rival", "feud" to "Locker room feud", "hotseat" to "Coach on the hot seat",
        "coachrival" to "The rival coach", "feudc" to "Feud on your team", "myseat" to "Your hot seat",
    )

    fun title(s: Storyline) = TITLES[s.kind] ?: "Storyline"

    fun active(lg: League) = lg.storylines.filter { it.season == lg.season && !it.done }
    fun pending(lg: League) = active(lg).filter { it.pending }

    private fun seasonLen(lg: League) = if (lg.pro) ProLeague.lastDay(lg) else Schedule.REG_DAYS
    private fun wait(lg: League) = if (lg.pro) 21 else 8

    // ---------- who's in it ----------

    private fun me(lg: League) = lg.hero
    private fun myTeam(lg: League) = lg.teams.getOrNull(lg.hero?.teamId ?: lg.userTeamId) ?: lg.user
    private fun pl(lg: League, id: Int) = lg.anyPlayer(id)
    private fun place(lg: League, t: Team) = if (lg.pro) "the ${t.name}" else t.school
    private fun poss(s: String) = if (s.endsWith("s")) "$s'" else "$s's"
    private fun march(lg: League) = if (lg.pro) "the playoffs" else "March"
    private fun boss(lg: League) = if (lg.pro) "the front office" else "the AD"

    // ---------- starting them ----------

    fun daily(lg: League) {
        if (lg.phase != Phase.REGULAR) {
            // The regular season's over: whatever's still open ends here.
            for (s in active(lg)) close(lg, s)
            return
        }
        val len = seasonLen(lg)
        val day = lg.day
        fun has(kind: String) = lg.storylines.any { it.season == lg.season && it.kind == kind }
        fun start(kind: String, a: Int, b: Int = -1) {
            lg.storylines += Storyline(kind, lg.season, a, b, nextDay = day, pending = true, pendingSince = day)
        }
        if (day >= (len * 0.08).roundToInt().coerceAtLeast(2)) {
            if (lg.playerMode) {
                val h = me(lg)
                if (h != null && h.teamId >= 0) {
                    val t = lg.team(h.teamId)
                    Rival.active(lg)?.let { if (!has("trash")) start("trash", it.id) }
                    if (!has("feud") && day >= (len * 0.15).roundToInt()) {
                        val mate = lg.rosterOf(t).filter { it.id != h.id && it.stats.gp > 0 }
                            .maxByOrNull { (if (it.pos == h.pos || it.pos2 == h.pos) 6.0 else 0.0) + it.stats.ppg + it.ovr * 0.1 }
                        if (mate != null && Rng.chance(0.5)) start("feud", mate.id)
                    }
                    val gp = t.w + t.l
                    if (!has("hotseat") && gp >= 6 && t.w.toDouble() / gp < 0.42 && day <= len * 0.6) start("hotseat", lg.coachOf(t).id, t.id)
                }
            } else {
                val u = lg.user
                if (!has("coachrival")) {
                    // A real rival if there is one; otherwise the best program in the conference.
                    val other = lg.rivalsOf(u.id).map { r -> if (r.a == u.id) r.b else r.a }.firstOrNull()
                        ?: lg.teams.filter { it.confId == u.confId && it.id != u.id }.maxByOrNull { it.prestige }?.id
                    if (other != null) start("coachrival", lg.coachOf(lg.team(other)).id, other)
                }
                if (!has("feudc") && day >= (len * 0.2).roundToInt()) {
                    val top = lg.rosterOf(u).filter { it.stats.gp > 0 }.sortedByDescending { it.stats.ppg }.take(3)
                    if (top.size >= 2 && Rng.chance(0.45)) start("feudc", top[0].id, top[1].id)
                }
                val gp = u.w + u.l
                if (!has("myseat") && gp >= 6 && (lg.userCoach.security < 45 || u.w.toDouble() / gp < 0.4) && day <= len * 0.6) start("myseat", lg.userCoach.id, u.id)
            }
        }
        // The next part comes up, or one left too long settles itself.
        for (s in active(lg)) {
            if (!s.pending && day >= s.nextDay) { s.pending = true; s.pendingSince = day }
            else if (s.pending && day - s.pendingSince > wait(lg)) choose(lg, s, 1, auto = true)
        }
    }

    // ---------- the parts ----------

    fun beat(lg: League, s: Storyline): Beat? {
        val h = me(lg)
        val t = myTeam(lg)
        val part = "Part ${s.stage + 1} of 3"
        return when (s.kind) {
            "trash" -> {
                val rv = pl(lg, s.a) ?: return null
                val rt = lg.teams.getOrNull(rv.teamId)
                val you = h?.last ?: "him"
                when (s.stage) {
                    0 -> Beat("THE RIVAL · $part", "${rv.name} takes the first shot",
                        "On a podcast: \"$you? Good player. Not the best in our class. Not close.\" It's everywhere by lunch.",
                        listOf(Choice("Fire back: \"Check the scoreboard.\"", "followers ↑ · it gets hotter"), Choice("Say nothing. Let the games talk.", "leadership ↑"),
                            Choice("High road: \"Great player. See him in ${march(lg)}.\"", "fans ↑ · cools it")))
                    1 -> Beat("THE RIVAL · $part", "It gets personal",
                        "${rv.short}${rt?.let { " (" + place(lg, it) + ")" } ?: ""}, after a big night: \"$you is a system player. Put him on my team, he comes off the bench.\"",
                        listOf(Choice("\"Next time we play, guard me yourself.\"", "followers ↑ · hotter"), Choice("Laugh it off in a video", "followers ↑↑"),
                            Choice("Call him. Settle it privately.", "leadership ↑ · cools it")))
                    else -> {
                        val r = lg.road
                        val w = r?.duels?.count { it.won } ?: 0
                        val l = (r?.duels?.size ?: 0) - w
                        Beat("THE RIVAL · $part", "The last word",
                            "The season's winding down. " + (if (w + l > 0) "You're $w-$l against ${rv.last} all time." else "You two haven't settled it on the floor yet.") + " Everyone's waiting to see who has the last word.",
                            listOf(Choice("Post the head-to-head", if (w >= l) "followers ↑↑" else "risky: he's ahead"), Choice("Say nothing", "leadership ↑"),
                                Choice("End it: \"Respect. Same time next year.\"", "leadership ↑ · fans ↑")))
                    }
                }
            }
            "feud" -> {
                val m = pl(lg, s.a) ?: return null
                when (s.stage) {
                    0 -> Beat("LOCKER ROOM FEUD · $part", "${m.name} wants the ball",
                        "To a reporter: \"I'd like to touch it more. Everybody sees who the offense is built for.\" Your name wasn't said. It didn't have to be.",
                        listOf(Choice("Talk to him one on one", "he's happier · leadership ↑"), Choice("Ignore it", "he stews"),
                            Choice("Answer in the press: \"Get open.\"", "followers ↑ · teammates ↓ · hotter")))
                    1 -> Beat("LOCKER ROOM FEUD · $part", "Words in the locker room",
                        "After a loss, you and ${m.short} got into it. Teammates had to step between you, and the beat writer heard about it.",
                        listOf(Choice("Apologize to the team", "teammates ↑ · leadership ↑"), Choice("Stand your ground", "he's angrier · hotter"),
                            Choice("Take it to the coach", "hotter, a little")))
                    else -> Beat("LOCKER ROOM FEUD · $part", if (s.heat >= 3) "The breaking point" else "One more test",
                        if (s.heat >= 3) "${m.short} skipped the team dinner. The beat writer calls it a fracture. The last stretch of the season is next."
                        else "It's quieter now, but the stretch run will tell you whether it's really over.",
                        listOf(Choice("Make peace in front of everyone", "he's happier · teammates ↑ · leadership ↑"), Choice("Let it ride", "nothing changes"),
                            Choice("Tell the coach: him or me", "needs leadership; costly if you lack it")))
                }
            }
            "hotseat" -> {
                val c = runCatching { lg.coach(s.a) }.getOrNull() ?: return null
                when (s.stage) {
                    0 -> Beat("COACH ON THE HOT SEAT · $part", "They want ${c.last} gone",
                        "${place(lg, t)} is ${t.w}-${t.l}, and the message boards want ${c.name} fired. Reporters want to know where you stand.",
                        listOf(Choice("\"We're playing for Coach.\"", "leadership ↑ · teammates ↑"), Choice("Stay out of it", "nothing changes"),
                            Choice("\"We need changes.\"", "followers ↑ · teammates ↓ · leadership ↓")))
                    1 -> Beat("COACH ON THE HOT SEAT · $part", "A vote of confidence",
                        "${boss(lg).replaceFirstChar { it.uppercase() }} gave ${c.last} a vote of confidence. Everybody knows what that usually means.",
                        listOf(Choice("Call a players-only meeting", "the team plays better · leadership ↑"), Choice("Focus on your own game", "followers ↑"),
                            Choice("Have your people look at other options", "followers ↑ · teammates ↓")))
                    else -> Beat("COACH ON THE HOT SEAT · $part", "The last home game",
                        "It might be ${c.last}'s last home game here. The microphone's in front of you after.",
                        listOf(Choice("\"He's my coach. Period.\"", "leadership ↑ · teammates ↑"), Choice("\"That's above my pay grade.\"", "nothing changes"),
                            Choice("\"Whatever happens, we'll be fine.\"", "followers ↑")))
                }
            }
            "coachrival" -> {
                val c = runCatching { lg.coach(s.a) }.getOrNull() ?: return null
                val rs = lg.teams.getOrNull(s.b) ?: return null
                val u = lg.user
                val real = lg.rivalsOf(u.id).any { it.involves(s.b) }
                when (s.stage) {
                    0 -> Beat("THE RIVAL COACH · $part", "${c.name} fires the first shot",
                        "At ${poss(place(lg, rs))} media day: \"${place(lg, u).replaceFirstChar { it.uppercase() }}? We own ${if (real) "this rivalry" else if (lg.pro) "this division" else "this conference"}. ${if (lg.pro) "Ask anybody." else "Ask the recruits."}\"",
                        listOf(Choice("\"Ask them about last ${march(lg)}.\"", "fans ↑ · buzz ↑ · hotter"), Choice("No comment", "the AD likes calm"),
                            Choice("\"He's a great coach. We'll see.\"", "cools it")))
                    1 -> if (lg.pro) Beat("THE RIVAL COACH · $part", "Tampering talk",
                        "${c.last}, on a radio show: \"Half that roster would rather play for us. Ask their agents.\"",
                        listOf(Choice("Call him out publicly", "hotter · front office ↓"), Choice("\"Our guys are happy here.\"", "players ↑"),
                            Choice("Let the standings talk", "front office ↑")))
                    else Beat("THE RIVAL COACH · $part", "A recruiting war",
                        "${c.last} is telling recruits you won't be at ${place(lg, u)} long. One of your targets said so on a visit.",
                        listOf(Choice("Call him out publicly", "buzz ↑ · hotter · AD ↓"), Choice("\"I'm here to stay.\"", "boosters ↑ · buzz ↑"),
                            Choice("Out-recruit him quietly", "+15 recruiting points")))
                    else -> Beat("THE RIVAL COACH · $part", if (real) "Rivalry week" else "The rematch",
                        "${c.last}: \"This game means more to us than it does to them.\"",
                        listOf(Choice("Guarantee a win", "fans ↑↑ · risky"), Choice("Keep it about the players", "players ↑"),
                            Choice("Open practice to the boosters", "boosters ↑")))
                }
            }
            "feudc" -> {
                val a = pl(lg, s.a) ?: return null
                val b = pl(lg, s.b) ?: return null
                when (s.stage) {
                    0 -> Beat("FEUD ON YOUR TEAM · $part", "${a.short} and ${b.short} get into it",
                        "Shoves at practice, words in the hallway, a door slammed. The team's watching how you handle it.",
                        listOf(Choice("Sit them both for a half", "both ↓ · team ↑ · cools it"), Choice("Call a team meeting", "everyone ↑ a little"),
                            Choice("Let them work it out", "hotter")))
                    1 -> Beat("FEUD ON YOUR TEAM · $part", "Shots and minutes",
                        "${a.short} says ${b.short} never passes him the ball. The box score backs him up.",
                        listOf(Choice("Draw up plays for ${a.short}", "${a.short} ↑ · ${b.short} ↓"), Choice("Back ${b.short}: \"He makes the right play.\"", "${b.short} ↑ · ${a.short} ↓"),
                            Choice("Sit them down together", "both ↑ · cools it")))
                    else -> Beat("FEUD ON YOUR TEAM · $part", if (s.heat >= 3) "The breaking point" else "Can they finish it together?",
                        if (s.heat >= 3) (if (lg.pro) "Rumor has it one of them wants a trade." else "Rumor has it one of them is looking at the transfer portal.") else "It's calmer, but they still don't sit together on the bus.",
                        listOf(Choice("Side with ${a.short}", "${b.short} may ${if (lg.pro) "want out" else "transfer"}"), Choice("Side with ${b.short}", "${a.short} may ${if (lg.pro) "want out" else "transfer"}"),
                            Choice("Make them room together on the road", if (s.heat <= 2) "likely peace" else "a gamble")))
                }
            }
            "myseat" -> {
                val u = lg.user
                when (s.stage) {
                    0 -> Beat("YOUR HOT SEAT · $part", "The columns start",
                        "\"Is it time for ${place(lg, u)} to move on from ${lg.userCoach.name}?\" ${u.w}-${u.l}, and the question's in print now.",
                        listOf(Choice("\"Judge us in ${march(lg)}.\"", "job security ↑ now · judged at season's end"), Choice("Point to injuries and youth", "a little security · fans ↓"),
                            Choice("Ignore it", "nothing changes")))
                    1 -> if (lg.pro) Beat("YOUR HOT SEAT · $part", "The owner wants a meeting", "The owner wants to hear the plan. In person, this week.",
                        listOf(Choice("Meet and show the plan", "security ↑"), Choice("Fire an assistant", "security ↑ · players ↓"),
                            Choice("Let the record speak", "security ↓")))
                    else {
                        val d = Facilities.donors(u).first()
                        Beat("YOUR HOT SEAT · $part", "The boosters want a meeting", "${d.name} and a group of big donors want to hear the plan. In person.",
                            listOf(Choice("Meet them and show the plan", "boosters ↑ · security ↑"), Choice("Fire an assistant", "security ↑ · players ↓"),
                                Choice("Decline the meeting", "boosters ↓ · security ↓")))
                    }
                    else -> Beat("YOUR HOT SEAT · $part", if (lg.pro) "The front office's statement" else "The AD's statement",
                        "${boss(lg).replaceFirstChar { it.uppercase() }} will \"evaluate everything after the season.\" The last stretch is your case.",
                        listOf(Choice("Ride the starters", "the team plays better · players tire"), Choice("Lean on the seniors", "players ↑"),
                            Choice("Make your case to the fans", "fans ↑ · security ↑")))
                }
            }
            else -> null
        }
    }

    // ---------- choosing ----------

    /** Your answer to the current part: its effects, its line in the log, and the next part's date. Returns the reaction. */
    fun choose(lg: League, s: Storyline, i: Int, auto: Boolean = false): String {
        val beat = beat(lg, s) ?: run { s.done = true; return "" }
        val r = lg.road
        val h = me(lg)
        val t = myTeam(lg)
        val c = lg.userCoach.takeIf { !lg.playerMode }
        fun followers(p: Double) { r?.let { it.followers = (it.followers * (1 + p)).roundToInt() } }
        fun lead(d: Int) { r?.let { it.leadership = (it.leadership + d).coerceIn(0, 100) } }
        fun persona(d: Int) { r?.let { it.persona += d } }
        fun mates(d: Int) { for (p in lg.rosterOf(t)) if (p.id != h?.id) p.happiness = (p.happiness + d).coerceIn(0, 99) }
        fun happy(id: Int, d: Int) { lg.playerOrNull(id)?.let { it.happiness = (it.happiness + d).coerceIn(0, 99) } }
        fun sec(d: Int) { c?.let { it.security = (it.security + d).coerceIn(0, 100) } }
        fun fans(d: Int) { t.fans = (t.fans + d).coerceIn(5.0, 100.0) }
        fun buzz(d: Int) { if (t.buzz >= 0) t.buzz = (t.buzz + d).coerceIn(0.0, 100.0) }
        fun boosters(d: Int) { t.boosterMood = (t.boosterMood + d).coerceIn(5, 100) }
        fun elo(d: Int) { t.elo += d }
        val k = i.coerceIn(0, 2)
        val reaction: String = when (s.kind) {
            "trash" -> when (s.stage) {
                0 -> when (k) { 0 -> { followers(0.06); persona(2); s.heat += 2; "The post did numbers. He saw it." }; 1 -> { lead(3); "Coaches noticed the restraint." }; else -> { followers(0.02); lead(1); s.heat -= 1; "Classy. The fans loved it." } }
                1 -> when (k) { 0 -> { followers(0.05); s.heat += 3; "Now there's a game everyone's circling." }; 1 -> { followers(0.08); persona(1); s.heat += 1; "The clip went viral." }; else -> { lead(2); s.heat -= 2; "You cleared the air. Mostly." } }
                else -> {
                    val w = r?.duels?.count { it.won } ?: 0
                    val l = (r?.duels?.size ?: 0) - w
                    when (k) { 0 -> if (w >= l) { followers(0.08); persona(2); "Last word: yours." } else { followers(-0.03); "It backfired. He's ahead, and he said so." }; 1 -> { lead(2); "You let your game do the talking." }; else -> { lead(3); fans(1); s.heat = 0; "Respect. Even his fans liked it." } }
                }
            }
            "feud" -> when (s.stage) {
                0 -> when (k) { 0 -> { happy(s.a, 8); lead(3); s.heat -= 1; "He appreciated it. For now." }; 1 -> { happy(s.a, -4); s.heat += 1; "He's stewing." }; else -> { happy(s.a, -10); followers(0.04); persona(2); mates(-2); s.heat += 3; "Your fans loved it. Your locker room didn't." } }
                1 -> when (k) { 0 -> { mates(3); happy(s.a, 5); lead(4); s.heat -= 2; "The team needed that." }; 1 -> { happy(s.a, -6); lead(1); s.heat += 2; "Some respect, and a colder locker room." }; else -> { happy(s.a, -3); s.heat += 1; "Coach handled it. He didn't love being put in the middle." } }
                else -> when (k) {
                    0 -> { happy(s.a, 12); mates(4); lead(5); s.heat = 0; "You made peace. The whole team exhaled." }
                    1 -> "It hangs over the rest of the season."
                    else -> if ((r?.leadership ?: 0) >= 60) { happy(s.a, -18); lead(2); s.flag = "a"; "Coach picked you. He's on the bench, and probably gone." } else { lead(-5); mates(-3); "It backfired. The room sided with him." }
                }
            }
            "hotseat" -> when (s.stage) {
                0 -> when (k) { 0 -> { lead(4); mates(2); followers(0.02); "Coach heard it. So did the team." }; 1 -> "You kept your head down."; else -> { followers(0.05); persona(3); mates(-3); lead(-4); "Your fans agreed. Your coach saw it." } }
                1 -> when (k) { 0 -> { mates(4); lead(5); elo(12); "The next practice was the sharpest all year." }; 1 -> { followers(0.02); lead(-1); "Your numbers are fine. The team's aren't." }; else -> { mates(-4); lead(-5); followers(0.03); "Word got around the locker room." } }
                else -> when (k) { 0 -> { lead(4); mates(3); "A moment the fans won't forget." }; 1 -> "Safe answer."; else -> { followers(0.03); "Diplomatic." } }
            }
            "coachrival" -> when (s.stage) {
                0 -> when (k) { 0 -> { fans(3); buzz(3); s.heat += 2; "Recruits loved it." }; 1 -> { sec(1); "The AD appreciated the calm." }; else -> { fans(-1); sec(1); s.heat -= 1; "Gracious. Your fans wanted more." } }
                1 -> if (lg.pro) when (k) { 0 -> { sec(-1); s.heat += 2; "Now it's a war." }; 1 -> { mates(3); "Your locker room heard it." }; else -> { sec(2); "The front office liked the restraint." } }
                else when (k) { 0 -> { buzz(4); sec(-1); s.heat += 2; "Now it's a war." }; 1 -> { boosters(4); buzz(2); "The boosters loved hearing it." }; else -> { lg.recruitPoints += 15; "Your staff made extra calls." } }
                else -> when (k) { 0 -> { fans(5); s.heat += 3; s.flag = "guarantee"; sec(-2); "Bold. Now you have to win it." }; 1 -> { mates(3); "Your players felt it was about them." }; else -> { boosters(6); "The boosters ate it up." } }
            }
            "feudc" -> when (s.stage) {
                0 -> when (k) { 0 -> { happy(s.a, -4); happy(s.b, -4); mates(2); s.heat -= 1; "Message received." }; 1 -> { mates(2); happy(s.a, 2); happy(s.b, 2); s.heat -= 1; "Everyone said their piece." }; else -> { s.heat += 2; "They didn't." } }
                1 -> when (k) { 0 -> { happy(s.a, 8); happy(s.b, -6); s.heat += 1; "${pl(lg, s.a)?.short} is happy. ${pl(lg, s.b)?.short} isn't." }; 1 -> { happy(s.b, 8); happy(s.a, -8); s.heat += 2; "${pl(lg, s.b)?.short} is happy. ${pl(lg, s.a)?.short} isn't." }; else -> { happy(s.a, 4); happy(s.b, 4); s.heat -= 2; sec(1); "Slow progress." } }
                else -> when (k) {
                    0 -> { happy(s.a, 10); happy(s.b, -20); s.flag = "b"; "${pl(lg, s.b)?.short} ${if (lg.pro) "wants out" else "is looking at the portal"}." }
                    1 -> { happy(s.b, 10); happy(s.a, -20); s.flag = "a"; "${pl(lg, s.a)?.short} ${if (lg.pro) "wants out" else "is looking at the portal"}." }
                    else -> if (s.heat <= 2) { happy(s.a, 10); happy(s.b, 10); mates(2); "It worked. They made peace." } else { happy(s.a, -5); happy(s.b, -5); "It didn't work." }
                }
            }
            "myseat" -> when (s.stage) {
                0 -> when (k) { 0 -> { sec(3); s.flag = "promise"; "Bought some time. March will judge it." }; 1 -> { sec(1); fans(-2); "Some fans called it excuses." }; else -> "The columns kept coming." }
                1 -> when (k) { 0 -> { boosters(8); sec(2); "They left believing." }; 1 -> { sec(4); mates(-3); "A scapegoat. The players noticed." }; else -> { boosters(-8); sec(-2); "They noticed you didn't come." } }
                else -> when (k) { 0 -> { elo(15); mates(-2); "The starters gave you everything." }; 1 -> { mates(3); "The seniors stepped up." }; else -> { fans(3); sec(1); "The crowd was behind you." } }
            }
            else -> ""
        }
        val choice = beat.choices.getOrNull(k)?.text ?: ""
        s.log += if (auto) "${beat.head}: you let it go." else "${beat.head}: $choice"
        s.heat = s.heat.coerceIn(0, 9)
        s.pending = false
        s.stage++
        if (s.stage >= 3) close(lg, s) else {
            val len = seasonLen(lg)
            s.nextDay = lg.day + (len * 0.32).roundToInt().coerceAtLeast(3)
        }
        return if (auto) "" else reaction
    }

    /** The end of a storyline: a line in the news, and a promise judged. */
    private fun close(lg: League, s: Storyline) {
        if (s.done) return
        s.done = true
        val t = myTeam(lg)
        val gp = (t.w + t.l).coerceAtLeast(1)
        val good = t.w.toDouble() / gp >= 0.55
        val headline = when (s.kind) {
            "trash" -> pl(lg, s.a)?.let { rv -> if (s.heat >= 4) "${lg.hero?.last} vs. ${rv.last}: the feud of the season isn't over" else "${lg.hero?.last} and ${rv.last} end the season with respect" }
            "feud" -> pl(lg, s.a)?.let { m -> if (s.flag == "a") "${m.last} lost the power struggle with ${lg.hero?.last}" else if (s.heat >= 3) "${lg.hero?.last} and ${m.last}: the locker room never healed" else "${lg.hero?.last} and ${m.last} put the feud behind them" }
            "hotseat" -> runCatching { lg.coach(s.a) }.getOrNull()?.let { c ->
                c.security = (c.security + if (good) 10 else -10).coerceIn(0, 100)
                if (good) "${c.last} saved his job down the stretch" else "${c.last}'s fate goes to ${boss(lg)}"
            }
            "coachrival" -> runCatching { lg.coach(s.a) }.getOrNull()?.let { c -> if (s.heat >= 4) "${lg.userCoach.last} vs. ${c.last}: the war of words will carry into next season" else "${c.last} and ${lg.userCoach.last}: a rivalry, but a respectful one" }
            "feudc" -> { val a = pl(lg, s.a); val b = pl(lg, s.b); if (a != null && b != null) (if (s.flag == "a") "${a.last} could be on the move after the feud with ${b.last}" else if (s.flag == "b") "${b.last} could be on the move after the feud with ${a.last}" else if (s.heat >= 3) "${a.last} and ${b.last} never made peace" else "${a.last} and ${b.last} finished the season together") else null }
            "myseat" -> {
                if (s.flag == "promise" && !good) lg.userCoach.security = (lg.userCoach.security - 6).coerceAtLeast(0)
                if (good) "${lg.userCoach.name} turned the season around" else "${lg.userCoach.name}'s future is the question of the offseason"
            }
            else -> null
        }
        if (s.kind == "coachrival" && s.flag == "guarantee") {
            val g = lg.games.lastOrNull { it.played && it.day <= lg.day && it.involves(lg.userTeamId) && it.involves(s.b) }
            if (g != null) {
                val won = g.winner() == lg.userTeamId
                lg.userCoach.security = (lg.userCoach.security + if (won) 4 else -5).coerceIn(0, 100)
                lg.user.fans = (lg.user.fans + if (won) 5 else -5).coerceIn(5.0, 100.0)
            }
        }
        if (headline != null) lg.news += News(lg.season, lg.day, headline, true, kind = "storyline", teamId = t.id, source = "Hoops Wire",
            body = s.log.mapIndexed { i, l -> "Part ${i + 1}. $l" }.joinToString("\n"))
    }
}
