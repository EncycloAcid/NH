package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The hometown kid: when the user's player suits up for the pro team from his own city (his
 * hometown, "City, ST", inside the team's metro area). The city makes a fuss when he comes home,
 * the home crowd lifts his shot, he's happier, his jersey sells, the hometown team pays a little
 * more to get him, and the shows ask if he can bring a title home. Leaving is news too.
 */
object Hometown {
    /** Each pro city (as teams are named) and the towns that count as home, with their state. */
    private val METROS: Map<String, List<String>> = mapOf(
        "atlanta" to "Atlanta GA, Decatur GA, Marietta GA, College Park GA, East Point GA, Stone Mountain GA, Smyrna GA, Lithonia GA, Sandy Springs GA",
        "boston" to "Boston MA, Cambridge MA, Dorchester MA, Roxbury MA, Brockton MA, Lynn MA, Quincy MA, Somerville MA, Medford MA, Newton MA",
        "brooklyn" to "Brooklyn NY, New York NY, Queens NY, Bronx NY, Harlem NY, Staten Island NY, Coney Island NY",
        "new york" to "New York NY, Brooklyn NY, Queens NY, Bronx NY, Harlem NY, Staten Island NY, Yonkers NY, Mount Vernon NY, New Rochelle NY",
        "new jersey" to "Newark NJ, Jersey City NJ, East Rutherford NJ, Paterson NJ, Elizabeth NJ, East Orange NJ, Trenton NJ, Camden NJ",
        "charlotte" to "Charlotte NC, Gastonia NC, Concord NC, Rock Hill SC",
        "chicago" to "Chicago IL, Evanston IL, Oak Park IL, Cicero IL, Harvey IL, Maywood IL, Joliet IL, Aurora IL, Gary IN",
        "cleveland" to "Cleveland OH, Akron OH, Lakewood OH, Euclid OH, Parma OH, Shaker Heights OH, East Cleveland OH, Lorain OH",
        "dallas" to "Dallas TX, Fort Worth TX, Arlington TX, Plano TX, Irving TX, Garland TX, Mesquite TX, Grand Prairie TX, DeSoto TX, Duncanville TX",
        "denver" to "Denver CO, Aurora CO, Lakewood CO, Boulder CO, Thornton CO, Westminster CO, Littleton CO",
        "detroit" to "Detroit MI, Flint MI, Pontiac MI, Southfield MI, Dearborn MI, Inkster MI, Highland Park MI, Saginaw MI",
        "golden state" to "San Francisco CA, Oakland CA, Berkeley CA, Richmond CA, Vallejo CA, San Jose CA, Hayward CA, Alameda CA, Daly City CA",
        "san francisco" to "San Francisco CA, Oakland CA, Berkeley CA, Daly City CA",
        "houston" to "Houston TX, Pasadena TX, Sugar Land TX, Missouri City TX, Pearland TX, Baytown TX, Galveston TX",
        "indiana" to "Indianapolis IN, Carmel IN, Fishers IN, Lawrence IN, Anderson IN, Muncie IN",
        "la" to "Los Angeles CA, Long Beach CA, Inglewood CA, Compton CA, Pasadena CA, Santa Monica CA, Glendale CA, Torrance CA, Carson CA, Anaheim CA, Santa Ana CA, Watts CA",
        "los angeles" to "Los Angeles CA, Long Beach CA, Inglewood CA, Compton CA, Pasadena CA, Santa Monica CA, Glendale CA, Torrance CA, Carson CA, Anaheim CA, Santa Ana CA, Watts CA",
        "memphis" to "Memphis TN, Germantown TN, Bartlett TN, Collierville TN, Southaven MS",
        "miami" to "Miami FL, Miami Gardens FL, Hialeah FL, Fort Lauderdale FL, Hollywood FL, Miami Beach FL, Opa-locka FL, Homestead FL",
        "milwaukee" to "Milwaukee WI, Racine WI, Waukesha WI, Wauwatosa WI, Kenosha WI",
        "minnesota" to "Minneapolis MN, St. Paul MN, Saint Paul MN, Bloomington MN, Brooklyn Park MN, Hopkins MN",
        "new orleans" to "New Orleans LA, Metairie LA, Kenner LA, Slidell LA, Marrero LA",
        "oklahoma city" to "Oklahoma City OK, Norman OK, Edmond OK, Midwest City OK, Moore OK",
        "orlando" to "Orlando FL, Kissimmee FL, Sanford FL, Winter Park FL, Apopka FL",
        "philadelphia" to "Philadelphia PA, Chester PA, Camden NJ, Norristown PA, Upper Darby PA",
        "phoenix" to "Phoenix AZ, Mesa AZ, Tempe AZ, Scottsdale AZ, Glendale AZ, Chandler AZ, Gilbert AZ",
        "portland" to "Portland OR, Gresham OR, Beaverton OR, Hillsboro OR, Vancouver WA",
        "sacramento" to "Sacramento CA, Elk Grove CA, Roseville CA, Davis CA, Stockton CA",
        "san antonio" to "San Antonio TX, New Braunfels TX, Converse TX, Schertz TX",
        "toronto" to "Toronto ON, Mississauga ON, Brampton ON, Scarborough ON",
        "utah" to "Salt Lake City UT, Provo UT, Ogden UT, West Valley City UT, Sandy UT, Orem UT",
        "washington" to "Washington DC, Silver Spring MD, Hyattsville MD, Bowie MD, Alexandria VA, Arlington VA, Landover MD",
        "seattle" to "Seattle WA, Tacoma WA, Renton WA, Kent WA, Bellevue WA, Everett WA",
        "vancouver" to "Vancouver BC, Burnaby BC, Surrey BC",
        "kansas city" to "Kansas City MO, Kansas City KS, Independence MO, Overland Park KS",
        "buffalo" to "Buffalo NY, Niagara Falls NY",
        "san diego" to "San Diego CA, Chula Vista CA, Oceanside CA",
        "cincinnati" to "Cincinnati OH, Covington KY",
        "st. louis" to "St. Louis MO, East St. Louis IL",
        "las vegas" to "Las Vegas NV, North Las Vegas NV, Henderson NV",
        "louisville" to "Louisville KY",
        "nashville" to "Nashville TN, Murfreesboro TN",
        "baltimore" to "Baltimore MD, Towson MD",
        "pittsburgh" to "Pittsburgh PA, McKeesport PA",
        "tampa bay" to "Tampa FL, St. Petersburg FL, Clearwater FL",
        "austin" to "Austin TX, Round Rock TX",
        "mexico city" to "Mexico City MX",
    ).mapValues { (_, v) -> v.split(", ").map { it.trim() } }

    private fun norm(s: String) = s.lowercase().replace(".", "").replace("saint ", "st ").trim()

    /** "Chicago, IL" → ("chicago", "IL"). */
    private fun parts(hometown: String): Pair<String, String> =
        norm(hometown.substringBefore(",")) to hometown.substringAfterLast(",", "").trim().uppercase()

    /** Whether [t] is the pro team of [hometown]'s city. */
    fun isHome(t: Team, hometown: String): Boolean {
        if (hometown.isBlank()) return false
        val (city, state) = parts(hometown)
        val towns = METROS[norm(t.school)]
        if (towns == null) return city == norm(t.school)
        return towns.any { e ->
            val town = norm(e.substringBeforeLast(' '))
            val st = e.substringAfterLast(' ')
            town == city && (state.isEmpty() || state == st)
        }
    }

    /** The user's player is playing for his hometown team right now. */
    fun active(lg: League): Boolean {
        if (!lg.pro || !lg.playerMode) return false
        val h = lg.hero ?: return false
        val t = lg.teams.getOrNull(h.teamId) ?: return false
        return isHome(t, h.hometown)
    }

    /** "Chicago": his hometown's name, as people say it. */
    fun city(lg: League): String = lg.hero?.hometown?.substringBefore(",")?.trim().orEmpty()

    /** A little lift on his shots in his hometown team's building: the crowd he grew up in. */
    fun shotBonus(lg: League, g: Game): Double {
        if (!active(lg)) return 0.0
        val h = lg.hero ?: return 0.0
        if (g.home != h.teamId || g.neutral) return 0.0
        return if (g.type == GameType.PLAYOFF) 0.02 else 0.012
    }

    /**
     * Runs every day and at every offseason step: notices when he comes home (drafted, signed or
     * traded to his city's team) or leaves, and gives home games their buzz.
     */
    fun check(lg: League, day: Int = lg.day) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        if (!lg.pro || !lg.playerMode || r.ended) return
        val now = if (active(lg)) h.teamId else -1
        if (now != r.homeTeam) {
            if (now >= 0) homecoming(lg, lg.team(now))
            else if (r.homeTeam >= 0 && lg.teams.getOrNull(r.homeTeam) != null) leaving(lg, lg.team(r.homeTeam))
            r.homeTeam = now
        }
        if (now < 0) return
        // A home game in his own city: the following grows, and the city talks.
        val g = Season.gamesOn(lg, day).firstOrNull { it.played && it.home == now && h.log.any { l -> l.gameId == it.id } } ?: return
        r.followers += (r.followers * 0.003).roundToInt() + 400
        val line = h.log.last { it.gameId == g.id }
        if (line.pts >= 25 || (g.winner() == now && line.pts >= 15 && Rng.chance(0.2))) {
            val town = city(lg)
            val fan = Rng.pick(listOf("${town.lowercase().replace(" ", "")}born", "${town.lowercase().replace(" ", "")}hoops", "raisedin${town.lowercase().replace(" ", "")}", "the${town.lowercase().replace(" ", "")}kid"))
            val text = Rng.pick(
                listOf(
                    "${line.pts} for the kid from $town. I watched him play at the rec center. Now look.",
                    "One of ours. ${h.last} in front of the whole city tonight. ${line.pts} points.",
                    "$town raised him and $town showed up for him. ${line.pts} points.",
                    "You can hear the whole block when ${h.last} scores. That's a hometown kid.",
                ),
            )
            Media.post(lg, fan, fan, "fan", text, Media.likes(lg, 0.012), gameId = g.id)
        }
    }

    private fun homecoming(lg: League, t: Team) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        val town = city(lg)
        r.followers = (r.followers * 1.08).roundToInt() + 25_000
        r.homeSince = lg.season
        lg.news += News(lg.season, lg.day, "Hometown kid comes home: ${h.name} joins the ${t.name}", true, -1, "move",
            "${h.name} grew up in $town. Now he plays for its team. \"I used to sit in the upper deck and dream about this,\" he said. \"Now I get to play for the people I grew up with.\"",
            "Hoops Wire", t.id, h.id)
        Media.post(lg, t.name, t.abbr.lowercase(), "team", "Born in $town. Built in $town. Welcome home, ${h.last}.", Media.likes(lg, 0.06), verified = true)
        Alerts.add(lg, "Hometown kid", "You're playing for ${town}'s team. The crowd's behind you at home, your jersey will move, and you'll love it here.", "signing")
    }

    private fun leaving(lg: League, t: Team) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        val town = city(lg)
        val years = (lg.season - r.homeSince).coerceAtLeast(1)
        r.followers = (r.followers * 0.98).roundToInt()
        lg.news += News(lg.season, lg.day, "${h.name} leaves home", true, -1, "move",
            "After $years ${if (years == 1) "season" else "seasons"} playing for his hometown ${t.name}, ${h.name} moves on. \"$town will always be home,\" he said.",
            "Hoops Wire", t.id, h.id)
    }

    /** What the hometown adds to his mood: +8, and the reason. */
    fun moodLift(lg: League): Pair<Int, String>? = if (active(lg)) 8 to "Playing for your hometown" else null
}
