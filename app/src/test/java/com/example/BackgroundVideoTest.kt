package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BackgroundVideoManager
import com.example.data.local.BackgroundVideoState
import com.example.data.local.VideoFileInfo
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
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundVideoTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("aura_music_video_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testVideoFileInfoOver50MBWarning() {
        val smallVideo = VideoFileInfo(
            sizeBytes = 25L * 1024L * 1024L,
            displayName = "sample.mp4"
        )
        assertFalse(smallVideo.isOver50MB)
        assertEquals("25.0 MB", smallVideo.sizeFormatted)

        val largeVideo = VideoFileInfo(
            sizeBytes = 60L * 1024L * 1024L,
            displayName = "heavy_video.mp4"
        )
        assertTrue(largeVideo.isOver50MB)
        assertEquals("60.0 MB", largeVideo.sizeFormatted)
    }

    @Test
    fun testGlobalAndSongSpecificVideoResolution() {
        // Create dummy video files
        val dir = File(context.filesDir, "background_videos").apply { mkdirs() }
        val globalFile = File(dir, "global_background.mp4").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val songFile = File(dir, "song_42.mp4").apply {
            writeBytes(byteArrayOf(5, 6, 7, 8))
        }

        val state = BackgroundVideoState(
            globalVideoPath = globalFile.absolutePath,
            songVideos = mapOf(42L to songFile.absolutePath),
            disableOnBatterySaver = false,
            isBatterySaverActive = false
        )

        // Song 42 gets its custom song-specific video
        assertEquals(songFile.absolutePath, state.activeVideoForSong(42L))

        // Other song (e.g., song 99) falls back to the global video
        assertEquals(globalFile.absolutePath, state.activeVideoForSong(99L))

        // No song playing falls back to global video
        assertEquals(globalFile.absolutePath, state.activeVideoForSong(null))
    }

    @Test
    fun testBatterySaverDisablesVideoWhenToggleEnabled() {
        val dir = File(context.filesDir, "background_videos").apply { mkdirs() }
        val globalFile = File(dir, "global_background.mp4").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        // When disableOnBatterySaver is true AND battery saver is active -> video returns null
        val stateBatterySaving = BackgroundVideoState(
            globalVideoPath = globalFile.absolutePath,
            songVideos = emptyMap(),
            disableOnBatterySaver = true,
            isBatterySaverActive = true
        )
        assertNull(stateBatterySaving.activeVideoForSong(1L))

        // When disableOnBatterySaver is false, video continues playing even on battery saver
        val stateIgnoreBattery = BackgroundVideoState(
            globalVideoPath = globalFile.absolutePath,
            songVideos = emptyMap(),
            disableOnBatterySaver = false,
            isBatterySaverActive = true
        )
        assertEquals(globalFile.absolutePath, stateIgnoreBattery.activeVideoForSong(1L))
    }

    @Test
    fun testContentHashDeduplicationAndMultiSongAssignment() = runBlocking {
        val manager = BackgroundVideoManager(context)

        // Create a dummy video file in cacheDir
        val sampleVideoBytes = "Sample Video Content 12345".toByteArray()
        val sourceFile = File(context.cacheDir, "test_input_video.mp4").apply {
            writeBytes(sampleVideoBytes)
        }
        val sourceUri = Uri.fromFile(sourceFile)

        // Set for song 10
        val success10 = manager.setSongVideo(10L, sourceUri)
        assertTrue(success10)
        val savedPath10 = manager.videoState.value.songVideos[10L]
        assertNotNull(savedPath10)
        assertTrue(File(savedPath10!!).exists())

        // Set same video for multiple songs: 20L and 30L
        val successMulti = manager.setSongsVideo(listOf(20L, 30L), sourceUri)
        assertTrue(successMulti)
        val savedPath20 = manager.videoState.value.songVideos[20L]
        val savedPath30 = manager.videoState.value.songVideos[30L]
        // Deduplication: must be identical file path!
        assertEquals(savedPath10, savedPath20)
        assertEquals(savedPath10, savedPath30)

        // Also set as global video with same sourceUri
        val successGlobal = manager.setGlobalVideo(sourceUri)
        assertTrue(successGlobal)
        val savedPathGlobal = manager.videoState.value.globalVideoPath
        assertEquals(savedPath10, savedPathGlobal)

        // Verify state has all songs and global
        val currentState = manager.videoState.value
        assertEquals(savedPath10, currentState.globalVideoPath)
        assertEquals(savedPath10, currentState.songVideos[10L])
        assertEquals(savedPath10, currentState.songVideos[20L])
        assertEquals(savedPath10, currentState.songVideos[30L])

        // Verify count of video files in directory: only 1 file stored!
        val videoDir = File(context.filesDir, "background_videos")
        val mp4Files = videoDir.listFiles { _, name -> name.endsWith(".mp4") } ?: emptyArray()
        assertEquals(1, mp4Files.size)

        // Priority check: song 10 gets its own video (which matches savedPath10)
        assertEquals(savedPath10, currentState.activeVideoForSong(10L))

        // Remove song 10 video: file must NOT be deleted because songs 20, 30 and global still use it
        manager.removeSongVideo(10L)
        assertTrue(File(savedPath10!!).exists())
        assertFalse(manager.videoState.value.hasCustomVideo(10L))
        // Song 10 now falls back to global video!
        assertEquals(savedPath10, manager.videoState.value.activeVideoForSong(10L))

        // Remove multi songs 20 and 30
        manager.removeSongsVideo(listOf(20L, 30L))
        // Still exists because global video still uses it
        assertTrue(File(savedPath10!!).exists())

        // Finally remove global video: now 0 references exist, file should be cleaned up
        manager.removeGlobalVideo()
        assertFalse(File(savedPath10!!).exists())
    }
}
