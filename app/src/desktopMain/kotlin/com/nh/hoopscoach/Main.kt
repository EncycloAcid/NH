package com.nh.hoopscoach

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nh.hoopscoach.ui.App
import com.nh.hoopscoach.ui.GameViewModel
import java.io.File

fun main() = application {
    val home = File(System.getProperty("user.home"), ".hoopsdynasty").apply { mkdirs() }
    PlatformContext.filesDir = home
    PlatformContext.versionName = "3.4-pro.182"
    val vm = GameViewModel()
    val state = rememberWindowState(width = 420.dp, height = 860.dp)
    Window(
        onCloseRequest = {
            vm.saveSoon()
            exitApplication()
        },
        title = "Hoops Dynasty Pro",
        state = state,
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                vm.back()
            } else {
                false
            }
        },
    ) {
        App(vm)
    }
}
