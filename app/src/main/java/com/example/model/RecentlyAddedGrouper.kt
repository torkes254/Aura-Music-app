package com.example.model

import com.example.data.local.RecentlyAddedTimeframe
import java.util.Calendar

object RecentlyAddedGrouper {

    const val SECTION_TODAY = "Today"
    const val SECTION_YESTERDAY = "Yesterday"
    const val SECTION_THIS_WEEK = "This week"
    const val SECTION_THIS_MONTH = "This month"
    const val SECTION_EARLIER = "Earlier"

    val SECTION_ORDER = listOf(
        SECTION_TODAY,
        SECTION_YESTERDAY,
        SECTION_THIS_WEEK,
        SECTION_THIS_MONTH,
        SECTION_EARLIER
    )

    /**
     * Determines which section bucket a timestamp belongs to.
     * @param dateAddedSeconds Unix timestamp (in seconds or millis)
     * @param nowMillis Reference current time in milliseconds
     */
    fun getSectionForTimestamp(
        dateAddedSeconds: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val songMillis = if (dateAddedSeconds > 1_000_000_000_000L) {
            dateAddedSeconds
        } else {
            dateAddedSeconds * 1000L
        }

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val startOfYesterday = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -5) // 7-day rolling window
        val startOfThisWeek = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -23) // 30-day rolling window
        val startOfThisMonth = cal.timeInMillis

        return when {
            songMillis >= startOfToday -> SECTION_TODAY
            songMillis >= startOfYesterday -> SECTION_YESTERDAY
            songMillis >= startOfThisWeek -> SECTION_THIS_WEEK
            songMillis >= startOfThisMonth -> SECTION_THIS_MONTH
            else -> SECTION_EARLIER
        }
    }

    /**
     * Filters songs according to the selected timeframe.
     */
    fun filterByTimeframe(
        songs: List<Song>,
        timeframe: RecentlyAddedTimeframe,
        nowMillis: Long = System.currentTimeMillis()
    ): List<Song> {
        val sorted = songs.sortedByDescending { it.dateAdded }
        if (timeframe.days <= 0) {
            return sorted
        }

        val cutoffMillis = nowMillis - (timeframe.days.toLong() * 24L * 60L * 60L * 1000L)
        return sorted.filter { song ->
            val songMillis = if (song.dateAdded > 1_000_000_000_000L) {
                song.dateAdded
            } else {
                song.dateAdded * 1000L
            }
            songMillis >= cutoffMillis
        }
    }

    /**
     * Groups a list of songs into sections: Today, Yesterday, This week, This month, Earlier.
     * Items in each section remain sorted newest first.
     * Only sections with at least one song are returned, in natural chronological order.
     */
    fun groupSongs(
        songs: List<Song>,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, List<Song>> {
        val sorted = songs.sortedByDescending { it.dateAdded }
        val map = linkedMapOf<String, MutableList<Song>>()
        SECTION_ORDER.forEach { map[it] = mutableListOf() }

        for (song in sorted) {
            val section = getSectionForTimestamp(song.dateAdded, nowMillis)
            map[section]?.add(song)
        }

        return map.filterValues { it.isNotEmpty() }
    }
}
