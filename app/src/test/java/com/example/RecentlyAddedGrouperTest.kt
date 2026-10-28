package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.RecentlyAddedPreferences
import com.example.data.local.RecentlyAddedTimeframe
import com.example.data.local.TabPreferences
import com.example.model.RecentlyAddedGrouper
import com.example.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentlyAddedGrouperTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("aura_music_recently_added_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aura_music_tab_preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun createSong(id: Long, title: String, dateAddedSec: Long, folder: String = "Music/"): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180000L,
            contentUri = Uri.parse("content://media/external/audio/media/$id"),
            dateAdded = dateAddedSec,
            folder = folder
        )
    }

    @Test
    fun testSectionBucketingForDates() {
        // Set a fixed reference time (12:00 PM today)
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 20, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMillis = cal.timeInMillis
        val nowSec = nowMillis / 1000L

        // 1. Added 1 hour ago -> Today
        val todaySec = nowSec - 3600L
        assertEquals("Today", RecentlyAddedGrouper.getSectionForTimestamp(todaySec, nowMillis))

        // 2. Added 26 hours ago -> Yesterday
        val yesterdaySec = nowSec - (26L * 3600L)
        assertEquals("Yesterday", RecentlyAddedGrouper.getSectionForTimestamp(yesterdaySec, nowMillis))

        // 3. Added 4 days ago -> This week
        val thisWeekSec = nowSec - (4L * 86400L)
        assertEquals("This week", RecentlyAddedGrouper.getSectionForTimestamp(thisWeekSec, nowMillis))

        // 4. Added 15 days ago -> This month
        val thisMonthSec = nowSec - (15L * 86400L)
        assertEquals("This month", RecentlyAddedGrouper.getSectionForTimestamp(thisMonthSec, nowMillis))

        // 5. Added 50 days ago -> Earlier
        val earlierSec = nowSec - (50L * 86400L)
        assertEquals("Earlier", RecentlyAddedGrouper.getSectionForTimestamp(earlierSec, nowMillis))
    }

    @Test
    fun testSongGroupingAndSortingOrder() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 20, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMillis = cal.timeInMillis
        val nowSec = nowMillis / 1000L

        val sEarlier = createSong(1, "Oldie", nowSec - (50L * 86400L))
        val sToday2 = createSong(2, "Today Afternoon", nowSec - 1800L)
        val sToday1 = createSong(3, "Today Morning", nowSec - 7200L)
        val sYesterday = createSong(4, "Yesterday Tune", nowSec - (25L * 3600L))
        val sWeek = createSong(5, "Few Days Ago", nowSec - (3L * 86400L))
        val sMonth = createSong(6, "Last Fortnight", nowSec - (14L * 86400L))

        // Pass in unsorted list
        val songs = listOf(sMonth, sEarlier, sToday1, sYesterday, sToday2, sWeek)
        val grouped = RecentlyAddedGrouper.groupSongs(songs, nowMillis)

        // Verify sections order
        val sectionKeys = grouped.keys.toList()
        assertEquals(listOf("Today", "Yesterday", "This week", "This month", "Earlier"), sectionKeys)

        // In Today section, newer song (sToday2) comes before older song (sToday1)
        val todaySongs = grouped["Today"]!!
        assertEquals(2, todaySongs.size)
        assertEquals("Today Afternoon", todaySongs[0].title)
        assertEquals("Today Morning", todaySongs[1].title)

        assertEquals("Yesterday Tune", grouped["Yesterday"]!!.first().title)
        assertEquals("Few Days Ago", grouped["This week"]!!.first().title)
        assertEquals("Last Fortnight", grouped["This month"]!!.first().title)
        assertEquals("Oldie", grouped["Earlier"]!!.first().title)
    }

    @Test
    fun testTimeframeFiltering() {
        val nowMillis = 1000000000000L
        val nowSec = nowMillis / 1000L

        val song1DayOld = createSong(1, "1 Day", nowSec - (1L * 86400L))
        val song5DaysOld = createSong(2, "5 Days", nowSec - (5L * 86400L))
        val song20DaysOld = createSong(3, "20 Days", nowSec - (20L * 86400L))
        val song60DaysOld = createSong(4, "60 Days", nowSec - (60L * 86400L))
        val song120DaysOld = createSong(5, "120 Days", nowSec - (120L * 86400L))

        val allSongs = listOf(song1DayOld, song5DaysOld, song20DaysOld, song60DaysOld, song120DaysOld)

        // 7 days filter: only 1 Day and 5 Days
        val res7 = RecentlyAddedGrouper.filterByTimeframe(allSongs, RecentlyAddedTimeframe.LAST_7_DAYS, nowMillis)
        assertEquals(2, res7.size)
        assertTrue(res7.any { it.title == "1 Day" })
        assertTrue(res7.any { it.title == "5 Days" })

        // 30 days filter (default): 1 Day, 5 Days, 20 Days
        val res30 = RecentlyAddedGrouper.filterByTimeframe(allSongs, RecentlyAddedTimeframe.LAST_30_DAYS, nowMillis)
        assertEquals(3, res30.size)
        assertTrue(res30.any { it.title == "20 Days" })
        assertFalse(res30.any { it.title == "60 Days" })

        // 90 days filter: 1, 5, 20, 60 Days
        val res90 = RecentlyAddedGrouper.filterByTimeframe(allSongs, RecentlyAddedTimeframe.LAST_90_DAYS, nowMillis)
        assertEquals(4, res90.size)
        assertTrue(res90.any { it.title == "60 Days" })
        assertFalse(res90.any { it.title == "120 Days" })

        // All filter: all 5 songs
        val resAll = RecentlyAddedGrouper.filterByTimeframe(allSongs, RecentlyAddedTimeframe.ALL, nowMillis)
        assertEquals(5, resAll.size)
    }

    @Test
    fun testEmptyStateMessages() {
        assertEquals("Nothing added in the last 7 days", RecentlyAddedTimeframe.LAST_7_DAYS.emptyMessage)
        assertEquals("Nothing added in the last 30 days", RecentlyAddedTimeframe.LAST_30_DAYS.emptyMessage)
        assertEquals("Nothing added in the last 90 days", RecentlyAddedTimeframe.LAST_90_DAYS.emptyMessage)
        assertEquals("Nothing added yet", RecentlyAddedTimeframe.ALL.emptyMessage)
    }

    @Test
    fun testPreferencesPersistence() {
        val prefs1 = RecentlyAddedPreferences(context)
        // Default is 30 days
        assertEquals(RecentlyAddedTimeframe.LAST_30_DAYS, prefs1.getTimeframe())

        // Update to 7 days
        prefs1.setTimeframe(RecentlyAddedTimeframe.LAST_7_DAYS)
        assertEquals(RecentlyAddedTimeframe.LAST_7_DAYS, prefs1.getTimeframe())

        // Verify new instance reads persisted value
        val prefs2 = RecentlyAddedPreferences(context)
        assertEquals(RecentlyAddedTimeframe.LAST_7_DAYS, prefs2.getTimeframe())

        // Test All
        prefs2.setTimeframe(RecentlyAddedTimeframe.ALL)
        assertEquals(RecentlyAddedTimeframe.ALL, RecentlyAddedPreferences(context).getTimeframe())
    }

    @Test
    fun testTabPreferencesSupportsRecentlyAdded() {
        val tabPrefs = TabPreferences(context)
        assertFalse(tabPrefs.getHiddenTabs().contains("Recently added"))

        tabPrefs.setTabHidden("Recently added", true)
        assertTrue(tabPrefs.getHiddenTabs().contains("Recently added"))

        tabPrefs.setTabHidden("Recently added", false)
        assertFalse(tabPrefs.getHiddenTabs().contains("Recently added"))
    }
}
