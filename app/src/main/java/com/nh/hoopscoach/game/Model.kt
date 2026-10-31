package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

enum class Pos { PG, SG, SF, PF, C }

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
    SLASHER("Slasher", "SLA", listOf(Attr.INS, Attr.PER, Attr.STL), "Explosive scorer who attacks the rim and finishes through contact"),
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
    ENFORCER("Enforcer", "ENF", listOf(Attr.INS, Attr.IDF, Attr.REB), "Physical big who owns the paint on both ends");
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

enum class Phase { REGULAR, CONF_TOURNEY, NCAA, WRAP_UP, OFFSEASON }

enum class GameType(val short: String) { NONCONF("NC"), CONF("CONF"), EVENT("EVT"), CONF_TOURNEY("CT"), NCAA("NCAA"), NIT("NIT") }

@Serializable
class StatLine(
    var gp: Int = 0, var gs: Int = 0, var sec: Int = 0,
    var pts: Int = 0, var fgm: Int = 0, var fga: Int = 0, var tpm: Int = 0, var tpa: Int = 0,
    var ftm: Int = 0, var fta: Int = 0, var orb: Int = 0, var drb: Int = 0, var ast: Int = 0,
    var stl: Int = 0, var blk: Int = 0, var tov: Int = 0, var pf: Int = 0,
) {
    val reb get() = orb + drb
    fun add(o: StatLine) {
        gp += o.gp; gs += o.gs; sec += o.sec; pts += o.pts; fgm += o.fgm; fga += o.fga; tpm += o.tpm; tpa += o.tpa
        ftm += o.ftm; fta += o.fta; orb += o.orb; drb += o.drb; ast += o.ast; stl += o.stl; blk += o.blk; tov += o.tov; pf += o.pf
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
) {
    val name get() = "$first $last"
    val short get() = "${first.first()}. $last"
    val ovr: Int get() = Ratings.ovr(pos, attrs)
    operator fun get(a: Attr) = attrs[a.ordinal]
    fun has(t: Trait) = t in traits
    val cls get() = classLabel(year)
}

fun classLabel(year: Int) = when (year) { 0 -> "HS"; 1 -> "FR"; 2 -> "SO"; 3 -> "JR"; 4 -> "SR"; else -> "GR" }

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
class BoxScore(val home: List<PlayerLine>, val away: List<PlayerLine>, val halves: List<IntArray>)

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
    var scouted: Boolean = false,
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
class News(val season: Int, val day: Int, val text: String, val mine: Boolean = false, val gameId: Int = -1, val kind: String = "") {
    /** Rivalry results carry their game so the headline can link to the box score. */
    val rivalry get() = kind == "rivalry" || (kind.isEmpty() && gameId >= 0)
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
class DraftPick(val season: Int, val pick: Int, val playerName: String, val pos: Pos, val teamId: Int, val school: String, val cls: Int, val ovr: Int, val look: Int = 0, val number: Int = 0, val pid: Int = -1)

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
    var seasonStartFollowers: Int = 0,
    /** Media persona from press answers: negative humble, positive brash. */
    var persona: Int = 0,
    /** Follower milestones already paid out by sponsors. */
    var milestone: Int = 0,
    /** The open postgame press question, if any. */
    var press: PressQuestion? = null,
    val pressLog: MutableList<String> = mutableListOf(),
)

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
class PressQuestion(val season: Int, val gameId: Int, val topic: String, val prompt: String, val answers: List<PressAnswer>)

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
