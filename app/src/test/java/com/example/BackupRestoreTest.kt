package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupCoverRef
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupManifest
import com.example.data.backup.BackupMode
import com.example.data.backup.BackupPlaylist
import com.example.data.backup.BackupSettings
import com.example.data.backup.BackupSongRef
import com.example.data.backup.SongKeyMatcher
import com.example.data.backup.UnlinkedSongManager
import com.example.data.local.BackgroundVideoManager
import com.example.data.local.PlayCountManager
import com.example.data.local.RecentlyAddedPreferences
import com.example.data.local.RecentlyAddedTimeframe
import com.example.data.local.TabPreferences
import com.example.data.repository.MusicRepository
import com.example.data.local.PlaylistDatabaseHelper
import com.example.model.SmartPlaylistRule
import com.example.model.SmartRuleType
import com.example.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear preferences
        context.getSharedPreferences("music_folder_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("tab_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aura_music_recently_added_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aura_music_background_videos", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aura_music_auto_backup", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aura_music_unlinked_songs_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aura_music_play_counts", Context.MODE_PRIVATE).edit().clear().commit()

        val dbHelper = PlaylistDatabaseHelper.getInstance(context)
        dbHelper.writableDatabase.delete("playlist_items", null, null)
        dbHelper.writableDatabase.delete("playlists", null, null)
    }

    private fun sampleSongs(): List<Song> {
        return listOf(
            Song(
                id = 101L,
                title = "Starboy",
                artist = "The Weeknd",
                album = "Starboy",
                albumId = 201L,
                durationMs = 230400L,
                contentUri = Uri.parse("content://media/external/audio/media/101"),
                folder = "Music/Pop",
                dataPath = "/storage/emulated/0/Music/Pop/Starboy.mp3",
                genre = "Pop",
                dateAdded = 1600000000L,
                playCount = 12
            ),
            Song(
                id = 102L,
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                albumId = 202L,
                durationMs = 200000L,
                contentUri = Uri.parse("content://media/external/audio/media/102"),
                folder = "Music/Synthwave",
                dataPath = "/storage/emulated/0/Music/Synthwave/Blinding Lights.mp3",
                genre = "Synthwave",
                dateAdded = 1610000000L,
                playCount = 35
            ),
            Song(
                id = 103L,
                title = "Faded",
                artist = "Alan Walker",
                album = "Different World",
                albumId = 203L,
                durationMs = 212000L,
                contentUri = Uri.parse("content://media/external/audio/media/103"),
                folder = "Music/EDM",
                dataPath = "/storage/emulated/0/Music/EDM/Faded.flac",
                genre = "EDM",
                dateAdded = 1620000000L,
                playCount = 5
            )
        )
    }

    @Test
    fun testSongKeyMatcherMatchingLevels() {
        val songs = sampleSongs()

        // 1. Exact match on relative path and duration
        val refExact = BackupSongRef(
            idInBackup = 1,
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationMs = 230400L,
            relativePath = "Pop/Starboy.mp3",
            fileName = "Starboy.mp3"
        )
        val matchedExact = SongKeyMatcher.findBestMatch(refExact, songs)
        assertNotNull(matchedExact)
        assertEquals(101L, matchedExact?.id)

        // 2. Duration tolerance (+/- 3 seconds = 3000ms)
        val refSlightDurationDiff = BackupSongRef(
            idInBackup = 1,
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationMs = 232000L, // +1600ms difference
            relativePath = "Pop/Starboy.mp3",
            fileName = "Starboy.mp3"
        )
        val matchedTolerance = SongKeyMatcher.findBestMatch(refSlightDurationDiff, songs)
        assertNotNull(matchedTolerance)
        assertEquals(101L, matchedTolerance?.id)

        // 3. Fallback when path changed: matched by filename + duration
        val refMovedFolder = BackupSongRef(
            idInBackup = 2,
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 200000L,
            relativePath = "OtherFolder/Blinding Lights.mp3",
            fileName = "Blinding Lights.mp3"
        )
        val matchedMoved = SongKeyMatcher.findBestMatch(refMovedFolder, songs)
        assertNotNull(matchedMoved)
        assertEquals(102L, matchedMoved?.id)

        // 4. Fallback when filename changed: matched by title + artist + duration
        val refRenamedFile = BackupSongRef(
            idInBackup = 3,
            title = "Faded",
            artist = "Alan Walker",
            album = "Different World",
            durationMs = 212000L,
            relativePath = "Downloads/Track01.flac",
            fileName = "Track01.flac"
        )
        val matchedRenamed = SongKeyMatcher.findBestMatch(refRenamedFile, songs)
        assertNotNull(matchedRenamed)
        assertEquals(103L, matchedRenamed?.id)

        // 5. Completely missing song returns null
        val refMissing = BackupSongRef(
            idInBackup = 99,
            title = "Non-existent Track",
            artist = "Unknown Ghost",
            album = "Ghost Album",
            durationMs = 120000L,
            relativePath = "Unknown/ghost.mp3",
            fileName = "ghost.mp3"
        )
        val matchedNull = SongKeyMatcher.findBestMatch(refMissing, songs)
        assertNull(matchedNull)
    }

    @Test
    fun testBackupManifestJsonRoundTrip() {
        val manifest = BackupManifest(
            appVersion = "1.0",
            version = 1,
            songs = listOf(
                BackupSongRef(
                    idInBackup = 1,
                    title = "Starboy",
                    artist = "The Weeknd",
                    album = "Starboy",
                    durationMs = 230000L,
                    relativePath = "Pop/Starboy.mp3",
                    fileName = "Starboy.mp3",
                    playCount = 42,
                    videoPathInZip = "videos/song_1.mp4"
                )
            ),
            playlists = listOf(
                BackupPlaylist(
                    id = 1L,
                    name = "Vibes",
                    songRefs = listOf(1),
                    isSmart = true,
                    matchAllRules = true,
                    rules = listOf(
                        SmartPlaylistRule(type = SmartRuleType.TITLE_CONTAINS, value = "Star, Night")
                    ),
                    exclusionKeywords = "remix",
                    customCoverPathInZip = "covers/playlist_vibes.jpg"
                )
            ),
            pickedCovers = listOf(
                BackupCoverRef(
                    albumKey = "Starboy_The Weeknd",
                    songRefId = 1,
                    imagePathInZip = "picked_covers/album_201.jpg"
                )
            ),
            settings = BackupSettings(
                hiddenTabs = setOf("Recently added"),
                enabledFolders = setOf("Music/Pop"),
                recentlyAddedDays = 30,
                videoDimming = 0.35f,
                showLargeAlbumArtWithVideo = false
            ),
            globalVideoPathInZip = "videos/global.mp4"
        )

        val json = manifest.toJson()
        val parsed = BackupManifest.fromJson(json)

        assertNotNull(parsed)
        assertEquals(1, parsed.version)
        assertEquals(1, parsed.songs.size)
        assertEquals("Starboy", parsed.songs.first().title)
        assertEquals(1, parsed.playlists.size)
        val pl = parsed.playlists.first()
        assertEquals("Vibes", pl.name)
        assertTrue(pl.isSmart)
        assertEquals("remix", pl.exclusionKeywords)
        assertEquals(1, pl.rules.size)
        assertEquals(SmartRuleType.TITLE_CONTAINS, pl.rules.first().type)
        assertEquals("picked_covers/album_201.jpg", parsed.pickedCovers.first().imagePathInZip)
        assertEquals(0.35f, parsed.settings.videoDimming, 0.001f)
        assertEquals(42, parsed.songs.first().playCount)
    }

    @Test
    fun testUnlinkedSongManagerLifecycle() {
        val unlinkedMgr = UnlinkedSongManager.getInstance(context)
        unlinkedMgr.clearAll()

        val refMissing = BackupSongRef(
            idInBackup = 77,
            title = "Missing Track",
            artist = "Lost Artist",
            album = "Unknown",
            durationMs = 195000L,
            relativePath = "Lost/lost.mp3",
            fileName = "lost.mp3"
        )

        val syntheticId = -100077L
        unlinkedMgr.saveUnlinkedSongs(listOf(Pair(syntheticId, refMissing)), replaceExisting = true)

        assertEquals(1, unlinkedMgr.unlinkedCount.value)
        val unlinkedList = unlinkedMgr.getUnlinkedSongs()
        assertEquals(1, unlinkedList.size)
        assertEquals("Missing Track", unlinkedList.first().second.title)

        // When a new scan discovers the missing song
        val discoveredSong = Song(
            id = 555L,
            title = "Missing Track",
            artist = "Lost Artist",
            album = "Found Album",
            albumId = 333L,
            durationMs = 195000L,
            contentUri = Uri.parse("content://media/audio/555"),
            folder = "Music/Lost",
            dataPath = "/storage/emulated/0/Music/Lost/lost.mp3"
        )

        var relinkedSyntheticId: Long? = null
        var relinkedRealSongId: Long? = null

        val relinkedCount = unlinkedMgr.relinkWithDiscoveredSongs(listOf(discoveredSong)) { oldId, newId ->
            relinkedSyntheticId = oldId
            relinkedRealSongId = newId
        }

        assertEquals(1, relinkedCount)
        assertEquals(syntheticId, relinkedSyntheticId)
        assertEquals(555L, relinkedRealSongId)
        assertEquals(0, unlinkedMgr.unlinkedCount.value)
    }

    @Test
    fun testExportAndInspectBackup() = runBlocking {
        val songs = sampleSongs()
        val repo = MusicRepository.getInstance(context)
        val backupManager = BackupManager.getInstance(context)

        // Setup some library data
        val pl1Id = repo.createPlaylist("Chill Pop")
        repo.addSongToPlaylist(pl1Id, 101L)

        repo.createSmartPlaylist(
            name = "Smart Weeknd",
            matchAllRules = true,
            rules = listOf(SmartPlaylistRule(SmartRuleType.ARTIST_EQUALS, "The Weeknd")),
            exclusionKeywords = ""
        )

        // Set video settings
        val bgManager = BackgroundVideoManager.getInstance(context)
        bgManager.setVideoDimming(0.25f)
        bgManager.setShowLargeAlbumArt(false)

        // Export to in-memory zip
        val outStream = ByteArrayOutputStream()
        val exportResult = backupManager.exportBackup(
            outputStream = outStream,
            includeMedia = false,
            allDiscoveredSongs = songs
        )

        assertTrue(exportResult.isSuccess)
        val zipBytes = outStream.toByteArray()
        assertTrue(zipBytes.isNotEmpty())

        // Verify audio files are NOT in the zip
        val zipIn = ZipInputStream(ByteArrayInputStream(zipBytes))
        var entry = zipIn.nextEntry
        var hasManifest = false
        while (entry != null) {
            val name = entry.name
            assertFalse("Audio file should NEVER be included in backup: $name", name.endsWith(".mp3") || name.endsWith(".flac"))
            if (name == "backup_manifest.json") {
                hasManifest = true
            }
            entry = zipIn.nextEntry
        }
        assertTrue("Zip must contain backup_manifest.json", hasManifest)

        // Inspect the backup
        val summary = backupManager.inspectBackup(ByteArrayInputStream(zipBytes), songs)
        assertNotNull(summary)
        assertTrue(summary!!.playlistCount >= 2)
        assertEquals(0, summary.unlinkedSongCount)
        assertEquals(songs.size, summary.matchedSongCount)
    }

    @Test
    fun testImportBackupWithUnlinkedSongsAndReplaceMode() = runBlocking {
        val songs = sampleSongs()
        val backupManager = BackupManager.getInstance(context)
        val repo = MusicRepository.getInstance(context)

        // Create a backup with 1 matched song (101L) and 1 missing song (99L)
        val manifest = BackupManifest(
            appVersion = "1.0",
            version = 1,
            songs = listOf(
                BackupSongRef(
                    idInBackup = 1,
                    title = "Starboy",
                    artist = "The Weeknd",
                    album = "Starboy",
                    durationMs = 230400L,
                    relativePath = "Pop/Starboy.mp3",
                    fileName = "Starboy.mp3",
                    playCount = 99
                ),
                BackupSongRef(
                    idInBackup = 99,
                    title = "Futuristic Missing Beat",
                    artist = "Unknown Cyber",
                    album = "Cyber",
                    durationMs = 180000L,
                    relativePath = "Cyber/cyber.mp3",
                    fileName = "cyber.mp3",
                    playCount = 7
                )
            ),
            playlists = listOf(
                BackupPlaylist(
                    id = 1L,
                    name = "Imported Mix",
                    songRefs = listOf(1, 99),
                    isSmart = false,
                    matchAllRules = true,
                    rules = emptyList(),
                    exclusionKeywords = ""
                )
            ),
            settings = BackupSettings(
                hiddenTabs = setOf("Artists"),
                recentlyAddedDays = 7
            )
        )

        val zipOut = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("backup_manifest.json"))
            zos.write(manifest.toJson().toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val zipBytes = zipOut.toByteArray()

        // Inspect first
        val summary = backupManager.inspectBackup(ByteArrayInputStream(zipBytes), songs)
        assertNotNull(summary)
        assertEquals(1, summary!!.matchedSongCount)
        assertEquals(1, summary.unlinkedSongCount)

        // Import in REPLACE mode
        val importResult = backupManager.importBackup(
            inputStream = ByteArrayInputStream(zipBytes),
            mode = BackupMode.REPLACE,
            currentSongs = songs
        )

        assertTrue(importResult.success)
        assertEquals(1, importResult.playlistsImported)
        assertEquals(1, importResult.unlinkedSongs)

        // Verify playlist songs
        val playlists = repo.getPlaylists().value
        val importedPl = playlists.find { it.name == "Imported Mix" }
        assertNotNull(importedPl)
        assertEquals(2, importedPl!!.songIds.size)
        assertTrue(importedPl.songIds.contains(101L)) // Matched song
        val syntheticMissingId = importedPl.songIds.first { it != 101L }
        assertTrue(syntheticMissingId < 0) // Missing song kept as synthetic ID

        // Verify unlinked manager has the missing song
        val unlinkedMgr = UnlinkedSongManager.getInstance(context)
        assertEquals(1, unlinkedMgr.unlinkedCount.value)
        val unlinkedList = unlinkedMgr.getUnlinkedSongs()
        val missingRef = unlinkedList.find { it.first == syntheticMissingId }
        assertNotNull(missingRef)
        assertEquals("Futuristic Missing Beat", missingRef!!.second.title)

        // Verify settings were updated
        val tabPrefs = TabPreferences(context)
        assertTrue(tabPrefs.getHiddenTabs().contains("Artists"))
        val recPrefs = RecentlyAddedPreferences(context)
        assertEquals(RecentlyAddedTimeframe.LAST_7_DAYS, recPrefs.getTimeframe())

        // Verify play counts were restored
        val playCountMgr = PlayCountManager.getInstance(context)
        assertEquals(99, playCountMgr.getPlayCount(101L))
    }
}
