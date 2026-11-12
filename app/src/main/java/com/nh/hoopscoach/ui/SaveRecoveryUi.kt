package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SaveRecoveryScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Gutter)) {
        Text("Couldn't resume this career", style = T.head, color = P.ink)
        Text(vm.loadError.orEmpty(), style = T.body, color = P.ink)
        Text("Your saves have not been deleted. Choose another slot or start a separate career.", style = T.body, color = P.sub)
        vm.saveSlots.forEach { slot ->
            SmallButton("Open ${slot.title}") { vm.loadSlot(slot.id) }
        }
        SmallButton("Start a new career", primary = true) { vm.startNewDynasty() }
    }
}
