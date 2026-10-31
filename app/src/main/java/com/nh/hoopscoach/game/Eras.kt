package com.nh.hoopscoach.game

/**
 * Era mode: start the pro career in a past season. Each era brings its stars on their real teams
 * (generated players fill out the rosters), its franchise names, how the game was played (pace,
 * three-point volume, physical defense) and its rules and money. The era stays fixed for the career.
 *
 * Ratings are on the game's pro scale and are estimates. Franchises use today's teams; a club that
 * didn't exist yet starts as an expansion team of generated players.
 */
enum class Era(
    val year: Int, val label: String, val title: String, val blurb: String,
    /** The salary cap that season, in thousands. */
    val cap: Int,
    /** Possession length (above 1 is slower), three-point volume, two-point shooting, and fouls. */
    val pace: Double, val threes: Double, val fg: Double, val fouls: Double,
) {
    TODAY(0, "Today", "Today's league", "The real teams and players of today, threes everywhere, the NBA Cup and the play-in.",
        154_647, 1.0, 1.0, 0.0, 1.0),
    Y2013(2013, "2013", "LeBron's Heat", "LeBron, Wade and Bosh chase a repeat. Durant and Westbrook in OKC, Lob City, Curry's first big year. The three is taking over.",
        58_044, 1.08, 0.44, -0.035, 0.95),
    Y2003(2003, "2003", "Shaq, Kobe and Duncan", "The Lakers' three-peat ends and Duncan wins again. McGrady, Iverson, Garnett, Kidd's Nets. Hand-checking and grind-it-out defense.",
        40_271, 1.09, 0.31, -0.065, 1.0),
    Y1996(1996, "1996", "Jordan's Bulls", "Jordan, Pippen and Rodman go 72-10. Shaq and Penny, Payton and Kemp, Malone and Stockton, Hakeem. A shorter three-point line.",
        23_000, 1.09, 0.34, -0.045, 1.0),
    Y1986(1986, "1986", "Bird and Magic", "Bird's Celtics, Magic and Kareem's Lakers, a young Jordan, Hakeem and Barkley. Fast breaks, post-ups, and hardly any threes.",
        4_945, 0.96, 0.07, -0.05, 1.0);

    /** The play-in and the NBA Cup are today's; first rounds were best of five until 2003. */
    val playIn get() = this == TODAY
    val cup get() = this == TODAY
    val firstRoundBestOf get() = if (year in 1..2002) 5 else 7
    /** The 65-game award rule (2023) and the supermax (2017) are recent. */
    val gamesRule get() = this == TODAY
    val supermax get() = this == TODAY
    /** The rookie game started in 1994. */
    val risingStars get() = year == 0 || year >= 1994
    /** Salaries and endorsements scale with the cap of the era. */
    val money get() = cap / ProCareer.CAP.toDouble()
}

object Eras {
    class Star(val name: String, val pos: Pos, val age: Int, val ovr: Int)
    class Club(val city: String, val name: String, val abbr: String, val primary: Long, val secondary: Long)

    /** The league's era (today for older saves and the college game). */
    fun of(lg: League): Era = lg.proState?.era?.let { e -> Era.entries.firstOrNull { it.name == e } } ?: Era.TODAY

    private val NJN = Club("New Jersey", "Nets", "NJN", 0xFF002A60, 0xFFCD1041)
    private val SEA = Club("Seattle", "SuperSonics", "SEA", 0xFF00653A, 0xFFFFC200)
    private val WSB = Club("Washington", "Bullets", "WSB", 0xFF002B5C, 0xFFE31837)
    private val VAN = Club("Vancouver", "Grizzlies", "VAN", 0xFF00B2A9, 0xFFE43C40)
    private val NOH = Club("New Orleans", "Hornets", "NOH", 0xFF00778B, 0xFF280071)
    private val BOBCATS = Club("Charlotte", "Bobcats", "CHA", 0xFFF26532, 0xFF29588B)

    /** Old names, by today's abbreviation. */
    fun club(era: Era, abbr: String): Club? = when (era) {
        Era.Y1986 -> mapOf("BKN" to NJN, "OKC" to SEA, "WAS" to WSB)
        Era.Y1996 -> mapOf("BKN" to NJN, "OKC" to SEA, "WAS" to WSB, "MEM" to VAN)
        Era.Y2003 -> mapOf("BKN" to NJN, "OKC" to SEA, "NOP" to NOH)
        Era.Y2013 -> mapOf("NOP" to NOH, "CHA" to BOBCATS)
        Era.TODAY -> emptyMap()
    }[abbr]

    /** Franchises that didn't exist yet: expansion clubs of generated players. */
    fun expansion(era: Era, abbr: String) = abbr in when (era) {
        Era.Y1986 -> setOf("CHA", "MEM", "MIA", "MIN", "NOP", "ORL", "TOR")
        Era.Y1996 -> setOf("NOP")
        Era.Y2003 -> setOf("CHA")
        else -> emptySet()
    }

    /** The era's stars by today's team abbreviation. */
    fun stars(era: Era): Map<String, List<Star>> = (RAW[era] ?: emptyMap()).mapValues { (_, s) ->
        s.split(";").map { it.trim() }.filter { it.isNotEmpty() }.map { e ->
            val (n, p, a, o) = e.split("/").map { it.trim() }
            Star(n, Pos.valueOf(p), a.toInt(), o.toInt())
        }
    }

    /** The headline names, for the picker. */
    fun headliners(era: Era, n: Int = 6): List<String> =
        stars(era).values.flatten().sortedByDescending { it.ovr }.take(n).map { it.name }

    private val RAW: Map<Era, Map<String, String>> = mapOf(
        Era.Y1986 to mapOf(
            "ATL" to "Dominique Wilkins/SF/26/95; Doc Rivers/PG/24/86; Kevin Willis/PF/23/84; Randy Wittman/SG/26/81",
            "BOS" to "Larry Bird/SF/29/99; Kevin McHale/PF/28/94; Robert Parish/C/32/91; Dennis Johnson/PG/31/87; Bill Walton/C/33/84; Danny Ainge/SG/26/84",
            "BKN" to "Buck Williams/PF/26/88; Micheal Ray Richardson/PG/30/86; Darryl Dawkins/C/28/83; Otis Birdsong/SG/30/83",
            "CHI" to "Michael Jordan/SG/23/96; Orlando Woolridge/SF/26/86; Charles Oakley/PF/22/83; Sidney Green/PF/25/80",
            "CLE" to "World B. Free/SG/32/86; Roy Hinson/PF/25/83; Phil Hubbard/PF/29/80",
            "DAL" to "Mark Aguirre/SF/26/91; Rolando Blackman/SG/27/89; Derek Harper/PG/24/85; Sam Perkins/PF/24/84",
            "DEN" to "Alex English/SF/32/92; Fat Lever/PG/25/87; Calvin Natt/SF/29/84; Wayne Cooper/C/29/81",
            "DET" to "Isiah Thomas/PG/24/95; Bill Laimbeer/C/28/88; Kelly Tripucka/SF/26/85; Joe Dumars/SG/22/84; Vinnie Johnson/SG/29/84",
            "GSW" to "Purvis Short/SF/28/87; Joe Barry Carroll/C/27/85; Chris Mullin/SG/22/82; Eric Floyd/PG/25/84",
            "HOU" to "Hakeem Olajuwon/C/23/96; Ralph Sampson/PF/25/91; Rodney McCray/SF/24/84; Lewis Lloyd/SG/27/82; Robert Reid/SG/30/81",
            "IND" to "Herb Williams/PF/27/84; Clark Kellogg/SF/24/83; Vern Fleming/PG/24/82; Wayman Tisdale/PF/21/82",
            "LAC" to "Marques Johnson/SF/29/87; Derek Smith/SG/24/85; Norm Nixon/PG/30/84; Benoit Benjamin/C/21/80",
            "LAL" to "Magic Johnson/PG/26/98; Kareem Abdul-Jabbar/C/38/92; James Worthy/SF/24/91; Byron Scott/SG/24/85; Michael Cooper/SG/30/83; Kurt Rambis/PF/27/80",
            "MIL" to "Sidney Moncrief/SG/28/92; Terry Cummings/PF/24/89; Paul Pressey/SF/27/85; Ricky Pierce/SG/26/83",
            "NYK" to "Patrick Ewing/C/23/92; Bernard King/SF/29/88; Bill Cartwright/C/28/82; Rory Sparrow/PG/27/80",
            "OKC" to "Tom Chambers/PF/26/88; Jack Sikma/C/30/86; Xavier McDaniel/SF/22/85; Gerald Henderson/PG/30/81",
            "PHI" to "Charles Barkley/PF/22/93; Moses Malone/C/30/92; Julius Erving/SF/35/88; Maurice Cheeks/PG/29/87",
            "PHX" to "Walter Davis/SG/31/88; Larry Nance/PF/26/88; James Edwards/C/30/83; Jay Humphries/PG/23/80",
            "POR" to "Clyde Drexler/SG/23/91; Kiki Vandeweghe/SF/27/89; Jim Paxson/SG/28/84; Terry Porter/PG/22/82; Sam Bowie/C/24/80",
            "SAC" to "Reggie Theus/PG/28/87; Eddie Johnson/SF/26/86; LaSalle Thompson/C/24/82; Otis Thorpe/PF/23/82",
            "SAS" to "Alvin Robertson/SG/23/88; Mike Mitchell/SF/30/86; Artis Gilmore/C/36/83; Johnny Moore/PG/27/82",
            "UTA" to "Adrian Dantley/SF/30/92; Karl Malone/PF/22/86; Mark Eaton/C/29/84; John Stockton/PG/23/83; Thurl Bailey/PF/25/82",
            "WAS" to "Jeff Malone/SG/24/86; Jeff Ruland/C/27/85; Gus Williams/PG/32/84; Cliff Robinson/PF/25/82",
        ),
        Era.Y1996 to mapOf(
            "ATL" to "Steve Smith/SG/26/88; Mookie Blaylock/PG/28/87; Christian Laettner/PF/26/85; Grant Long/PF/29/82",
            "BOS" to "Dino Radja/PF/28/85; Rick Fox/SF/26/83; Dana Barros/PG/28/82; Dee Brown/SG/27/82",
            "BKN" to "Jayson Williams/PF/27/84; Armon Gilliam/PF/31/83; Shawn Bradley/C/23/82; Chris Childs/PG/28/82",
            "CHA" to "Glen Rice/SF/28/90; Larry Johnson/PF/26/89; Kenny Anderson/PG/25/86; Dell Curry/SG/31/83",
            "CHI" to "Michael Jordan/SG/32/99; Scottie Pippen/SF/30/95; Dennis Rodman/PF/34/88; Toni Kukoc/SF/27/86; Ron Harper/PG/32/82; Luc Longley/C/27/80",
            "CLE" to "Terrell Brandon/PG/25/88; Tyrone Hill/PF/27/83; Chris Mills/SF/26/82; Bobby Phills/SG/26/82",
            "DAL" to "Jason Kidd/PG/22/89; Jim Jackson/SG/25/87; Jamal Mashburn/SF/23/86; George McCloud/SF/28/82",
            "DEN" to "Dikembe Mutombo/C/29/89; Mahmoud Abdul-Rauf/PG/26/85; Antonio McDyess/PF/21/84; LaPhonso Ellis/PF/26/82",
            "DET" to "Grant Hill/SF/23/93; Allan Houston/SG/25/85; Joe Dumars/SG/32/84; Otis Thorpe/PF/33/83",
            "GSW" to "Latrell Sprewell/SG/25/88; Chris Mullin/SF/32/85; B.J. Armstrong/PG/28/82; Joe Smith/PF/20/82",
            "HOU" to "Hakeem Olajuwon/C/33/97; Clyde Drexler/SG/33/90; Robert Horry/PF/25/84; Sam Cassell/PG/26/84; Mario Elie/SF/32/81",
            "IND" to "Reggie Miller/SG/30/91; Rik Smits/C/29/86; Mark Jackson/PG/30/84; Dale Davis/PF/26/83; Derrick McKey/SF/29/82",
            "LAC" to "Loy Vaught/PF/27/85; Brian Williams/C/26/82; Pooh Richardson/PG/29/81; Terry Dehere/SG/24/80",
            "LAL" to "Magic Johnson/PG/36/86; Cedric Ceballos/SF/26/86; Nick Van Exel/PG/24/85; Vlade Divac/C/28/85; Eddie Jones/SG/24/84",
            "MEM" to "Bryant Reeves/C/22/82; Greg Anthony/PG/28/81; Blue Edwards/SF/30/80",
            "MIA" to "Alonzo Mourning/C/26/92; Tim Hardaway/PG/29/87; Rex Chapman/SG/28/82; Kurt Thomas/PF/23/80",
            "MIL" to "Vin Baker/PF/24/89; Glenn Robinson/SF/23/88; Sherman Douglas/PG/29/82; Johnny Newman/SF/32/80",
            "MIN" to "Tom Gugliotta/PF/26/86; Isaiah Rider/SG/24/85; Kevin Garnett/PF/19/83; Terry Porter/PG/32/80",
            "NYK" to "Patrick Ewing/C/33/92; Anthony Mason/PF/29/85; John Starks/SG/30/84; Charles Oakley/PF/32/84; Derek Harper/PG/34/82",
            "OKC" to "Gary Payton/PG/27/94; Shawn Kemp/PF/26/93; Detlef Schrempf/SF/33/87; Hersey Hawkins/SG/30/84",
            "ORL" to "Shaquille O'Neal/C/23/97; Penny Hardaway/PG/24/94; Horace Grant/PF/30/85; Dennis Scott/SF/27/84; Nick Anderson/SG/28/84",
            "PHI" to "Jerry Stackhouse/SG/21/84; Clarence Weatherspoon/PF/25/84; Vernon Maxwell/SG/30/81",
            "PHX" to "Charles Barkley/PF/32/94; Kevin Johnson/PG/30/89; Danny Manning/PF/29/85; Michael Finley/SF/22/84",
            "POR" to "Rod Strickland/PG/29/88; Clifford Robinson/PF/29/86; Arvydas Sabonis/C/31/86; Aaron McKie/SG/23/80",
            "SAC" to "Mitch Richmond/SG/30/92; Brian Grant/PF/23/84; Olden Polynice/C/31/82; Tyus Edney/PG/22/80",
            "SAS" to "David Robinson/C/30/97; Sean Elliott/SF/27/88; Avery Johnson/PG/30/84; Vinny Del Negro/SG/29/82; Chuck Person/SF/31/81",
            "TOR" to "Damon Stoudamire/PG/22/85; Tracy Murray/SF/24/81; Oliver Miller/C/25/80",
            "UTA" to "Karl Malone/PF/32/97; John Stockton/PG/33/93; Jeff Hornacek/SG/32/86; Bryon Russell/SF/25/81",
            "WAS" to "Juwan Howard/PF/22/88; Chris Webber/PF/22/88; Gheorghe Muresan/C/25/84; Rasheed Wallace/PF/21/82",
        ),
        Era.Y2003 to mapOf(
            "ATL" to "Shareef Abdur-Rahim/PF/26/89; Jason Terry/PG/25/86; Glenn Robinson/SF/30/85",
            "BOS" to "Paul Pierce/SF/25/94; Antoine Walker/PF/26/90; Tony Delk/SG/29/81; Eric Williams/SF/30/81",
            "BKN" to "Jason Kidd/PG/29/95; Kenyon Martin/PF/25/86; Richard Jefferson/SF/22/84; Kerry Kittles/SG/28/83",
            "CHI" to "Jalen Rose/SF/30/87; Jamal Crawford/SG/22/82; Eddy Curry/C/20/82; Tyson Chandler/C/20/81",
            "CLE" to "Zydrunas Ilgauskas/C/27/86; Ricky Davis/SG/23/85; Carlos Boozer/PF/21/82; Darius Miles/SF/21/81",
            "DAL" to "Dirk Nowitzki/PF/24/95; Steve Nash/PG/29/91; Michael Finley/SG/30/87; Nick Van Exel/PG/31/83; Raef LaFrentz/C/26/81",
            "DEN" to "Juwan Howard/PF/29/83; Marcus Camby/C/29/82; Nene/PF/20/81",
            "DET" to "Ben Wallace/C/28/89; Richard Hamilton/SG/25/87; Chauncey Billups/PG/26/87; Tayshaun Prince/SF/22/81",
            "GSW" to "Antawn Jamison/SF/26/87; Gilbert Arenas/PG/21/86; Jason Richardson/SG/22/85; Troy Murphy/PF/22/81",
            "HOU" to "Steve Francis/PG/26/90; Yao Ming/C/22/87; Cuttino Mobley/SG/27/85; Kelvin Cato/C/28/80",
            "IND" to "Jermaine O'Neal/PF/24/92; Ron Artest/SF/23/87; Brad Miller/C/27/85; Reggie Miller/SG/37/84; Jamaal Tinsley/PG/24/81",
            "LAC" to "Elton Brand/PF/24/90; Lamar Odom/SF/23/86; Andre Miller/PG/27/85; Corey Maggette/SF/23/85",
            "LAL" to "Shaquille O'Neal/C/31/98; Kobe Bryant/SG/24/97; Derek Fisher/PG/28/81; Robert Horry/PF/32/81; Rick Fox/SF/33/80",
            "MEM" to "Pau Gasol/PF/22/88; Jason Williams/PG/27/84; Shane Battier/SF/24/83; Mike Miller/SF/22/83",
            "MIA" to "Eddie Jones/SG/31/86; Caron Butler/SF/22/84; Brian Grant/PF/30/82; Travis Best/PG/30/80",
            "MIL" to "Gary Payton/PG/34/87; Sam Cassell/PG/33/86; Michael Redd/SG/23/85; Tim Thomas/SF/25/83",
            "MIN" to "Kevin Garnett/PF/26/98; Wally Szczerbiak/SF/25/86; Troy Hudson/PG/26/82; Rasho Nesterovic/C/26/81",
            "NOP" to "Baron Davis/PG/23/90; Jamal Mashburn/SF/30/88; P.J. Brown/PF/33/83; David Wesley/SG/32/82",
            "NYK" to "Allan Houston/SG/31/87; Latrell Sprewell/SF/32/86; Kurt Thomas/PF/30/81; Howard Eisley/PG/30/79",
            "OKC" to "Ray Allen/SG/27/92; Rashard Lewis/SF/23/87; Brent Barry/SG/31/83; Vladimir Radmanovic/PF/22/80",
            "ORL" to "Tracy McGrady/SG/23/98; Grant Hill/SF/30/85; Drew Gooden/PF/21/82; Darrell Armstrong/PG/34/81",
            "PHI" to "Allen Iverson/SG/27/95; Eric Snow/PG/29/83; Kenny Thomas/PF/25/82; Aaron McKie/SG/30/81",
            "PHX" to "Stephon Marbury/PG/25/90; Shawn Marion/SF/24/90; Amar'e Stoudemire/PF/20/86; Penny Hardaway/SG/31/82",
            "POR" to "Rasheed Wallace/PF/28/89; Bonzi Wells/SG/26/85; Damon Stoudamire/PG/29/82; Scottie Pippen/SF/37/82; Zach Randolph/PF/21/82",
            "SAC" to "Chris Webber/PF/30/93; Peja Stojakovic/SF/25/90; Mike Bibby/PG/24/88; Vlade Divac/C/34/83; Bobby Jackson/PG/29/83",
            "SAS" to "Tim Duncan/PF/26/99; Tony Parker/PG/20/85; Manu Ginobili/SG/25/84; David Robinson/C/37/84; Stephen Jackson/SF/24/82",
            "TOR" to "Vince Carter/SG/26/92; Morris Peterson/SF/25/82; Antonio Davis/C/34/82; Alvin Williams/PG/28/81",
            "UTA" to "Karl Malone/PF/39/89; Andrei Kirilenko/SF/21/85; John Stockton/PG/41/86; Matt Harpring/SF/26/84",
            "WAS" to "Jerry Stackhouse/SG/28/87; Michael Jordan/SG/40/88; Larry Hughes/SG/24/83; Christian Laettner/PF/33/80",
        ),
        Era.Y2013 to mapOf(
            "ATL" to "Josh Smith/PF/27/88; Al Horford/C/26/88; Jeff Teague/PG/24/84; Kyle Korver/SF/31/82",
            "BOS" to "Rajon Rondo/PG/26/89; Paul Pierce/SF/35/88; Kevin Garnett/PF/36/88; Jeff Green/SF/26/82",
            "BKN" to "Deron Williams/PG/28/89; Brook Lopez/C/24/88; Joe Johnson/SG/31/87; Gerald Wallace/SF/30/82",
            "CHA" to "Kemba Walker/PG/22/86; Gerald Henderson/SG/25/82; Michael Kidd-Gilchrist/SF/19/81",
            "CHI" to "Derrick Rose/PG/24/90; Joakim Noah/C/28/89; Luol Deng/SF/27/87; Carlos Boozer/PF/31/85; Jimmy Butler/SF/23/82",
            "CLE" to "Kyrie Irving/PG/20/90; Anderson Varejao/C/30/84; Tristan Thompson/PF/21/82; Dion Waiters/SG/21/82",
            "DAL" to "Dirk Nowitzki/PF/34/90; O.J. Mayo/SG/25/84; Shawn Marion/SF/34/83; Vince Carter/SG/36/82",
            "DEN" to "Ty Lawson/PG/25/87; Andre Iguodala/SF/29/87; Danilo Gallinari/SF/24/85; Kenneth Faried/PF/23/84",
            "DET" to "Greg Monroe/C/22/86; Brandon Knight/PG/21/82; Andre Drummond/C/19/82",
            "GSW" to "Stephen Curry/PG/24/93; David Lee/PF/29/87; Klay Thompson/SG/22/86; Andrew Bogut/C/28/84; Harrison Barnes/SF/20/81",
            "HOU" to "James Harden/SG/23/94; Chandler Parsons/SF/24/84; Jeremy Lin/PG/24/84; Omer Asik/C/26/83",
            "IND" to "Paul George/SF/22/91; David West/PF/32/87; Roy Hibbert/C/26/86; George Hill/PG/26/84; Lance Stephenson/SG/22/82",
            "LAC" to "Chris Paul/PG/27/96; Blake Griffin/PF/23/92; Jamal Crawford/SG/32/85; DeAndre Jordan/C/24/85",
            "LAL" to "Kobe Bryant/SG/34/95; Dwight Howard/C/27/91; Pau Gasol/PF/32/86; Steve Nash/PG/39/86",
            "MEM" to "Marc Gasol/C/28/91; Zach Randolph/PF/31/89; Mike Conley/PG/25/87; Tony Allen/SG/31/82",
            "MIA" to "LeBron James/SF/28/99; Dwyane Wade/SG/31/93; Chris Bosh/PF/28/90; Ray Allen/SG/37/84; Shane Battier/SF/34/81",
            "MIL" to "Monta Ellis/SG/27/86; Brandon Jennings/PG/23/85; Larry Sanders/C/24/83; Ersan Ilyasova/PF/25/82",
            "MIN" to "Kevin Love/PF/24/90; Nikola Pekovic/C/27/85; Ricky Rubio/PG/22/84; Andrei Kirilenko/SF/31/82",
            "NOP" to "Anthony Davis/PF/19/86; Eric Gordon/SG/24/84; Ryan Anderson/PF/24/84; Greivis Vasquez/PG/26/82",
            "NYK" to "Carmelo Anthony/SF/28/95; Tyson Chandler/C/30/87; J.R. Smith/SG/27/85; Amar'e Stoudemire/PF/30/83; Raymond Felton/PG/28/82",
            "OKC" to "Kevin Durant/SF/24/98; Russell Westbrook/PG/24/94; Serge Ibaka/PF/23/87; Kevin Martin/SG/30/83",
            "ORL" to "Arron Afflalo/SG/27/84; Nikola Vucevic/C/22/84; J.J. Redick/SG/28/82; Glen Davis/PF/27/80",
            "PHI" to "Jrue Holiday/PG/22/87; Thaddeus Young/PF/24/84; Evan Turner/SF/24/82; Spencer Hawes/C/24/81",
            "PHX" to "Goran Dragic/PG/26/85; Marcin Gortat/C/28/84; Jared Dudley/SF/27/81; Michael Beasley/SF/24/80",
            "POR" to "LaMarcus Aldridge/PF/27/90; Damian Lillard/PG/22/87; Nicolas Batum/SF/24/85; Wesley Matthews/SG/26/84",
            "SAC" to "DeMarcus Cousins/C/22/89; Isaiah Thomas/PG/24/84; Tyreke Evans/SG/23/84; Jason Thompson/PF/26/80",
            "SAS" to "Tim Duncan/PF/36/92; Tony Parker/PG/30/92; Kawhi Leonard/SF/21/86; Manu Ginobili/SG/35/85; Danny Green/SG/25/82",
            "TOR" to "Rudy Gay/SF/26/86; DeMar DeRozan/SG/23/86; Kyle Lowry/PG/26/85; Jonas Valanciunas/C/20/82",
            "UTA" to "Al Jefferson/C/28/87; Paul Millsap/PF/27/87; Gordon Hayward/SF/22/85; Derrick Favors/PF/21/82",
            "WAS" to "John Wall/PG/22/88; Bradley Beal/SG/19/83; Nene/PF/30/83; Emeka Okafor/C/30/82",
        ),
    )
}
