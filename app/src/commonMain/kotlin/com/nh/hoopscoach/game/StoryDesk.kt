package com.nh.hoopscoach.game

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Writes the preview's storylines from their verified topics (see [PreviewTopics]): a kicker, a
 * headline and a body in the voice of a sports weekly. Every kind of story has many headlines and
 * sentence choices, and an issue never uses the same headline pattern twice, so two title contenders
 * on the same cover don't read the same.
 */
object StoryDesk {
    private class Pen(seed: Long) {
        val r = kotlin.random.Random(seed)
        val usedHeads = mutableSetOf<String>()
        fun one(vararg o: String) = o[r.nextInt(o.size)]
        /** A headline pattern this issue hasn't used yet (by its template, not its filled text). */
        fun head(kind: String, options: List<String>, fill: (String) -> String): String {
            val fresh = options.filterNot { "$kind|$it" in usedHeads }.ifEmpty { options }
            val pick = fresh[r.nextInt(fresh.size)]
            usedHeads += "$kind|$pick"
            return fill(pick)
        }
    }

    private fun pct(p: Double) = "${(p * 100).roundToInt()}%"
    private fun f1(d: Double) = String.format(Locale.US, "%.1f", d)
    private fun nick(lg: League, t: Team) = if (lg.pro) t.name.substringAfterLast(' ') else t.mascot
    private fun name(lg: League, t: Team) = if (lg.pro) "the ${t.name}" else t.school
    private fun Name(lg: League, t: Team) = name(lg, t).replaceFirstChar { it.uppercase() }
    private fun cls(p: Player) = when (p.cls.uppercase()) { "FR" -> "freshman"; "SO" -> "sophomore"; "JR" -> "junior"; "SR" -> "senior"; else -> p.cls.lowercase() }
    private fun line(s: StatLine) = "${f1(s.ppg)} points" + (if (s.rpg >= 4) ", ${f1(s.rpg)} rebounds" else "") + (if (s.apg >= 3) " and ${f1(s.apg)} assists" else "")

    fun write(lg: League, topics: List<PreviewTopic>): List<Story> {
        val pen = Pen(lg.season * 104_729L + lg.userTeamId)
        return topics.mapNotNull { tp -> runCatching { story(lg, pen, tp) }.getOrNull() }
    }

    private fun titleOdds(lg: League, id: Int): Pair<Double, String> =
        if (lg.pro) (lg.proState?.preview?.proj?.get(id)?.let { it.title to "${it.w}-${it.l}" } ?: (0.0 to ""))
        else (lg.preview?.odds?.get(id)?.title ?: 0.0) to (lg.preview?.proj?.get(id)?.let { "${it.w}-${it.l}" } ?: "")

    private fun story(lg: League, pen: Pen, tp: PreviewTopic): Story? {
        val t = lg.teams.getOrNull(tp.teamId) ?: return null
        val N = name(lg, t); val Nc = Name(lg, t); val nk = nick(lg, t)
        val roster = lg.rosterOf(t).sortedByDescending { it.ovr }
        val star = roster.firstOrNull()
        val second = roster.getOrNull(1)
        val coach = lg.coachOf(t)
        val conf = lg.conf(t.confId).name.let { if (lg.pro) "the $it" else it }
        // "the New York Knicks" inside a headline, "The New York Knicks" when it opens one.
        val headName = if (lg.pro) "the ${t.name}" else t.school
        fun fillRaw(s: String, extra: Map<String, String>) = (mapOf("team" to headName, "nick" to nk, "school" to t.school, "star" to (star?.name ?: nk), "starlast" to (star?.last ?: nk),
            "coach" to coach.name, "coachlast" to coach.last, "conf" to conf) + extra).entries.fold(s) { acc, (k, v) -> acc.replace("{$k}", v) }
        fun fill(s: String, extra: Map<String, String> = emptyMap()) = fillRaw(s, extra).replaceFirstChar { it.uppercase() }
        return when (tp.kind) {
            "title" -> {
                val p = Regex("(\\d+)% title chance").find(tp.facts)?.groupValues?.get(1)?.toDoubleOrNull()?.div(100) ?: titleOdds(lg, t.id).first
                val rec = Regex("projected (\\d+-\\d+)").find(tp.facts)?.groupValues?.get(1) ?: titleOdds(lg, t.id).second
                val head = pen.head("title", listOf(
                    "{team}: the title case", "Why {team} can win it all", "{team} and the weight of expectation", "The case for {team}",
                    "{nick} built for April", "Is this {team}'s year?", "{team} have everything but the trophy", "Everybody's chasing {team}",
                    "{starlast} and {team} take aim at the title", "The favorite's burden: {team}", "{team}: all in", "What could stop {team}?",
                )) { fill(it) }
                val kicker = pen.one("The favorite", "Title watch", "Contender", "The front-runner", "Championship or bust")
                val body = listOf(
                    pen.one("We gave $N a ${pct(p)} chance to win it all, ", "$Nc won the title in ${pct(p)} of our simulated seasons, ", "A ${pct(p)} title chance doesn't sound like much until you see the rest of the field: ") +
                        pen.one("and a projected $rec record.", "with the average season ending $rec.", "on the way to a projected $rec.", "and nobody projects better than $rec."),
                    star?.let { s ->
                        val prior = s.career.lastOrNull { it.season == lg.season - 1 && it.s.gp > 0 }?.s
                        pen.one("It starts with ${s.name}", "The engine is ${s.name}", "Everything runs through ${s.name}", "${s.name} is the reason") +
                            (if (lg.pro) ", ${s.age}, " else ", a ${cls(s)} ${s.posLabel}, ") +
                            (prior?.let { pen.one("who averaged ${line(it)} last season.", "coming off ${line(it)} a night.", "fresh off ${line(it)}.") } ?: pen.one("with an overall of ${s.ovr} and more to show.", "the best player on the roster at ${s.ovr}."))
                    } ?: "",
                    second?.let { pen.one("${it.name} gives ${coach.last} a second option most contenders would build around.", "Behind the star, ${it.name} is the piece that makes the whole thing work.", "Don't sleep on ${it.name}, who would start for almost anyone in $conf.") } ?: "",
                    pen.one("The worry is the same as it is for every favorite: one bad night in a single-elimination field.", "History says favorites lose more often than they win. ${coach.last} has to make this team the exception.",
                        "The schedule will tell us plenty by January. The bracket will tell us the rest.", "Everybody on the schedule will treat this game as their season. That's the cost of being the team to beat."),
                ).filter { it.isNotBlank() }
                Story(kicker, head, body.joinToString("\n\n"), t.id, star?.id ?: -1)
            }
            "turnover" -> {
                val head = pen.head("turnover", listOf(
                    "{team}: rebuilding the rotation", "A whole new {team}", "Meet the new {nick}", "{team} start over on the fly", "Who are these {nick}?",
                    "Spare parts and big hopes at {school}", "{coachlast}'s reload", "New faces, same expectations for {team}", "{team} turn the page",
                )) { fill(it) }
                Story(pen.one("New look", "Reload", "Roster turnover", "Starting fresh"), head, listOf(
                    tp.facts.substringBefore(" Newcomers"),
                    pen.one("That's a lot of minutes to replace, and ${coach.last} knows it.", "Losing that much production would sink most programs. $Nc is betting it won't.", "Chemistry will take time. Time is the one thing nobody gets in November."),
                    tp.facts.substringAfter("Newcomers: ", "").takeIf { it.isNotBlank() }?.let { pen.one("Watch $it.", "The new names to know: $it.", "The newcomers carrying the load: $it.") } ?: "",
                    pen.one("If the new pieces fit by conference play, $N could be dangerous.", "The first month will be messy. March is the measuring stick.", "It might take a while. It might not. That's what makes $N interesting."),
                ).filter { it.isNotBlank() }.joinToString("\n\n"), t.id)
            }
            "injury" -> {
                val p = lg.playerOrNull(tp.pid) ?: return null
                val head = pen.head("injury", listOf(
                    "{team}: covering {player}'s absence", "Life without {player}", "{team} wait on {player}", "The hole {player} leaves", "Next man up at {school}",
                    "{team}'s first test: no {playerlast}", "How {team} survive without {playerlast}",
                )) { fill(it, mapOf("player" to p.name, "playerlast" to p.last)) }
                val next = roster.firstOrNull { it.id != p.id && it.pos == p.pos } ?: roster.firstOrNull { it.id != p.id }
                Story(pen.one("Injury report", "Next man up", "Medical watch", "Absence"), head, listOf(
                    tp.facts,
                    pen.one("That's a ${p.ovr}-rated ${p.posLabel} out of the lineup.", "${p.last} was supposed to be a big part of this.", "It's the kind of loss that changes a rotation."),
                    next?.let { pen.one("${it.name} gets the first crack at those minutes.", "Expect more of ${it.name} while ${p.last} is out.", "The opportunity falls to ${it.name}.") } ?: "",
                    pen.one("If $N can tread water until ${p.last} is back, the ceiling hasn't changed.", "The question isn't whether $N misses him. It's how much.", "Every win without him is a bonus."),
                ).filter { it.isNotBlank() }.joinToString("\n\n"), t.id, p.id)
            }
            "coach" -> {
                val prev = coach.history.lastOrNull()
                val head = pen.head("coach", listOf(
                    "{team}: a new voice on the bench", "{coachlast} takes over {team}", "The {coachlast} era begins", "{team} hand it to {coachlast}",
                    "A fresh start under {coachlast}", "{coachlast}'s first test", "New coach, new rules at {school}", "{team} turn to {coachlast}",
                )) { fill(it) }
                Story(pen.one("New coach", "Coaching change", "Sideline shift", "First year"), head, listOf(
                    prev?.let { pen.one("${coach.name} arrives from ${it.team}, where he went ${it.w}-${it.l} last season.", "${coach.last} spent last season at ${it.team} (${it.w}-${it.l}).", "${coach.name} comes over from ${it.team} after a ${it.w}-${it.l} year.") } ?: "${coach.name} takes over.",
                    pen.one("He's ${coach.w}-${coach.l} for his career.", "The career record: ${coach.w}-${coach.l}.", "${coach.w} wins and ${coach.l} losses so far.") +
                        (if (coach.titles > 0) pen.one(" He's won it all before.", " He owns a title.", " There's a championship on his résumé.") else ""),
                    pen.one("First-year coaches usually get a grace period. ${coach.last} might not need one.", "The players are still learning his system. So are the fans.", "Year one is for installing habits. The wins can come later."),
                ).filter { it.isNotBlank() }.joinToString("\n\n"), t.id)
            }
            "production" -> {
                val p = lg.playerOrNull(tp.pid) ?: return null
                val s = p.career.lastOrNull { it.season == lg.season - 1 && it.s.gp > 0 }?.s
                val head = pen.head("production", listOf(
                    "{player}: proven production", "{player} picks up where he left off", "Nobody scores like {player}", "The {playerlast} problem",
                    "Can anyone guard {player}?", "{player} wants more", "Bucket-getter: {player}", "{player} and the encore",
                )) { fill(it, mapOf("player" to p.name, "playerlast" to p.last)) }
                Story(pen.one("Star watch", "The scorer", "Production", "Must-see"), head, listOf(
                    s?.let { pen.one("${p.name} averaged ${line(it)} last season.", "Last year's line for ${p.name}: ${line(it)}.", "${p.last} put up ${line(it)} a year ago.") } ?: tp.facts,
                    pen.one("Defenses know he's coming. They still haven't figured out how to stop him.", "The numbers are real, and they travel.", "He'll see every gimmick defense in the book this season."),
                    pen.one("If he does it again, $N is a problem for everybody.", "Repeating it is the hard part.", "The question is what he does for an encore."),
                ).joinToString("\n\n"), t.id, p.id)
            }
            "rivalry" -> {
                val g = lg.games.firstOrNull { !it.played && lg.rivalry(it) != null && it.home == t.id } ?: return null
                val rv = lg.rivalry(g) ?: return null
                val home = lg.team(g.home); val away = lg.team(g.away)
                val head = pen.head("rivalry", listOf(
                    "{home} and {away}: a date to circle", "Circle day {day}: {away} at {home}", "{rivalry}: the renewal", "Bad blood: {away} vs. {home}",
                    "The {rivalry} is back", "Mark your calendar: {away} at {home}",
                )) { fill(it, mapOf("home" to home.school, "away" to away.school, "day" to "${g.day}", "rivalry" to rv.name.ifBlank { "${home.school}-${away.school} rivalry" })) }
                Story(pen.one("Rivalry", "Circle it", "Bad blood", "Grudge match"), head, listOf(
                    tp.facts,
                    pen.one("The series stands at ${rv.winsFor(home.id)}-${rv.winsFor(away.id)} in ${home.school}'s favor, at least by our count.", "All-time: ${home.school} ${rv.winsFor(home.id)}, ${away.school} ${rv.winsFor(away.id)}.",
                        "${home.school} owns ${rv.winsFor(home.id)} of these, ${away.school} ${rv.winsFor(away.id)}."),
                    pen.one("Throw out the records. Nobody does in this one.", "One of these fan bases will be unbearable for a year.", "It's the game both sides check for first when the schedule comes out."),
                ).joinToString("\n\n"), t.id)
            }
            else -> null
        }
    }
}
