package com.nh.hoopscoach.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nh.hoopscoach.PlatformContext
import com.nh.hoopscoach.squareJpegThumbnail
import com.nh.hoopscoach.game.GameEngine
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Reactions
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.SaveManager
import com.nh.hoopscoach.game.SaveSlot
import com.nh.hoopscoach.game.Season
import kotlinx.coroutines.CoroutineExceptionHandler
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
    TIMELINE("Timeline", "Everything this world remembers, as it happened"),
    MOMENTS("Highlight Reel", "Upsets, buzzer-beaters and Cinderella runs"),
    AWARDS("Champions & Awards", "Every national award winner"),
    DRAFT("Draft History", "Every pick, every year"),
    HOF("Hall of Fame", "Inducted players and coaches"),
    RECORDS("Record Book", "Single-game, season and career records"),
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
    data class BuildLab(val playerId: Int) : Route
    data object RecruitPreview : Route
    data object Social : Route
    data class Article(val newsIndex: Int) : Route
    data object Trophies : Route
    data object Staff : Route
    data object MyPlayer : Route
    data class Portrait(val playerId: Int) : Route
    data object TopTen : Route
    data object Mixtape : Route
    /** The relationship web around a coach (kind 'C') or a team ('T'). */
    data class Web(val kind: Char, val id: Int) : Route
    data object Calendar : Route
    data object Cinderella : Route
    data object Designer : Route
    data object SeasonCard : Route
    data object Challenges : Route
    data object Explore : Route
    data class ConfPage(val confId: Int) : Route
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

class GameViewModel : ViewModel() {
    private val filesDir: java.io.File get() = PlatformContext.filesDir
    private val mutex = Mutex()

    var activeSlotId by mutableStateOf("default")
        private set
    var saveSlots by mutableStateOf<List<SaveSlot>>(emptyList())
        private set
    var activeSlot by mutableStateOf<SaveSlot?>(null)
        private set

    var league by mutableStateOf<League?>(null)
        private set
    /** A generated league waiting for the user to pick a school. */
    var draft by mutableStateOf<League?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    /** Why the new-career league couldn't be built, shown with a way to try again instead of a spinner that never ends. */
    var draftError by mutableStateOf<String?>(null)

    /**
     * Any job that fails, even with an Error that the jobs' own `catch (e: Exception)` misses, clears the
     * busy screen and says so. Without this a failure left "Building your league" up forever: on Android
     * the app crashed, but on PC nothing closed the window.
     */
    private val failSafe = CoroutineExceptionHandler { _, e ->
        e.printStackTrace()
        runCatching { File(filesDir, "last-error.txt").writeText(com.nh.hoopscoach.CrashReporter.redact("${java.util.Date()}\n${busy ?: "background job"}\n${e.stackTraceToString()}")) }
        val what = busy
        busy = null
        progress = null
        progressNote = ""
        loading = false
        errorDetails = "Hoops Dynasty ${com.nh.hoopscoach.PlatformContext.versionName} · ${System.getProperty("os.name")} · Java ${System.getProperty("java.version")}\n" +
            com.nh.hoopscoach.CrashReporter.redact(e.stackTraceToString()).lines().take(40).joinToString("\n")
        if (league == null && draft == null) draftError = "${e.javaClass.simpleName}: ${e.message ?: "no details"}"
        else message = "${what ?: "That"} failed (${e.javaClass.simpleName}). Details saved to last-error.txt."
    }
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
        if (autoLoad) viewModelScope.launch(failSafe) {
            try {
                                val pair = withContext(Dispatchers.IO) {
                    SaveManager.migrateLegacyIfNeeded(filesDir)
                    val activeId = SaveManager.getActiveSlotId(filesDir)
                    SaveManager.resumeSlot(filesDir, activeId)
                }
                league = pair?.first
                activeSlot = pair?.second
                activeSlotId = pair?.second?.id ?: SaveManager.getActiveSlotId(filesDir)
                loading = false
                refreshSaveSlots()
                if (pair == null) generateDraft()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { showLoadError(e) }
        }
    }

    fun refreshSaveSlots() {
        viewModelScope.launch(failSafe) {
                        val slots = try { withContext(Dispatchers.IO) { SaveManager.listSlots(filesDir) } }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { emptyList() }
            saveSlots = slots
            activeSlot = slots.firstOrNull { it.id == activeSlotId }
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
        refreshWidget()
    }

    /** The home-screen widget: team and record, the next game, the latest headline (Android only). */
    private fun refreshWidget() {
        val lg = league ?: return
        runCatching {
            val u = lg.user
            val g = com.nh.hoopscoach.game.Season.userGameToday(lg) ?: com.nh.hoopscoach.game.Season.nextUserGame(lg)
            val next = when {
                lg.phase == Phase.OFFSEASON -> "Offseason"
                lg.phase == Phase.WRAP_UP -> "Season complete: ${u.history.lastOrNull()?.result ?: ""}".trimEnd(' ', ':')
                g != null -> "Next: ${if (g.neutral) "vs" else if (g.home == u.id) "vs" else "at"} ${lg.team(g.opp(u.id)).school}" +
                    (if (!lg.pro) " · " + com.nh.hoopscoach.game.SeasonCalendar.dateOf(lg.season, g.day).let { "${it.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US)} ${it.dayOfMonth}" } else "")
                else -> com.nh.hoopscoach.game.Season.statusLine(lg)
            }
            val head = (lg.news.lastOrNull { it.mine } ?: lg.news.lastOrNull())?.text.orEmpty()
            com.nh.hoopscoach.updateHomeWidget("${if (lg.pro) u.name else u.school} ${u.record}", next, head)
        }
    }

    private var saveJob: Job? = null
    private val saveMutex = Mutex()
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(failSafe) {
            delay(700)
            saveNow()
        }
    }

    fun saveSoon() {
        viewModelScope.launch(failSafe) { saveNow() }
    }

    internal suspend fun saveNow(): Boolean = saveMutex.withLock {
        val lg = league ?: return@withLock true
        val slotId = activeSlotId
        try {
            val bytes = mutex.withLock { withContext(Dispatchers.Default) { SaveIO.encode(lg) } }
            val saved = withContext(Dispatchers.IO) {
                                SaveManager.saveSlot(filesDir, slotId, bytes, lg)
            }
            if (activeSlotId == slotId) activeSlot = saved
            refreshSaveSlots()
            true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { toast("Couldn't save this career (${e.javaClass.simpleName}). Existing save files have been kept."); false }
    }

    private fun showLoadError(error: Exception) {
        loading = false
        busy = null
        loadError = "The selected slot couldn't be opened (${error.javaClass.simpleName})."
        refreshSaveSlots()
    }

    fun loadSlot(id: String) {
        viewModelScope.launch(failSafe) {
            try {
                                if (!saveNow()) return@launch
                val loaded = withContext(Dispatchers.IO) {
                    SaveManager.resumeSlot(filesDir, id)
                }
                if (loaded != null) {
                    mutex.withLock {
                        league = loaded.first
                        activeSlot = loaded.second
                        activeSlotId = loaded.second.id
                        draft = null
                        stack.clear()
                        sheet = null
                        live = null
                        tab = Tab.TODAY
                        loadError = null
                    }
                    refreshSaveSlots()
                    toast("Loaded: ${loaded.second.title}")
                } else {
                    showLoadError(IllegalStateException("Missing save"))
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { showLoadError(e) }
        }
    }

    fun cloneSlot(id: String, newTitle: String) {
        viewModelScope.launch(failSafe) {
                        saveNow()
            val created = withContext(Dispatchers.IO) {
                SaveManager.cloneSlot(filesDir, id, newTitle)
            }
            if (created != null) {
                refreshSaveSlots()
                toast("Snapshot created: ${created.title}")
            } else {
                toast("Couldn't create snapshot")
            }
        }
    }

    fun quickSnapshot() {
        val lg = league ?: return
        val title = "${lg.user.school} Checkpoint (${lg.season})"
        cloneSlot(activeSlotId, title)
    }

    fun renameSlot(id: String, newTitle: String) {
        viewModelScope.launch(failSafe) {
                        val updated = withContext(Dispatchers.IO) {
                SaveManager.renameSlot(filesDir, id, newTitle)
            }
            if (updated != null) {
                refreshSaveSlots()
                toast("Renamed to: ${updated.title}")
            }
        }
    }

    fun deleteSlot(id: String) {
        viewModelScope.launch(failSafe) {
                        val wasActive = (id == activeSlotId)
            withContext(Dispatchers.IO) {
                SaveManager.deleteSlot(filesDir, id)
            }
            if (wasActive) {
                val remaining = withContext(Dispatchers.IO) { SaveManager.listSlots(filesDir) }
                if (remaining.isNotEmpty()) {
                    loadSlot(remaining.first().id)
                } else {
                    mutex.withLock {
                        league = null
                        activeSlot = null
                        activeSlotId = "default"
                        stack.clear()
                        sheet = null
                        live = null
                    }
                    generateDraft()
                }
            }
            refreshSaveSlots()
            toast("Dynasty deleted")
        }
    }

    fun startNewDynasty() {
        viewModelScope.launch(failSafe) {
            if (!saveNow()) return@launch
            mutex.withLock {
                league = null
                activeSlot = null
                activeSlotId = "slot_${System.currentTimeMillis()}_${(100..999).random()}"
                loadError = null
                stack.clear()
                sheet = null
                live = null
            }
            generateDraft()
        }
    }

    private fun ensureFreshSlot() {
                val existing = File(SaveManager.getSavesDir(filesDir), "$activeSlotId.sav")
        if (existing.exists() && league == null) {
            activeSlotId = "slot_${System.currentTimeMillis()}_${(100..999).random()}"
            activeSlot = null
        }
    }

    fun toast(text: String) { message = text }

    /** Quick change to the league; runs on the main thread. */
    fun act(block: (League) -> Unit) {
        val lg = league ?: return
        viewModelScope.launch(failSafe) {
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
        viewModelScope.launch(failSafe) {
            delay(60) // let the UI leave any screen that reads the league
            mutex.withLock { withContext(Dispatchers.Default) { block(lg); Reactions.pass(lg) } }
            busy = null
            progress = null
            progressNote = ""
            bump()
        }
    }

    // ---------- new game ----------

    /**
     * Runs [work] on its own thread with a 64 MB stack (thread stacks are smaller on some systems than
     * others), handing back its result or rethrowing what it threw, Errors included.
     */
    private suspend fun <T> buildOnBigStack(work: () -> T): T = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val t = Thread(null, {
            val r = runCatching(work)
            r.onSuccess { cont.resumeWith(Result.success(it)) }.onFailure { cont.resumeWith(Result.failure(it)) }
        }, "league-builder", 64L * 1024 * 1024)
        t.isDaemon = true
        t.start()
    }

    /** The full report of the last failure, for the Copy button on the error screen. */
    var errorDetails by mutableStateOf("")

    fun generateDraft(startYear: Int = 2026) {
        busy = "Building your league"
        draftError = null
        viewModelScope.launch(failSafe) {
            val lg = buildOnBigStack { LeagueGen.newLeague(startYear) }
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
        viewModelScope.launch(failSafe) {
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
        ensureFreshSlot()
        busy = "Signing your scholarship"
        viewModelScope.launch(failSafe) {
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
        viewModelScope.launch(failSafe) {
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
        viewModelScope.launch(failSafe) {
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
        viewModelScope.launch(failSafe) {
            delay(60)
            val ok = runCatching {
                mutex.withLock { withContext(Dispatchers.Default) { com.nh.hoopscoach.game.NbaCoach.fromRetirement(lg, teamId) } }
            }.onFailure { e -> e.printStackTrace() }.isSuccess
            stack.clear()
            sheet = null
            live = null
            tab = Tab.TODAY
            busy = null
            if (!ok) toast("Couldn't take the job. Your save is unchanged.")
            bump()
        }
    }

    /** His social profile picture from a photo: shrunk to 512 px and kept in the app's files. */
    fun setProfilePhoto(bytes: ByteArray) {
        val lg = league ?: return
        val r = lg.road ?: return
        viewModelScope.launch(failSafe) {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    val jpeg = squareJpegThumbnail(bytes) ?: return@runCatching null
                    val n = "profile_${System.currentTimeMillis()}.jpg"
                    File(filesDir, n).writeBytes(jpeg)
                    if (r.avatarFile.isNotEmpty() && r.avatarFile != n) File(filesDir, r.avatarFile).delete()
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
        viewModelScope.launch(failSafe) {
            delay(60)
            val pro = runCatching {
                mutex.withLock {
                    withContext(Dispatchers.Default) { com.nh.hoopscoach.game.NbaCoach.fromCollege(college, slot).also { com.nh.hoopscoach.game.ProCareer.next(it) } }
                }
            }.getOrElse { e ->
                e.printStackTrace()
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
        ensureFreshSlot()
        busy = "Building the ${if (year >= 2027) "league" else "${year - 1}-${(year % 100).toString().padStart(2, '0')} league"}"
        viewModelScope.launch(failSafe) {
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

    /** A challenge picked on the new-game screen, started along with the career. */
    var pendingChallenge by mutableStateOf<String?>(null)

    fun startCareer(teamId: Int, first: String, last: String) {
        val d = draft ?: return
        ensureFreshSlot()
        busy = "Setting up your first season"
        val challenge = pendingChallenge?.takeIf { com.nh.hoopscoach.game.Challenges.teamFor(d, it) == teamId }
        pendingChallenge = null
        viewModelScope.launch(failSafe) {
            withContext(Dispatchers.Default) {
                Offseason.claimTeam(d, teamId, first, last)
                challenge?.let { com.nh.hoopscoach.game.Challenges.begin(d, it) }
            }
            draft = null
            league = d
            tab = Tab.TODAY
            stack.clear()
            busy = null
            bump()
        }
    }

    /** Bytes for backing up the active dynasty slot. */
    suspend fun exportSlotBytes(id: String): ByteArray? = withContext(Dispatchers.IO) {
        SaveManager.getSlotBytes(filesDir, id)
    }

    /** Replaces or adds dynasty from a backup file into a slot. */
    fun importSave(bytes: ByteArray) {
        viewModelScope.launch(failSafe) {
            val imported = runCatching {
                withContext(Dispatchers.IO) {
                    SaveManager.importSave(filesDir, bytes)
                }
            }.getOrNull()
            if (imported == null) {
                toast("That file isn't a valid Hoops Dynasty save")
                return@launch
            }
            mutex.withLock {
                league = imported.first
                activeSlot = imported.second
                activeSlotId = imported.second.id
                draft = null
                stack.clear()
                sheet = null
                live = null
                tab = Tab.TODAY
            }
            refreshSaveSlots()
            bump()
            toast("Dynasty imported: ${imported.second.title}")
        }
    }

    fun abandonCareer() {
        deleteSlot(activeSlotId)
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

    private suspend fun finishLive(lv: LiveGame, lg: League) {
        if (lv.done) return
        mutex.withLock {
            if (!lv.done) {
                lv.engine.simToEnd()
                lv.engine.commit()
                lv.tick++
                com.nh.hoopscoach.game.Sim.playDay(lg)
                lv.done = true
            }
        }
        bump()
    }

    /** Called when the final buzzer sounds (or the user skips ahead). */
    fun completeLive() {
        val lv = live ?: return
        val lg = league ?: return
        if (lv.done) return
        viewModelScope.launch(failSafe) { finishLive(lv, lg) }
    }

    fun closeLive() {
        val lv = live
        val lg = league
        if (lv != null && !lv.done && lg != null) {
            viewModelScope.launch(failSafe) { finishLive(lv, lg) }
        }
        stack.removeAll { it is Route.Live }
        live = null
    }

    // ---------- navigation ----------

    /** What the keyboard's Space/Enter and S do right now (set by the continue bar while it's showing; PC only). */
    var keyPrimary: (() -> Unit)? = null
    var keySim: (() -> Unit)? = null

    fun push(r: Route) { stack.add(r) }

    fun openPlayer(id: Int) { sheet = id }

    fun openGuide(g: Guide, tab: Int = guideTab) { guideTab = tab; guide = g }

    fun back(): Boolean {
        if (busy != null) return true       // nothing moves while the league is being worked on
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
