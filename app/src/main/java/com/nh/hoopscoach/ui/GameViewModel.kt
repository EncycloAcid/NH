package com.nh.hoopscoach.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nh.hoopscoach.game.GameEngine
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Season
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab(val label: String) { TODAY("Today"), TEAM("Team"), RECRUIT("Recruit"), LEAGUE("League"), CAREER("Career") }

enum class HistoryKind(val title: String, val sub: String) {
    SEASONS("Season Review", "Champions, awards and Final Fours by year"),
    MOMENTS("Highlight Reel", "Upsets, buzzer-beaters and Cinderella runs"),
    AWARDS("Champions & Awards", "Every national award winner"),
    DRAFT("Draft History", "Every pick, every year"),
    HOF("Hall of Fame", "Inducted players and coaches"),
    RECORDS("Record Book", "Single-game records"),
}

sealed interface Route {
    data class TeamPage(val teamId: Int) : Route
    data class Box(val gameId: Int) : Route
    data object Live : Route
    data class BracketView(val confId: Int) : Route
    data class Schedule(val teamId: Int) : Route
    data class History(val kind: HistoryKind) : Route
    data object Settings : Route
    data class EditTeam(val teamId: Int) : Route
    data class Focus(val playerId: Int) : Route
    data class RecruitView(val playerId: Int) : Route
    data object Leaders : Route
    data object CoachDev : Route
    data object WrapUp : Route
    data object Preview : Route
    data class EditPlayer(val playerId: Int) : Route
    data object RecruitPreview : Route
}

/** A game being watched possession by possession. */
class LiveGame(val engine: GameEngine) {
    var tick by mutableIntStateOf(0)
    var speed by mutableIntStateOf(2)
    var done by mutableStateOf(false)
    /** Player mode: the big moment waiting for a choice, and how the last one went. */
    var moment by mutableStateOf<GameEngine.BigMoment?>(null)
    var momentResult by mutableStateOf<String?>(null)
}

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "dynasty.sav")
    private val mutex = Mutex()

    var league by mutableStateOf<League?>(null)
        private set
    /** A generated league waiting for the user to pick a school. */
    var draft by mutableStateOf<League?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var rev by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var tab by mutableStateOf(Tab.TODAY)
    val stack = mutableStateListOf<Route>()
    var sheet by mutableStateOf<Int?>(null)
    /** The open in-app guide, if any, and its tab. */
    var guide by mutableStateOf<Guide?>(null)
    var guideTab by mutableIntStateOf(0)
    var live by mutableStateOf<LiveGame?>(null)
    var message by mutableStateOf<String?>(null)

    // Sub-tab memory so switching tabs doesn't reset what you were looking at.
    var teamTab by mutableIntStateOf(0)
    /** The playoff round shown on the bracket page; -1 follows the current round. */
    var proRound by mutableIntStateOf(-1)
    var leagueConf by mutableIntStateOf(-1)
    var leagueTab by mutableIntStateOf(0)
    var awardsScope by mutableIntStateOf(0)
    var wrapPage by mutableIntStateOf(0)
    var recruitView by mutableIntStateOf(1)
    var recruitFilter by mutableIntStateOf(0)
    var offRecruitTab by mutableIntStateOf(0)
    var draftTab by mutableIntStateOf(0)
    var recruitPos by mutableStateOf<com.nh.hoopscoach.game.Pos?>(null)
    /** Teams picked for the Bubble Watch resume comparison (up to two). */
    val bubblePick = mutableStateListOf<Int>()

    companion object {
        /** Tests turn this off and install a league themselves. */
        var autoLoad = true
    }

    init {
        if (autoLoad) viewModelScope.launch {
            val lg = withContext(Dispatchers.IO) {
                runCatching { if (file.exists()) SaveIO.decode(file.readBytes()) else null }.getOrNull()
            }
            league = lg
            loading = false
            if (lg == null) generateDraft()
        }
    }

    /** For tests: show the new-career screen with a given league. */
    fun installDraft(lg: League) {
        draft = lg
        busy = null
        loading = false
    }

    /** For tests and previews. */
    fun install(lg: League) {
        league = lg
        loading = false
        rev++
    }

    private fun bump() {
        rev++
        scheduleSave()
    }

    private var saveJob: Job? = null
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            saveNow()
        }
    }

    fun saveSoon() {
        viewModelScope.launch { saveNow() }
    }

    private suspend fun saveNow() {
        val lg = league ?: return
        val bytes = mutex.withLock { withContext(Dispatchers.Default) { SaveIO.encode(lg) } }
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.path + ".tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
        }
    }

    fun toast(text: String) { message = text }

    /** Quick change to the league; runs on the main thread. */
    fun act(block: (League) -> Unit) {
        val lg = league ?: return
        viewModelScope.launch {
            mutex.withLock { block(lg) }
            bump()
        }
    }

    /** Longer work: the UI swaps to a progress screen until it finishes. */
    private fun heavy(label: String, block: suspend (League) -> Unit) {
        val lg = league ?: return
        if (busy != null) return
        busy = label
        viewModelScope.launch {
            delay(60) // let the UI leave any screen that reads the league
            mutex.withLock { withContext(Dispatchers.Default) { block(lg) } }
            busy = null
            bump()
        }
    }

    // ---------- new game ----------

    fun generateDraft() {
        busy = "Building your league"
        viewModelScope.launch {
            val lg = withContext(Dispatchers.Default) { LeagueGen.newLeague() }
            draft = lg
            busy = null
        }
    }

    // ---------- Road to the League ----------

    /** The player being created on the new game screen, and the schools offering him. */
    var roadHero by mutableStateOf<com.nh.hoopscoach.game.Player?>(null)
    var roadOffers by mutableStateOf<List<com.nh.hoopscoach.game.SchoolOffer>>(emptyList())

    /** Draft Night: how many picks are in so far, for [revealSeason]. Lives here so a tab switch doesn't restart the night. */
    var reveal by mutableIntStateOf(0)
    var revealSeason by mutableIntStateOf(-1)

    fun roadFindOffers(spec: com.nh.hoopscoach.game.Road.Spec) {
        val d = draft ?: return
        busy = "Coaches are calling"
        viewModelScope.launch {
            val (h, o) = withContext(Dispatchers.Default) {
                val h = com.nh.hoopscoach.game.Road.createHero(d, spec)
                h to com.nh.hoopscoach.game.Road.offers(d, h)
            }
            roadHero = h
            roadOffers = o
            busy = null
        }
    }

    fun roadBack() { roadHero = null; roadOffers = emptyList() }

    fun startRoad(teamId: Int) {
        val d = draft ?: return
        val h = roadHero ?: return
        busy = "Signing your scholarship"
        viewModelScope.launch {
            withContext(Dispatchers.Default) { com.nh.hoopscoach.game.Road.start(d, h, teamId) }
            draft = null
            roadHero = null
            roadOffers = emptyList()
            league = d
            tab = Tab.TODAY
            stack.clear()
            busy = null
            bump()
        }
    }

    /** One step of the player-mode offseason. */
    /** Draft Night is over: build the pro league and move the career into it. */
    fun goPro(era: com.nh.hoopscoach.game.Era = com.nh.hoopscoach.game.Era.TODAY) {
        val lg = league ?: return
        if (busy != null || !(lg.road?.goingPro ?: false)) return
        busy = "Welcome to the league"
        viewModelScope.launch {
            delay(60)
            val pro = mutex.withLock { withContext(Dispatchers.Default) { com.nh.hoopscoach.game.ProLeague.create(lg, era) } }
            stack.clear()
            sheet = null
            live = null
            tab = Tab.TODAY
            league = pro
            busy = null
            bump()
        }
    }

    /** A retired pro takes a college job: a new save in the coach game with his name and reputation. */
    fun retireToCoach(school: String) {
        val pro = league ?: return
        if (busy != null || pro.road?.retired != true) return
        busy = "Taking the job at $school"
        viewModelScope.launch {
            delay(60)
            val college = mutex.withLock {
                withContext(Dispatchers.Default) { com.nh.hoopscoach.game.Afterward.becomeCoach(pro, com.nh.hoopscoach.game.LeagueGen.newLeague(pro.season + 1), school) }
            }
            stack.clear()
            sheet = null
            live = null
            tab = Tab.TODAY
            league = college
            busy = null
            bump()
        }
    }

    /** One step through the pro offseason. */
    fun proNext() {
        val ps = league?.proState ?: return
        val label = when (ps.step) {
            com.nh.hoopscoach.game.ProStep.REVIEW -> "Draft night"
            com.nh.hoopscoach.game.ProStep.DRAFT -> "Free agency opens"
            com.nh.hoopscoach.game.ProStep.FREE_AGENCY -> "Summer"
            com.nh.hoopscoach.game.ProStep.SUMMER -> "Opening night"
        }
        heavy(label) {
            com.nh.hoopscoach.game.ProCareer.next(it)
            if (it.phase == Phase.REGULAR) tab = Tab.TODAY
        }
    }

    fun roadNext() {
        val os = league?.offseason ?: return
        val label = when (os.step) {
            com.nh.hoopscoach.game.OffStep.ROSTER -> "Running Draft Night"
            com.nh.hoopscoach.game.OffStep.DRAFT -> "Summer workouts"
            else -> "Getting ready for the new season"
        }
        heavy(label) {
            com.nh.hoopscoach.game.Road.next(it)
            wrapPage = 0
            if (it.phase == com.nh.hoopscoach.game.Phase.REGULAR) tab = Tab.TODAY
        }
    }

    fun startCareer(teamId: Int, first: String, last: String) {
        val d = draft ?: return
        busy = "Setting up your first season"
        viewModelScope.launch {
            withContext(Dispatchers.Default) { Offseason.claimTeam(d, teamId, first, last) }
            draft = null
            league = d
            tab = Tab.TODAY
            stack.clear()
            busy = null
            bump()
        }
    }

    /** Writes the current dynasty to a file the user picked. */
    fun exportSave(uri: android.net.Uri) {
        val lg = league ?: return
        viewModelScope.launch {
            val ok = runCatching {
                val bytes = mutex.withLock { withContext(Dispatchers.Default) { SaveIO.encode(lg) } }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                }
            }.isSuccess
            toast(if (ok) "Save backed up" else "Couldn't write the backup file")
        }
    }

    /** Replaces the current dynasty with one from a backup file. */
    fun importSave(uri: android.net.Uri) {
        viewModelScope.launch {
            val lg = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    SaveIO.decode(bytes)
                }
            }.getOrNull()
            if (lg == null) { toast("That file isn't a Hoops Dynasty save"); return@launch }
            mutex.withLock {
                league = lg
                draft = null
                stack.clear()
                sheet = null
                live = null
                tab = Tab.TODAY
            }
            bump()
            toast("Save restored: ${lg.user.school}, ${lg.season}")
        }
    }

    fun abandonCareer() {
        viewModelScope.launch {
            mutex.withLock {
                saveJob?.cancel()
                withContext(Dispatchers.IO) { file.delete() }
                league = null
                stack.clear()
                sheet = null
                live = null
            }
            generateDraft()
        }
    }

    // ---------- simulation ----------

    fun simDay() {
        val lg = league ?: return
        if (Season.seasonOver(lg)) return
        heavy("Simulating") { com.nh.hoopscoach.game.Sim.playDay(it) }
    }

    fun simUntil(label: String, stop: (League) -> Boolean) = heavy(label) { lg ->
        var guard = 0
        while (!Season.seasonOver(lg) && guard++ < 200) {
            com.nh.hoopscoach.game.Sim.playDay(lg)
            if (stop(lg)) break
        }
    }

    fun simDays(n: Int) {
        val lg = league ?: return
        val target = lg.day + n
        simUntil("Simulating") { it.day >= target || it.phase != Phase.REGULAR }
    }

    fun simToPhase(phase: Phase, label: String) = simUntil(label) { it.phase == phase }

    fun beginOffseason() = heavy("Heading into the offseason") { Offseason.begin(it) }

    /** Moves the offseason forward one step (or one recruiting round). */
    fun offseasonNext() {
        val os = league?.offseason ?: return
        val label = when (os.step) {
            com.nh.hoopscoach.game.OffStep.ROSTER -> "Running Draft Night"
            com.nh.hoopscoach.game.OffStep.CAREER -> "Players are training this summer"
            com.nh.hoopscoach.game.OffStep.RECRUITING -> if (os.round >= Offseason.ROUNDS) "Signing day" else "Locking in round ${os.round}"
            com.nh.hoopscoach.game.OffStep.TRYOUTS -> "Opening the season"
            else -> "Loading"
        }
        heavy(label) {
            Offseason.advance(it)
            if (it.phase == Phase.REGULAR) { wrapPage = 0; tab = Tab.TODAY }
        }
    }

    fun simOffseason() = heavy("Simulating the offseason") {
        Offseason.autoComplete(it)
        wrapPage = 0
        tab = Tab.TODAY
    }

    // ---------- live games ----------

    fun startLive() {
        val lg = league ?: return
        val g = Season.userGameToday(lg) ?: return
        live = LiveGame(GameEngine(lg, g, true).also { it.interactive = lg.playerMode })
        stack.add(Route.Live)
    }

    /** Called when the final buzzer sounds (or the user skips ahead). */
    fun completeLive() {
        val lv = live ?: return
        val lg = league ?: return
        if (lv.done) return
        viewModelScope.launch {
            mutex.withLock {
                lv.engine.simToEnd()
                lv.engine.commit()
                lv.tick++
                com.nh.hoopscoach.game.Sim.playDay(lg)
            }
            lv.done = true
            bump()
        }
    }

    fun closeLive() {
        if (live?.done == false) completeLive()
        stack.removeAll { it is Route.Live }
        live = null
    }

    // ---------- navigation ----------

    fun push(r: Route) { stack.add(r) }

    fun openPlayer(id: Int) { sheet = id }

    fun openGuide(g: Guide, tab: Int = guideTab) { guideTab = tab; guide = g }

    fun back(): Boolean {
        if (guide != null) { guide = null; return true }
        if (sheet != null) { sheet = null; return true }
        val top = stack.lastOrNull() ?: return false
        if (top is Route.Live) { closeLive(); return true }
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun select(t: Tab) {
        stack.clear()
        tab = t
    }
}
