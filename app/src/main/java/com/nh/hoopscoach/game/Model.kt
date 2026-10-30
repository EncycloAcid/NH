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
    RIM_PROTECTOR("Rim Protector", "RIM", listOf(Attr.IDF, Attr.BLK, Attr.REB), "Anchors the defense and erases mistakes at the rim");
}

enum class Trait(val label: String, val good: Boolean) {
    AMBITIOUS("Ambitious", false),
    BAG_CHASER("Bag Chaser", false),
    DISLOYAL("Disloyal", false),
    LOYAL("Loyal", true),
    TEAM_PLAYER("Team Player", true),
    GYM_RAT("Gym Rat", true),
    CLUTCH("Clutch", true),
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

enum class GameType(val short: String) { NONCONF("NC"), CONF("CONF"), EVENT("EVT"), CONF_TOURNEY("CT"), NCAA("NCAA") }

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
class SeasonLine(val season: Int, val team: String, val conf: String, val cls: Int, val ovr: Int, val s: StatLine)

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
) {
    val name get() = "$school $mascot"
    val gp get() = w + l
    val winPct get() = if (gp == 0) 0.0 else w.toDouble() / gp
    val record get() = "$w-$l"
    val confRecord get() = "$cw-$cl"
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
) {
    val done get() = champion >= 0
}

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
    val p: Player,
    var interest: MutableMap<Int, Int> = mutableMapOf(),
    var offers: MutableList<Int> = mutableListOf(),
    var committed: Int = -1,
    var scouted: Boolean = false,
    var onBoard: Boolean = false,
    var lastVisit: Int = -99,
    var nilAsk: Int = 0,
    var nilOffer: Int = 0,
    var estOvr: Int = 0,
    var estPot: Int = 0,
    var transfer: Boolean = false,
)

@Serializable
class News(val season: Int, val day: Int, val text: String, val mine: Boolean = false)

@Serializable
class DraftPick(val season: Int, val pick: Int, val playerName: String, val pos: Pos, val teamId: Int, val school: String, val cls: Int, val ovr: Int, val look: Int = 0, val number: Int = 0)

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
)

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
    BUDGET("NIL Budget"), RECRUITING("Recruiting"),
}

@Serializable
class GradEntry(val pid: Int, val name: String, val pos: Pos, val ovr: Int, val nil: Int, val line: String, val note: String)

@Serializable
class Growth(val pid: Int, val before: Int, val after: Int, val gains: IntArray, val weight: Int)

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
    var recruitPoints: Int = 0,
    var seasonAwards: MutableList<AwardWinner> = mutableListOf(),
    var confAwards: MutableList<AwardWinner> = mutableListOf(),
    var cotyCoachId: Int = -1,
    var apHistory: MutableList<Int> = mutableListOf(),
    var norms: DoubleArray? = null,
) {
    @Transient private var playerIndex: HashMap<Int, Player> = HashMap()
    @Transient private var gameIndex: HashMap<Int, Game> = HashMap()
    @Transient private var coachIndex: HashMap<Int, Coach> = HashMap()

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

    fun rosterOf(t: Team): List<Player> = t.roster.map { player(it) }
}
