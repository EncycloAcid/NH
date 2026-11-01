package com.nh.hoopscoach.game

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object SaveIO {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(lg: League): ByteArray {
        val text = json.encodeToString(League.serializer(), lg)
        val bos = ByteArrayOutputStream(text.length / 4)
        GZIPOutputStream(bos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return bos.toByteArray()
    }

    /**
     * A save stores a transfer twice: once in League.players and once inside his Recruit. After a load
     * they're separate objects, so point each recruit at the league's copy. Then heal saves written
     * before this fix: drop roster entries for players who no longer exist and repair those lineups.
     */
    fun repair(lg: League): Int {
        for (r in lg.recruits) lg.playerOrNull(r.p.id)?.let { if (it !== r.p) r.p = it }
        var fixed = 0
        for (t in lg.teams) {
            val before = t.roster.size
            val kept = t.roster.filter { lg.playerOrNull(it) != null }.distinct()
            if (kept.size != before) {
                t.roster.clear()
                t.roster.addAll(kept)
                fixed += before - kept.size
            }
            val stale = t.plan.slots.any { (it.starter != -1 && it.starter !in t.roster) || (it.reserve != -1 && it.reserve !in t.roster) }
            if (kept.size != before || stale) Gameplan.validate(lg, t)
            for (pid in t.roster) lg.player(pid).let { if (it.teamId < 0) it.teamId = t.id }
        }
        return fixed
    }

    fun decode(bytes: ByteArray): League {
        val text = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
        val lg = json.decodeFromString(League.serializer(), text)
        lg.reindex()
        repair(lg)
        NbaAwards.migrate(lg)
        if (lg.pro && Eras.of(lg) == Era.TODAY && lg.players.none { it.fingerprint != null }) Careers.fingerprint(lg)
        // Saves from before team salary caps: everyone gets a deal.
        if (lg.pro && lg.players.any { it.teamId >= 0 && it.salary <= 0 && it.id != lg.heroId }) Payroll.sign(lg)
        if (lg.pro && lg.proState?.picks?.isEmpty() == true) Picks.ensure(lg)
        if (!lg.pro) {
            Rivalries.ensure(lg)
            Brand.ensure(lg)
            Recruiting.ensure(lg)
        }
        return lg
    }
}
