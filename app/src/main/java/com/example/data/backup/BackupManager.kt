package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.cover.CoverArtResolver
import com.example.data.local.BackgroundVideoManager
import com.example.data.local.FolderPreferences
import com.example.data.local.PlayCountManager
import com.example.data.local.PlaylistCoverManager
import com.example.data.local.RecentlyAddedPreferences
import com.example.data.local.RecentlyAddedTimeframe
import com.example.data.local.TabPreferences
import com.example.data.repository.MusicRepository
import com.example.model.Playlist
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportResult(
    val success: Boolean,
    val message: String,
    val playlistsImported: Int = 0,
    val matchedSongs: Int = 0,
    val unlinkedSongs: Int = 0,
    val coversImported: Int = 0,
    val videosImported: Int = 0
)

class BackupManager(private val context: Context) {

    private val musicRepo = MusicRepository.getInstance(context)
    private val tabPrefs = TabPreferences(context)
    private val folderPrefs = FolderPreferences(context)
    private val recentlyAddedPrefs = RecentlyAddedPreferences(context)
    private val videoManager = BackgroundVideoManager.getInstance(context)
    private val playCountManager = PlayCountManager.getInstance(context)
    private val coverArtResolver = CoverArtResolver.getInstance(context)
    private val unlinkedSongManager = UnlinkedSongManager.getInstance(context)

    private val pickedCoversDir = File(context.filesDir, "picked_cover_art").apply { mkdirs() }
    private val playlistCoversDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
    private val videosDir = File(context.filesDir, "background_videos").apply { mkdirs() }

    fun computeMediaSizeBytes(): Long {
        var total = 0L
        fun addDir(dir: File) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile) total += file.length()
                }
            }
        }
        addDir(pickedCoversDir)
        addDir(playlistCoversDir)
        addDir(videosDir)
        return total
    }

    suspend fun exportBackup(
        outputStream: OutputStream,
        includeMedia: Boolean,
        allDiscoveredSongs: List<Song>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val songMapByLocalId = allDiscoveredSongs.associateBy { it.id }
            val playCounts = playCountManager.getPlayCounts()
            val videoState = videoManager.videoState.value
            val currentPlaylists = musicRepo.getPlaylists().value

            // Collect all song IDs that should be in the backup
            val songIdsToInclude = mutableSetOf<Long>()
            currentPlaylists.forEach { pl -> songIdsToInclude.addAll(pl.songIds) }
            playCounts.keys.forEach { songIdsToInclude.add(it) }
            videoState.songVideos.keys.forEach { songIdsToInclude.add(it) }

            // If library has other discovered songs, also include them so playlist rules & links are complete
            allDiscoveredSongs.forEach { songIdsToInclude.add(it.id) }

            // Map each song ID to an idInBackup (1..N)
            val idToBackupId = mutableMapOf<Long, Int>()
            val backupSongs = mutableListOf<BackupSongRef>()
            var nextBackupId = 1

            for (songId in songIdsToInclude) {
                val song = songMapByLocalId[songId] ?: continue
                val idInBackup = nextBackupId++
                idToBackupId[songId] = idInBackup

                val videoPath = videoState.songVideos[songId]
                val videoZipEntry = if (includeMedia && videoPath != null && File(videoPath).exists()) {
                    "videos/${File(videoPath).name}"
                } else null

                val songRef = SongKeyMatcher.createSongRef(
                    idInBackup = idInBackup,
                    song = song,
                    playCount = playCounts[songId] ?: 0,
                    videoPathInZip = videoZipEntry
                )
                backupSongs.add(songRef)
            }

            // Playlists
            val backupPlaylists = currentPlaylists.map { pl ->
                val songRefs = pl.songIds.mapNotNull { idToBackupId[it] }
                val customCoverZipEntry = if (includeMedia && pl.customCoverPath != null && File(pl.customCoverPath).exists()) {
                    "playlist_covers/${File(pl.customCoverPath).name}"
                } else null

                BackupPlaylist(
                    id = pl.id,
                    name = pl.name,
                    songRefs = songRefs,
                    isSmart = pl.isSmart,
                    matchAllRules = pl.matchAllRules,
                    rules = pl.rules,
                    exclusionKeywords = pl.exclusionKeywords,
                    customCoverPathInZip = customCoverZipEntry
                )
            }

            // Settings
            val (knownFolders, enabledFolders) = folderPrefs.getPersistedFolderSets()
            val backupSettings = BackupSettings(
                hiddenTabs = tabPrefs.getHiddenTabs(),
                enabledFolders = enabledFolders,
                knownFolders = knownFolders,
                recentlyAddedDays = recentlyAddedPrefs.getTimeframe().days,
                disableOnBatterySaver = videoState.disableOnBatterySaver,
                showLargeAlbumArtWithVideo = videoState.showLargeAlbumArtWithVideo,
                videoDimming = videoState.videoDimming
            )

            // Picked covers
            val backupPickedCovers = mutableListOf<BackupCoverRef>()
            if (pickedCoversDir.exists() && pickedCoversDir.isDirectory) {
                pickedCoversDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 0) {
                        val name = file.name
                        val albumKey = name.removeSuffix(".jpg").replace("___", ":::")
                        val entryName = "picked_covers/$name"
                        backupPickedCovers.add(
                            BackupCoverRef(
                                albumKey = albumKey,
                                songRefId = null,
                                imagePathInZip = entryName
                            )
                        )
                    }
                }
            }

            // Global video
            val globalVideoZipEntry = if (includeMedia && videoState.globalVideoPath != null && File(videoState.globalVideoPath).exists()) {
                "videos/${File(videoState.globalVideoPath).name}"
            } else null

            val manifest = BackupManifest(
                version = BackupManifest.CURRENT_VERSION,
                createdAt = System.currentTimeMillis(),
                includesMedia = includeMedia,
                settings = backupSettings,
                songs = backupSongs,
                playlists = backupPlaylists,
                pickedCovers = backupPickedCovers,
                globalVideoPathInZip = globalVideoZipEntry
            )

            // Write to ZipOutputStream
            val zipOut = ZipOutputStream(BufferedOutputStream(outputStream))
            val writtenFiles = mutableSetOf<String>()

            // 1. Manifest
            val manifestEntry = ZipEntry("backup_manifest.json")
            zipOut.putNextEntry(manifestEntry)
            val manifestBytes = manifest.toJson().toString(2).toByteArray(StandardCharsets.UTF_8)
            zipOut.write(manifestBytes)
            zipOut.closeEntry()

            // 2. Media files (if requested)
            if (includeMedia) {
                fun writeLocalFileToZip(file: File, zipEntryPath: String) {
                    if (!file.exists() || file.length() == 0L || zipEntryPath in writtenFiles) return
                    writtenFiles.add(zipEntryPath)
                    try {
                        zipOut.putNextEntry(ZipEntry(zipEntryPath))
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing $zipEntryPath to zip", e)
                    }
                }

                // Global video
                if (videoState.globalVideoPath != null) {
                    writeLocalFileToZip(File(videoState.globalVideoPath), "videos/${File(videoState.globalVideoPath).name}")
                }
                // Song videos
                for (vPath in videoState.songVideos.values) {
                    writeLocalFileToZip(File(vPath), "videos/${File(vPath).name}")
                }
                // Playlist covers
                if (playlistCoversDir.exists()) {
                    playlistCoversDir.listFiles()?.forEach { file ->
                        if (file.isFile) writeLocalFileToZip(file, "playlist_covers/${file.name}")
                    }
                }
                // Picked covers
                if (pickedCoversDir.exists()) {
                    pickedCoversDir.listFiles()?.forEach { file ->
                        if (file.isFile) writeLocalFileToZip(file, "picked_covers/${file.name}")
                    }
                }
            }

            zipOut.finish()
            zipOut.flush()
            Result.success(backupSongs.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting backup", e)
            Result.failure(e)
        }
    }

    suspend fun inspectBackup(
        inputStream: InputStream,
        currentSongs: List<Song>
    ): BackupSummary? = withContext(Dispatchers.IO) {
        try {
            val zipIn = ZipInputStream(BufferedInputStream(inputStream))
            var entry: ZipEntry?
            var manifestJson: String? = null

            while (zipIn.nextEntry.also { entry = it } != null) {
                if (entry!!.name == "backup_manifest.json") {
                    val bytes = zipIn.readBytes()
                    manifestJson = String(bytes, StandardCharsets.UTF_8)
                    break
                }
            }
            if (manifestJson == null) return@withContext null

            val manifest = BackupManifest.fromJson(JSONObject(manifestJson))

            var matched = 0
            var unlinked = 0
            for (songRef in manifest.songs) {
                val match = SongKeyMatcher.findBestMatch(songRef, currentSongs)
                if (match != null) {
                    matched++
                } else {
                    unlinked++
                }
            }

            BackupSummary(
                version = manifest.version,
                createdAt = manifest.createdAt,
                includesMedia = manifest.includesMedia,
                playlistCount = manifest.playlists.size,
                totalSongRefCount = manifest.songs.size,
                matchedSongCount = matched,
                unlinkedSongCount = unlinked,
                pickedCoverCount = manifest.pickedCovers.size,
                hasGlobalVideo = manifest.globalVideoPathInZip != null,
                songVideoCount = manifest.songs.count { it.videoPathInZip != null },
                manifest = manifest
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed inspecting backup", e)
            null
        }
    }

    suspend fun importBackup(
        inputStream: InputStream,
        mode: BackupMode,
        currentSongs: List<Song>
    ): ImportResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "backup_import_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            // Unpack ZIP into tempDir
            val zipIn = ZipInputStream(BufferedInputStream(inputStream))
            var entry: ZipEntry?
            while (zipIn.nextEntry.also { entry = it } != null) {
                val targetFile = File(tempDir, entry!!.name)
                if (entry!!.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { out ->
                        zipIn.copyTo(out)
                    }
                }
                zipIn.closeEntry()
            }

            val manifestFile = File(tempDir, "backup_manifest.json")
            if (!manifestFile.exists()) {
                return@withContext ImportResult(false, "Invalid backup: backup_manifest.json missing")
            }

            val manifest = BackupManifest.fromJson(JSONObject(manifestFile.readText(StandardCharsets.UTF_8)))

            // Match songs: idInBackup -> Song ID (real local ID, or synthetic negative ID)
            val backupIdToLocalId = mutableMapOf<Int, Long>()
            val unlinkedSongsList = mutableListOf<Pair<Long, BackupSongRef>>()
            var matchedCount = 0
            var unlinkedCount = 0

            for (songRef in manifest.songs) {
                val matched = SongKeyMatcher.findBestMatch(songRef, currentSongs)
                if (matched != null) {
                    backupIdToLocalId[songRef.idInBackup] = matched.id
                    matchedCount++
                } else {
                    val syntheticId = -(100000L + songRef.idInBackup)
                    backupIdToLocalId[songRef.idInBackup] = syntheticId
                    unlinkedSongsList.add(Pair(syntheticId, songRef))
                    unlinkedCount++
                }
            }

            // Register unlinked songs
            unlinkedSongManager.saveUnlinkedSongs(
                songs = unlinkedSongsList,
                replaceExisting = (mode == BackupMode.REPLACE)
            )

            // 1. Settings
            val settings = manifest.settings
            if (mode == BackupMode.REPLACE) {
                // Hidden tabs
                val currentTabs = tabPrefs.getHiddenTabs().toMutableSet()
                currentTabs.forEach { tabPrefs.setTabHidden(it, false) }
                settings.hiddenTabs.forEach { tabPrefs.setTabHidden(it, true) }

                // Folders
                folderPrefs.applyPersistedFolderSets(settings.knownFolders, settings.enabledFolders)

                // Recently added
                val tf = RecentlyAddedTimeframe.values().find { it.days == settings.recentlyAddedDays }
                    ?: RecentlyAddedTimeframe.LAST_30_DAYS
                recentlyAddedPrefs.setTimeframe(tf)

                // Video options
                videoManager.setDisableOnBatterySaver(settings.disableOnBatterySaver)
                videoManager.setShowLargeAlbumArt(settings.showLargeAlbumArtWithVideo)
                videoManager.setVideoDimming(settings.videoDimming)
            } else {
                // MERGE: add newly hidden tabs
                settings.hiddenTabs.forEach { tabPrefs.setTabHidden(it, true) }
                // Merge folders
                val (curKnown, curEnabled) = folderPrefs.getPersistedFolderSets()
                folderPrefs.applyPersistedFolderSets(
                    curKnown + settings.knownFolders,
                    curEnabled + settings.enabledFolders
                )
            }

            // 2. Media files extraction
            fun copyDirContents(srcSubDirName: String, destDir: File, clearFirst: Boolean) {
                val src = File(tempDir, srcSubDirName)
                if (!src.exists()) return
                destDir.mkdirs()
                if (clearFirst) {
                    destDir.listFiles()?.forEach { it.delete() }
                }
                src.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        val destFile = File(destDir, f.name)
                        f.copyTo(destFile, overwrite = true)
                    }
                }
            }

            if (manifest.includesMedia) {
                copyDirContents("picked_covers", pickedCoversDir, clearFirst = (mode == BackupMode.REPLACE))
                copyDirContents("playlist_covers", playlistCoversDir, clearFirst = (mode == BackupMode.REPLACE))
                copyDirContents("videos", videosDir, clearFirst = (mode == BackupMode.REPLACE))
            }

            // 3. Play counts
            for (songRef in manifest.songs) {
                if (songRef.playCount > 0) {
                    val localId = backupIdToLocalId[songRef.idInBackup]
                    if (localId != null) {
                        if (mode == BackupMode.REPLACE) {
                            playCountManager.setPlayCount(localId, songRef.playCount)
                        } else {
                            val cur = playCountManager.getPlayCount(localId)
                            if (songRef.playCount > cur) {
                                playCountManager.setPlayCount(localId, songRef.playCount)
                            }
                        }
                    }
                }
            }

            // 4. Background Videos
            if (manifest.globalVideoPathInZip != null) {
                val videoFileName = File(manifest.globalVideoPathInZip).name
                val targetFile = File(videosDir, videoFileName)
                if (targetFile.exists() && (mode == BackupMode.REPLACE || videoManager.videoState.value.globalVideoPath == null)) {
                    videoManager.setGlobalVideoPathDirect(targetFile.absolutePath)
                }
            }

            val importedSongVideos = mutableMapOf<Long, String>()
            for (songRef in manifest.songs) {
                if (songRef.videoPathInZip != null) {
                    val vFile = File(videosDir, File(songRef.videoPathInZip).name)
                    val localId = backupIdToLocalId[songRef.idInBackup]
                    if (vFile.exists() && localId != null) {
                        importedSongVideos[localId] = vFile.absolutePath
                    }
                }
            }
            if (importedSongVideos.isNotEmpty()) {
                videoManager.applyImportedSongVideos(importedSongVideos, replace = (mode == BackupMode.REPLACE))
            }

            // 5. Playlists
            val existingPlaylists = musicRepo.getPlaylists().value.toMutableList()
            var playlistsCount = 0

            val mappedPlaylists = manifest.playlists.map { bPl ->
                val resolvedSongIds = bPl.songRefs.mapNotNull { backupIdToLocalId[it] }
                val coverPath = if (bPl.customCoverPathInZip != null) {
                    File(playlistCoversDir, File(bPl.customCoverPathInZip).name).absolutePath
                } else null

                Playlist(
                    id = bPl.id,
                    name = bPl.name,
                    songIds = resolvedSongIds,
                    customCoverPath = coverPath,
                    isSmart = bPl.isSmart,
                    matchAllRules = bPl.matchAllRules,
                    rules = bPl.rules,
                    exclusionKeywords = bPl.exclusionKeywords
                )
            }

            if (mode == BackupMode.REPLACE) {
                musicRepo.replaceAllPlaylists(mappedPlaylists)
                playlistsCount = mappedPlaylists.size
            } else {
                // MERGE: For each imported playlist, if one with same name exists, merge songIds
                val mergedList = existingPlaylists.toMutableList()
                for (impPl in mappedPlaylists) {
                    val existingIdx = mergedList.indexOfFirst { it.name.equals(impPl.name, ignoreCase = true) }
                    if (existingIdx >= 0) {
                        val existing = mergedList[existingIdx]
                        val combinedIds = (existing.songIds + impPl.songIds).distinct()
                        mergedList[existingIdx] = existing.copy(
                            songIds = combinedIds,
                            customCoverPath = existing.customCoverPath ?: impPl.customCoverPath
                        )
                    } else {
                        // generate unique ID if needed
                        val newId = System.currentTimeMillis() + mergedList.size
                        mergedList.add(impPl.copy(id = newId))
                    }
                    playlistsCount++
                }
                musicRepo.replaceAllPlaylists(mergedList)
            }

            // Refresh picked cover map
            coverArtResolver.refreshCustomArt()

            ImportResult(
                success = true,
                message = "Backup imported successfully",
                playlistsImported = playlistsCount,
                matchedSongs = matchedCount,
                unlinkedSongs = unlinkedCount,
                coversImported = manifest.pickedCovers.size,
                videosImported = (if (manifest.globalVideoPathInZip != null) 1 else 0) + importedSongVideos.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed importing backup", e)
            ImportResult(false, "Failed importing backup: ${e.message}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    companion object {
        private const val TAG = "BackupManager"

        @Volatile
        private var INSTANCE: BackupManager? = null

        fun getInstance(context: Context): BackupManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackupManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
