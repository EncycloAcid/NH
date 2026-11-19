package com.nh.hoopscoach

import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Season
import java.io.File
import kotlin.system.exitProcess

/**
 * CI check run with the packaged app's own Java and jars on a Windows machine: builds a league the way
 * "New career" does, plays a full season, saves and reloads it, and heads into the offseason. Prints
 * SMOKE OK and exits 0, or prints the error and exits 1.
 */
fun main() {
    val home = File(System.getProperty("java.io.tmpdir"), "hoops-smoke").apply { mkdirs() }
    PlatformContext.filesDir = home
    var failure: Throwable? = null
    val t = Thread(null, {
        try {
            run()
        } catch (e: Throwable) {
            failure = e
        }
    }, "smoke", 64L shl 20)
    t.start()
    t.join()
    val e = failure
    if (e != null) {
        println("SMOKE FAILED")
        e.printStackTrace(System.out)
        exitProcess(1)
    }
    println("SMOKE OK")
    exitProcess(0)
}

private fun run() {
    val home = PlatformContext.filesDir
    println("${System.getProperty("os.name")} ${System.getProperty("os.version")} · Java ${System.getProperty("java.version")}")
    // Classes whose names differ only by case once collided on Windows; make sure both load.
    Class.forName("com.nh.hoopscoach.game.GamePlan")
    Class.forName("com.nh.hoopscoach.game.Tactics")

    var ms = System.currentTimeMillis()
    val draft = LeagueGen.newLeague(2026)
    println("League built: ${draft.teams.size} teams, ${draft.players.size} players in ${System.currentTimeMillis() - ms} ms")

    val lg = Offseason.claimTeam(draft, 5, "Test", "Coach")
    ms = System.currentTimeMillis()
    var days = 0
    while (lg.phase != Phase.WRAP_UP && days++ < 200) Season.playDay(lg)
    check(lg.phase == Phase.WRAP_UP) { "season never finished (phase ${lg.phase} after $days days)" }
    println("Season played: ${lg.games.count { it.played }} games in ${System.currentTimeMillis() - ms} ms; ${lg.user.name} ${lg.user.record}")

    val bytes = SaveIO.encode(lg)
    File(home, "smoke.save").writeBytes(bytes)
    val back = SaveIO.decode(File(home, "smoke.save").readBytes())
    check(back.players.size == lg.players.size) { "save lost players" }
    println("Saved and reloaded: ${bytes.size / 1024} KB")

    Offseason.begin(back)
    Offseason.autoComplete(back)
    println("Offseason reached: phase ${back.phase}, season ${back.season}")
}
