package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League

/** One term in the glossary: what it's called, the section it sits in, and what it means, in a line or two. */
class HelpTerm(val term: String, val group: String, val text: String, val also: List<String> = emptyList())

/**
 * Help: how each mode is played, and every term the game uses, explained in plain words and searchable.
 * Opened from the ? on Today, the sim menu and Settings.
 */
object Help {
    const val RATINGS = "Ratings and players"
    const val TEAM = "Running your team"
    const val COLLEGE = "College basketball"
    const val MONEY = "Recruiting, NIL and money"
    const val NBA = "The NBA"
    const val PLAYER = "Your player"

    val TERMS = listOf(
        // Ratings and players
        HelpTerm("OVR (overall)", RATINGS, "How good a player is right now, from all his ratings weighted for his position. Higher is better: the ring's color tells you at a glance, orange below average, blue good, green great.", listOf("overall", "rating")),
        HelpTerm("POT (potential)", RATINGS, "How high his overall can climb. Young players grow toward it each offseason and with training. It's a projection, so it can move.", listOf("potential", "ceiling")),
        HelpTerm("OFF and DEF", RATINGS, "His offense (scoring, shooting, handling, passing) and defense (rebounding, perimeter and interior defense, steals, blocks) ratings.", listOf("offense", "defense")),
        HelpTerm("OUT · INS · PLAY · DEF · REB", RATINGS, "His ratings in five groups: outside shooting, inside scoring, playmaking, defense and rebounding."),
        HelpTerm("Play style", RATINGS, "What kind of player he is: Sharpshooter, Rim Protector, Pure Point and so on. It sets which ratings grow fastest and what he's asked to do.", listOf("archetype", "style")),
        HelpTerm("Badges", RATINGS, "Perks for doing one thing well, each a small boost (like a little more on threes). Earn the tiers, Bronze, Silver, Gold and Legend, by doing it in games; his ratings cap how high each one can go.", listOf("bronze", "silver", "gold", "legend")),
        HelpTerm("Traits", RATINGS, "Personality tags like Confident Scorer, Team Player, Bag Chaser or Loyal. They change how he reacts to minutes, money and losing.", listOf("personality")),
        HelpTerm("Morale", RATINGS, "How happy he is: Happy, Content, Unhappy, or ready to leave. Minutes, winning and keeping your promises move it.", listOf("happy", "unhappy", "mood")),
        HelpTerm("Rating range", RATINGS, "Before you've scouted someone, his OVR and POT show as a range (like 82-99). Scouting narrows it.", listOf("range", "fog")),
        HelpTerm("Injuries", RATINGS, "Graded minor (a few games), moderate, severe or season-ending. For your own players you choose the recovery plan on the injury report: rest fully, standard, or rush back.", listOf("injury", "hurt", "recovery")),
        HelpTerm("Questionable · Doubtful · Limited", RATINGS, "Questionable or doubtful: a minor injury, likely back in a game or two, and he can play through it. Limited: back, but not 100% yet, so he shoots a little worse and is likelier to get hurt again.", listOf("playing hurt", "play through")),

        // Running your team
        HelpTerm("Rotation and minutes", TEAM, "Each game has 200 player-minutes to share, 40 at each position (240 and 48 in the NBA). The slider at each position splits them between the starter and his backup.", listOf("lineup", "minutes", "starters")),
        HelpTerm("Usage", TEAM, "How much of the offense runs through a player. Raise it for your best scorer, lower it for a shooter who should only take open shots."),
        HelpTerm("Game plan", TEAM, "Your offense and defense styles, like Run & Gun, Grind It Out, Pack Line or a full-court press. Auto lets your staff pick what fits the roster.", listOf("offense", "defense", "auto", "tactics")),
        HelpTerm("Power", TEAM, "Your team's strength from its results so far, like a power ranking."),
        HelpTerm("Training focus and XP", TEAM, "Players earn XP from games and practice. Every 500 XP raises the three ratings of their focus, so pick the focus that fills the holes in his game.", listOf("xp", "develop", "focus")),
        HelpTerm("Locker room", TEAM, "Players who need attention (unhappy about minutes, a feud, a slump) show up here. Handle them before it spreads.", listOf("chemistry", "feud")),
        HelpTerm("Home court", TEAM, "Playing at home is worth a few points. A louder building and a bigger crowd add to it."),
        HelpTerm("Assistants", TEAM, "Up to three coaches on your staff, each adding to one thing: offense, defense, recruiting or player development. Good ones get hired away as head coaches.", listOf("staff")),

        // College basketball
        HelpTerm("NET", COLLEGE, "The NCAA's team ranking, built from results, margin and who you played. Lower is better: NET 1 is the best team in the country."),
        HelpTerm("Quad 1-4", COLLEGE, "Wins and losses sorted by the opponent's NET and where you played. Quad 1 wins impress the committee; Quad 3 and 4 losses hurt the most.", listOf("quadrant", "q1")),
        HelpTerm("WAB (wins above bubble)", COLLEGE, "How many more games you've won than a bubble team would have against your schedule. Above zero, you're in good shape.", listOf("wab", "bubble")),
        HelpTerm("SOS", COLLEGE, "Strength of schedule: how tough your opponents have been.", listOf("schedule")),
        HelpTerm("Bubble Watch", COLLEGE, "The teams right on the edge of the NCAA field: locks, the last four in, the first four out, and why.", listOf("last four", "first four out")),
        HelpTerm("Selection Sunday and seeds", COLLEGE, "The committee picks 68 teams and seeds them 1 to 16 in four regions. Win your conference tournament and you're in, whatever your record.", listOf("seed", "ncaa tournament", "auto-bid")),
        HelpTerm("First Four", COLLEGE, "Four play-in games before the round of 64."),
        HelpTerm("NIT", COLLEGE, "The tournament for good teams that just missed the NCAA field."),
        HelpTerm("AP Top 25", COLLEGE, "The weekly poll of the best teams. A ranked team's number shows next to its name, like #10 Louisville.", listOf("poll", "ranked")),
        HelpTerm("Prestige", COLLEGE, "Your program's standing. It moves once a season, at the end of it: winning, March runs and history raise it, losing lowers it."),
        HelpTerm("Buzz", COLLEGE, "Short-term heat from big wins and March. It fades fast."),
        HelpTerm("Pull", COLLEGE, "How big your program looks to recruits, coaches and players. Recruits call it recruiting pull.", listOf("recruiting pull", "program pull")),
        HelpTerm("Legacy and exposure", COLLEGE, "Legacy is your program's history (titles, Final Fours, legends). Exposure is how much national TV your conference gets."),
        HelpTerm("Expected wins and job security", COLLEGE, "Your boss's target for the season. Beat it and your job is safe; fall well short and the seat gets hot.", listOf("hot seat", "fired", "security")),
        HelpTerm("Redshirt", COLLEGE, "A season spent practicing, not playing, to develop without using a year of eligibility."),
        HelpTerm("Transfer portal", COLLEGE, "Where players go to leave for another school. Unhappy players can enter it; you can also recruit from it.", listOf("transfer")),

        // Recruiting, NIL and money
        HelpTerm("Stars and rank", MONEY, "A recruit's grade: 5 stars is the best. The number under it is his national rank.", listOf("5-star", "recruit")),
        HelpTerm("Scouting points", MONEY, "Spent to scout a recruit in three looks: film, a campus visit, then a full report. Each look narrows his ratings range.", listOf("scouting", "scout")),
        HelpTerm("Targets", MONEY, "The recruits you're after. Add them on Recruit; the school leading for each one is shown next to him."),
        HelpTerm("Recruiting rounds", MONEY, "After the season, recruiting runs in five rounds. Visits, offers and your pitch move recruits toward you, and they commit as it goes."),
        HelpTerm("NIL", MONEY, "Name, image and likeness money. In college your NIL budget pays players and recruits; as a player, it's what brands pay you.", listOf("budget")),
        HelpTerm("Compliance", MONEY, "Pay well above a player's market value and you earn warnings; enough of them bring an investigation. Paying market value clears them over time.", listOf("warning", "investigation")),
        HelpTerm("Facilities fund", MONEY, "Money from ticket sales and boosters for building: the arena, practice facility, recruiting office and more.", listOf("arena", "facilities")),
        HelpTerm("Boosters", MONEY, "The program's donors. Wins, ranked wins, rivalry wins and March make them happy, and happy boosters give more."),

        // The NBA
        HelpTerm("Salary cap", NBA, "The spending limit for signing other teams' free agents. Over it, you can still re-sign your own players and use exceptions.", listOf("cap", "payroll")),
        HelpTerm("Tax line and aprons", NBA, "Spend past the tax line and the team pays a tax on every dollar over. The aprons above it block certain trades and signings.", listOf("luxury tax", "apron")),
        HelpTerm("MLE (mid-level exception)", NBA, "Lets a team over the cap sign one free agent up to a set amount.", listOf("mle", "exception")),
        HelpTerm("Rookie scale and two-way", NBA, "First-round picks sign set contracts by draft slot. A two-way deal is a small contract for a young or undrafted player.", listOf("rookie", "two-way")),
        HelpTerm("Restricted free agent", NBA, "A player coming off his rookie deal. His team can match any offer sheet to keep him.", listOf("rfa", "offer sheet")),
        HelpTerm("Options and extensions", NBA, "A player or team option is a last year either side can pick up or decline. An extension re-signs a player in the last year of his deal, before free agency.", listOf("option", "extend")),
        HelpTerm("Play-in", NBA, "Seeds 7 to 10 in each conference play for the last two playoff spots."),
        HelpTerm("NBA Cup", NBA, "The in-season tournament: group games in November, then knockout rounds and the final in Las Vegas.", listOf("cup")),
        HelpTerm("Draft lottery", NBA, "The teams that miss the playoffs; the worst records have the best odds at the top picks.", listOf("lottery")),
        HelpTerm("Trade deadline", NBA, "The last day of the regular season you can make trades.", listOf("deadline")),
        HelpTerm("Coach's trust", PLAYER, "As a pro, how much your coach trusts you: it grows with good games and doing what he asks, and it's minutes. Talk to him every ten days on Me, Team: ask for a bigger role, ask what he needs (one thing over five games), or tell him you'll do whatever the team needs.", listOf("coach", "trust", "minutes", "role")),
        HelpTerm("All-Star Saturday", PLAYER, "Invited to the 3-Point Contest or the Dunk Contest? Play your round from Today in the days before the break: it's your score when the weekend comes.", listOf("dunk contest", "3-point contest", "three-point contest", "all-star")),
        HelpTerm("Owner and job security", NBA, "The owner's verdict on you, out of 100. Beat the projected wins and win playoff series to raise it; fall too low and you're fired.", listOf("owner", "fired")),

        // Your player
        HelpTerm("Draft stock and big board", PLAYER, "Where scouts rank you for the NBA draft. Big games and a good combine move you up.", listOf("draft", "big board")),
        HelpTerm("Draft combine", PLAYER, "Before the draft: measurements, athletic tests, shooting and interviews with teams. Good results move you up the board.", listOf("combine")),
        HelpTerm("Followers", PLAYER, "Your social following. It grows with big games and makes your NIL deals worth more."),
        HelpTerm("Fatigue", PLAYER, "How tired you are. Late nights and heavy minutes add to it; tired legs play worse and get hurt more.", listOf("tired", "rest")),
        HelpTerm("Shootaround", PLAYER, "Ten shots before a game on the shot meter. Make eight or more and you're feeling it that night."),
        HelpTerm("Pick'em", PLAYER, "Pick the winners of the week's three biggest games. Get them right for card packs.", listOf("pickem")),
        HelpTerm("Cards and the binder", PLAYER, "Win games to earn card packs, open them to collect players, and finish sets for more packs.", listOf("cards", "packs", "binder")),
        HelpTerm("Legacy", PLAYER, "Your career score from awards, titles and points. When you retire, your son can carry the name into his own career.", listOf("son", "family")),
        HelpTerm("You and your father", PLAYER, "Playing as a son: your father calls after big nights, bad nights and before big games. Answer, take his film sessions and work out with him in the preseason and you get closer; the closer you are, the more it lifts you to see him in the stands. Strained, he skips the regular-season trips.", listOf("dad", "father", "bond", "film", "workout")),
        HelpTerm("The family feud", PLAYER, "If your father had a rival, your rival is that rival's son.", listOf("feud", "rival")),
        HelpTerm("The long way", PLAYER, "Not drafted? Play in the G League or overseas and play your way into an NBA call-up.", listOf("g league", "overseas", "undrafted")),
    )

    /** The sections in the order that suits this career: what you'll meet first comes first. */
    fun groups(lg: League): List<String> = when {
        lg.playerMode -> listOf(PLAYER, RATINGS, if (lg.pro) NBA else COLLEGE, TEAM, MONEY, if (lg.pro) COLLEGE else NBA)
        lg.pro -> listOf(TEAM, NBA, RATINGS, MONEY, COLLEGE, PLAYER)
        else -> listOf(TEAM, COLLEGE, MONEY, RATINGS, NBA, PLAYER)
    }

    /** How this mode is played, tab by tab. */
    fun howToPlay(lg: League): List<Pair<String, String>> = when {
        lg.playerMode -> listOf(
            "Today" to "What's happening: your next game, messages, choices off the court, and how you're doing.",
            "Team" to "Your team's roster, schedule and program.",
            "Me" to "Your player: ratings, card, contract or NIL deal, growth, and the award races.",
            "League" to "Standings, polls, leaders and the postseason.",
            "Career" to "Your career so far, trophies, and settings.",
            "Playing" to "Play takes the court yourself; Sim skips to the box score. The menu next to them sims a week or more.",
        )
        lg.pro -> listOf(
            "Today" to "Your next game, the owner's mood, press conferences and anything waiting on you.",
            "Team" to "Roster, lineup and game plan, payroll and contracts, the business side, and your staff.",
            "Trades" to "Build a trade with any team, see who fits your needs, and answer offers.",
            "League" to "Standings, the playoff picture, the NBA Cup, leaders, awards and history.",
            "Career" to "Your record, trophies, coaching skills, and settings.",
            "Playing" to "Play coaches the game live; Sim day and Next game skip ahead. The menu next to them sims to the deadline, the All-Star break or the playoffs.",
        )
        else -> listOf(
            "Today" to "Your next game, the season so far, news, and anything that needs you.",
            "Team" to "Roster, lineup and game plan, player development, and your program (prestige, fans, money).",
            "Recruit" to "Next year's class: scout prospects, pick targets, and the transfer portal.",
            "League" to "The polls, standings, leaders, Bubble Watch, the postseason and history.",
            "Career" to "Your record, job security, trophies, coaching skills, and settings.",
            "Playing" to "Play coaches the game live; Sim skips to the result. The menu next to them sims a week, to Selection Sunday, or the rest of the season.",
        )
    }

    fun search(q: String): List<HelpTerm> {
        val s = q.trim().lowercase()
        if (s.isEmpty()) return TERMS
        return TERMS.filter { t -> t.term.lowercase().contains(s) || t.also.any { it.contains(s) } || t.text.lowercase().contains(s) }
            .sortedBy { t -> if (t.term.lowercase().contains(s) || t.also.any { it.contains(s) }) 0 else 1 }
    }
}

/** A round icon button for a screen header (the calendar, help). */
@Composable
fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(P.surface).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = P.sub, modifier = Modifier.size(21.dp)) }
}

/** The round ? on Today's header: opens Help. */
@Composable
fun HelpButton(vm: GameViewModel) = HeaderIcon(MenuIcons.Help, "Help") { vm.push(Route.Help("")) }

/** How this mode is played, and every term in the game, searchable. [focus] opens with that term searched. */
@Composable
fun HelpScreen(vm: GameViewModel, lg: League, focus: String) {
    var query by remember(focus) { mutableStateOf(focus) }
    val found = Help.search(query)
    val searching = query.isNotBlank()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Help", "How to play · what every term means", onBack = { vm.back() })
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(40) }, singleLine = true,
                label = { Text("Search a term (NET, Prestige, cap...)") }, colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
            )
        }
        if (!searching) item {
            Section("How to play")
            Panel {
                Help.howToPlay(lg).forEachIndexed { i, (k, v) ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Text(k, style = T.bodyB, color = P.accent)
                    Text(v, style = T.small, color = P.sub)
                }
            }
        }
        if (searching && found.isEmpty()) item {
            Spacer(Modifier.height(20.dp))
            Empty("Nothing matches \"${query.trim()}\". Try a shorter word.")
        }
        // A search reads as one list, best match first; otherwise the glossary goes section by section.
        if (searching && found.isNotEmpty()) item {
            Section("${found.size} ${if (found.size == 1) "match" else "matches"}")
            GroupList(found) { t ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
                    Text(t.group.uppercase(), style = T.label.copy(fontSize = 9.sp), color = P.faint)
                    Text(t.term, style = T.bodyB, color = P.ink)
                    Text(t.text, style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (!searching) for (g in Help.groups(lg)) {
            val terms = found.filter { it.group == g }
            if (terms.isEmpty()) continue
            item {
                Section(g)
                GroupList(terms) { t ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
                        Text(t.term, style = T.bodyB, color = P.ink)
                        Text(t.text, style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
