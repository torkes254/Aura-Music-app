package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PlaylistDatabaseHelper
import com.example.model.Song
import com.example.playback.ShuffleQueueManager
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SongDeletionAndSelectionTest {

    private fun createDummySong(id: Long, title: String, artist: String = "Test Artist", album: String = "Test Album"): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = 1L,
            durationMs = 180000L,
            contentUri = Uri.parse("file:///dummy/path/$title.mp3"),
            dateAdded = System.currentTimeMillis() / 1000
        )
    }

    @Test
    fun testSelectionModeTogglingAndAutoExit() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MusicViewModel(app)

        val song1 = createDummySong(1L, "Song 1")
        val song2 = createDummySong(2L, "Song 2")
        val song3 = createDummySong(3L, "Song 3")
        val allSongs = listOf(song1, song2, song3)

        // Initial state
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedSongIds.isEmpty())

        // Start selection with song 1
        viewModel.startSelection(song1.id)
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(1L), viewModel.uiState.value.selectedSongIds)

        // Toggle song 2 -> both selected
        viewModel.toggleSongSelection(song2.id)
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedSongIds)

        // Toggle song 1 again -> deselected, only song 2 left
        viewModel.toggleSongSelection(song1.id)
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(2L), viewModel.uiState.value.selectedSongIds)

        // Toggle song 2 -> deselecting everything exits selection mode automatically
        viewModel.toggleSongSelection(song2.id)
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedSongIds.isEmpty())

        // Test select all
        viewModel.selectAllVisible(allSongs)
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(1L, 2L, 3L), viewModel.uiState.value.selectedSongIds)

        // Test cancel/clear selection
        viewModel.clearSelection()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedSongIds.isEmpty())
    }

    @Test
    fun testQueueManagerRemovesDeletedSongsAndSkipsPlaying() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val queueManager = ShuffleQueueManager(app)
        val s1 = createDummySong(10L, "Track 1")
        val s2 = createDummySong(20L, "Track 2")
        val s3 = createDummySong(30L, "Track 3")
        val list = listOf(s1, s2, s3)

        // Play track 1
        queueManager.playSong(s1, list)
        assertEquals(s1.id, queueManager.getCurrentSong()?.id)
        assertEquals(3, queueManager.queueState.value.queue.size)

        // Delete track 1 (currently playing)
        queueManager.removeSongs(setOf(10L))

        // Should have skipped to track 2
        assertEquals(s2.id, queueManager.getCurrentSong()?.id)
        assertEquals(2, queueManager.queueState.value.queue.size)
        assertFalse(queueManager.queueState.value.queue.any { it.id == 10L })

        // Delete remaining songs (20 and 30)
        queueManager.removeSongs(setOf(20L, 30L))
        assertNull(queueManager.getCurrentSong())
        assertTrue(queueManager.queueState.value.queue.isEmpty())
    }

    @Test
    fun testDatabaseRemovesDeletedSongsFromAllPlaylists() {
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val dbHelper = PlaylistDatabaseHelper(app)

            val pl1 = dbHelper.insertPlaylist("Rock")
            val pl2 = dbHelper.insertPlaylist("Pop")

            dbHelper.addSongToPlaylist(pl1, 100L)
            dbHelper.addSongToPlaylist(pl1, 200L)
            dbHelper.addSongToPlaylist(pl1, 300L)

            dbHelper.addSongToPlaylist(pl2, 200L)
            dbHelper.addSongToPlaylist(pl2, 400L)

            // Verify initial playlist songs
            assertEquals(listOf(100L, 200L, 300L), dbHelper.getSongIdsForPlaylistFlow(pl1).first())
            assertEquals(listOf(200L, 400L), dbHelper.getSongIdsForPlaylistFlow(pl2).first())

            // Delete 200L from all playlists
            dbHelper.removeSongsFromAllPlaylists(setOf(200L))

            assertEquals(listOf(100L, 300L), dbHelper.getSongIdsForPlaylistFlow(pl1).first())
            assertEquals(listOf(400L), dbHelper.getSongIdsForPlaylistFlow(pl2).first())

            // Clean up
            dbHelper.deletePlaylist(pl1)
            dbHelper.deletePlaylist(pl2)
        }
    }

    @Test
    fun testViewModelDeletionConfirmationAndFinalizeFlow() {
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val viewModel = MusicViewModel(app)

            val song1 = createDummySong(501L, "Song 501")
            val song2 = createDummySong(502L, "Song 502")

            // Request delete single song via menu
            viewModel.requestDeleteSongs(listOf(song1))
            assertTrue(viewModel.uiState.value.showDeleteConfirmDialog)
            assertEquals(1, viewModel.uiState.value.songsPendingDeletion.size)
            assertEquals(song1.id, viewModel.uiState.value.songsPendingDeletion.first().id)

            // Cancel
            viewModel.cancelPendingDelete()
            assertFalse(viewModel.uiState.value.showDeleteConfirmDialog)
            assertTrue(viewModel.uiState.value.songsPendingDeletion.isEmpty())

            // Bulk request delete via selection
            viewModel.startSelection(song1.id)
            viewModel.toggleSongSelection(song2.id)
            viewModel.requestDeleteSelected(listOf(song1, song2))

            assertTrue(viewModel.uiState.value.showDeleteConfirmDialog)
            assertEquals(2, viewModel.uiState.value.songsPendingDeletion.size)

            // Finalize deletion of both songs
            viewModel.finalizeDeletion(setOf(501L, 502L))

            assertFalse(viewModel.uiState.value.showDeleteConfirmDialog)
            assertFalse(viewModel.uiState.value.isSelectionMode)
            assertTrue(viewModel.uiState.value.selectedSongIds.isEmpty())
            assertTrue(viewModel.uiState.value.songsPendingDeletion.isEmpty())
            assertEquals("Deleted 2 songs", viewModel.uiState.value.userMessage)

            viewModel.clearUserMessage()
            assertNull(viewModel.uiState.value.userMessage)
        }
    }
}
