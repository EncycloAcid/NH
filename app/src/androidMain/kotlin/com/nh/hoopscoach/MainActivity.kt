package com.nh.hoopscoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.nh.hoopscoach.ui.App
import com.nh.hoopscoach.ui.GameViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        PlatformContext.saveFile = File(filesDir, "dynasty.sav")
        PlatformContext.versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "3.4"
        AndroidAssets.appContext = applicationContext
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App(vm) }
    }

    override fun onStop() {
        super.onStop()
        vm.saveSoon()
    }
}
