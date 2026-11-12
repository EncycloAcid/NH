package com.nh.hoopscoach

import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.SaveManager
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SaveManagerTest {

    @Test
    fun testSaveAndLoadSlot() {
        val tempDir = createTempDir("save_manager_test")
        try {
            val lg = LeagueGen.newLeague()
            val bytes = SaveIO.encode(lg)
            val slot = SaveManager.saveSlot(tempDir, "test_slot", bytes, lg, "Test Dynasty")

            assertEquals("test_slot", slot.id)
            assertEquals("Test Dynasty", slot.title)
            assertEquals(lg.user.school, slot.school)
            assertEquals(lg.season, slot.season)

            val loaded = SaveManager.loadSlot(tempDir, "test_slot")
            assertNotNull(loaded)
            assertEquals(lg.teams.size, loaded!!.first.teams.size)
            assertEquals("test_slot", loaded.second.id)
            assertTrue(loaded.second.isCurrent)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRollingBackupsAndCorruptionRecovery() {
        val tempDir = createTempDir("backup_recovery_test")
        try {
            val lg1 = LeagueGen.newLeague(2026)
            val bytes1 = SaveIO.encode(lg1)
            SaveManager.saveSlot(tempDir, "slot1", bytes1, lg1, "Version 1")

            val savesDir = SaveManager.getSavesDir(tempDir)
            val savFile = File(savesDir, "slot1.sav")
            val bakFile = File(savesDir, "slot1.sav.bak")
            val bak2File = File(savesDir, "slot1.sav.bak2")

            assertTrue(savFile.exists())
            assertFalse(bakFile.exists())

            // Second save -> .bak created
            val lg2 = LeagueGen.newLeague(2027)
            val bytes2 = SaveIO.encode(lg2)
            SaveManager.saveSlot(tempDir, "slot1", bytes2, lg2, "Version 2")
            assertTrue(bakFile.exists())
            assertFalse(bak2File.exists())

            // Third save -> .bak2 created
            val lg3 = LeagueGen.newLeague(2028)
            val bytes3 = SaveIO.encode(lg3)
            SaveManager.saveSlot(tempDir, "slot1", bytes3, lg3, "Version 3")
            assertTrue(bakFile.exists())
            assertTrue(bak2File.exists())

            // Corrupt primary .sav: should recover from .bak (Version 2)
            savFile.writeBytes("corrupted".toByteArray())
            val recovered1 = SaveManager.loadSlot(tempDir, "slot1")
            assertNotNull(recovered1)
            assertEquals(2027, recovered1!!.first.season)

            // Corrupt primary and .bak: should recover from .bak2 (Version 1)
            savFile.writeBytes("corrupted1".toByteArray())
            bakFile.writeBytes("corrupted2".toByteArray())
            val recovered2 = SaveManager.loadSlot(tempDir, "slot1")
            assertNotNull(recovered2)
            assertEquals(2026, recovered2!!.first.season)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testCloningAndRenaming() {
        val tempDir = createTempDir("clone_rename_test")
        try {
            val lg = LeagueGen.newLeague(2026)
            val bytes = SaveIO.encode(lg)
            SaveManager.saveSlot(tempDir, "original", bytes, lg, "Original Slot")

            // Clone to snapshot
            val snapshot = SaveManager.cloneSlot(tempDir, "original", "Playoff Checkpoint")
            assertNotNull(snapshot)
            assertEquals("Playoff Checkpoint", snapshot!!.title)
            assertNotEquals("original", snapshot.id)

            val slots = SaveManager.listSlots(tempDir)
            assertEquals(2, slots.size)

            // Rename snapshot
            val renamed = SaveManager.renameSlot(tempDir, snapshot.id, "Pre-Finals Checkpoint")
            assertNotNull(renamed)
            assertEquals("Pre-Finals Checkpoint", renamed!!.title)

            val loadedSnapshot = SaveManager.loadSlot(tempDir, snapshot.id)
            assertNotNull(loadedSnapshot)
            assertEquals("Pre-Finals Checkpoint", loadedSnapshot!!.second.title)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testLegacyMigration() {
        val tempDir = createTempDir("legacy_migration_test")
        try {
            val lg = LeagueGen.newLeague(2026)
            val legacyFile = File(tempDir, "dynasty.sav")
            val legacyBak = File(tempDir, "dynasty.sav.bak")
            legacyFile.writeBytes(SaveIO.encode(lg))
            legacyBak.writeBytes(SaveIO.encode(lg))

            // Listing slots should auto-migrate dynasty.sav into saves/default.sav
            val slots = SaveManager.listSlots(tempDir)
            assertEquals(1, slots.size)
            assertEquals("default", slots[0].id)
            assertEquals(lg.user.school, slots[0].school)

            val loaded = SaveManager.loadSlot(tempDir, "default")
            assertNotNull(loaded)
            assertEquals(lg.teams.size, loaded!!.first.teams.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDeleteSlot() {
        val tempDir = createTempDir("delete_slot_test")
        try {
            val lg1 = LeagueGen.newLeague(2026)
            val lg2 = LeagueGen.newLeague(2027)
            SaveManager.saveSlot(tempDir, "slot1", SaveIO.encode(lg1), lg1, "Dynasty 1")
            SaveManager.saveSlot(tempDir, "slot2", SaveIO.encode(lg2), lg2, "Dynasty 2")

            var slots = SaveManager.listSlots(tempDir)
            assertEquals(2, slots.size)

            SaveManager.deleteSlot(tempDir, "slot1")
            slots = SaveManager.listSlots(tempDir)
            assertEquals(1, slots.size)
            assertEquals("slot2", slots[0].id)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testImportAndExport() {
        val tempDir = createTempDir("import_export_test")
        try {
            val lg = LeagueGen.newLeague(2026)
            val bytes = SaveIO.encode(lg)

            // Import
            val imported = SaveManager.importSave(tempDir, bytes, "Imported Dynasty")
            assertNotNull(imported)
            assertEquals("Imported Dynasty", imported!!.second.title)
            assertEquals(lg.user.school, imported.second.school)

            // Export
            val exportedBytes = SaveManager.getSlotBytes(tempDir, imported.second.id)
            assertNotNull(exportedBytes)
            val decoded = SaveIO.decode(exportedBytes!!)
            assertEquals(lg.teams.size, decoded.teams.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testFormatHelpers() {
        assertEquals("Just now", SaveManager.formatSaveTime(System.currentTimeMillis()))
        assertEquals("5m ago", SaveManager.formatSaveTime(System.currentTimeMillis() - 5 * 60 * 1000L))
        assertEquals("2h ago", SaveManager.formatSaveTime(System.currentTimeMillis() - 2 * 3600 * 1000L))
        assertEquals("3d ago", SaveManager.formatSaveTime(System.currentTimeMillis() - 3 * 86400 * 1000L))

        assertEquals("500 B", SaveManager.formatFileSize(500L))
        assertEquals("12 KB", SaveManager.formatFileSize(12 * 1024L))
        assertEquals("1.5 MB", SaveManager.formatFileSize((1.5 * 1024 * 1024).toLong()))
    }
}
