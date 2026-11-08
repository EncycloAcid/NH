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
import com.nh.hoopscoach.game.Reactions
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
    GREATEST("Greatest Teams", "Every champion, rated on one scale"),
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
    data object NbaPreview : Route
    data object News : Route
    data class EditPlayer(val playerId: Int) : Route
    data object RecruitPreview : Route
    data object Social : Route
    data class Article(val newsIndex: Int) : Route
    data object Trophies : Route
    data object Staff : Route
    data object MyPlayer : Route
    data class Portrait(val playerId: Int) : Route
}

/** A game being watched possession by possession. */
class LiveGame(val engine: GameEngine) {
    var tick by mutableIntStateOf(0)
    var speed by mutableIntStateOf(2)
    var done by mutableStateOf(false)
    /** Player mode: the big moment waiting for a choice, and how the last one went. */
    var moment by mutableStateOf<GameEngine.BigMoment?>(null)
    var momentResult by mutableStateOf<String?>(null)
    /** Coach mode: the call waiting for a decision (a timeout, the closing five, the last play). */
    var call by mutableStateOf<GameEngine.CoachCall?>(null)
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
    /** How far along a long sim is (0 to 1), when its length is known, and a line on where it stands. */
    var progress by mutableStateOf<Float?>(null)
    var progressNote by mutableStateOf("")
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
    /** The Playoffs tab: 0 the bracket, 1 the series round by round. */
    var playoffView by mutableIntStateOf(0)
    var leagueConf by mutableIntStateOf(-1)
    var leagueTab by mutableIntStateOf(0)
    /** The pro Me tab's page (You, Team, Growth, Media) and how free-agent offers are sorted. */
    var meTab by mutableIntStateOf(0)
    /** The Social screen's tab: 0 about you, 1 around the league. */
    var socialTab by mutableIntStateOf(0)
    var faSort by mutableIntStateOf(0)
    /** The season the Awards page shows (0 for the current one). */
    var awardsSeason by mutableIntStateOf(0)
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
            mutex.withLock { block(lg); Reactions.pass(lg) }
            bump()
        }
    }

    /** Longer work: the UI swaps to a progress screen until it finishes. */
    private fun heavy(label: String, block: suspend (League) -> Unit) {
        val lg = league ?: return
        if (busy != null) return
        busy = label
        progress = null
        progressNote = ""
        viewModelScope.launch {
            delay(60) // let the UI leave any screen that reads the league
            mutex.withLock { withContext(Dispatchers.Default) { block(lg); Reactions.pass(lg) } }
            busy = null
            progress = null
            progressNote = ""
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

    /** The last player made on the creator: it comes back filled in after the offers, so nothing (the era above all) resets. */
    var roadSpec: com.nh.hoopscoach.game.Road.Spec? = null

    fun roadFindOffers(spec: com.nh.hoopscoach.game.Road.Spec) {
        val d0 = draft ?: return
        roadSpec = spec
        busy = "Coaches are calling"
        viewModelScope.launch {
            // A different era needs a college league that starts in that era's season, with its real players.
            val d = if (d0.era == spec.era.name) d0 else withContext(Dispatchers.Default) {
                if (spec.era == com.nh.hoopscoach.game.Era.TODAY) LeagueGen.newLeague() else com.nh.hoopscoach.game.EraCollege.newLeague(spec.era)
            }
            draft = d
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
            roadSpec = null
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
        if (busy != null || pro.road?.ended != true) return
        busy = "Taking the job at $school"
        viewModelScope.launch {
            delay(60)
            val college = mutex.withLock {
                withContext(Dispatchers.Default) {
                    val start = pro.season + com.nh.hoopscoach.game.Afterward.yearsAway(pro)
                    com.nh.hoopscoach.game.Afterward.becomeCoach(pro, com.nh.hoopscoach.game.LeagueGen.newLeague(start), school).also {
                        // His NBA world comes along, so an NBA job later returns to the league he played in.
                        if (pro.pro) it.proWorld = runCatching { com.nh.hoopscoach.game.SaveIO.pack(pro) }.getOrDefault("")
                    }
                }
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

    /** A retired pro goes straight to an NBA bench: this league becomes his coaching career. */
    fun retireToNba(teamId: Int) {
        val lg = league ?: return
        if (busy != null || lg.road?.retired != true) return
        busy = "Taking over the ${lg.team(teamId).name}"
        viewModelScope.launch {
            delay(60)
            val ok = runCatching {
                mutex.withLock { withContext(Dispatchers.Default) { com.nh.hoopscoach.game.NbaCoach.fromRetirement(lg, teamId) } }
            }.onFailure { e -> android.util.Log.e("Hoops", "NBA bench move failed", e) }.isSuccess
            stack.clear()
            sheet = null
            live = null
            tab = Tab.TODAY
            busy = null
            if (!ok) toast("Couldn't take the job. Your save is unchanged.")
            bump()
        }
    }

    /** His social profile picture from a photo on the phone: shrunk to 512 px and kept in the app's files. */
    fun setProfilePhoto(uri: android.net.Uri) {
        val lg = league ?: return
        val r = lg.road ?: return
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                        ?: return@runCatching null
                    // A square from the middle, so the circle crops it well.
                    val side = minOf(bmp.width, bmp.height)
                    val square = android.graphics.Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
                    val out = android.graphics.Bitmap.createScaledBitmap(square, minOf(side, 512), minOf(side, 512), true)
                    val n = "profile_${System.currentTimeMillis()}.jpg"
                    File(app.filesDir, n).outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
                    // The old one goes.
                    if (r.avatarFile.isNotEmpty() && r.avatarFile != n) File(app.filesDir, r.avatarFile).delete()
                    n
                }.getOrNull()
            }
            if (name == null) { toast("Couldn't use that picture."); return@launch }
            mutex.withLock { r.avatarStyle = "photo"; r.avatarFile = name }
            bump()
            toast("Profile picture updated.")
        }
    }

    /** Draft-night work that picks many players at once (simming to the user's pick, or the rest of the draft). */
    fun draftSim(label: String, block: (League) -> Unit) = heavy(label) { block(it) }

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

    /** A college coach takes an NBA job: the new league replaces the college save. */
    fun goNbaCoach(slot: String) {
        val college = league ?: return
        if (busy != null) return
        busy = "Heading to the NBA"
        viewModelScope.launch {
            delay(60)
            val pro = runCatching {
                mutex.withLock {
                    withContext(Dispatchers.Default) { com.nh.hoopscoach.game.NbaCoach.fromCollege(college, slot).also { com.nh.hoopscoach.game.ProCareer.next(it) } }
                }
            }.getOrElse { e ->
                android.util.Log.e("Hoops", "NBA move failed", e)
                busy = null
                toast("Couldn't make the move to the NBA: ${e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            stack.clear()
            sheet = null
            live = null
            tab = Tab.TODAY
            league = pro
            busy = null
            bump()
        }
    }

    /** An NBA coaching career: [year]'s real league (the season it ends), coaching the team in [slot]. */
    fun startNbaCoach(year: Int, slot: String, first: String, last: String) {
        if (busy != null) return
        busy = "Building the ${if (year >= 2027) "league" else "${year - 1}-${(year % 100).toString().padStart(2, '0')} league"}"
        viewModelScope.launch {
            val lg = withContext(Dispatchers.Default) {
                com.nh.hoopscoach.game.ProLeague.coachLeague(year, slot, first, last).also { com.nh.hoopscoach.game.ProCareer.next(it) }
            }
            draft = null
            roadHero = null
            league = lg
            tab = Tab.TODAY
            stack.clear()
            busy = null
            bump()
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

    /**
     * Sim day by day until [stop]. With [days] (how many days it should take), the loading screen
     * shows a progress bar; either way it shows the day and the user's record as it goes.
     */
    fun simUntil(label: String, days: Int? = null, stop: (League) -> Boolean) = heavy(label) { lg ->
        var guard = 0
        var done = 0
        while (!Season.seasonOver(lg) && guard++ < 200) {
            com.nh.hoopscoach.game.Sim.playDay(lg)
            done++
            val note = "Day ${lg.day} · ${lg.user.w}-${lg.user.l}"
            val p = days?.takeIf { it > 1 }?.let { (done.toFloat() / it).coerceIn(0f, 1f) }
            withContext(Dispatchers.Main) { progress = p; progressNote = note }
            if (stop(lg)) break
        }
    }

    fun simDays(n: Int) {
        val lg = league ?: return
        val target = lg.day + n
        simUntil("Simulating", n) { it.day >= target || it.phase != Phase.REGULAR }
    }

    /** Days left in the regular season: up to the last regular-season game on the schedule. */
    fun regularDaysLeft(lg: League): Int? =
        lg.games.filter { !it.played && (it.type == com.nh.hoopscoach.game.GameType.CONF || it.type == com.nh.hoopscoach.game.GameType.NONCONF) }
            .maxOfOrNull { it.day }?.let { it - lg.day + 1 }?.takeIf { it > 1 }

    fun simToPhase(phase: Phase, label: String, days: Int? = null) = simUntil(label, days) { it.phase == phase }

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
        val stopped = Offseason.autoComplete(it, stopForNba = true)
        wrapPage = 0
        tab = Tab.TODAY
        if (stopped) toast("A big decision is waiting on the Career step: decide before you sim ahead")
    }

    // ---------- live games ----------

    fun startLive() {
        val lg = league ?: return
        val g = Season.userGameToday(lg) ?: return
        live = LiveGame(GameEngine(lg, g, true).also {
            it.interactive = lg.playerMode
            // Coaching it: timeouts, the closing five and the last plays are his calls.
            if (!lg.playerMode) it.startCoaching(if (g.home == lg.userTeamId) 0 else 1)
        })
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
