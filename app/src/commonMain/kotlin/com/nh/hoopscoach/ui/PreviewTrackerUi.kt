package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.PreviewTrack
import com.nh.hoopscoach.game.TeamPreview

@Composable
internal fun PreviewTeamCopy(report: TeamPreview?, showScouting: Boolean = true) {
    if (report == null) return
    Section("The defining angles")
    Panel {
        report.angles.forEach { angle ->
            Text(angle.kind.uppercase(), style = T.label, color = P.accent)
            Text(angle.text, style = T.body, color = P.ink)
            Spacer(Modifier.height(10.dp))
        }
        if (showScouting) {
            Text(report.strength, style = T.small, color = P.ink)
            Spacer(Modifier.height(8.dp))
            Text(report.concern, style = T.small, color = P.sub)
        }
    }
}

internal fun LazyListScope.previewTracker(lg: League, track: PreviewTrack) {
    item { PreviewTrackerPanel(lg, track) }
}

@Composable
internal fun PreviewTrackerPanel(lg: League, track: PreviewTrack) {
    if (lg.teams.none { it.gp > 0 }) return
    Section("How our picks are doing")
    Panel {
        Text("Preseason projection vs current record and rank", style = T.bodyB, color = P.ink)
        Text("Pace checks are provisional; postseason calls use current rank as a proxy, not a new simulation. Awards await voting.", style = T.small, color = P.sub)
        track.teams.forEach { row ->
            val team = lg.team(row.teamId)
            Text(if (lg.pro) team.name else team.school, style = T.bodyB, color = P.accent, modifier = Modifier.padding(top = 12.dp))
            Text("Preseason: ${row.preseason}", style = T.small, color = P.sub)
            Text("Now: ${row.current}", style = T.small, color = P.ink)
            Text(when (row.onTrack) { true -> "On track"; false -> "Off track"; null -> "Too early to judge" },
                style = T.label, color = when (row.onTrack) { true -> P.good; false -> P.bad; null -> P.sub })
        }
        if (track.calls.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("BOLD PICKS · LIVE", style = T.label, color = P.accent)
            track.calls.forEach { call ->
                Text(call.text, style = T.bodyB, color = P.ink, modifier = Modifier.padding(top = 12.dp))
                Text(call.status, style = T.label, color = when (call.status) { "On track" -> P.good; "Off track" -> P.bad; else -> P.sub })
                Text(call.evidence, style = T.small, color = P.sub)
            }
        }
    }
}
