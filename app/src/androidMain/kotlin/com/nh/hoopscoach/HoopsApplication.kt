package com.nh.hoopscoach

import android.app.Application
import android.os.Build

class HoopsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val device = "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}; SDK ${Build.VERSION.SDK_INT}"
        CrashReporter.install(filesDir, device)
        runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            CrashReporter.version = "${info.versionName} (${if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()})"
        }
        com.nh.hoopscoach.game.AiDiagnostics.install(filesDir)
        PlatformContext.filesDir = filesDir
        AndroidAssets.appContext = applicationContext
    }
}
