package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PlaylistDatabaseHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistDatabaseTest {

    @Test
    fun testPlaylistCreationAndQuery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbHelper = PlaylistDatabaseHelper(context)

        val playlistId = dbHelper.insertPlaylist("Favorites")
        assertTrue(playlistId > 0)

        dbHelper.addSongToPlaylist(playlistId, 101L)
        dbHelper.addSongToPlaylist(playlistId, 102L)

        val playlists = dbHelper.getAllPlaylistsFlow().first()
        val fav = playlists.find { it.id == playlistId }
        assertTrue(fav != null)
        assertEquals("Favorites", fav?.name)
        assertEquals(2, fav?.songCount)

        val songIds = dbHelper.getSongIdsForPlaylistFlow(playlistId).first()
        assertEquals(listOf(101L, 102L), songIds)

        dbHelper.removeSongFromPlaylist(playlistId, 101L)
        val updatedSongIds = dbHelper.getSongIdsForPlaylistFlow(playlistId).first()
        assertEquals(listOf(102L), updatedSongIds)

        dbHelper.deletePlaylist(playlistId)
        val afterDelete = dbHelper.getAllPlaylistsFlow().first()
        assertTrue(afterDelete.none { it.id == playlistId })
    }
}
