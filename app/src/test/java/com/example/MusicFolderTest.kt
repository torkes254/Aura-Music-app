package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FolderPreferences
import com.example.data.repository.MusicRepository
import com.example.model.Song
import com.example.playback.ShuffleQueueManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicFolderTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("aura_music_folder_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aura_music_queue_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testFolderPreferencesFirstLaunchChecksAllFoldersWithoutNewBadge() {
        val prefs = FolderPreferences(context)
        assertFalse(prefs.isInitialized())

        val folders = listOf("Music/Rock/", "Music/Pop/", "Download/")
        val counts = mapOf("Music/Rock/" to 5, "Music/Pop/" to 8, "Download/" to 3)

        val (items, enabled) = prefs.reconcileFolders(folders, counts)

        assertTrue(prefs.isInitialized())
        assertEquals(3, items.size)
        assertTrue(items.all { it.isChecked })
        assertTrue(items.none { it.isNew })
        assertEquals(folders.toSet(), enabled)
        assertEquals(folders.toSet(), prefs.getEnabledFolders())
    }

    @Test
    fun testNewFoldersAppearingLaterAreIncludedByDefaultWithNewBadge() {
        val prefs = FolderPreferences(context)

        // Initial launch with two folders
        val initialFolders = listOf("Music/Rock/", "Download/")
        val initialCounts = mapOf("Music/Rock/" to 5, "Download/" to 3)
        prefs.reconcileFolders(initialFolders, initialCounts)

        // User unchecks "Download/"
        prefs.updateFolderSelection(setOf("Music/Rock/"), initialFolders.toSet())
        assertEquals(setOf("Music/Rock/"), prefs.getEnabledFolders())

        // Later, user adds audio files in "Podcasts/"
        val updatedFolders = listOf("Music/Rock/", "Download/", "Podcasts/")
        val updatedCounts = mapOf("Music/Rock/" to 5, "Download/" to 3, "Podcasts/" to 4)

        val (newItems, newEnabled) = prefs.reconcileFolders(updatedFolders, updatedCounts)

        // "Podcasts/" should be enabled by default and have isNew = true
        val podcastItem = newItems.first { it.path == "Podcasts/" }
        assertTrue(podcastItem.isChecked)
        assertTrue(podcastItem.isNew)

        // "Download/" should remain unchecked and not be marked new
        val downloadItem = newItems.first { it.path == "Download/" }
        assertFalse(downloadItem.isChecked)
        assertFalse(downloadItem.isNew)

        // Enabled set must contain Music/Rock/ and Podcasts/, but NOT Download/
        assertEquals(setOf("Music/Rock/", "Podcasts/"), newEnabled)

        // Acknowledging clears the new badge
        prefs.acknowledgeNewFolders()
        assertTrue(prefs.getNewFolders().isEmpty())
    }

    @Test
    fun testSelectAllAndSelectNonePersist() {
        val prefs = FolderPreferences(context)
        val folders = listOf("Music/Rock/", "Music/Jazz/", "Download/")
        val counts = mapOf("Music/Rock/" to 2, "Music/Jazz/" to 3, "Download/" to 4)
        prefs.reconcileFolders(folders, counts)

        // Select None
        prefs.updateFolderSelection(emptySet(), folders.toSet())
        assertTrue(prefs.getEnabledFolders().isEmpty())

        // Reconcile again, should still be empty
        val (noneItems, noneEnabled) = prefs.reconcileFolders(folders, counts)
        assertTrue(noneEnabled.isEmpty())
        assertTrue(noneItems.none { it.isChecked })

        // Select All
        prefs.updateFolderSelection(folders.toSet(), folders.toSet())
        assertEquals(folders.toSet(), prefs.getEnabledFolders())

        val (allItems, allEnabled) = prefs.reconcileFolders(folders, counts)
        assertEquals(folders.toSet(), allEnabled)
        assertTrue(allItems.all { it.isChecked })
    }

    @Test
    fun testPlayingTrackFinishesWhenFolderUncheckedAndRemovedFromQueueAndShuffleBag() {
        val manager = ShuffleQueueManager(context)

        val songRock1 = Song(1L, "Rock 1", "Artist A", "Album A", 1L, 100000L, Uri.parse("content://1"), folder = "Music/Rock/")
        val songRock2 = Song(2L, "Rock 2", "Artist A", "Album A", 1L, 100000L, Uri.parse("content://2"), folder = "Music/Rock/")
        val songDownload = Song(3L, "Download 1", "Artist B", "Album B", 2L, 100000L, Uri.parse("content://3"), folder = "Download/")

        val allSongs = listOf(songRock1, songRock2, songDownload)
        manager.updateLibrary(allSongs)

        // Start playing songDownload (from "Download/")
        manager.playSong(songDownload, allSongs)
        assertEquals(songDownload.id, manager.getCurrentSong()?.id)

        // User unchecks "Download/": allowedSongs now only has Rock 1 and Rock 2
        val allowedSongs = listOf(songRock1, songRock2)
        // With isCurrentTrackPlaying = true
        manager.updateLibrary(allowedSongs, isCurrentTrackPlaying = true, allKnownSongs = allSongs)

        // 1. Current song should NOT be cancelled/nulled; it is allowed to finish!
        assertEquals(songDownload.id, manager.getCurrentSong()?.id)

        // 2. Queue and up next must NOT contain any songs from "Download/"
        val qState = manager.queueState.value
        assertFalse(qState.upNextSongs.any { it.folder == "Download/" })

        // 3. When song finishes, nextTrack() plays from allowed songs (Rock 1 or Rock 2)
        val nextTrack = manager.nextTrack()
        assertTrue(nextTrack != null)
        assertTrue(nextTrack!!.id == songRock1.id || nextTrack.id == songRock2.id)
        assertEquals("Music/Rock/", nextTrack.folder)
    }

    @Test
    fun testExtractFolderFallback() {
        val repo = MusicRepository(context)

        // Normal relative path
        assertEquals("Music/Rock/", repo.extractFolder("Music/Rock/", null))
        assertEquals("Download/", repo.extractFolder("Download", null))

        // Data path fallback
        assertEquals("Music/Album/", repo.extractFolder(null, "/storage/emulated/0/Music/Album/song.mp3"))
        assertEquals("Download/", repo.extractFolder(null, "/storage/emulated/0/Download/song.mp3"))
        assertEquals("Music/", repo.extractFolder(null, null))
    }
}
