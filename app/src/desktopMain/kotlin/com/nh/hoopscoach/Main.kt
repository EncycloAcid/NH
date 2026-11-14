package com.nh.hoopscoach

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nh.hoopscoach.ui.App
import com.nh.hoopscoach.ui.GameViewModel
import java.io.File

fun main() {
    val home = File(System.getProperty("user.home"), ".hoopsdynasty").apply { mkdirs() }
    PlatformContext.filesDir = home
    SaveTransfer.host = DesktopTransferHost()
    PlatformContext.versionName = "3.4-pro.208"
    // A Windows app has no console, so errors go to a log beside the saves (the previous run's is kept).
    runCatching {
        val log = File(home, "hoops-log.txt")
        if (log.exists()) log.copyTo(File(home, "hoops-log-previous.txt"), overwrite = true)
        val out = java.io.PrintStream(java.io.FileOutputStream(log), true, "UTF-8")
        out.println("Hoops Dynasty Pro ${PlatformContext.versionName} · ${System.getProperty("os.name")} ${System.getProperty("os.version")} · Java ${System.getProperty("java.version")} · ${java.util.Date()}")
        System.setErr(out)
    }
    CrashReporter.version = PlatformContext.versionName
    CrashReporter.install(home, "${System.getProperty("os.name")} ${System.getProperty("os.version")}; Java ${System.getProperty("java.version")}")
    application {
        // Made once and remembered: if the window recomposes (a display-scaling change can do that on
        // Windows), a fresh game must not be created and send it back to "Building your league". Made
        // inside the application so Compose sets up the windowing system first.
        val vm = androidx.compose.runtime.remember { GameViewModel() }
        Game(vm)
    }
}

private val TAB_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)

@androidx.compose.runtime.Composable
private fun androidx.compose.ui.window.ApplicationScope.Game(vm: GameViewModel) {
    // A desktop-sized window: sidebar, the screen and the league panel side by side. Narrow it and it
    // becomes the phone layout.
    val state = rememberWindowState(width = 1380.dp, height = 880.dp)
    Window(
        onCloseRequest = {
            vm.saveSoon()
            exitApplication()
        },
        title = "Hoops Dynasty Pro",
        state = state,
        // Keys a text box doesn't take: Esc back, Space/Enter continue, S sim the day, W sim a week,
        // 1-5 tabs, Ctrl+S save, F1 the list.
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) false
            else when {
                event.key == Key.Escape -> vm.back()
                event.isCtrlPressed && event.key == Key.S -> { vm.saveSoon(); vm.toast("Saved"); true }
                event.key == Key.F1 -> { vm.toast("Space or Enter: continue · S: sim the day · W: sim a week · 1-5: tabs · Esc: back · Ctrl+S: save"); true }
                vm.busy != null || vm.league == null || event.isCtrlPressed || event.isAltPressed -> false
                event.key == Key.Spacebar || event.key == Key.Enter -> vm.keyPrimary?.let { it(); true } ?: false
                event.key == Key.S -> vm.keySim?.let { it(); true } ?: false
                event.key == Key.W -> if (vm.league?.phase == com.nh.hoopscoach.game.Phase.REGULAR && vm.keySim != null) { vm.simDays(7); true } else false
                event.key in TAB_KEYS -> { vm.select(com.nh.hoopscoach.ui.Tab.entries[TAB_KEYS.indexOf(event.key)]); true }
                else -> false
            }
        },
    ) {
        androidx.compose.runtime.LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(400, 620) }
        App(vm)
    }
}
