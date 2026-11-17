package com.nh.hoopscoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.nh.hoopscoach.ui.App
import com.nh.hoopscoach.ui.GameViewModel

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private var gameStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        PlatformContext.filesDir = filesDir
        PlatformContext.versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "3.9-pro.267"
        AndroidAssets.appContext = applicationContext
        if (com.nh.hoopscoach.game.OnDeviceAi.engine == null) com.nh.hoopscoach.game.OnDeviceAi.engine = GeminiNanoEngine()
        lifecycleScope.launch { runCatching { com.nh.hoopscoach.game.OnDeviceAi.refresh() } }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val trace = CrashReporter.pending(filesDir)
        if (trace == null) showGame() else {
            val text = android.widget.TextView(this).apply {
                this.text = trace
                setTextIsSelectable(true)
                setPadding(24, 16, 24, 16)
            }
            val scroll = android.widget.ScrollView(this).apply { addView(text) }
            val dialog = android.app.AlertDialog.Builder(this)
                .setTitle("Previous crash — please share this report")
                .setView(scroll).setCancelable(false)
                .setPositiveButton("Continue") { _, _ -> CrashReporter.acknowledge(filesDir); showGame() }
                .setNeutralButton("Copy", null).setNegativeButton("Share", null).create()
            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Hoops Dynasty crash", trace))
                }
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    runCatching { startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, trace), "Share crash report")) }
                }
            }
            dialog.show()
        }
    }

    private fun showGame() {
        gameStarted = true
        CrashReporter.lastScreen = "Loading career"
        setContent { App(vm) }
    }

    override fun onStart() {
        super.onStart()
        // AICore only answers apps on screen: Gemini Nano requests wait for this.
        com.nh.hoopscoach.game.OnDeviceAi.onScreen = true
    }

    override fun onStop() {
        super.onStop()
        com.nh.hoopscoach.game.OnDeviceAi.onScreen = false
        if (gameStarted) vm.saveSoon()
    }
}
