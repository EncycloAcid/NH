package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class SaveSlot(
    val id: String,
    val title: String,
    val school: String,
    val mascot: String,
    val abbr: String,
    val season: Int,
    val record: String,
    val mode: String, // "College Coach", "Road to League", "NBA Coach"
    val coachOrPlayer: String,
    val primaryColor: Int,
    val secondaryColor: Int,
    val lastSaved: Long,
    val sizeBytes: Long = 0L,
    val isCurrent: Boolean = false,
)

object SaveManager {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun getSavesDir(rootDir: File): File = File(rootDir, "saves").apply { if (!exists()) mkdirs() }

    fun getActiveSlotFile(rootDir: File): File = File(getSavesDir(rootDir), "active_slot.txt")

    fun getActiveSlotId(rootDir: File): String {
        val activeFile = getActiveSlotFile(rootDir)
        if (activeFile.exists()) {
            val id = runCatching { activeFile.readText().trim() }.getOrNull()
            if (!id.isNullOrBlank()) {
                val sav = File(getSavesDir(rootDir), "$id.sav")
                if (sav.exists() || File(getSavesDir(rootDir), "$id.sav.bak").exists()) {
                    return id
                }
            }
        }
        val defaultSav = File(getSavesDir(rootDir), "default.sav")
        if (defaultSav.exists() || File(getSavesDir(rootDir), "default.sav.bak").exists()) {
            return "default"
        }
        val saves = getSavesDir(rootDir).listFiles { f -> f.extension == "sav" } ?: emptyArray()
        if (saves.isNotEmpty()) {
            return saves.maxByOrNull { it.lastModified() }?.nameWithoutExtension ?: "default"
        }
        return "default"
    }

    fun setActiveSlotId(rootDir: File, id: String) {
        runCatching {
            getActiveSlotFile(rootDir).writeText(id)
        }
    }

    /**
     * Seamless backward compatibility: if a legacy dynasty.sav exists in rootDir,
     * import it into saves/default.sav so the user keeps all existing progress.
     */
    fun migrateLegacyIfNeeded(rootDir: File) {
        val legacy = File(rootDir, "dynasty.sav")
        if (!legacy.exists()) return

        val savesDir = getSavesDir(rootDir)
        val defaultSav = File(savesDir, "default.sav")
        if (!defaultSav.exists()) {
            runCatching {
                legacy.copyTo(defaultSav, overwrite = true)
                val legacyBak = File(rootDir, "dynasty.sav.bak")
                if (legacyBak.exists()) {
                    legacyBak.copyTo(File(savesDir, "default.sav.bak"), overwrite = true)
                }
                val lg = runCatching { SaveIO.decode(defaultSav.readBytes()) }.getOrNull()
                val meta = if (lg != null) {
                    metaFromLeague("default", "${lg.user.school} Dynasty", lg, defaultSav.lastModified(), defaultSav.length())
                } else {
                    SaveSlot(
                        id = "default",
                        title = "Default Dynasty",
                        school = "Hoops",
                        mascot = "Dynasty",
                        abbr = "HD",
                        season = 2026,
                        record = "0-0",
                        mode = "College Coach",
                        coachOrPlayer = "Coach",
                        primaryColor = 0xFF1E3A8A.toInt(),
                        secondaryColor = 0xFFF59E0B.toInt(),
                        lastSaved = defaultSav.lastModified(),
                        sizeBytes = defaultSav.length(),
                        isCurrent = true,
                    )
                }
                File(savesDir, "default.meta.json").writeText(json.encodeToString(SaveSlot.serializer(), meta))
                setActiveSlotId(rootDir, "default")
            }
        }
    }

    fun metaFromLeague(
        id: String,
        title: String,
        lg: League,
        lastSaved: Long = System.currentTimeMillis(),
        sizeBytes: Long = 0L,
        isCurrent: Boolean = false,
    ): SaveSlot {
        val u = runCatching { lg.user }.getOrNull()
        val school = u?.school ?: "Hoops"
        val mascot = u?.mascot ?: "Dynasty"
        val abbr = u?.abbr ?: "HD"
        val primary = (u?.primary ?: 0xFF1E3A8AL).toInt()
        val secondary = (u?.secondary ?: 0xFFF59E0BL).toInt()
        val rec = if (u != null) "${u.w}-${u.l}" else "0-0"
        val modeName = when {
            lg.mode == GameMode.PLAYER -> "Road to League"
            lg.pro -> "NBA Coach"
            else -> "College Coach"
        }
        val person = when {
            lg.mode == GameMode.PLAYER -> lg.hero?.let { "${it.name} (${it.pos})" } ?: "Player"
            else -> lg.coaches.firstOrNull { it.id == lg.userCoachId }?.name ?: "Coach"
        }
        return SaveSlot(
            id = id,
            title = title.ifBlank { "$school Dynasty" },
            school = school,
            mascot = mascot,
            abbr = abbr,
            season = lg.season,
            record = rec,
            mode = modeName,
            coachOrPlayer = person,
            primaryColor = primary,
            secondaryColor = secondary,
            lastSaved = lastSaved,
            sizeBytes = sizeBytes,
            isCurrent = isCurrent,
        )
    }

    /**
     * Lists all saved dynasty slots, scanning metadata and generating missing metadata on the fly.
     */
    fun listSlots(rootDir: File): List<SaveSlot> {
        migrateLegacyIfNeeded(rootDir)
        val savesDir = getSavesDir(rootDir)
        val activeId = getActiveSlotId(rootDir)
        val savFiles = savesDir.listFiles { f -> f.extension == "sav" } ?: emptyArray()

        val results = mutableListOf<SaveSlot>()
        for (sav in savFiles) {
            val id = sav.nameWithoutExtension
            val metaFile = File(savesDir, "$id.meta.json")
            var slot: SaveSlot? = null
            if (metaFile.exists()) {
                slot = runCatching {
                    json.decodeFromString(SaveSlot.serializer(), metaFile.readText())
                }.getOrNull()
            }
            if (slot == null) {
                // Fallback: decode league to generate metadata
                val lg = runCatching { SaveIO.decode(sav.readBytes()) }.getOrNull()
                if (lg != null) {
                    slot = metaFromLeague(
                        id = id,
                        title = "${lg.user.school} Dynasty",
                        lg = lg,
                        lastSaved = sav.lastModified(),
                        sizeBytes = sav.length(),
                        isCurrent = (id == activeId),
                    )
                    runCatching {
                        metaFile.writeText(json.encodeToString(SaveSlot.serializer(), slot))
                    }
                }
            }
            if (slot != null) {
                results.add(slot.copy(sizeBytes = sav.length(), isCurrent = (id == activeId)))
            }
        }

        results.sortWith { a, b ->
            if (a.isCurrent != b.isCurrent) {
                if (a.isCurrent) -1 else 1
            } else {
                b.lastSaved.compareTo(a.lastSaved)
            }
        }
        return results
    }

    /**
     * Writes save bytes with two-stage rolling backup (.bak and .bak2) and atomic write (.tmp -> rename).
     */
    fun saveSlot(
        rootDir: File,
        id: String,
        bytes: ByteArray,
        lg: League,
        title: String? = null,
    ): SaveSlot {
        val savesDir = getSavesDir(rootDir)
        val savFile = File(savesDir, "$id.sav")
        val tmpFile = File(savesDir, "$id.sav.tmp")
        val bakFile = File(savesDir, "$id.sav.bak")
        val bak2File = File(savesDir, "$id.sav.bak2")
        val metaFile = File(savesDir, "$id.meta.json")

        // 1. Write to temp file
        FileOutputStream(tmpFile).use { fos ->
            fos.write(bytes)
            fos.fd.sync()
        }

        // 2. Rolling backup rotation (.bak -> .bak2, .sav -> .bak)
        if (bakFile.exists()) {
            runCatching { bakFile.copyTo(bak2File, overwrite = true) }
        }
        if (savFile.exists()) {
            runCatching { savFile.copyTo(bakFile, overwrite = true) }
        }

        // 3. Atomic rename
        if (!tmpFile.renameTo(savFile)) {
            savFile.delete()
            tmpFile.renameTo(savFile)
        }

        // 4. Update metadata
        val existingMeta = if (metaFile.exists()) {
            runCatching { json.decodeFromString(SaveSlot.serializer(), metaFile.readText()) }.getOrNull()
        } else null

        val slotTitle = title ?: existingMeta?.title ?: "${lg.user.school} Dynasty"
        val meta = metaFromLeague(
            id = id,
            title = slotTitle,
            lg = lg,
            lastSaved = System.currentTimeMillis(),
            sizeBytes = savFile.length(),
            isCurrent = true,
        )
        runCatching {
            metaFile.writeText(json.encodeToString(SaveSlot.serializer(), meta))
        }

        // 5. Set active slot
        setActiveSlotId(rootDir, id)

        // 6. Backward compatibility mirror: if this is "default", mirror to root dynasty.sav
        if (id == "default") {
            runCatching {
                val rootSav = File(rootDir, "dynasty.sav")
                savFile.copyTo(rootSav, overwrite = true)
                if (bakFile.exists()) {
                    bakFile.copyTo(File(rootDir, "dynasty.sav.bak"), overwrite = true)
                }
            }
        }

        return meta
    }

    /**
     * Loads a save slot with multi-tier fallback (.sav -> .bak -> .bak2).
     */
    fun loadSlot(rootDir: File, id: String): Pair<League, SaveSlot>? {
        migrateLegacyIfNeeded(rootDir)
        val savesDir = getSavesDir(rootDir)
        val savFile = File(savesDir, "$id.sav")
        val bakFile = File(savesDir, "$id.sav.bak")
        val bak2File = File(savesDir, "$id.sav.bak2")
        val metaFile = File(savesDir, "$id.meta.json")

        var lg: League? = null

        // Attempt 1: primary save
        if (savFile.exists()) {
            lg = runCatching { SaveIO.decode(savFile.readBytes()) }.getOrNull()
        }

        // Attempt 2: primary backup
        if (lg == null && bakFile.exists()) {
            lg = runCatching { SaveIO.decode(bakFile.readBytes()) }.getOrNull()
            if (lg != null) {
                if (savFile.exists()) savFile.copyTo(File(savesDir, "$id.sav.corrupt.${System.currentTimeMillis()}"))
                runCatching { bakFile.copyTo(savFile, overwrite = true) }
            }
        }

        // Attempt 3: secondary backup
        if (lg == null && bak2File.exists()) {
            lg = runCatching { SaveIO.decode(bak2File.readBytes()) }.getOrNull()
            if (lg != null) {
                if (savFile.exists()) savFile.copyTo(File(savesDir, "$id.sav.corrupt.${System.currentTimeMillis()}"))
                runCatching { bak2File.copyTo(savFile, overwrite = true) }
            }
        }

        if (lg == null) {
            // Isolate corrupted file if it exists
            if (savFile.exists()) {
                val corrupt = File(savesDir, "$id.sav.corrupt.${System.currentTimeMillis()}")
                runCatching { savFile.copyTo(corrupt, overwrite = true) }
            }
            return null
        }

        setActiveSlotId(rootDir, id)

        var meta = if (metaFile.exists()) {
            runCatching { json.decodeFromString(SaveSlot.serializer(), metaFile.readText()) }.getOrNull()
        } else null

        if (meta == null) {
            meta = metaFromLeague(id, "${lg.user.school} Dynasty", lg, savFile.lastModified(), savFile.length(), isCurrent = true)
            runCatching { metaFile.writeText(json.encodeToString(SaveSlot.serializer(), meta)) }
        } else {
            meta = meta.copy(isCurrent = true, sizeBytes = savFile.length())
        }

        return Pair(lg, meta)
    }

    fun resumeSlot(rootDir: File, id: String): Pair<League, SaveSlot>? {
        val loaded = loadSlot(rootDir, id)
        if (loaded == null) {
            val dir = File(rootDir, "saves")
            val exists = listOf(".sav", ".sav.bak", ".sav.bak2").any { File(dir, id + it).exists() } ||
                dir.listFiles()?.any { it.name.endsWith(".sav") } == true || File(rootDir, "dynasty.sav").exists()
            check(!exists) { "The selected save could not be read. All save files have been kept." }
        } else {
            val lg = loaded.first
            check(lg.userTeamId in lg.teams.indices && lg.teams[lg.userTeamId].id == lg.userTeamId) { "Invalid saved team." }
            check(!lg.pro || lg.proState != null) { "Missing saved pro season." }
        }
        return loaded
    }

    /**
     * Clones an existing slot into a new slot (checkpoint / snapshot).
     */
    fun cloneSlot(rootDir: File, sourceId: String, newTitle: String): SaveSlot? {
        val savesDir = getSavesDir(rootDir)
        val srcFile = File(savesDir, "$sourceId.sav")
        if (!srcFile.exists()) return null

        val newId = "slot_${System.currentTimeMillis()}_${(100..999).random()}"
        val destFile = File(savesDir, "$newId.sav")
        srcFile.copyTo(destFile, overwrite = true)

        val srcMetaFile = File(savesDir, "$sourceId.meta.json")
        val srcMeta = if (srcMetaFile.exists()) {
            runCatching { json.decodeFromString(SaveSlot.serializer(), srcMetaFile.readText()) }.getOrNull()
        } else null

        val newMeta = if (srcMeta != null) {
            srcMeta.copy(
                id = newId,
                title = newTitle.ifBlank { "${srcMeta.school} Snapshot" },
                lastSaved = System.currentTimeMillis(),
                sizeBytes = destFile.length(),
                isCurrent = false,
            )
        } else {
            val lg = runCatching { SaveIO.decode(destFile.readBytes()) }.getOrNull() ?: return null
            metaFromLeague(newId, newTitle, lg, System.currentTimeMillis(), destFile.length(), isCurrent = false)
        }

        File(savesDir, "$newId.meta.json").writeText(json.encodeToString(SaveSlot.serializer(), newMeta))
        return newMeta
    }

    /**
     * Renames an existing save slot.
     */
    fun renameSlot(rootDir: File, id: String, newTitle: String): SaveSlot? {
        val savesDir = getSavesDir(rootDir)
        val metaFile = File(savesDir, "$id.meta.json")
        val meta = if (metaFile.exists()) {
            runCatching { json.decodeFromString(SaveSlot.serializer(), metaFile.readText()) }.getOrNull()
        } else null

        val updated = if (meta != null) {
            meta.copy(title = newTitle.trim().ifBlank { meta.title })
        } else {
            val savFile = File(savesDir, "$id.sav")
            if (!savFile.exists()) return null
            val lg = runCatching { SaveIO.decode(savFile.readBytes()) }.getOrNull() ?: return null
            metaFromLeague(id, newTitle, lg, savFile.lastModified(), savFile.length())
        }

        runCatching {
            metaFile.writeText(json.encodeToString(SaveSlot.serializer(), updated))
        }
        return updated
    }

    /**
     * Deletes a save slot and all associated backup/metadata files.
     */
    fun deleteSlot(rootDir: File, id: String): Boolean {
        val savesDir = getSavesDir(rootDir)
        File(savesDir, "$id.sav").delete()
        File(savesDir, "$id.sav.bak").delete()
        File(savesDir, "$id.sav.bak2").delete()
        File(savesDir, "$id.sav.tmp").delete()
        File(savesDir, "$id.meta.json").delete()

        if (id == "default") {
            File(rootDir, "dynasty.sav").delete()
            File(rootDir, "dynasty.sav.bak").delete()
            File(rootDir, "dynasty.sav.tmp").delete()
        }

        val activeId = getActiveSlotId(rootDir)
        if (activeId == id) {
            val remaining = listSlots(rootDir)
            if (remaining.isNotEmpty()) {
                setActiveSlotId(rootDir, remaining.first().id)
            } else {
                getActiveSlotFile(rootDir).delete()
            }
        }
        return true
    }

    /**
     * Imports bytes from an external save file (.hdsave or .sav) into a new slot.
     */
    fun importSave(rootDir: File, bytes: ByteArray, customTitle: String? = null): Pair<League, SaveSlot>? {
        val lg = runCatching { SaveIO.decode(bytes) }.getOrNull() ?: return null
        val id = "slot_${System.currentTimeMillis()}_${(100..999).random()}"
        val title = customTitle ?: "${lg.user.school} Dynasty"
        val slot = saveSlot(rootDir, id, bytes, lg, title)
        return Pair(lg, slot)
    }

    fun getSlotBytes(rootDir: File, id: String): ByteArray? {
        val savesDir = getSavesDir(rootDir)
        val savFile = File(savesDir, "$id.sav")
        if (savFile.exists()) return savFile.readBytes()
        val bakFile = File(savesDir, "$id.sav.bak")
        if (bakFile.exists()) return bakFile.readBytes()
        return null
    }

    fun formatSaveTime(epochMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = (now - epochMillis).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> "Just now"
            diff < 3600_000L -> "${diff / 60_000L}m ago"
            diff < 86400_000L -> "${diff / 3600_000L}h ago"
            diff < 7 * 86400_000L -> "${diff / 86400_000L}d ago"
            else -> {
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                sdf.format(Date(epochMillis))
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes <= 0L -> "0 KB"
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${(bytes + 1023L) / 1024L} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
        }
    }
}
