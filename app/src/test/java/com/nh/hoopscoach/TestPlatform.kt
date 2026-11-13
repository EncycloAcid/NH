package com.nh.hoopscoach

import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.ui.GameViewModel
import java.io.File

/** Sets up shared platform state and returns a ViewModel for instrumented JVM tests. */
fun testViewModel(): GameViewModel {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    PlatformContext.filesDir = app.filesDir
    PlatformContext.versionName = "test"
    AndroidAssets.appContext = app
    return GameViewModel()
}
