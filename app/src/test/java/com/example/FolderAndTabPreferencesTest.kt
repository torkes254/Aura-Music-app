package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.cover.CoverArtTagCleaner
import com.example.data.cover.FuzzyMatcher
import com.example.data.local.FolderPreferences
import com.example.data.local.TabPreferences
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
class FolderAndTabPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("aura_music_folder_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aura_music_tab_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testFolderPreferencesFirstLaunchAndNewFolderDetection() {
        val folderPrefs = FolderPreferences(context)

        // 1. First launch: everything is checked, no new badge
        val initialFolders = listOf("Music/Rock/", "Music/Pop/")
        val counts = mapOf("Music/Rock/" to 10, "Music/Pop/" to 5)

        val (items1, enabled1) = folderPrefs.reconcileFolders(initialFolders, counts)
        assertEquals(2, items1.size)
        assertTrue(items1.all { it.isChecked })
        assertFalse(items1.any { it.isNew })
        assertEquals(setOf("Music/Rock/", "Music/Pop/"), enabled1)

        // 2. User unchecks "Music/Pop/"
        folderPrefs.updateFolderSelection(
            newEnabledFolders = setOf("Music/Rock/"),
            allCurrentFolders = initialFolders.toSet()
        )

        // 3. Next scan discovers a new folder "Download/Music/"
        val secondScanFolders = listOf("Music/Rock/", "Music/Pop/", "Download/Music/")
        val counts2 = counts + ("Download/Music/" to 3)

        val (items2, enabled2) = folderPrefs.reconcileFolders(secondScanFolders, counts2)
        assertEquals(3, items2.size)

        // Previously unchecked "Music/Pop/" remains unchecked
        val popItem = items2.first { it.path == "Music/Pop/" }
        assertFalse(popItem.isChecked)
        assertFalse(popItem.isNew)

        // Newly appeared folder is checked by default and marked "isNew"
        val downloadItem = items2.first { it.path == "Download/Music/" }
        assertTrue(downloadItem.isChecked)
        assertTrue(downloadItem.isNew)
        assertTrue("Download/Music/" in enabled2)

        // 4. Acknowledging clears new badge
        folderPrefs.acknowledgeNewFolders()
        val (items3, _) = folderPrefs.reconcileFolders(secondScanFolders, counts2)
        val downloadItemAfterAck = items3.first { it.path == "Download/Music/" }
        assertFalse(downloadItemAfterAck.isNew)
    }

    @Test
    fun testTabPreferencesPersistsHiddenTabs() {
        val tabPrefs1 = TabPreferences(context)
        assertTrue(tabPrefs1.getHiddenTabs().isEmpty())

        tabPrefs1.setTabHidden("Albums", true)
        tabPrefs1.setTabHidden("Playlists", true)

        val hidden1 = tabPrefs1.getHiddenTabs()
        assertTrue(hidden1.contains("Albums"))
        assertTrue(hidden1.contains("Playlists"))
        assertFalse(hidden1.contains("Artists"))

        val tabPrefs2 = TabPreferences(context)
        val hidden2 = tabPrefs2.getHiddenTabs()
        assertEquals(hidden1, hidden2)

        tabPrefs2.setTabHidden("Albums", false)
        assertFalse(tabPrefs2.getHiddenTabs().contains("Albums"))
        assertTrue(tabPrefs2.getHiddenTabs().contains("Playlists"))
    }

    @Test
    fun testCoverArtTagCleanerCleansTitleAndSplitsArtist() {
        val result1 = CoverArtTagCleaner.clean(
            rawTitle = "02. Bohemian Rhapsody (Official Video) [HD] 🎵",
            rawArtist = "Queen"
        )
        assertEquals("Bohemian Rhapsody", result1.title)
        assertEquals("Queen", result1.artist)

        val result2 = CoverArtTagCleaner.clean(
            rawTitle = "Levitating (feat. DaBaby) (Lyrics)",
            rawArtist = "Dua Lipa"
        )
        assertEquals("Levitating", result2.title)
        assertEquals("Dua Lipa", result2.artist)

        val result3 = CoverArtTagCleaner.clean(
            rawTitle = "Coldplay - Fix You (Audio)",
            rawArtist = "Unknown Artist"
        )
        assertEquals("Fix You", result3.title)
        assertEquals("Coldplay", result3.artist)

        val result4 = CoverArtTagCleaner.clean(
            rawTitle = "1 - The Beatles – Hey Jude [Remastered]",
            rawArtist = "<unknown>"
        )
        assertEquals("Hey Jude", result4.title)
        assertEquals("The Beatles", result4.artist)
    }

    @Test
    fun testFuzzyMatcherPrecision() {
        assertTrue(FuzzyMatcher.similarity("Coldplay", "Coldplay") > 0.99)

        assertTrue(
            FuzzyMatcher.isCloseMatch(
                targetArtist = "Coldplay",
                targetTitle = "Fix You",
                candidateArtist = "Coldplay",
                candidateTitle = "Fix You (Live in Madrid)"
            )
        )

        assertFalse(
            FuzzyMatcher.isCloseMatch(
                targetArtist = "Daft Punk",
                targetTitle = "Get Lucky",
                candidateArtist = "Pharrell Williams",
                candidateTitle = "Happy"
            )
        )
    }
}
