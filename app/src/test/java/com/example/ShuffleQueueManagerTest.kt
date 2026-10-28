package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.model.Song
import com.example.playback.ShuffleQueueManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShuffleQueueManagerTest {

    private lateinit var context: Context
    private lateinit var songs: List<Song>

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("aura_music_queue_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        songs = (1..6).map { id ->
            Song(
                id = id.toLong(),
                title = "Track $id",
                artist = "Artist",
                album = "Album",
                albumId = 1L,
                durationMs = 180000L,
                contentUri = Uri.parse("content://media/audio/$id")
            )
        }
    }

    @Test
    fun testShuffleBagPlaysEveryTrackExactlyOnceBeforeRefill() {
        val manager = ShuffleQueueManager(context)
        manager.updateLibrary(songs)

        val firstSong = manager.startShuffleAll(songs)
        assertTrue(firstSong != null)

        val playedIds = mutableListOf(firstSong!!.id)
        // 5 more tracks should play to finish the cycle of 6 tracks
        for (i in 1..5) {
            val nextSong = manager.nextTrack()
            assertTrue(nextSong != null)
            playedIds.add(nextSong!!.id)
        }

        // Each track must have played exactly once in the cycle
        assertEquals(6, playedIds.size)
        assertEquals(songs.map { it.id }.toSet(), playedIds.toSet())

        // 7th track starts the new cycle; last track of old cycle must not be first of new cycle
        val lastPlayedOfOldCycle = playedIds.last()
        val firstOfNewCycle = manager.nextTrack()
        assertTrue(firstOfNewCycle != null)
        assertNotEquals(lastPlayedOfOldCycle, firstOfNewCycle!!.id)
    }

    @Test
    fun testHistoryNavigationWithPreviousAndNext() {
        val manager = ShuffleQueueManager(context)
        manager.updateLibrary(songs)

        val s1 = manager.startShuffleAll(songs)!!
        val s2 = manager.nextTrack()!!
        val s3 = manager.nextTrack()!!

        // Previous should go back to s2, then s1
        val prev1 = manager.previousTrack()!!
        assertEquals(s2.id, prev1.id)

        val prev2 = manager.previousTrack()!!
        assertEquals(s1.id, prev2.id)

        // Next after going back returns forward through history before pulling new tracks
        val fwd1 = manager.nextTrack()!!
        assertEquals(s2.id, fwd1.id)

        val fwd2 = manager.nextTrack()!!
        assertEquals(s3.id, fwd2.id)

        // Only after reaching the end of history does it pull a new track
        val s4 = manager.nextTrack()!!
        assertFalse(listOf(s1.id, s2.id, s3.id).contains(s4.id))
    }

    @Test
    fun testTapSpecificSongWhileShuffleIsOn() {
        val manager = ShuffleQueueManager(context)
        manager.updateLibrary(songs)

        val s1 = manager.startShuffleAll(songs)!!
        // Pick a specific song from remaining bag
        val targetSong = songs.first { it.id != s1.id }

        val playedTarget = manager.playSong(targetSong, songs)
        assertEquals(targetSong.id, playedTarget.id)

        // Current song should be targetSong and it is marked played
        assertEquals(targetSong.id, manager.queueState.value.currentSong?.id)

        // The remaining tracks in bag should not contain targetSong
        val upNextIds = manager.queueState.value.upNextSongs.map { it.id }
        assertFalse(upNextIds.contains(targetSong.id))
    }

    @Test
    fun testDynamicLibrarySongAddedAndRemoved() {
        val manager = ShuffleQueueManager(context)
        manager.updateLibrary(songs)
        manager.startShuffleAll(songs)

        // Add a new song
        val newSong = Song(
            id = 99L,
            title = "New Track",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 120000L,
            contentUri = Uri.parse("content://media/audio/99")
        )
        val expandedSongs = songs + newSong
        manager.updateLibrary(expandedSongs)

        // New song must be present in upNext or context
        val upNext = manager.queueState.value.upNextSongs.map { it.id }
        assertTrue(upNext.contains(99L))

        // Delete a song
        val shrinkSongs = expandedSongs.filter { it.id != 99L }
        manager.updateLibrary(shrinkSongs)

        val afterDeleteUpNext = manager.queueState.value.upNextSongs.map { it.id }
        assertFalse(afterDeleteUpNext.contains(99L))
    }

    @Test
    fun testPersistenceResumesCycle() {
        val manager1 = ShuffleQueueManager(context)
        manager1.updateLibrary(songs)
        val s1 = manager1.startShuffleAll(songs)!!
        val s2 = manager1.nextTrack()!!
        manager1.updatePosition(42000L)

        // Create new instance mimicking app reopen
        val manager2 = ShuffleQueueManager(context)
        manager2.updateLibrary(songs)

        assertTrue(manager2.queueState.value.isShuffleEnabled)
        assertEquals(s2.id, manager2.queueState.value.currentSong?.id)
        assertEquals(42000L, manager2.queueState.value.currentPositionMs)
    }

    @Test
    fun testUpNextQueueShowsNextTracksFromBag() {
        val manager = ShuffleQueueManager(context)
        manager.updateLibrary(songs)
        manager.startShuffleAll(songs)

        val upNext = manager.queueState.value.upNextSongs
        assertEquals(5, upNext.size) // 6 songs total, 1 playing, 5 up next
    }

    @Test
    fun testSkipNextAlwaysMovesToDifferentSongEvenWhenRepeatOneIsOn() {
        val manager = ShuffleQueueManager(context)
        manager.updateLibrary(songs)
        manager.playSong(songs[0], songs)

        // Set repeat mode to 1 (Repeat One)
        manager.toggleRepeat() // mode 1
        assertEquals(1, manager.queueState.value.repeatMode)

        // A manual skip command MUST move to a different song, not replay the same song
        val nextSong = manager.nextTrack(isManualSkip = true)
        assertNotNull(nextSong)
        assertNotEquals(songs[0].id, nextSong!!.id)
        assertEquals(songs[1].id, nextSong.id)
    }

    @Test
    fun testRepeatOneAppliesWhenSongEndsNaturally() {
        val manager = ShuffleQueueManager(context)
        manager.updateLibrary(songs)
        manager.playSong(songs[0], songs)

        // Set repeat mode to 1 (Repeat One)
        manager.toggleRepeat() // mode 1
        assertEquals(1, manager.queueState.value.repeatMode)

        // When song ends naturally (isManualSkip = false), repeat one should repeat the same song
        val naturalNext = manager.nextTrack(isManualSkip = false)
        assertNotNull(naturalNext)
        assertEquals(songs[0].id, naturalNext!!.id)
    }

    @Test
    fun testPreviousTrackRestartsIfMoreThan3SecondsOtherwiseGoesToHistory() {
        val manager = ShuffleQueueManager(context)
        manager.updateLibrary(songs)
        manager.playSong(songs[0], songs)
        val s2 = manager.nextTrack(isManualSkip = true)!!
        assertEquals(songs[1].id, s2.id)

        // 1. If more than 3 seconds into the song (>3000ms), previousTrack restarts current song
        val restarted = manager.previousTrack(currentPositionMs = 5000L)
        assertNotNull(restarted)
        assertEquals(s2.id, restarted!!.id) // Still s2!
        assertEquals(0L, manager.queueState.value.currentPositionMs)

        // 2. If 3 seconds or less (<=3000ms), previousTrack goes to previous song in history
        val prevSong = manager.previousTrack(currentPositionMs = 1500L)
        assertNotNull(prevSong)
        assertEquals(songs[0].id, prevSong!!.id) // Returned s1 (songs[0])!
    }

    @Test
    fun testFullOrderedQueueHoldsAllSongsWheneverPlaybackStarts() {
        val manager = ShuffleQueueManager(context)
        manager.updateLibrary(songs)

        // Playing song from normal list
        manager.playSong(songs[2], songs)
        val queue = manager.getFullOrderedQueue()
        assertEquals(songs.size, queue.size)
        assertEquals(songs.map { it.id }, queue.map { it.id })
        assertEquals(2, manager.getCurrentQueueIndex())

        // Playing with shuffle on
        manager.startShuffleAll(songs)
        val shuffledQueue = manager.getFullOrderedQueue()
        assertEquals(songs.size, shuffledQueue.size)
        assertEquals(songs.map { it.id }.toSet(), shuffledQueue.map { it.id }.toSet())
    }
}
