package com.nh.hoopscoach

import android.app.Application
import android.content.ClipboardManager
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.*
import com.nh.hoopscoach.ui.GameViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashRecoveryTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun saveEncodingFailureIsCaughtInsteadOfEscapingTheCoroutine() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        GameViewModel.autoLoad = false
        try {
            val vm = GameViewModel(app)
            val lg = LeagueGen.newLeague()
            lg.user.elo = Double.NaN
            vm.install(lg)
            vm.loadSlot("another-slot")
            val deadline = System.nanoTime() + 10_000_000_000L
            while (vm.message == null && System.nanoTime() < deadline) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.yield()
            }
            assertTrue(vm.message.orEmpty().startsWith("Couldn't save"))
            assertSame(lg, vm.league)
        } finally { GameViewModel.autoLoad = true }
    }
    @Test fun credentialsAreRedactedFromCrashReports() {
        val text = CrashReporter.redact("{\"googleApiKey\":\"private-key\",\"openAiAccountPassword\":\"private-password\"} https://host?key=private-query Bearer private-token")
        listOf("private-key", "private-password", "private-query", "private-token").forEach { assertFalse(text.contains(it)) }
    }
    @Test fun characterCacheCannotRetainEveryFullResolutionLayer() {
        val cache = org.robolectric.util.ReflectionHelpers.getStaticField<android.util.LruCache<String, android.graphics.Bitmap>>(com.nh.hoopscoach.ui.SpriteSheets::class.java, "cache")
        cache.evictAll()
        repeat(20) { cache.put("test$it", android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)) }
        assertTrue(cache.maxSize() <= 16 * 1024 * 1024)
        assertTrue(cache.size() <= cache.maxSize())
        assertNull(cache.get("test0")); assertNotNull(cache.get("test19"))
        cache.evictAll()
    }
    @Test fun capturesMetadataAndChainsEvenWhenWritingFails() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        var chained = 0
        CrashReporter.lastScreen = "Settings/AI"
        val previous = Thread.UncaughtExceptionHandler { _, e -> assertEquals("test crash", e.message); chained++ }
        CrashReporter.Handler(temp.root, previous).uncaughtException(Thread.currentThread(), IllegalStateException("test crash"))
        val report = CrashReporter.pending(temp.root)!!
        listOf("Hoops Dynasty", "SDK 34", "Thread:", "Settings/AI", "IllegalStateException: test crash", "CrashRecoveryTest").forEach { assertTrue(report.contains(it)) }
        CrashReporter.Handler(temp.newFile(), previous).uncaughtException(Thread.currentThread(), IllegalStateException("test crash"))
        assertEquals(2, chained)
        CrashReporter.acknowledge(temp.root)
        assertNull(CrashReporter.pending(temp.root)); assertTrue(File(temp.root, "last-crash.txt").exists())
    }
    @Test fun crashDialogPrecedesViewModelAndSupportsCopyAndShare() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File(app.filesDir, "pending-crash.txt")
        file.writeText("a captured crash")
        GameViewModel.autoLoad = false
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.isShowing)
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).performClick()
            assertEquals("a captured crash", app.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
            assertTrue(dialog.isShowing); assertTrue(file.exists())
            assertEquals("Application startup", CrashReporter.lastScreen)
        } finally { controller.pause().stop().destroy(); file.delete(); GameViewModel.autoLoad = true }
    }
    @Test fun freshInstallIsNotMistakenForCorruption() { assertNull(SaveManager.resumeSlot(temp.root, "missing")) }
    @Test fun corruptSaveIsPreservedAndAnotherSlotCanBeOpened() {
        val dir = File(temp.root, "saves").apply { mkdirs() }
        val bad = File(dir, "bad.sav").apply { writeText("damaged bytes") }
        val lg = LeagueGen.newLeague()
        SaveManager.saveSlot(temp.root, "good", SaveIO.encode(lg), lg)
        assertTrue(runCatching { SaveManager.resumeSlot(temp.root, "bad") }.isFailure)
        assertEquals("damaged bytes", bad.readText())
        assertNotNull(SaveManager.resumeSlot(temp.root, "good"))
    }
    @Test fun restoringBackupKeepsFailedPrimary() {
        val lg = LeagueGen.newLeague()
        SaveManager.saveSlot(temp.root, "backup", SaveIO.encode(lg), lg)
        val dir = File(temp.root, "saves")
        File(dir, "backup.sav").copyTo(File(dir, "backup.sav.bak"))
        File(dir, "backup.sav").writeText("failed primary")
        assertNotNull(SaveManager.resumeSlot(temp.root, "backup"))
        assertTrue(dir.listFiles()!!.any { it.name.contains(".corrupt.") && it.readText() == "failed primary" })
        assertTrue(File(dir, "backup.sav.bak").exists())
    }
}
