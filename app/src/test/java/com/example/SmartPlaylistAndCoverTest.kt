package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PlaylistCoverManager
import com.example.data.local.SmartPlaylistEvaluator
import com.example.data.repository.MusicRepository
import com.example.model.Playlist
import com.example.model.SmartPlaylistRule
import com.example.model.SmartRuleType
import com.example.model.Song
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
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartPlaylistAndCoverTest {

    private fun sampleSongs(): List<Song> {
        return listOf(
            Song(
                id = 1L,
                title = "Nightcore - Angel with a Shotgun",
                artist = "Nightcore Universe",
                album = "Anime Beats",
                albumId = 10L,
                durationMs = 180000L,
                contentUri = Uri.parse("content://media/audio/1"),
                folder = "Music/Nightcore/",
                genre = "Nightcore",
                dateAdded = 1000000L,
                playCount = 15
            ),
            Song(
                id = 2L,
                title = "Midnight Drive (Slowed & Reverb)",
                artist = "Aura Synth",
                album = "Synthwave Nights",
                albumId = 20L,
                durationMs = 210000L,
                contentUri = Uri.parse("content://media/audio/2"),
                folder = "Music/Synthwave/",
                genre = "Synthwave",
                dateAdded = 1000500L,
                playCount = 5
            ),
            Song(
                id = 3L,
                title = "Neon Horizon (Night Core Remix)",
                artist = "Aura Synth",
                album = "Chiptune Dreams",
                albumId = 30L,
                durationMs = 195000L,
                contentUri = Uri.parse("content://media/audio/3"),
                folder = "Music/AuraSynth/",
                genre = "Chiptune",
                dateAdded = 1000200L,
                playCount = 42
            ),
            Song(
                id = 4L,
                title = "Acoustic Live Demo",
                artist = "Indie Band",
                album = "Live Sessions",
                albumId = 40L,
                durationMs = 150000L,
                contentUri = Uri.parse("content://media/audio/4"),
                folder = "Music/Indie/",
                genre = "Rock",
                dateAdded = 1000100L,
                playCount = 1
            )
        )
    }

    @Test
    fun testSmartPlaylistEvaluation_TitleContains_IgnorePunctuationAndCase() {
        val songs = sampleSongs()
        val pl = Playlist(
            id = 100L,
            name = "Nightcore",
            isSmart = true,
            rules = listOf(
                SmartPlaylistRule(SmartRuleType.TITLE_CONTAINS, "nightcore, night core")
            )
        )

        val matched = SmartPlaylistEvaluator.evaluate(pl, songs)
        // Songs 1 and 3 should match (song 1 has "Nightcore -", song 3 has "Night Core")
        assertEquals(2, matched.size)
        assertTrue(matched.any { it.id == 1L })
        assertTrue(matched.any { it.id == 3L })
    }

    @Test
    fun testSmartPlaylistEvaluation_ArtistEquals() {
        val songs = sampleSongs()
        val pl = Playlist(
            id = 101L,
            name = "Aura Synth Only",
            isSmart = true,
            rules = listOf(
                SmartPlaylistRule(SmartRuleType.ARTIST_EQUALS, "aura synth")
            )
        )

        val matched = SmartPlaylistEvaluator.evaluate(pl, songs)
        assertEquals(2, matched.size)
        assertTrue(matched.all { it.artist == "Aura Synth" })
    }

    @Test
    fun testSmartPlaylistEvaluation_MatchAllVsAny() {
        val songs = sampleSongs()
        // Rule 1: Artist contains "Aura Synth", Rule 2: Title contains "Slowed"
        val r1 = SmartPlaylistRule(SmartRuleType.ARTIST_CONTAINS, "Aura Synth")
        val r2 = SmartPlaylistRule(SmartRuleType.TITLE_CONTAINS, "Slowed")

        // Match ALL: Only Song 2 matches both
        val plAll = Playlist(
            id = 102L,
            name = "Slowed Aura Synth",
            isSmart = true,
            matchAllRules = true,
            rules = listOf(r1, r2)
        )
        val matchedAll = SmartPlaylistEvaluator.evaluate(plAll, songs)
        assertEquals(1, matchedAll.size)
        assertEquals(2L, matchedAll[0].id)

        // Match ANY: Song 2 and Song 3 match (Aura Synth has songs 2 & 3, Slowed has song 2)
        val plAny = Playlist(
            id = 103L,
            name = "Aura Synth or Slowed",
            isSmart = true,
            matchAllRules = false,
            rules = listOf(r1, r2)
        )
        val matchedAny = SmartPlaylistEvaluator.evaluate(plAny, songs)
        assertEquals(2, matchedAny.size)
        assertTrue(matchedAny.any { it.id == 2L })
        assertTrue(matchedAny.any { it.id == 3L })
    }

    @Test
    fun testSmartPlaylistEvaluation_ExclusionKeywords() {
        val songs = sampleSongs()
        val pl = Playlist(
            id = 104L,
            name = "No Live or Demo",
            isSmart = true,
            rules = emptyList(), // match all non-excluded
            exclusionKeywords = "live, demo"
        )

        val matched = SmartPlaylistEvaluator.evaluate(pl, songs)
        // Song 4 has "Live" in album and "Demo" in title, must be excluded
        assertEquals(3, matched.size)
        assertFalse(matched.any { it.id == 4L })
    }

    @Test
    fun testSmartPlaylistEvaluation_RespectsMusicFolders() {
        val songs = sampleSongs()
        val pl = Playlist(
            id = 105L,
            name = "All Songs",
            isSmart = true,
            rules = emptyList()
        )

        val enabledFolders = setOf("Music/Nightcore/", "Music/AuraSynth/")
        val matched = SmartPlaylistEvaluator.evaluate(pl, songs, enabledFolders)
        assertEquals(2, matched.size)
        assertTrue(matched.any { it.id == 1L })
        assertTrue(matched.any { it.id == 3L })
    }

    @Test
    fun testSmartPlaylistEvaluation_MostPlayed() {
        val songs = sampleSongs()
        val pl = Playlist(
            id = 106L,
            name = "Top Tracks",
            isSmart = true,
            rules = listOf(
                SmartPlaylistRule(SmartRuleType.MOST_PLAYED, "2")
            )
        )

        val matched = SmartPlaylistEvaluator.evaluate(pl, songs)
        // Top 2 by play count: Song 3 (42) and Song 1 (15)
        assertEquals(2, matched.size)
        assertEquals(3L, matched[0].id)
        assertEquals(1L, matched[1].id)
    }

    @Test
    fun testSuggestPlaylists() {
        val songs = sampleSongs()
        val suggestions = SmartPlaylistEvaluator.suggestPlaylists(songs)
        assertTrue(suggestions.isNotEmpty())
        // Should find "Nightcore" or "Best of Aura Synth"
        assertTrue(suggestions.any { it.name.contains("Nightcore", ignoreCase = true) || it.name.contains("Aura Synth", ignoreCase = true) })
    }

    @Test
    fun testMusicRepository_SmartPlaylistPersistenceAndConversion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MusicRepository(context)

        val smartId = repo.createSmartPlaylist(
            name = "Chiptune Hits",
            matchAllRules = true,
            rules = listOf(SmartPlaylistRule(SmartRuleType.GENRE_CONTAINS, "Chiptune")),
            exclusionKeywords = "demo"
        )

        val playlists = repo.getPlaylists().first()
        val smartPl = playlists.find { it.id == smartId }
        assertNotNull(smartPl)
        assertTrue(smartPl!!.isSmart)
        assertEquals("Chiptune Hits", smartPl.name)
        assertEquals(1, smartPl.rules.size)
        assertEquals(SmartRuleType.GENRE_CONTAINS, smartPl.rules[0].type)
        assertEquals("Chiptune", smartPl.rules[0].value)

        // Convert to regular playlist
        repo.convertSmartToRegularPlaylist(smartId, listOf(3L, 99L))
        val afterConvert = repo.getPlaylists().first().find { it.id == smartId }
        assertNotNull(afterConvert)
        assertFalse(afterConvert!!.isSmart)
        assertEquals(listOf(3L, 99L), afterConvert.songIds)
    }

    @Test
    fun testCustomPlaylistCover_SaveCropAndRemove() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coverManager = PlaylistCoverManager.getInstance(context)

        // Create a temporary non-square image
        val testImageFile = File(context.cacheDir, "test_landscape.jpg")
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        FileOutputStream(testImageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        val uri = Uri.fromFile(testImageFile)

        val playlistId = 8888L
        val savedPath = coverManager.saveCustomCover(playlistId, uri)
        assertNotNull(savedPath)
        assertTrue(File(savedPath!!).exists())

        val retrievedPath = coverManager.getCoverPath(playlistId)
        assertEquals(savedPath, retrievedPath)

        // Remove custom cover
        val removed = coverManager.removeCustomCover(playlistId)
        assertTrue(removed)
        assertNull(coverManager.getCoverPath(playlistId))
    }
}
