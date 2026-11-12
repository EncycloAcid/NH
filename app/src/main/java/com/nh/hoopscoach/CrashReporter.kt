package com.nh.hoopscoach

import android.app.Application
import android.os.Build
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object CrashReporter {
    private var reserve: ByteArray? = null
    @Volatile var version = "unknown"
    @Volatile var lastScreen = "Application startup"
    fun pending(root: File): String? = runCatching { File(root, "pending-crash.txt").takeIf { it.exists() }?.readText() }.getOrNull()
    fun acknowledge(root: File) {
        runCatching { File(root, "pending-crash.txt").renameTo(File(root, "last-crash.txt")) }
    }
    fun redact(text: String): String = text
        .replace(Regex("(?i)(https?://)[^\\s/@]+:[^\\s/@]+@"), "$1[redacted]@")
        .replace(Regex("\\b(?:sk-[A-Za-z0-9_-]{10,}|AIza[0-9A-Za-z_-]{12,})\\b"), "[redacted]")
        .replace(Regex("(?i)(\\\"[^\\\"]*(?:api[_-]?key|password|token|secret)[^\\\"]*\\\"\\s*:\\s*\\\")[^\\\"]*"), "$1[redacted]")
        .replace(Regex("(?i)([?&](?:key|api_key|token|access_token)=)[^&\\s]+"), "$1[redacted]")
        .replace(Regex("(?i)(Bearer\\s+)[^\\s]+"), "$1[redacted]")

    class Handler(private val root: File, val previous: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        private val writing = AtomicBoolean(false)
        override fun uncaughtException(thread: Thread, error: Throwable) {
            try {
                if (writing.compareAndSet(false, true)) runCatching {
                    reserve = null
                    File(root, "pending-crash.txt").writeText(redact(buildString {
                        appendLine("Hoops Dynasty $version")
                        appendLine("${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}; SDK ${Build.VERSION.SDK_INT}")
                        appendLine("Thread: ${thread.name}; screen: $lastScreen")
                        appendLine(error.stackTraceToString())
                    }))
                }
            } finally { previous?.uncaughtException(thread, error) }
        }
    }

    fun install(root: File) {
        lastScreen = "Application startup"
        reserve = ByteArray(128 * 1024)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(Handler(root, if (previous is Handler) previous.previous else previous))
    }
}

class HoopsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(filesDir)
        runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            CrashReporter.version = "${info.versionName} (${if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()})"
        }
        com.nh.hoopscoach.game.AiDiagnostics.install(filesDir)
    }
}
