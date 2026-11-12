package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

enum class Pos {
    PG, SG, SF, PF, C;
    val label: String get() = name
}

/** The ten player attributes, stored in this order inside [Player.attrs]. */
enum class Attr(val short: String, val label: String) {
    INS("INS", "Inside"),
    MID("MID", "Mid-Range"),
    OUT("OUT", "3-Point"),
    HND("HND", "Handling"),
    PAS("PAS", "Passing"),
    REB("REB", "Rebound"),
    PER("PER", "Perimeter D"),
    IDF("INT", "Interior D"),
    STL("STL", "Stealing"),
    BLK("BLK", "Blocking");
}

enum class Arch(val label: String, val short: String, val boosts: List<Attr>, val blurb: String) {
    MANUAL("Manual Allocation", "MAN", emptyList(), "Bank level-ups and choose an archetype when you want to spend them."),
    SHARPSHOOTER("Sharpshooter", "SHP", listOf(Attr.INS, Attr.MID, Attr.OUT), "Elite shooter who stretches the floor from all three levels"),
    FLOOR_GENERAL("Floor General", "FLG", listOf(Attr.MID, Attr.HND, Attr.PAS), "Court commander who orchestrates the offense and controls tempo"),
    PLAYMAKER("Playmaker", "PLY", listOf(Attr.INS, Attr.HND, Attr.PAS), "Creative passer who finds open teammates and creates easy baskets"),
    SLASHER("Slasher", "SLA", listOf(Attr.INS, Attr.PER, Attr.STL), "Attacks the rim on offense and jumps the passing lanes on defense"),
    LOCKDOWN("Lockdown Defender", "LCK", listOf(Attr.PER, Attr.STL, Attr.BLK), "Suffocating defender who shuts down the opposing team's best player"),
    COMBO_GUARD("Combo Guard", "CMB", listOf(Attr.MID, Attr.OUT, Attr.HND), "Scores and creates off the dribble from either guard spot"),
    POINT_FORWARD("Point Forward", "PTF", listOf(Attr.HND, Attr.PAS, Attr.REB), "Big wing who runs the offense and pushes the ball off the glass"),
    STRETCH("Stretch", "STR", listOf(Attr.MID, Attr.OUT, Attr.REB), "Forward who spaces the floor and pulls bigs away from the rim"),
    POST_SCORER("Post Scorer", "PST", listOf(Attr.INS, Attr.MID, Attr.REB), "Back-to-the-basket scorer with soft touch around the rim"),
    REBOUNDER("Rebounder", "REB", listOf(Attr.REB, Attr.IDF, Attr.BLK), "Relentless on the glass and finishes second chances"),
    RIM_PROTECTOR("Rim Protector", "RIM", listOf(Attr.IDF, Attr.BLK, Attr.REB), "Anchors the defense and erases mistakes at the rim"),
    THREE_AND_D("3-and-D Wing", "3&D", listOf(Attr.OUT, Attr.PER, Attr.STL), "Knocks down open threes and guards the other team's best scorer"),
    SHOT_CREATOR("Shot Creator", "SCR", listOf(Attr.INS, Attr.MID, Attr.HND), "Breaks down defenders and gets his shot whenever he wants"),
    TABLE_SETTER("Table Setter", "TBL", listOf(Attr.HND, Attr.PAS, Attr.STL), "Pass-first point guard who pushes the pace and pressures the ball"),
    BALL_HAWK("Ball Hawk", "HWK", listOf(Attr.HND, Attr.PER, Attr.STL), "Lives in the passing lanes and turns steals into layups"),
    GLUE_GUY("Glue Guy", "GLU", listOf(Attr.PAS, Attr.REB, Attr.PER), "Makes the extra pass, grabs the tough rebound, takes the charge"),
    RIM_RUNNER("Rim Runner", "RUN", listOf(Attr.INS, Attr.REB, Attr.BLK), "Sprints the floor and finishes lobs and putbacks above the rim"),
    POINT_CENTER("Point Center", "PTC", listOf(Attr.INS, Attr.PAS, Attr.REB), "Big man who runs the offense from the post and the elbows"),
    STRETCH_FIVE("Stretch Five", "ST5", listOf(Attr.OUT, Attr.IDF, Attr.BLK), "Center who protects the rim and pulls opposing bigs out to the arc"),
    ENFORCER("Enforcer", "ENF", listOf(Attr.INS, Attr.IDF, Attr.REB), "Physical big who owns the paint on both ends"),
    TWO_WAY_GUARD("Two-Way Guard", "2WG", listOf(Attr.OUT, Attr.HND, Attr.PER), "Hits threes off the bounce and picks up the ball full court"),
    PEST("Pest", "PES", listOf(Attr.PAS, Attr.PER, Attr.STL), "Hounds the ball for 94 feet and makes the simple pass"),
    MICROWAVE("Microwave", "MIC", listOf(Attr.INS, Attr.OUT, Attr.HND), "Instant offense: gets to the rim or pulls up from deep the moment he checks in"),
    MOVEMENT_SHOOTER("Movement Shooter", "MOV", listOf(Attr.MID, Attr.OUT, Attr.PAS), "Never stops running off screens, and the defense bends to find him"),
    BULLY_GUARD("Bully Guard", "BLY", listOf(Attr.INS, Attr.REB, Attr.PER), "Big guard who backs down smaller defenders, crashes the glass and bodies up on defense"),
    HIGH_FLYER("High Flyer", "FLY", listOf(Attr.INS, Attr.PER, Attr.BLK), "Finishes above the rim and chases down blocks in transition"),
    FACE_UP_FOUR("Face-Up Four", "FUP", listOf(Attr.MID, Attr.HND, Attr.REB), "Faces up from the elbow, drives past slower bigs and cleans the glass"),
    SWITCHABLE_BIG("Switchable Big", "SWB", listOf(Attr.PER, Attr.IDF, Attr.BLK), "Big who can guard all five spots and still protect the rim"),
    PICK_AND_POP("Pick-and-Pop Big", "PNP", listOf(Attr.MID, Attr.OUT, Attr.IDF), "Sets the screen, pops to the arc and holds his own in the paint"),
    HUSTLE_BIG("Hustle Big", "HUS", listOf(Attr.REB, Attr.STL, Attr.BLK), "All energy: tips, deflections, loose balls and second chances"),
    LOGO_SHOOTER("Logo Shooter", "LGO", listOf(Attr.OUT, Attr.HND, Attr.PAS), "Pulls up from the logo and punishes every defender who goes under the screen"),
    ISO_WING("Iso Wing", "ISO", listOf(Attr.MID, Attr.HND, Attr.PER), "Wins one-on-one at both ends: the pull-up on offense, the lockdown on defense"),
    ATHLETIC_FOUR("Athletic Four", "ATH", listOf(Attr.INS, Attr.PER, Attr.REB), "Runs the floor, guards wings and owns the glass"),
    UNICORN("Unicorn", "UNI", listOf(Attr.OUT, Attr.HND, Attr.BLK), "A seven-footer who handles, shoots threes and blocks everything at the rim"),
    // Point guards
    SCORING_POINT("Scoring Point", "SPG", listOf(Attr.INS, Attr.MID, Attr.OUT), "Score-first point guard who hunts his own shot from every spot"),
    SPEEDSTER("Speedster", "SPD", listOf(Attr.INS, Attr.HND, Attr.STL), "A blur in the open floor who gets downhill before the defense is set"),
    PNR_MAESTRO("Pick-and-Roll Maestro", "PNR", listOf(Attr.MID, Attr.OUT, Attr.PAS), "Reads every pick-and-roll: the pocket pass, the pull-up, the skip to the corner"),
    POINT_OF_ATTACK("Point-of-Attack Defender", "POA", listOf(Attr.PER, Attr.STL, Attr.REB), "Picks up full court, fights over every screen and rebounds like a wing"),
    // Shooting guards
    SPOT_UP("Spot-Up Shooter", "SPU", listOf(Attr.MID, Attr.OUT, Attr.REB), "Waits on the wing, catches and fires, and crashes the long rebounds"),
    MIDRANGE_MAESTRO("Midrange Maestro", "MRM", listOf(Attr.MID, Attr.HND, Attr.PER), "Lives at the elbow with a pull-up nobody can stop, and guards his man on the other end"),
    DOWNHILL_GUARD("Downhill Guard", "DHG", listOf(Attr.INS, Attr.MID, Attr.PER), "Attacks closeouts, finishes in traffic and sits down on defense"),
    SPARK_PLUG("Spark Plug", "SPK", listOf(Attr.INS, Attr.OUT, Attr.PAS), "Instant energy off the bench: scores in bunches and finds the hot hand"),
    // Small forwards
    THREE_LEVEL("Three-Level Scorer", "3LV", listOf(Attr.INS, Attr.MID, Attr.OUT), "Scores at the rim, from the elbow and from deep"),
    POST_WING("Post Wing", "PWG", listOf(Attr.INS, Attr.MID, Attr.REB), "Backs down smaller wings and turns over either shoulder"),
    CORNER_SPECIALIST("Corner Specialist", "CRN", listOf(Attr.OUT, Attr.REB, Attr.PER), "Lives in the corner, guards the wing and boxes out"),
    TWO_WAY_WING("Two-Way Wing", "2WW", listOf(Attr.MID, Attr.OUT, Attr.PER), "Creates his own shot and still takes the toughest assignment"),
    // Power forwards
    LOW_POST_FOUR("Low-Post Four", "LPF", listOf(Attr.INS, Attr.REB, Attr.IDF), "Old-school power forward: drop steps, hook shots and boxing out"),
    PLAYMAKING_FOUR("Playmaking Four", "PMF", listOf(Attr.MID, Attr.HND, Attr.PAS), "Runs the offense from the top of the key and hits every cutter"),
    DUNKER_SPOT("Dunker Spot Big", "DNK", listOf(Attr.INS, Attr.REB, Attr.STL), "Lurks on the baseline for dump-offs, putbacks and deflections"),
    DEFENSIVE_FOUR("Defensive Anchor", "DAF", listOf(Attr.PER, Attr.IDF, Attr.STL), "Guards the other team's best forward and wins the hustle plays"),
    // Centers
    GLASS_CLEANER("Glass Cleaner", "GLS", listOf(Attr.REB, Attr.PAS, Attr.IDF), "Owns the defensive glass and starts the break with an outlet"),
    ELBOW_HUB("Elbow Hub", "ELB", listOf(Attr.MID, Attr.PAS, Attr.IDF), "Runs the offense from the elbow with a soft jumper and sharp passes"),
    MOBILE_BIG("Mobile Big", "MOB", listOf(Attr.PER, Attr.REB, Attr.BLK), "Switches onto guards, contests at the rim and runs the floor"),
    PAINT_BEAST("Paint Beast", "BST", listOf(Attr.INS, Attr.IDF, Attr.BLK), "Overpowers everyone inside on both ends of the floor"),
    // More point guards
    JUMBO_PLAYMAKER("Jumbo Playmaker", "JPM", listOf(Attr.INS, Attr.PAS, Attr.REB), "Big point guard who posts up smaller guards, grabs the rebound and leads the break"),
    PULL_UP_ARTIST("Pull-Up Artist", "PUA", listOf(Attr.MID, Attr.OUT, Attr.HND), "Rises off the dribble from anywhere: the step-back three, the pull-up jumper"),
    TWO_WAY_POINT("Two-Way Point", "2WP", listOf(Attr.OUT, Attr.PAS, Attr.PER), "Runs the offense, spaces the floor and takes the other team's point guard"),
    CRAFTY_FINISHER("Crafty Finisher", "CRF", listOf(Attr.INS, Attr.MID, Attr.PAS), "Floaters, scoop layups and dump-offs: wins in the paint without jumping over anyone"),
    // More shooting guards
    SECONDARY_CREATOR("Secondary Creator", "SEC", listOf(Attr.INS, Attr.HND, Attr.PAS), "Second ball handler who attacks a tilted defense and finds the open man"),
    PERIMETER_STOPPER("Perimeter Stopper", "STP", listOf(Attr.OUT, Attr.PER, Attr.STL), "Hounds the best guard all night and makes the open three"),
    CRASH_GUARD("Crash Guard", "CRG", listOf(Attr.INS, Attr.REB, Attr.STL), "Crashes the glass from the wing, jumps passing lanes and scores on the scraps"),
    OFF_BALL_CUTTER("Off-Ball Cutter", "CUT", listOf(Attr.INS, Attr.OUT, Attr.PER), "Never stands still: backdoor cuts, corner threes and honest defense"),
    // More small forwards
    SHOT_MAKER("Tough Shot Maker", "SHM", listOf(Attr.MID, Attr.OUT, Attr.HND), "Fadeaways, step-backs and contested jumpers: the shot clock is never a problem"),
    CONNECTOR("Connector", "CON", listOf(Attr.OUT, Attr.PAS, Attr.REB), "Spaces the floor, keeps the ball moving and finishes the possession on the glass"),
    RANGY_DEFENDER("Rangy Defender", "RNG", listOf(Attr.PER, Attr.BLK, Attr.REB), "Long arms everywhere: contests on the wing, weak-side blocks and box-outs"),
    TRANSITION_WING("Transition Wing", "TRN", listOf(Attr.INS, Attr.OUT, Attr.STL), "Gets out and runs: steals, layups on the break and trailing threes"),
    // More power forwards
    STRETCH_REBOUNDER("Stretch Rebounder", "SRB", listOf(Attr.OUT, Attr.REB, Attr.BLK), "Spaces the floor, cleans the glass and swats shots from the weak side"),
    TWO_WAY_FOUR("Two-Way Four", "2WF", listOf(Attr.OUT, Attr.PER, Attr.IDF), "Guards every frontcourt spot and hits the corner three"),
    SHORT_ROLL("Short-Roll Playmaker", "SRL", listOf(Attr.INS, Attr.PAS, Attr.IDF), "Catches in the short roll and makes the right read; holds the paint on defense"),
    BRUISER("Bruiser", "BRU", listOf(Attr.INS, Attr.MID, Attr.IDF), "All muscle: bully post-ups, elbow jumpers and a wall in the paint"),
    // More centers
    LOB_THREAT("Lob Threat", "LOB", listOf(Attr.INS, Attr.PER, Attr.BLK), "Rolls hard, catches everything above the rim and switches onto guards"),
    SPACING_FIVE("Pick-and-Pop Five", "SP5", listOf(Attr.MID, Attr.OUT, Attr.REB), "Center who pops to the arc on offense and still owns the defensive glass"),
    POST_PLAYMAKER("Post Playmaker", "PPM", listOf(Attr.INS, Attr.MID, Attr.PAS), "Draws double teams on the block and picks them apart with his passing"),
    SHOT_BLOCKER("Shot Blocker", "SWT", listOf(Attr.IDF, Attr.STL, Attr.BLK), "Times every shot at the rim: blocks, deflections and steals in the paint"),
    // GOAT Build Lab archetypes (PR #7)
    VOLUME_SCORER("Volume Scorer", "VOL", listOf(Attr.INS, Attr.MID, Attr.OUT), "Primary option who can get buckets from every level, night after night"),
    TRANSITION_FINISHER("Transition Finisher", "TRF", listOf(Attr.INS, Attr.HND, Attr.STL), "Turns steals and outlets into easy points in the open floor"),
    MIDRANGE_ASSASSIN("Midrange Assassin", "MDA", listOf(Attr.MID, Attr.HND, Attr.PER), "Lives in the midrange and creates pull-ups off the dribble"),
    SWITCH_BIG("Switch Big", "SBG", listOf(Attr.PER, Attr.IDF, Attr.REB), "Can guard one through five and cleans the glass"),
    PURE_POINT("Pure Point", "PPT", listOf(Attr.HND, Attr.PAS, Attr.PER), "Pass-first floor general who sets up teammates and pressures the ball"),
    FACILITATING_WING("Facilitating Wing", "FCW", listOf(Attr.MID, Attr.PAS, Attr.PER), "Wing who makes the right read, hits the open midrange and checks the other wing");

}

enum class Trait(val label: String, val good: Boolean) {
    AMBITIOUS("Ambitious", false),
    BAG_CHASER("Bag Chaser", false),
    DISLOYAL("Disloyal", false),
    LOYAL("Loyal", true),
    TEAM_PLAYER("Team Player", true),
    GYM_RAT("Gym Rat", true),
    CLUTCH("Clutch", true),
    /** Earned, never rolled: a walk-on whose game suddenly took off. Develops faster. */
    LATE_BLOOMER("Late Bloomer", true),
}

enum class Offense(val label: String, val blurb: String) {
    BALANCED("Balanced", "Take what the defense gives you."),
    INSIDE("Inside Attack", "Pound the paint and live at the line."),
    PERIMETER("Perimeter", "Spread the floor and let it fly from three."),
    UPTEMPO("Run & Gun", "Push the pace for more possessions."),
    GRIND("Grind It Out", "Slow it down and work for good shots."),
}

enum class Defense(val label: String, val blurb: String) {
    MAN("Man-to-Man", "Solid, no-frills defense."),
    ZONE("2-3 Zone", "Protects the paint, gives up threes."),
    PRESS("Full-Court Press", "Forces turnovers but allows easy looks."),
    PACKLINE("Pack Line", "Walls off the rim, lowers turnovers forced."),
}

enum class Phase { REGULAR, CONF_TOURNEY, NCAA, WRAP_UP, OFFSEASON, PLAYOFFS }

/** College basketball, or (once the user's player is drafted) the pro league. */
enum class Level { COLLEGE, PRO }

/** Active redshirt status for the season just played or underway. */
enum class Redshirt { NONE, ACADEMIC, MEDICAL }

enum class GameType(val short: String) { NONCONF("NC"), CONF("CONF"), EVENT("EVT"), CONF_TOURNEY("CT"), NCAA("NCAA"), NIT("NIT"), PLAYOFF("PO"), CUP("CUP") }

@Serializable
class StatLine(
    var gp: Int = 0, var gs: Int = 0, var sec: Int = 0,
    var pts: Int = 0, var fgm: Int = 0, var fga: Int = 0, var tpm: Int = 0, var tpa: Int = 0,
    var ftm: Int = 0, var fta: Int = 0, var orb: Int = 0, var drb: Int = 0, var ast: Int = 0,
    var stl: Int = 0, var blk: Int = 0, var tov: Int = 0, var pf: Int = 0,
    /** Points in clutch time: the last five minutes of the fourth quarter or overtime, within five points. */
    var clutch: Int = 0,
    /** Plus-minus: the team's points minus the other team's while he was on the floor. */
    var pm: Int = 0,
) {
    val reb get() = orb + drb
    fun add(o: StatLine) {
        gp += o.gp; gs += o.gs; sec += o.sec; pts += o.pts; fgm += o.fgm; fga += o.fga; tpm += o.tpm; tpa += o.tpa
        ftm += o.ftm; fta += o.fta; orb += o.orb; drb += o.drb; ast += o.ast; stl += o.stl; blk += o.blk; tov += o.tov; pf += o.pf
        clutch += o.clutch; pm += o.pm
    }
    /** What was added since [o] (a snapshot of this line taken earlier). */
    fun since(o: StatLine?): StatLine {
        if (o == null) return copy()
        return StatLine(gp - o.gp, gs - o.gs, sec - o.sec, pts - o.pts, fgm - o.fgm, fga - o.fga, tpm - o.tpm, tpa - o.tpa,
            ftm - o.ftm, fta - o.fta, orb - o.orb, drb - o.drb, ast - o.ast, stl - o.stl, blk - o.blk, tov - o.tov, pf - o.pf, clutch - o.clutch, pm - o.pm)
    }
    fun per(v: Int): Double = if (gp == 0) 0.0 else v.toDouble() / gp
    val ppg get() = per(pts)
    val rpg get() = per(reb)
    val apg get() = per(ast)
    val spg get() = per(stl)
    val bpg get() = per(blk)
    val mpg get() = if (gp == 0) 0.0 else sec / 60.0 / gp
    val fgPct get() = if (fga == 0) 0.0 else fgm.toDouble() / fga
    val tpPct get() = if (tpa == 0) 0.0 else tpm.toDouble() / tpa
    val ftPct get() = if (fta == 0) 0.0 else ftm.toDouble() / fta
    fun copy() = StatLine().also { it.add(this) }
}

@Serializable
class SeasonLine(
    val season: Int, val team: String, val conf: String, val cls: Int, val ovr: Int, val s: StatLine,
    /** NCAA Tournament games that season, if any. */
    val ncaa: StatLine? = null,
    /** The team's record and how its season ended. */
    val w: Int = 0, val l: Int = 0, val result: String = "",
    val teamId: Int = -1,
)

@Serializable
class GameLogEntry(
    val gameId: Int, val oppId: Int, val site: String, val won: Boolean, val score: String,
    val pts: Int, val reb: Int, val ast: Int, val stl: Int, val blk: Int, val tov: Int, val min: Int,
)

/** One highlight play: who, against whom, what happened, when, and how big it was (for the Top 10). */
@Serializable
class Highlight(
    val season: Int, val day: Int, val gameId: Int, val pid: Int, val name: String, val teamId: Int, val oppId: Int,
    val kind: String, val text: String, val time: String, val score: Double,
)

@Serializable
class Player(
    val id: Int,
    var first: String,
    var last: String,
    var pos: Pos,
    var year: Int,
    var height: Int,
    var weight: Int,
    var attrs: IntArray,
    var pot: Int,
    var style: Arch,
    var focus: Arch,
    var teamId: Int,
    var number: Int,
    var hometown: String,
    var highSchool: String,
    var country: String? = null,
    var stars: Int = 3,
    var recruitRank: Int = 0,
    var posRank: Int = 0,
    var traits: MutableList<Trait> = mutableListOf(),
    var look: Int = 0,
    var xp: Int = 0,
    var banked: Int = 0,
    var seasonLevels: Int = 0,
    var careerLevels: Int = 0,
    var seasonGains: IntArray = IntArray(10),
    var ovrHistory: MutableList<Int> = mutableListOf(),
    var happiness: Int = 70,
    var nil: Int = 0,
    var stats: StatLine = StatLine(),
    var tourney: StatLine = StatLine(),
    var career: MutableList<SeasonLine> = mutableListOf(),
    var log: MutableList<GameLogEntry> = mutableListOf(),
    var awards: MutableList<String> = mutableListOf(),
    var transferFrom: Int = -1,
    var favorite: Boolean = false,
    /** On a walk-on spot: no scholarship. */
    var walkOn: Boolean = false,
    /** Hidden: how well his body holds up (1-99; 0 means not set, see Longevity). Never shown. */
    var longevity: Int = 0,
    /** Hurt: what it is, how many of his team's games he'll still miss (0 = healthy) and how many in all. */
    var injury: String = "",
    var injGames: Int = 0,
    var injTotal: Int = 0,
    /** His summer development plan, set by the user's coach: "shooting", "playmaking", "inside", "defense", "conditioning" or "". */
    var devPlan: String = "",
    /** His custom portrait (see Face), or "" to draw one from [look]. */
    var face: String = "",
    /** What people call him ("The Professor"), and his gear ([Gear], encoded). */
    var nickname: String = "",
    var gear: String = "",
    /** This season's shots by zone (see [ShotChart.ZONES]): tries and makes, and the season they're for. */
    var zoneA: IntArray = IntArray(5),
    var zoneM: IntArray = IntArray(5),
    var zoneSeason: Int = 0,
    /** Season he made the team at walk-on tryouts (0 = he didn't). */
    var tryout: Int = 0,
    /** Hidden: this walk-on has a breakout in him. */
    var gem: Boolean = false,
    /** Walk-on story so far: 0 nothing yet, 1 broke out, 2 became a star. */
    var story: Int = 0,
    /** Career highs: points, rebounds, assists, threes, steals, blocks; and the game for each. */
    var highs: IntArray = IntArray(6),
    var highGames: MutableList<String> = mutableListOf(),
    /** For former players: the last school and how he left. */
    var lastTeam: Int = -1,
    var left: String = "",
    /** Pro league: age, and pro seasons finished (0 for a rookie). */
    var age: Int = 0,
    var proYears: Int = 0,
    /** A pro's salary (thousands a season) and the years left on his deal (the user's player's live in his contract). */
    var salary: Int = 0,
    var contractYears: Int = 0,
    /** Headshot file for real pro players (assets/nba/heads/<photo>.webp), or empty. */
    var photo: String = "",
    /** A second position he can play (a neighbor of his first), or null. */
    var pos2: Pos? = null,
    /** Pro contract clause/option: "" none, "PO" player option, "TO" team option, "NTC" no-trade clause, "RFA" restricted, "QO" qualifying offer. */
    var clause: String = "",
    /** Pro contract structure: "FLAT", "FRONT" (front-loaded), or "BACK" (back-loaded). */
    var structure: String = "",
    /** An extension signed during the season: his salary from next season (0 when none). */
    var nextSalary: Int = 0,
    /** 2K-style build title from his attribute shape (e.g. "Two-Way 3-Level Scorer"). */
    var buildName: String = "",
    /** Closest NBA-legend flavor matches (display names). */
    var likeA: String = "",
    var likeB: String = "",
    /** Provenance when a GOAT preset or offspring was applied. */
    var goatId: String = "",
    var parentA: String = "",
    var parentB: String = "",
    /** Sitting out (academic) or hardship claimed (medical) this season. */
    var redshirt: Redshirt = Redshirt.NONE,
    /** Already used an academic redshirt earlier in his career. */
    var usedAcademicRs: Boolean = false,
    /** Already used a medical hardship earlier in his career. */
    var usedMedicalRs: Boolean = false,
    /** Class labels get the RS prefix after any redshirt. */
    var rsClass: Boolean = false,
    /** Personal shoe deal company (ShoeCo.name), pay in $K/season, seasons left. */
    var shoe: String = "",
    var shoePay: Int = 0,
    var shoeLeft: Int = 0,
    /** Pending endorsement offer for the user to accept or decline. */
    var shoeOffer: String = "",
    var shoeOfferPay: Int = 0,
    var shoeOfferYears: Int = 0,
) {
    /** Out injured. */
    val out get() = injGames > 0
    val name get() = "$first $last".trim()
    /** "PG/SG" for a two-position player, else just his position. */
    val posLabel get() = pos2?.let { "$pos/$it" } ?: "$pos"
    /** He can play [p]: his first position or his second. */
    fun plays(p: Pos) = pos == p || pos2 == p
    /** "J. Smith", or the one name for players known by one ("Nene"). */
    val short get() = if (last.isEmpty() || first.isEmpty()) name else "${first.first()}. $last"
    val ovr: Int get() = Ratings.ovr(pos, attrs)
    operator fun get(a: Attr) = attrs[a.ordinal]
    fun has(t: Trait) = t in traits
    val cls get() = classLabel(year, rsClass || redshirt != Redshirt.NONE)
}

fun classLabel(year: Int, rs: Boolean = false): String {
    val base = when (year) { 0 -> "HS"; 1 -> "FR"; 2 -> "SO"; 3 -> "JR"; 4 -> "SR"; PRO_YEAR -> "PRO"; else -> "GR" }
    return if (rs && year in 1..4) "RS $base" else base
}

/** The class year every pro player carries. */
const val PRO_YEAR = 9

@Serializable
class Slot(var starter: Int, var reserve: Int, var starterMin: Int, var starterUsg: Int, var reserveUsg: Int)

@Serializable
class GamePlan(
    var offense: Offense = Offense.BALANCED,
    var defense: Defense = Defense.MAN,
    var slots: MutableList<Slot> = mutableListOf(),
    var auto: Boolean = true,
)

@Serializable
class TeamSeason(
    val season: Int, val w: Int, val l: Int, val cw: Int, val cl: Int,
    val result: String, val prestige: Int, val net: Int, val ap: Int, val coach: String,
    /** Average scoring margin, and the playoff record (NBA), for rating the great teams. */
    val margin: Double = 0.0, val pw: Int = 0, val pl: Int = 0,
)

@Serializable
class Team(
    val id: Int,
    var school: String,
    var mascot: String,
    var abbr: String,
    var confId: Int,
    var prestige: Int,
    var primary: Long,
    var secondary: Long,
    var logo: Int,
    var coachId: Int = -1,
    /** The franchise's place in today's league (its abbreviation today), whatever it's called this season. */
    var slot: String = "",
    var roster: MutableList<Int> = mutableListOf(),
    var plan: GamePlan = GamePlan(),
    var w: Int = 0, var l: Int = 0, var cw: Int = 0, var cl: Int = 0, var streak: Int = 0,
    var elo: Double = 1500.0,
    var expWins: Double = 0.0,
    var totals: StatLine = StatLine(),
    var opp: StatLine = StatLine(),
    var ap: Int = 0,
    var net: Int = 0,
    var prevPrestige: Int = 0,
    var history: MutableList<TeamSeason> = mutableListOf(),
    var titles: Int = 0,
    var finalFours: Int = 0,
    var tourneys: Int = 0,
    var confTitles: Int = 0,
    var bonuses: MutableList<NilBonus> = mutableListOf(),
    /** The business: ticket price (0 cheap to 4 luxury), the upgrade budget and this season's gate (thousands), upgrades bought. */
    var price: Int = 2,
    var cash: Long = -1,
    var gate: Long = 0,
    var upgrades: MutableList<String> = mutableListOf(),
    /** Home arena capacity, set once when the league is created (or an old save loads). */
    var arena: Int = 0,
    /** Prestige won or lost in rivalry games this season. */
    var rivalryDelta: Int = 0,
    /** Fan mood 5-100: wins fill the arena and the NIL pool, losses empty them. */
    var fans: Double = 0.0,
    var homeGames: Int = 0,
    var homeAtt: Long = 0,
    var sellouts: Int = 0,
    /** Program Hall of Fame; retired jerseys hang in the rafters. */
    val legends: MutableList<Legend> = mutableListOf(),
    /** Long-run standing, 5-99. Moves a few points a decade. -1 until first computed. */
    var legacy: Double = -1.0,
    /** Short-term national heat, 0-100. Big wins and March runs raise it; it fades. -1 until first computed. */
    var buzz: Double = -1.0,
    /** Apparel / shoe company partner (ShoeCo.name). Empty until assigned. */
    var apparel: String = "",
    /** Seasons left on the school apparel contract. */
    var apparelLeft: Int = 0,
) {
    val name get() = "$school $mascot"
    val gp get() = w + l
    val winPct get() = if (gp == 0) 0.0 else w.toDouble() / gp
    val record get() = "$w-$l"
    val confRecord get() = "$cw-$cl"
}

/** A program legend: Hall of Fame member, and for the very best, a retired jersey. */
@Serializable
class Legend(
    val name: String,
    val pos: Pos? = null,
    val number: Int = -1,
    val from: Int,
    val to: Int,
    val line: String,
    val honors: List<String> = emptyList(),
    val inducted: Int,
    val retired: Boolean = false,
    val coach: Boolean = false,
    val score: Int = 0,
    val pid: Int = -1,
) {
    val years get() = if (from == to) "$from" else "$from-${to % 100}"
}

@Serializable
class CoachSeason(val season: Int, val teamId: Int, val team: String, val w: Int, val l: Int, val result: String, val prestige: Int)

@Serializable
class Coach(
    val id: Int,
    var first: String,
    var last: String,
    var age: Int,
    var teamId: Int,
    var off: Int, var def: Int, var rec: Int, var dev: Int,
    var look: Int,
    var w: Int = 0, var l: Int = 0,
    var tourneys: Int = 0, var finalFours: Int = 0, var titles: Int = 0, var confTitles: Int = 0,
    var natCoty: Int = 0, var confCoty: Int = 0, var drafted: Int = 0, var hof: Int = 0,
    var isUser: Boolean = false,
    var security: Int = 70,
    var history: MutableList<CoachSeason> = mutableListOf(),
    var points: Int = 0,
    var skills: MutableList<CoachSkill> = mutableListOf(),
    /** The season he went into the Hall of Fame as a coach (0: not yet). */
    var hofSeason: Int = 0,
    /** The user's contract (see [CoachContracts]): years left, salary a year (thousands), the season he signed, and his career earnings. */
    var contractYears: Int = 0,
    var contractSalary: Int = 0,
    var contractSigned: Int = 0,
    var earnings: Int = 0,
    /** An extension on the table: years, salary, whether it's an early one (his deal still runs), and whether he's countered. */
    var offerYears: Int = 0,
    var offerSalary: Int = 0,
    var offerEarly: Boolean = false,
    var countered: Boolean = false,
) {
    fun has(s: CoachSkill) = s in skills
    val name get() = "$first $last"
    val winPct get() = if (w + l == 0) 0.0 else w.toDouble() / (w + l)
    val rating: Int get() = (((off + def + rec + dev) / 4.0) * 0.55 + winPct * 38 + titles * 2.5 + finalFours * 0.6).toInt().coerceIn(1, 99)
}

@Serializable
class Conference(val id: Int, val name: String, val abbr: String, val tier: Int, val teamIds: MutableList<Int>)

@Serializable
class PlayerLine(val pid: Int, val name: String, val pos: Pos, val starter: Boolean, val s: StatLine)

@Serializable
class BoxScore(val home: List<PlayerLine>, val away: List<PlayerLine>, val halves: List<IntArray>, val quarters: Boolean = false)

@Serializable
class Game(
    val id: Int,
    val day: Int,
    var home: Int,
    var away: Int,
    val neutral: Boolean,
    val type: GameType,
    val label: String = "",
    var hs: Int = -1,
    var aws: Int = -1,
    var ot: Int = 0,
    var box: BoxScore? = null,
    /** Crowd size, set when the game tips off. */
    var att: Int = 0,
    /** Prestige the winner took from the loser in a rivalry game. */
    var swing: Int = 0,
    /** The user's games: every shot, packed (see [ShotChart]). */
    var shots: MutableList<Long> = mutableListOf(),
) {
    val played get() = hs >= 0
    fun winner() = if (hs > aws) home else away
    fun loser() = if (hs > aws) away else home
    fun involves(t: Int) = home == t || away == t
    fun opp(t: Int) = if (home == t) away else home
    fun scoreFor(t: Int) = if (home == t) hs else aws
    fun scoreAgainst(t: Int) = if (home == t) aws else hs
    fun site(t: Int) = when { neutral -> "N"; home == t -> "vs"; else -> "@" }
}

/** A single-elimination bracket. Slots hold team ids; -1 is an empty seed (a bye), -2 is not decided yet. */
@Serializable
class Bracket(
    val name: String,
    val short: String,
    val confId: Int,
    val startDay: Int,
    val roundNames: List<String>,
    val seeds: Map<Int, Int>,
    val rounds: MutableList<MutableList<Int>>,
    val gameIds: MutableList<MutableList<Int>> = mutableListOf(),
    var champion: Int = -1,
    var current: Int = 0,
    /** NCAA only: First Four games that fill an empty Round of 64 slot. */
    val playIns: MutableList<PlayIn> = mutableListOf(),
) {
    val done get() = champion >= 0
    fun playInOf(teamId: Int) = playIns.firstOrNull { it.a == teamId || it.b == teamId }
    /** Every team in the bracket, First Four teams included. */
    fun hasTeam(teamId: Int) = teamId in seeds
}

/** A First Four game: the winner takes Round of 64 [slot] as a No. [seed] seed. */
@Serializable
class PlayIn(val slot: Int, val a: Int, val b: Int, val seed: Int, var gameId: Int = -1)

@Serializable
class InSeasonEvent(
    val name: String,
    val teams: List<Int>,
    val startDay: Int,
    val gameIds: MutableList<Int> = mutableListOf(),
    var placement: MutableList<Int> = mutableListOf(),
)

@Serializable
class Recruit(
    /** For transfers this is the same player as in League.players; SaveIO.decode relinks it after a load. */
    var p: Player,
    /** Interest in each school still in the race, 0-100. */
    var interest: MutableMap<Int, Int> = mutableMapOf(),
    var committed: Int = -1,
    /** True once [scoutLevel] reaches 3 (real OVR/POT known). Kept for older saves. */
    var scouted: Boolean = false,
    /**
     * How deep the user has scouted him: 0 unscouted, 1 film, 2 campus visit, 3 full report.
     * Each step narrows the OVR/POT fog toward his real ratings.
     */
    var scoutLevel: Int = 0,
    /** Hidden noise on the first look; scouting shrinks how much of it you still see. */
    var noiseO: Int = 0,
    var noiseP: Int = 0,
    var onBoard: Boolean = false,
    var nilAsk: Int = 0,
    var nilOffer: Int = 0,
    var estOvr: Int = 0,
    var estPot: Int = 0,
    var transfer: Boolean = false,
    /** Recruiting points each school has on him this round. The user's stay put between rounds. */
    var alloc: MutableMap<Int, Int> = mutableMapOf(),
    /** What matters most to him. */
    var pref: RecruitPref = RecruitPref.NONE,
    /** Round he committed in (6 = after the last round). */
    var round: Int = 0,
)

enum class RecruitPref(val label: String, val blurb: String) {
    NONE("No strong lean", "Weighs everything about evenly."),
    HOME("Close to home", "Location counts double: in-state schools get a big edge, faraway ones a penalty."),
    MINUTES("Early playing time", "Playing time counts double: he wants to start, not sit behind veterans."),
    WINNING("Winning now", "Wants a team that's winning right now."),
    BRAND("A big-name program", "Program strength counts extra: Recruiting Pull matters most."),
    NIL("NIL money", "NIL offers count 60% more with him."),
    PROS("A path to the pros", "Recent draft picks count three times as much."),
}

/** What happened when a recruiting round locked, from the user's side. */
@Serializable
class RoundReport(
    val round: Int,
    val mine: MutableList<Int> = mutableListOf(),
    val lost: MutableList<Int> = mutableListOf(),
    var dropped: Int = 0,
    var entered: Int = 0,
    var commits: Int = 0,
    var open: Int = 0,
)

@Serializable
class News(
    val season: Int, val day: Int, var text: String, val mine: Boolean = false, val gameId: Int = -1, val kind: String = "",
    /** A short article behind the headline, and the outlet that ran it. */
    var body: String = "", var source: String = "",
    /** The team and player the story is about, for its picture and links. */
    val teamId: Int = -1, val pid: Int = -1,
    /** What people said about it: players, coaches, analysts, insiders and fans. */
    var reactions: List<Reaction> = emptyList(),
    /** Explicitly marks whether this article was generated and labeled by AI. */
    var aiLabeled: Boolean = false,
) {
    /** Rivalry results carry their game so the headline can link to the box score. */
    val rivalry get() = kind == "rivalry" || (kind.isEmpty() && gameId >= 0)
    val isAiGenerated get() = aiLabeled || source.split(" ", "·", "-", "/", "_").any { it.equals("AI", ignoreCase = true) } || source.contains("Ollama", ignoreCase = true) || source.contains("Gemini", ignoreCase = true) || source.contains("ChatGPT", ignoreCase = true)
}

/** Settings for local Ollama LLM integration to control and generate stories. */
@Serializable
class OllamaConfig(
    var enabled: Boolean = false,
    var host: String = "http://localhost:11434",
    var model: String = "llama3.2:1b",
    var autoGenerate: Boolean = false,
    var pressConference: Boolean = true,
    var socialFeed: Boolean = true,
    var gameRecaps: Boolean = true,
    var tradeRumors: Boolean = true,
    var scoutingKeys: Boolean = true,
    var draftScouting: Boolean = true,
    var injuryReports: Boolean = true,
    var awardCeremonies: Boolean = true,
    var halftimeSpeeches: Boolean = true,
    var playerDevelopment: Boolean = true,
    // Speed & Performance Optimizations
    var speedMode: String = "turbo", // "turbo", "balanced", "quality"
    var numThreads: Int = 0, // 0 = auto-detect optimal CPU cores
    var contextSize: Int = 512, // 512 tokens for rapid mobile prefill & low KV cache memory
    var keepAliveMinutes: Int = 30, // keep warm in RAM to avoid cold reload delays
    var cacheResponses: Boolean = true, // instant 0ms load for identical queries
    // Multi-Provider AI (Google Gemini, OpenAI ChatGPT, Local Ollama)
    var provider: String = "ollama", // "ollama", "google", "openai"
    var googleApiKey: String = "",
    var googleModel: String = "gemini-1.5-flash",
    var googleAccountEmail: String = "",
    var googleAccountName: String = "",
    var googleAccountPassword: String = "",
    var googleSignedIn: Boolean = false,
    var openAiApiKey: String = "",
    var openAiModel: String = "gpt-4o-mini",
    var openAiAccountEmail: String = "",
    var openAiAccountName: String = "",
    var openAiAccountPassword: String = "",
    var openAiSignedIn: Boolean = false,
    var customEndpoint: String = "",
) {
    val isGoogleSignedIn: Boolean
        get() = googleSignedIn || googleAccountEmail.isNotBlank() || googleApiKey.isNotBlank()

    val isOpenAiSignedIn: Boolean
        get() = openAiSignedIn || openAiAccountEmail.isNotBlank() || openAiApiKey.isNotBlank()

    val googleDisplayEmail: String
        get() = googleAccountEmail.ifBlank { if (googleApiKey.isNotBlank()) "Connected Google Account" else "Not Signed In" }

    val openAiDisplayEmail: String
        get() = openAiAccountEmail.ifBlank { if (openAiApiKey.isNotBlank()) "Connected ChatGPT Account" else "Not Signed In" }

    val activeModelName: String
        get() = when (provider.lowercase()) {
            "google" -> googleModel.ifBlank { "gemini-1.5-flash" }
            "openai" -> openAiModel.ifBlank { "gpt-4o-mini" }
            else -> model.ifBlank { "llama3.2:1b" }
        }

    val providerDisplayName: String
        get() = when (provider.lowercase()) {
            "google" -> if (isGoogleSignedIn && googleAccountEmail.isNotBlank()) "Google Gemini (${googleAccountEmail.substringBefore("@")})" else "Google Gemini"
            "openai" -> if (isOpenAiSignedIn && openAiAccountEmail.isNotBlank()) "ChatGPT (${openAiAccountEmail.substringBefore("@")})" else "ChatGPT (OpenAI)"
            else -> "Local Ollama"
        }

    val fullModelDisplayName: String
        get() = when (provider.lowercase()) {
            "google" -> "Google $activeModelName"
            "openai" -> "ChatGPT ($activeModelName)"
            else -> "Ollama $activeModelName"
        }

    val shortActionName: String
        get() = when (provider.lowercase()) {
            "google" -> "Gemini"
            "openai" -> "ChatGPT"
            else -> "Ollama"
        }
}

enum class MomentKind(val tag: String) {
    BUZZER("Buzzer-beater"), FORCED_OT("Forces overtime"), UPSET("Upset alert"), CLASSIC("Instant classic"),
    CINDERELLA("Cinderella"), TITLE("Champions"),
}

/** A big moment for the highlight reel. Self-contained: it outlives the season's games and box scores. */
@Serializable
class Moment(
    val season: Int,
    val day: Int,
    val kind: MomentKind,
    /** Where it happened, e.g. "NCAA South · First Round". */
    val stage: String,
    val headline: String,
    val detail: String,
    val winner: Int,
    val loser: Int,
    val wSeed: Int = 0,
    val lSeed: Int = 0,
    val wScore: Int = 0,
    val lScore: Int = 0,
    val ot: Int = 0,
    val mine: Boolean = false,
    val weight: Int = 0,
    val gameId: Int = -1,
)

/** A traditional rivalry between two programs, with the series record since the dynasty began. */
@Serializable
class Rivalry(
    val a: Int,
    val b: Int,
    val name: String = "",
    /** Played at a neutral site every year (e.g. Braggin' Rights in St. Louis). */
    val neutral: Boolean = false,
    var aWins: Int = 0,
    var bWins: Int = 0,
) {
    fun involves(t: Int) = a == t || b == t
    fun other(t: Int) = if (a == t) b else a
    fun winsFor(t: Int) = if (a == t) aWins else bWins
    val title get() = name.ifEmpty { "Rivalry" }
}

@Serializable
class DraftPick(
    val season: Int, val pick: Int, val playerName: String, val pos: Pos, val teamId: Int, val school: String,
    val cls: Int, val ovr: Int, val look: Int = 0, val number: Int = 0, val pid: Int = -1,
    /** NBA club that took him on Draft Night (college flavor). */
    val nba: String = "",
    /** NBA shoe company (ShoeCo.name) and first-year pay in $K. */
    val shoe: String = "",
    val shoePay: Int = 0,
    /** Pro-career draft destination (era-aware team name), set when the hero enters the league. */
    var proTeam: String = "",
)

@Serializable
class HofEntry(val season: Int, val name: String, val pos: Pos, val school: String, val summary: String, val isCoach: Boolean = false)

@Serializable
class RecordEntry(val label: String, var value: Int, var holder: String, var detail: String)

@Serializable
class AwardWinner(val award: String, val pid: Int, val name: String, val teamId: Int, val line: String)

@Serializable
class SeasonSummary(
    val season: Int,
    val champion: Int,
    val runnerUp: Int,
    val finalFour: List<Int>,
    val awards: List<AwardWinner>,
    val cotyName: String,
    val cotyTeam: Int,
    val confChamps: Map<Int, Int>,
)

@Serializable
class WrapUp(
    val season: Int,
    val teamId: Int,
    val result: String,
    val seed: Int,
    val path: List<Int>,
    val pathWon: List<Boolean>,
    val w: Int, val l: Int, val regW: Int, val regL: Int, val cw: Int, val cl: Int,
    val expWins: Double,
    val net: Int, val ap: Int, val confFinish: Int, val prestige: Int, val prestigeDelta: Int,
    val q1: String, val q2: String, val wab: Double, val sosRank: Int,
    val awards: List<AwardWinner>,
    val coachAwards: List<String>,
    val prestigeBefore: Int = 0,
    val coachPoints: Int = 0,
    val drivers: List<Driver> = emptyList(),
    val signals: List<Driver> = emptyList(),
    val confTourney: String = "",
    /** "NCAA", "NIT" or "" for the postseason event the team played in. */
    val event: String = "",
    /** Short round names for each game in [path]. */
    val pathRounds: List<String> = emptyList(),
    val nitReward: NitReward? = null,
    /** What the season earned (Prestige moves toward it), and the pieces that added up to it. */
    val earned: Int = 0,
    val earnedParts: List<Driver> = emptyList(),
    /** How far Prestige moved toward [earned] this season, and the rising streak behind it. */
    val climbRate: Double = 0.0,
    val streak: Int = 0,
)

/** What an NIT title earned the program. */
@Serializable
class NitReward(val prestige: Int, val points: Int, val xp: Int, val players: Int)

/** A team's selection resume. Quadrant records are stored as [q1w, q1l, q2w, q2l, q3w, q3l, q4w, q4l]. */
@Serializable
class Sheet(
    val teamId: Int,
    val net: Int,
    val w: Int,
    val l: Int,
    val q: IntArray,
    val sos: Int,
    val ncSos: Int,
    val wab: Double,
    val wabRank: Int,
    val score: Double,
    var rank: Int = 0,
) {
    fun qw(n: Int) = q[(n - 1) * 2]
    fun ql(n: Int) = q[(n - 1) * 2 + 1]
    fun quad(n: Int) = "${qw(n)}-${ql(n)}"
    val record get() = "$w-$l"
}

@Serializable
class BubbleGroup(val key: String, val title: String, val note: String, val ids: List<Int>)

/** Bubble Watch: a projection during the season, or the committee's final call on Selection Sunday. */
@Serializable
class Bubble(
    val season: Int,
    val final: Boolean,
    val groups: List<BubbleGroup>,
    val sheets: List<Sheet>,
    val seeds: Map<Int, Int> = emptyMap(),
    /** At-large order (1 = first at-large pick); auto-bid teams get the spot they would have held. */
    val atLargeRank: Map<Int, Int> = emptyMap(),
    val atLargeSpots: Int = 36,
) {
    @Transient private var index: Map<Int, Sheet>? = null
    fun sheet(id: Int): Sheet? = (index ?: sheets.associateBy { it.teamId }.also { index = it })[id]
    fun groupOf(id: Int): BubbleGroup? = groups.firstOrNull { id in it.ids }
}

@Serializable
class Driver(val label: String, val detail: String, val value: String, val good: Boolean)

@Serializable
class JobOffer(val teamId: Int, val note: String)

enum class CoachSkill(val label: String, val blurb: String) {
    OFF_TACTICIAN("Off Tactician", "Gives a small boost to your offensive tactics."),
    DEF_TACTICIAN("Def Tactician", "Gives a small boost to your defensive tactics."),
    RECRUITER("Recruiter", "Gives a small increase to recruiting interest gains."),
    DEVELOPER("Developer", "Gives a small increase to in-season XP and offseason development."),
    CULTURE("Culture", "Gives a small boost to player happiness and retention."),
}

@Serializable
class NilBonus(val label: String, val amount: Int, var seasonsLeft: Int, val conference: Boolean = false)

enum class OffStep(val title: String) {
    ROSTER("Roster & NIL"), DRAFT("Draft Night"), CAREER("Career Decision"), DEVELOPMENT("Player Development"),
    BUDGET("NIL Budget"), RECRUITING("Recruiting"), TRYOUTS("Walk-on Tryouts"),
}

/** A student trying out for a walk-on spot. Ratings stay hidden; [notes] are drill grades 0-3. */
@Serializable
class Tryout(val p: Player, var notes: IntArray, var looked: Boolean = false, var kept: Boolean = false)

@Serializable
class GradEntry(val pid: Int, val name: String, val pos: Pos, val ovr: Int, val nil: Int, val line: String, val note: String)

@Serializable
class Growth(val pid: Int, val before: Int, val after: Int, val gains: IntArray, val weight: Int)

enum class GameMode { COACH, PLAYER }

enum class NilDeal(val label: String, val blurb: String) {
    NONE("No deal", ""),
    STEADY("Hometown dealership", "Full value, guaranteed, no strings."),
    PERFORMANCE("Performance deal", "60% guaranteed, plus a bonus for every point you average and for All-America honors."),
    BRAND("National brand", "150% of your value, but appearances and shoots cost 15% of your development this season."),
}

/** A school's scholarship offer to the user's player: out of high school or out of the transfer portal. */
@Serializable
class SchoolOffer(val teamId: Int, val nil: Int, val role: String, val ahead: Int, val closeness: Int)

/** Player-mode state: NIL deal, offseason decisions and how the career ended. */
@Serializable
class RoadState(
    var startSeason: Int = 0,
    var dealSeason: Int = 0,
    var deal: NilDeal = NilDeal.NONE,
    var dealBase: Int = 0,
    /** NIL earned over the career, in thousands. */
    var earned: Int = 0,
    var declared: Boolean = false,
    var portal: Boolean = false,
    val offers: MutableList<SchoolOffer> = mutableListOf(),
    val schools: MutableList<Int> = mutableListOf(),
    var ended: Boolean = false,
    var draftPick: Int = 0,
    var ending: String = "",
    /** One line per finished season for the career story. */
    val story: MutableList<String> = mutableListOf(),
    /** Social following. Every follower adds a dollar a year to his NIL value. */
    var followers: Int = 0,
    /** His social profile picture: "" his jersey (or headshot), "logo" his team's, "cartoon", or "photo" (a picture from his phone, [avatarFile]). */
    var avatarStyle: String = "",
    var avatarFile: String = "",
    var seasonStartFollowers: Int = 0,
    /** Media persona from press answers: negative humble, positive brash. */
    var persona: Int = 0,
    /** Follower milestones already paid out by sponsors. */
    var milestone: Int = 0,
    /** The open postgame press question, if any. */
    var press: PressQuestion? = null,
    val pressLog: MutableList<String> = mutableListOf(),
    /** His social feed (newest last), and when the last award-race column ran (season * 1000 + day). */
    val feed: MutableList<Post> = mutableListOf(),
    /** Around the league: the day's stories and what people said about them (the Social screen's second tab). */
    val leagueFeed: MutableList<Post> = mutableListOf(),
    var lastColumn: Int = 0,
    /** What his feed has already covered: awards on his record, his team and level, his deal, his best scoring night, career-point milestones. */
    var seenAwards: Int = -1,
    var seenTeam: Int = -2,
    var seenPro: Boolean = false,
    var seenDeal: String = "",
    var bestPts: Int = -1,
    var seenPoints: Int = -1,
    /** When each press topic was last asked (season * 1000 + day), so the same questions don't come every week. */
    val pressAsked: MutableMap<String, Int> = mutableMapOf(),
    /** Leadership (0-100): seasons as a starter, winning beyond the projection, and how he talks; 60 and up, he's a captain. */
    var leadership: Int = 15,
    /** Practice this season: total effort (0 rest to 1 extra work each day) and days, and the last summer's growth, explained. */
    var work: Double = 0.0,
    var workDays: Int = 0,
    var growthNote: String = "",
    /** This week's practice plan, and how worn down he is (0 fresh, 100 exhausted). */
    var practice: Practice = Practice.TEAM,
    var fatigue: Int = 0,
    /** Practice work banked toward each rating; every 300 is +1. */
    var practiceXp: IntArray = IntArray(10),
    /** Player of the Year ladder: this week's rank, last week's, and the day it was taken. */
    var raceRank: Int = 0,
    var lastRaceRank: Int = 0,
    var raceDay: Int = -99,
    /** Big moments in live games: how many he got, and how many he came through in. */
    var moments: Int = 0,
    var momentsWon: Int = 0,
    /** NIL money spent on his own staff; the bank is earned minus spent. */
    var spent: Int = 0,
    /** Staff hired for [hiredSeason]; contracts run one season. */
    val hired: MutableList<Staff> = mutableListOf(),
    var hiredSeason: Int = 0,
    /** Season whose guaranteed NIL was already paid at signing. */
    var paidSeason: Int = 0,
    /** Draft Night: the season he was in the draft, his big-board rank going in, and the season whose green room he watched to the end. */
    var draftSeason: Int = -1,
    var draftRank: Int = 0,
    var draftWatched: Int = -1,
    /** Live games jump straight to his plays. */
    var spotlight: Boolean = true,
    /** His signature style ([Signature]): dunk, go-to move, jumper and celebration. */
    var sigDunk: Int = 0,
    var sigMove: Int = 0,
    var sigShot: Int = 0,
    var celebration: Int = 0,
    /** Big moments use a timing meter for his own shots. */
    var shotMeter: Boolean = true,
    /** His signature shoe deal (see [Shoes]), the season brands last made offers, and the offers on the table. */
    var shoe: ShoeDeal? = null,
    /** Calling it a career: the season he said would be his last, the season he came back from retirement, and the teams that already honored him on the farewell tour. */
    var farewellSeason: Int = 0,
    /** The hometown team he's playing for right now (-1 when he isn't), and the season he came home (see [Hometown]). */
    var homeTeam: Int = -1,
    var homeSince: Int = 0,
    var comebackSeason: Int = 0,
    val tourStops: MutableList<Int> = mutableListOf(),
    var shoeOffered: Int = 0,
    val shoeOffers: MutableList<ShoeDeal> = mutableListOf(),
    /** The class rival: a top recruit from the same class who follows him all career. */
    var rivalId: Int = -1,
    var rivalName: String = "",
    val duels: MutableList<Duel> = mutableListOf(),
    /** How the rival's college career ended ("went No. 4 in the 2027 draft"), once it has. */
    var rivalLeft: String = "",
    var rivalPick: Int = 0,
    var rivalDraft: Int = 0,
    /** Summer events: the offseason (by season) whose summer is already booked, and what happened. */
    var summer: Int = -1,
    var summerNote: String = "",
    /** Pro career: drafted (or signed undrafted) and headed to the pros, and everything after. */
    var goingPro: Boolean = false,
    var undrafted: Boolean = false,
    var proStart: Int = 0,
    var contract: Contract? = null,
    val proTeams: MutableList<Int> = mutableListOf(),
    val proStory: MutableList<String> = mutableListOf(),
    val proOffers: MutableList<ProOffer> = mutableListOf(),
    var rings: Int = 0,
    var allStars: Int = 0,
    var mvps: Int = 0,
    var retired: Boolean = false,
    /** College numbers, kept when he turns pro. */
    var collegeTotals: StatLine? = null,
    var collegeSeasons: Int = 0,
    var collegeSchools: String = "",
    /** Season his salary for that season was paid. */
    var salarySeason: Int = 0,
    /** The order pro teams pick in on his Draft Night (franchise indexes). */
    val proOrder: MutableList<Int> = mutableListOf(),
    /** Trades: when he last asked out (season * 2, +1 in the offseason), the teams that want him, and his last move. */
    var tradeKey: Int = -1,
    val suitors: MutableList<Suitor> = mutableListOf(),
    var tradedSeason: Int = 0,
    var tradedDay: Int = -1,
    var tradeNote: String = "",
    /** The last trade: whether he asked for it, his first words after (0 humble, 1 even, 2 brash; -1 none), and the old team he hasn't been back to play yet (see [TradeCall]). */
    var tradeAsked: Boolean = false,
    var tradeReaction: Int = -1,
    var returnTeam: Int = -1,
    var returnSeason: Int = 0,
    /** Free agency: a player option on his last year that he has to decide on this summer. */
    var optionPending: Boolean = false,
    /** Restricted free agency after a first-round rookie deal: his team can match any offer sheet. */
    var restricted: Boolean = false,
    /** Playoff highlight reel, and his best pro single games (points, rebounds, assists, threes, steals, blocks). */
    val reel: MutableList<ReelEntry> = mutableListOf(),
    var proBest: IntArray = IntArray(6),
    /** Recruiting a star: the summer he last pitched one, the season he last pushed for a trade, and how it went. */
    var pitchSeason: Int = -1,
    var pushSeason: Int = -1,
    var recruitNote: String = "",
    /** After he retires: the numbers hung in the rafters, and his Hall of Fame class (0 if not in). */
    val jerseys: MutableList<String> = mutableListOf(),
    var hofClass: Int = 0,
    /** His overall the day he left college, before the step up to the pros. */
    var collegeOvr: Int = 0,
    /** The all-time list: his rank (0 until he's on it), the names below him, and the last time he passed someone. */
    var allTimeRank: Int = 0,
    val allTimePassed: MutableList<String> = mutableListOf(),
    /** Names already announced as passed, so a back-and-forth with an active player isn't news twice. */
    val allTimeNamed: MutableList<String> = mutableListOf(),
    var allTimeNote: String = "",
    var allTimeSeason: Int = 0,
    var allTimeDay: Int = -1,
)

/** A team that would trade for him, and the players it would send back. */
@Serializable
class Suitor(val teamId: Int, val sends: List<Int>, val role: String, val ahead: Int, val pickKeys: List<Int> = emptyList())

/** A completed trade: who went each way. */
@Serializable
class Trade(
    val season: Int, val day: Int, val offseason: Boolean, val teamA: Int, val teamB: Int,
    val toB: List<String>, val toA: List<String>, val hero: Boolean = false,
)

/** A pro contract. Salary in thousands of dollars a season. */
@Serializable
class Contract(val teamId: Int, val salary: Int, var years: Int, val kind: String, val signed: Int = 0, var option: Boolean = false)

/** A free-agent offer. */
@Serializable
class ProOffer(
    val teamId: Int, var salary: Int, val years: Int, val role: String, val ahead: Int, val winPct: Double,
    /** The most the team will pay (hidden), whether he's countered yet, and how the team answered. */
    val max: Int = 0, var countered: Boolean = false, var note: String = "",
)

/**
 * A playoff series, best of seven unless [bestOf] says otherwise (play-in and NBA Cup games are one game).
 * [conf] is -1 for the Finals; [slot] orders the bracket. Round 0 is the play-in.
 */
@Serializable
class Series(
    val round: Int, val conf: Int, val slot: Int, val hi: Int, val lo: Int,
    var hiWins: Int = 0, var loWins: Int = 0, val gameIds: MutableList<Int> = mutableListOf(),
    val bestOf: Int = 7,
) {
    private val need get() = bestOf / 2 + 1
    val done get() = hiWins == need || loWins == need
    val winner get() = if (hiWins == need) hi else if (loWins == need) lo else -1
    val loser get() = if (hiWins == need) lo else if (loWins == need) hi else -1
    fun involves(t: Int) = hi == t || lo == t
}

/** One finished pro season, for the history page. */
@Serializable
class ProSeasonSummary(
    val season: Int, val champion: Int, val runnerUp: Int, val mvp: String, val finalsMvp: String, val roy: String,
    val heroResult: String = "",
)

enum class ProStep { REVIEW, DRAFT, FREE_AGENCY, SUMMER }

/** A draft prospect on Draft Night: the player (not on a team yet), his place on the consensus board, his school and college line, and how well the user has scouted him. */
@Serializable
class Prospect(
    val p: Player, val rank: Int, val school: String, val line: String,
    /** Hidden: how far the first look misses his real overall and ceiling (scouting closes the gap). */
    val noiseO: Int, val noiseP: Int,
    /** 0 to 3 visits by the user's scouts. */
    var scout: Int = 0,
)

/** One pick made on Draft Night, with its grade (where he was on the board against where he went). */
@Serializable
class DraftPickMade(val pick: Int, val teamId: Int, val pid: Int, val name: String, val pos: String, val rank: Int, val grade: String)

/** The NBA coach's Draft Night, in progress: the order (a team for each pick), the prospects left, the picks made, scouting points. */
@Serializable
class DraftState(
    val season: Int,
    val order: MutableList<Int>,
    val prospects: MutableList<Prospect>,
    var at: Int = 0,
    val log: MutableList<DraftPickMade> = mutableListOf(),
    var scoutPoints: Int = 10,
    var done: Boolean = false,
)

/** Pro league state that the college game doesn't have. */
@Serializable
class ProState(
    val series: MutableList<Series> = mutableListOf(),
    val seeds: MutableMap<Int, Int> = mutableMapOf(),
    val allStars: MutableList<Int> = mutableListOf(),
    var allStarSeason: Int = 0,
    var allStarNote: String = "",
    var champion: Int = -1,
    val history: MutableList<ProSeasonSummary> = mutableListOf(),
    var step: ProStep = ProStep.FREE_AGENCY,
    /** Draft Night, while the NBA coach's draft is on (null once free agency opens, and in player careers). */
    var draftNight: DraftState? = null,
    /** The last Summer League: champion, MVP, and the lines worth keeping. */
    var summerLeague: SummerRecap? = null,
    /** Rookies the user's team drafted this summer, and the order the lottery set. */
    val draftedByUser: MutableList<Int> = mutableListOf(),
    val retiredThisSummer: MutableList<String> = mutableListOf(),
    /** The rival, if he was still in college when the user turned pro: he enters next year's draft. */
    var pendingRival: Player? = null,
    /** Growth over the last summer: player id to overall before. */
    val before: MutableMap<Int, Int> = mutableMapOf(),
    /** Every trade, newest last, and the players already moved this season (nobody is traded twice in one). */
    val trades: MutableList<Trade> = mutableListOf(),
    val moved: MutableList<Int> = mutableListOf(),
    /** All-time list scores of players who retired in this league, by name. */
    val frozen: MutableMap<String, Double> = mutableMapOf(),
    /** This season's calendar: the All-Star break, the trade deadline and the last regular-season day (58-game defaults for older saves). */
    var allStarDay: Int = 30,
    var deadline: Int = 40,
    var lastDay: Int = 59,
    /** The records book, and this summer's star free agents. */
    val records: MutableList<BookRecord> = mutableListOf(),
    val freeStars: MutableList<Int> = mutableListOf(),
    /** The NBA Cup: six groups of five (East first), the knockout bracket, its days and its winner. */
    val cupGroups: MutableList<MutableList<Int>> = mutableListOf(),
    val cup: MutableList<Series> = mutableListOf(),
    val cupSeeds: MutableMap<Int, Int> = mutableMapOf(),
    var cupQfDay: Int = 0,
    var cupSfDay: Int = 0,
    var cupFinalDay: Int = 0,
    var cupChampion: Int = -1,
    var cupMvp: String = "",
    var cupNote: String = "",
    /** Game 83: Rivalry Night. */
    var rivalryDay: Int = 0,
    /** All-Star Saturday and the Rising Stars: one line per event. */
    val weekend: MutableList<String> = mutableListOf(),
    /** Lottery night: the 14 lottery teams worst first, and the order the balls set. */
    val lotteryPre: MutableList<Int> = mutableListOf(),
    val lotteryPicks: MutableList<Int> = mutableListOf(),
    var lotterySeason: Int = 0,
    /** Awards night: the MVP vote. */
    val mvpVotes: MutableList<Vote> = mutableListOf(),
    /** Era mode: the season this career started in (an [Era] name). */
    var era: String = "TODAY",
    /** The league follows the real list of teams each season (expansions and moves); games a team plays this season. */
    var realTeams: Boolean = false,
    /** A future league's growth: the season new teams join and their cities, and a franchise on the move. */
    var expandAt: Int = 0,
    val expandWith: MutableList<String> = mutableListOf(),
    var moveAt: Int = 0,
    var moveTeam: Int = -1,
    var moveTo: String = "",
    var games: Int = 0,
    /** Every honor of this season (weekly and monthly awards, awards night, the playoffs, the Cup and All-Star), and the season awards of past ones. */
    val honors: MutableList<Honor> = mutableListOf(),
    /** Season stats and team records (wins, losses, points for and against) when this week and month began, to measure them. */
    val weekStart: MutableMap<Int, StatLine> = mutableMapOf(),
    val monthStart: MutableMap<Int, StatLine> = mutableMapOf(),
    val weekTeams: MutableMap<Int, IntArray> = mutableMapOf(),
    val monthTeams: MutableMap<Int, IntArray> = mutableMapOf(),
    var weekDay: Int = 0,
    var week: Int = 0,
    var month: Int = 0,
    /** Each team's wins last season, for Coach and Executive of the Year. */
    val lastWins: MutableMap<Int, Int> = mutableMapOf(),
    val allStarStarters: MutableList<Int> = mutableListOf(),
    /** This summer's free-agent signings and coaching changes around the league, for the offseason screens. */
    val faMoves: MutableList<String> = mutableListOf(),
    val staffMoves: MutableList<String> = mutableListOf(),
    /** Future first-round picks and who owns them; each team's general manager. */
    val picks: MutableList<PickAsset> = mutableListOf(),
    val execs: MutableMap<Int, String> = mutableMapOf(),
    /** This season's preseason magazine. */
    var preview: NbaPreview? = null,
    /** NBA coach mode: the user's own expiring players he wants back, his free-agent offers (player id to salary), */
    val userKeep: MutableList<Int> = mutableListOf(),
    /** NBA coach mode: the front office re-signs and extends the players worth keeping. */
    var autoResign: Boolean = false,
    val userOffers: MutableMap<Int, Int> = mutableMapOf(),
    /** the owner's target for this season (projected wins), and whether he's just been fired (with the teams that called). */
    var expect: Int = -1,
    var fired: Boolean = false,
    val jobOffers: MutableList<Int> = mutableListOf(),
    /** This summer's results for the user's team: who signed, re-signed or left. */
    val userMoves: MutableList<String> = mutableListOf(),
    /** A coach who came up from college: his college record in a line. */
    var collegeResume: String = "",
    /** Last week's power rankings, best first, for movement. */
    val powerLast: MutableList<Int> = mutableListOf(),
    /** The projection re-run during the season (results so far, current strength), and the day it was run. */
    var live: Map<Int, NbaProj> = emptyMap(),
    var liveDay: Int = 0,
    /** Trade offers other teams have made the user (NBA coach mode). */
    val tradeOffers: MutableList<TradeOffer> = mutableListOf(),
    /** Per-team Trade Block (player ids), Untouchables (player ids), and custom positional Needs / Surplus. */
    val onTheBlock: MutableMap<Int, MutableList<Int>> = mutableMapOf(),
    val untouchables: MutableMap<Int, MutableList<Int>> = mutableMapOf(),
    val customNeeds: MutableMap<Int, MutableList<Pos>> = mutableMapOf(),
    val customSurplus: MutableMap<Int, MutableList<Pos>> = mutableMapOf(),
    /** Retained trade salaries and stretched contract buyouts per team (max 3 active per team). */
    val retained: MutableMap<Int, MutableList<RetainedSalary>> = mutableMapOf(),
    /** Interactive Trade Deadline Day hour (0 = not started, 1..5 = hours remaining, 6 = deadline passed). */
    var deadlineHour: Int = 0,
    /** Interactive multi-round Free Agency frenzy state (0 = not open, 1..6 = current round, 7 = finished). */
    var faRound: Int = 0,
    val faCases: MutableList<FaCase> = mutableListOf(),
    val faUsedMle: MutableList<Int> = mutableListOf(),
    val faWatchlist: MutableList<Int> = mutableListOf(),
    val pendingRfaMatches: MutableMap<Int, ContractBid> = mutableMapOf(),
)

/** Dead cap from retaining up to 50% of a contract in a trade or stretching a buyout. */
@Serializable
class RetainedSalary(
    val pid: Int,
    val name: String,
    val amount: Int,
    var yearsLeft: Int,
    val kind: String = "Retained",
)

/** A live contract bid in Free Agency (from the user or an AI team). */
@Serializable
class ContractBid(
    val teamId: Int,
    var salary: Int,
    var years: Int,
    var clause: String = "",
    var structure: String = "FLAT",
    val bird: Boolean = false,
    val mle: Boolean = false,
    val rfaSheet: Boolean = false,
    val roundPlaced: Int = 1,
)

/** A free agent on the open market during the multi-round Free Agency frenzy. */
@Serializable
class FaCase(
    val pid: Int,
    val fromTeamId: Int,
    val askSalary: Int,
    val askYears: Int = 2,
    val askClause: String = "",
    val restricted: Boolean = false,
    val bids: MutableList<ContractBid> = mutableListOf(),
    var signedTeamId: Int = -1,
    var statusNote: String = "",
)

/** A first-round pick in [season]'s draft, originally [original]'s, now [owner]'s, with optional top-[protection] protection. */
@Serializable
class PickAsset(val season: Int, val original: Int, var owner: Int, var protection: Int = 0)

/**
 * One honor: [award] like "Player of the Week" or "MVP", for a player ([pid]) or a coach or front
 * office ([pid] -1). [period] names the week or month ("Week 3", "December"); [conf] is 0 East,
 * 1 West, or -1 league-wide.
 */
@Serializable
class Honor(
    val season: Int, val day: Int, val award: String, val pid: Int, val name: String, val teamId: Int,
    val line: String = "", val period: String = "", val conf: Int = -1,
)

/** A pop-up for a big moment: an award, an All-Star nod, a trade, a record, a title. */
@Serializable
class Alert(val title: String, val body: String, val kind: String = "")

/** One player's share of the MVP vote: 100 voters, 10-7-5-3-1. */
@Serializable
class Vote(val pid: Int, val name: String, val teamId: Int, val points: Int, val firsts: Int)

/** One night in his playoff highlight reel. */
@Serializable
class ReelEntry(val season: Int, val title: String, val line: String, val big: Boolean = false)

/**
 * A record: [scope] is "Game", "Season" or "Career". [base] is a real holder's total before this
 * league, so an active holder's record keeps growing as he plays.
 */
@Serializable
class BookRecord(
    val key: String, val label: String, val scope: String, var value: Double, var holder: String, var season: Int,
    var mine: Boolean = false, val base: Double = 0.0, val decimals: Int = 0,
)

/** One head-to-head game against the class rival. */
@Serializable
class Duel(val season: Int, val gameId: Int, val opp: Int, val me: Int, val him: Int, val won: Boolean)

/** Summer invitations: camps sharpen three ratings; the U19 national team raises everything a little and the profile a lot. */
enum class SummerEvent(val label: String, val blurb: String, val boosts: List<Attr>) {
    SHOOTING_CAMP("Elite Shooting Camp", "A week of game-speed reps with pro shooting coaches.", listOf(Attr.OUT, Attr.MID, Attr.INS)),
    GUARD_CAMP("Elite Guard Camp", "Pick-and-roll reads, ball pressure and a handle that holds up against the best guards in the country.", listOf(Attr.HND, Attr.PAS, Attr.PER)),
    WING_CAMP("Elite Wing Camp", "Attack closeouts, finish through contact and guard the other team's best scorer.", listOf(Attr.INS, Attr.PER, Attr.STL)),
    BIGS_CAMP("Elite Bigs Camp", "Footwork, post counters and rim protection with former pros.", listOf(Attr.INS, Attr.REB, Attr.IDF)),
    LOCKDOWN_CAMP("Lockdown Defense Camp", "Slides, deflections and contests until your legs give out.", listOf(Attr.PER, Attr.STL, Attr.BLK)),
    U19("U19 National Team", "Represent the USA at the U19 World Cup against the best teenagers on the planet.", emptyList()),
    NATIONAL_TEAM("Team USA", "The best players in the country, one summer, one gold medal to chase.", emptyList()),
}

/** People the user's player can pay out of his NIL money, one season at a time. Costs in thousands. */
enum class Staff(val label: String, val blurb: String, val cost: Int) {
    TRAINER("Personal trainer", "Develop 25% faster from games and practice.", 150),
    NUTRITIONIST("Nutritionist", "Fatigue builds a quarter slower, and you recover faster between games.", 90),
    SHOOTING_COACH("Shooting coach", "+3% on jump shots, and +5% when you take the big shot late.", 120),
    AGENT("Agent", "Negotiates 15% more on NIL deals, bonuses and sponsor payouts.", 80),
}

/** The weekly practice plan in player mode. */
enum class Practice(val label: String, val blurb: String, val tiring: Int, val boosts: List<Attr>, val xp: Int) {
    TEAM("Team practice", "The normal schedule: steady work on your training focus.", 3, emptyList(), 14),
    SHOOTING("Shooting reps", "Extra shots after practice. Inside, Mid-Range and 3-Point grow faster.", 7, listOf(Attr.INS, Attr.MID, Attr.OUT), 40),
    FILM("Film study", "Watch tape. Handling, Passing and Perimeter D grow. Easy on the legs.", 2, listOf(Attr.HND, Attr.PAS, Attr.PER), 30),
    WEIGHTS("Weight room", "Lift heavy. Inside, Rebound and Interior D grow. The most tiring.", 9, listOf(Attr.INS, Attr.REB, Attr.IDF), 40),
    REST("Rest", "No extra work. Fatigue drops fast.", -12, emptyList(), 0),
}

/** One answer at the postgame podium and what it does. */
@Serializable
class PressAnswer(
    val text: String,
    /** Follower change as a share of the current following (0.05 = +5%). */
    val followers: Double,
    /** Change to the school's fan mood. */
    val fans: Int = 0,
    /** Change to teammates' happiness. */
    val teammates: Int = 0,
    val persona: Int = 0,
)

@Serializable
class PressQuestion(val season: Int, val gameId: Int, val topic: String, val prompt: String, val answers: List<PressAnswer>, val reporter: String = "")

/** A social post about the user's player: the team account, fans, doubters, analysts, highlight accounts, teammates, the rival. */
/** One reaction to a story: who said it (and in what role), what they said, and how many liked it. */
@Serializable
class Reaction(val who: String, val role: String, val text: String, val likes: Int, val avatar: String = "", val verified: Boolean = false, val tone: Int = 0)

@Serializable
class Post(
    val season: Int, val day: Int, val name: String, val handle: String, val kind: String, val text: String,
    val likes: Int, val verified: Boolean = false, val gameId: Int = -1,
    /** A real account's avatar (assets/social/<avatar>.webp), or empty. */
    val avatar: String = "",
    /** The story it's about (a headline in the news), for the league feed: tap to read it. */
    val re: String = "",
)

@Serializable
class OffseasonState(
    var step: OffStep = OffStep.ROSTER,
    val graduates: MutableList<GradEntry> = mutableListOf(),
    val entrants: MutableList<Int> = mutableListOf(),
    val pitched: MutableList<Int> = mutableListOf(),
    val transferredOut: MutableList<String> = mutableListOf(),
    val released: MutableList<String> = mutableListOf(),
    val offers: MutableList<JobOffer> = mutableListOf(),
    var fired: Boolean = false,
    /** NBA teams that want the user (their places in today's league: "BOS", "LAL"...). */
    val nbaOffers: MutableList<String> = mutableListOf(),
    /** Conferences inviting the user's school to join. */
    val confInvites: MutableList<ConfInvite> = mutableListOf(),
    val growth: MutableList<Growth> = mutableListOf(),
    var round: Int = 1,
    val signed: MutableList<Int> = mutableListOf(),
    var freedGrad: Int = 0,
    var freedDraft: Int = 0,
    var freedOther: Int = 0,
    var draftedCount: Int = 0,
    val tryouts: MutableList<Tryout> = mutableListOf(),
    /** The user's walk-ons who broke out this summer. */
    val breakouts: MutableList<Int> = mutableListOf(),
    /** Legends the user's program honored this offseason. */
    val honors: MutableList<Legend> = mutableListOf(),
    /** Closer looks left at tryouts: each one reveals a candidate's overall. */
    var looks: Int = 0,
    /** The last recruiting round's results. */
    var report: RoundReport? = null,
    /** Prospects nobody signed when the cycle ended. */
    var unsigned: Int = 0,
    /** User roster players with a pending personal shoe offer this offseason. */
    val shoeOffers: MutableList<Int> = mutableListOf(),
)

@Serializable
class League(
    var season: Int,
    var day: Int = 1,
    var phase: Phase = Phase.REGULAR,
    val teams: MutableList<Team>,
    val players: MutableList<Player>,
    val coaches: MutableList<Coach>,
    val confs: List<Conference>,
    var games: MutableList<Game> = mutableListOf(),
    var confBrackets: MutableList<Bracket> = mutableListOf(),
    var ncaa: Bracket? = null,
    var nit: Bracket? = null,
    var bubble: Bubble? = null,
    val rivals: MutableList<Rivalry> = mutableListOf(),
    var rivalsReady: Boolean = false,
    /** The highlight reel: upsets, buzzer-beaters and other big moments, every season. */
    val moments: MutableList<Moment> = mutableListOf(),
    /** Former players whose careers stay on record: the user's, every draft pick and All-Americans. */
    val alumni: MutableList<Player> = mutableListOf(),
    /** This season's preview magazine. */
    var preview: Preview? = null,
    var events: MutableList<InSeasonEvent> = mutableListOf(),
    var recruits: MutableList<Recruit> = mutableListOf(),
    var userTeamId: Int,
    var userCoachId: Int,
    var nextPlayerId: Int,
    var nextGameId: Int = 1,
    var nextCoachId: Int,
    val news: MutableList<News> = mutableListOf(),
    val history: MutableList<SeasonSummary> = mutableListOf(),
    val draftHistory: MutableList<DraftPick> = mutableListOf(),
    val hallOfFame: MutableList<HofEntry> = mutableListOf(),
    val records: MutableList<RecordEntry> = mutableListOf(),
    var wrapUp: WrapUp? = null,
    var offseason: OffseasonState? = null,
    /** Scouting points: refill every game day in season and every round in the offseason. */
    var recruitPoints: Int = 0,
    /** The user's recruiting points for the whole offseason cycle. */
    var recruitBudget: Int = 0,
    var seasonAwards: MutableList<AwardWinner> = mutableListOf(),
    var confAwards: MutableList<AwardWinner> = mutableListOf(),
    var cotyCoachId: Int = -1,
    var apHistory: MutableList<Int> = mutableListOf(),
    var norms: DoubleArray? = null,
    /** Coach a program, or play one player's college career ("Road to the League"). */
    var mode: GameMode = GameMode.COACH,
    /** In player mode, the user's player. */
    var heroId: Int = -1,
    var road: RoadState? = null,
    var level: Level = Level.COLLEGE,
    var proState: ProState? = null,
    /** Era mode from the start: the college years are that era's ("TODAY" otherwise), and the real players already placed (their data id to player id). */
    var era: String = "TODAY",
    val eraIds: MutableMap<String, Int> = mutableMapOf(),
    /** Big moments waiting to pop up for the user, oldest first. */
    val alerts: MutableList<Alert> = mutableListOf(),
    /** How many news stories have been through the reactions pass. */
    var newsSeen: Int = 0,
    /** A coach who played first (Road to the League): his playing career, carried from save to save. */
    var playedAs: PlayedAs? = null,
    /** The NBA league he played in, packed (SaveIO.pack) while he coaches in college, so a later NBA job returns to the same world. */
    var proWorld: String = "",
    /** A big moment that takes the whole screen until it's dismissed: his retirement, or his introduction as a coach. */
    var ceremony: Ceremony? = null,
    /** Moments waiting their turn behind [ceremony]. */
    var ceremonyQueue: MutableList<Ceremony> = mutableListOf(),
    /** The trophy room: every achievement unlocked across the career (carried from save to save). */
    var achievements: MutableList<Achievement> = mutableListOf(),
    var trophiesSeeded: Boolean = false,
    /** The owner's or AD's goals for this season, and what each one is worth to his job. */
    var goals: MutableList<Goal> = mutableListOf(),
    var goalStake: Int = 0,
    /** Team USA: this summer's call (if any), and the gold medals he's coached. */
    var usa: UsaTeam? = null,
    /** The defending champion's season: who, how many titles in a row, and which stories have run. */
    var defense: TitleRun? = null,
    /** The user's assistant coaches, this offseason's candidates, and the coaching tree: assistants who became head coaches. */
    var staff: MutableList<Assistant> = mutableListOf(),
    var staffPool: MutableList<Assistant> = mutableListOf(),
    var staffPoolSeason: Int = 0,
    var tree: MutableList<TreeBranch> = mutableListOf(),
    var nextAssistantId: Int = 1,
    /** The user's head-to-head with every coach he's faced, and how heated each one is. */
    var coachRivals: MutableList<CoachRival> = mutableListOf(),
    /** Draw every player as a retro bit portrait, real headshots included. */
    var retroAll: Boolean = false,
    /** Picks every social account's profile picture: random for each save, the same all through it. */
    var pfpSeed: Long = 0,
    /** This week's sports-TV debate about the user (see [TvShow]). */
    var tv: TvSegment? = null,
    /** The last few debate topics, so the show doesn't repeat itself. */
    val tvKinds: MutableList<String> = mutableListOf(),
    /** Jersey sales: the top 15 (player ids), the list before, and the season they're for. */
    val jerseyTop: MutableList<Int> = mutableListOf(),
    val jerseyLast: MutableList<Int> = mutableListOf(),
    var jerseySeason: Int = 0,
    /** Highlights (see [Highlights]): today's best plays so far, the last night's Top 10 and its day, and the user's own best plays this season. */
    val dayPlays: MutableList<Highlight> = mutableListOf(),
    var topPlays: List<Highlight> = emptyList(),
    var topDay: Int = -1,
    var topSeason: Int = 0,
    val myPlays: MutableList<Highlight> = mutableListOf(),
    var usaGolds: Int = 0,
    var ollama: OllamaConfig = OllamaConfig(),
) {
    @Transient private var playerIndex: HashMap<Int, Player> = HashMap()
    @Transient private var gameIndex: HashMap<Int, Game> = HashMap()
    @Transient private var coachIndex: HashMap<Int, Coach> = HashMap()
    @Transient private var rivalIndex: HashMap<Long, Rivalry>? = null

    fun reindex() {
        playerIndex = HashMap(players.size * 2)
        players.forEach { playerIndex[it.id] = it }
        gameIndex = HashMap(games.size * 2)
        games.forEach { gameIndex[it.id] = it }
        coachIndex = HashMap()
        coaches.forEach { coachIndex[it.id] = it }
    }

    fun player(id: Int): Player = playerIndex[id] ?: players.first { it.id == id }.also { playerIndex[id] = it }
    fun playerOrNull(id: Int): Player? = playerIndex[id] ?: players.firstOrNull { it.id == id }?.also { playerIndex[id] = it }
    /** A current player or, failing that, a former one. */
    fun anyPlayer(id: Int): Player? = playerOrNull(id) ?: alumni.firstOrNull { it.id == id }
    fun addPlayer(p: Player) { players.add(p); playerIndex[p.id] = p }
    fun removePlayer(p: Player) { players.remove(p); playerIndex.remove(p.id) }

    fun game(id: Int): Game = gameIndex[id] ?: games.first { it.id == id }.also { gameIndex[id] = it }
    fun addGame(g: Game) { games.add(g); gameIndex[g.id] = g }

    fun team(id: Int) = teams[id]
    fun conf(id: Int) = confs[id]
    fun coach(id: Int): Coach = coachIndex[id] ?: coaches.first { it.id == id }.also { coachIndex[id] = it }
    fun coachOf(t: Team): Coach = coach(t.coachId)

    val user get() = teams[userTeamId]
    val userCoach get() = coach(userCoachId)

    val playerMode get() = mode == GameMode.PLAYER
    val pro get() = level == Level.PRO
    /** True when the user runs this team's bench. In player mode every team is coached by the AI. */
    fun humanCoach(t: Team) = mode == GameMode.COACH && t.id == userTeamId
    fun humanCoach(teamId: Int) = mode == GameMode.COACH && teamId == userTeamId
    /** The school the user recruits for, or -1 in player mode (every school recruits on its own). */
    val recruiterId get() = if (mode == GameMode.COACH) userTeamId else -1
    /** The user's player in player mode, on a roster or (once his college career is over) among the alumni. */
    val hero: Player? get() = if (heroId < 0) null else anyPlayer(heroId)

    fun rosterOf(t: Team): List<Player> = t.roster.map { player(it) }

    fun rivalry(x: Int, y: Int): Rivalry? {
        val idx = rivalIndex?.takeIf { it.size == rivals.size } ?: HashMap<Long, Rivalry>().also { m ->
            for (r in rivals) m[pairKey(r.a, r.b)] = r
            rivalIndex = m
        }
        return idx[pairKey(x, y)]
    }

    fun rivalry(g: Game): Rivalry? = rivalry(g.home, g.away)

    fun rivalsOf(teamId: Int): List<Rivalry> = rivals.filter { it.involves(teamId) }

    private fun pairKey(x: Int, y: Int): Long = if (x < y) x.toLong() * 100_000 + y else y.toLong() * 100_000 + x
}
